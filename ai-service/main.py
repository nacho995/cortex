import asyncio
import os
import re
import yaml
import httpx
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel
from dotenv import load_dotenv
from scanner import scan_project
from auth import register_user, validate_token, check_rate_limit, track_usage, get_usage_stats, upgrade_plan, PLANS
from router import get_route, detect_complexity
from llm_client import call_llm

load_dotenv()

app = FastAPI(title="Cortex AI Service")

GROQ_API_KEY = os.getenv("GROQ_API_KEY")
GROQ_URL = "https://api.groq.com/openai/v1/chat/completions"
MODEL = "llama-3.1-8b-instant"

OPENAI_API_KEY = os.getenv("OPENAI_API_KEY")
OPENAI_URL = "https://api.openai.com/v1/chat/completions"


def _load_custom_agents(agents_dir: str | None) -> list[dict]:
    """Load custom agent definitions from YAML files in .architect/agents/."""
    if not agents_dir:
        return []

    from pathlib import Path
    agents_path = Path(agents_dir)
    if not agents_path.is_dir():
        return []

    custom = []
    for yml_file in sorted(agents_path.glob("*.yaml")) + sorted(agents_path.glob("*.yml")):
        try:
            data = yaml.safe_load(yml_file.read_text())
            if data and isinstance(data, dict) and "name" in data and "personality" in data:
                custom.append({
                    "name": data["name"],
                    "role": data.get("role", data["name"].lower().replace(" ", "-")),
                    "system": (
                        data["personality"] + " "
                        "Write in normal sentence case. NEVER use all caps. "
                        "Keep your response under 150 words."
                    ),
                    "color": data.get("color", "#FFFFFF"),
                })
        except Exception:
            continue

    return custom


AGENTS = [
    {
        "name": "Architect",
        "role": "architect",
        "system": (
            "You are the Architect agent. You think in terms of design patterns, "
            "scalability, separation of concerns, and long-term maintainability. "
            "You favor clean architecture, SOLID principles, and well-defined abstractions. "
            "You push back on shortcuts that create coupling or technical debt. "
            "Be opinionated and specific. Give concrete pattern recommendations. "
            "Write in normal sentence case. NEVER use all caps. "
            "Keep your response under 150 words."
        ),
    },
    {
        "name": "Pragmatic",
        "role": "pragmatic",
        "system": (
            "You are the Pragmatic agent. You prioritize shipping working software fast. "
            "You believe over-engineering kills more projects than technical debt. "
            "You favor the simplest solution that works, MVPs, iterating based on real data, "
            "and refactoring only when proven necessary. You challenge unnecessary abstractions. "
            "You are skeptical of 'future-proofing' and prefer YAGNI. "
            "Be direct, challenge over-engineering, give real-world tradeoffs. "
            "Write in normal sentence case. NEVER use all caps. "
            "Keep your response under 150 words."
        ),
    },
    {
        "name": "Security",
        "role": "security",
        "system": (
            "You are the Security agent. You evaluate every decision through the lens of "
            "attack surface, threat modeling, and defense in depth. You care about input validation, "
            "authentication, authorization, secrets management, and data protection. "
            "You identify risks others overlook and propose concrete mitigations. "
            "You are not paranoid but methodical. You demand security is built in, not bolted on. "
            "Be specific about threats and countermeasures. "
            "Write in normal sentence case. NEVER use all caps. "
            "Keep your response under 150 words."
        ),
    },
    {
        "name": "DevOps",
        "role": "devops",
        "system": (
            "You are the DevOps agent. You think about deployment, CI/CD pipelines, "
            "observability, monitoring, infrastructure cost, and operational complexity. "
            "You care about how things run in production, not just how they look in dev. "
            "You push for containerization, health checks, logging, and automation. "
            "You challenge decisions that are easy to build but hard to operate. "
            "Be practical about infrastructure tradeoffs and operational cost. "
            "Write in normal sentence case. NEVER use all caps. "
            "Keep your response under 150 words."
        ),
    },
]


LANG_INSTRUCTION = {
    "en": "",
    "es": "You MUST respond entirely in Spanish.",
    "fr": "You MUST respond entirely in French.",
    "pt": "You MUST respond entirely in Portuguese.",
    "de": "You MUST respond entirely in German.",
    "it": "You MUST respond entirely in Italian.",
    "ja": "You MUST respond entirely in Japanese.",
    "zh": "You MUST respond entirely in Chinese.",
    "ko": "You MUST respond entirely in Korean.",
}


class DebateRequest(BaseModel):
    topic: str
    lang: str = "en"
    context: dict | None = None
    rounds: int = 2
    agents_dir: str | None = None
    token: str | None = None


def _build_context_block(context: dict | None) -> str:
    """Format project context into a readable block for the system prompt."""
    if not context:
        return ""

    parts = ["\n\n--- PROJECT CONTEXT (base your answer on this real project) ---"]
    if context.get("project_name"):
        parts.append(f"Project: {context['project_name']}")
    if context.get("languages"):
        parts.append(f"Languages: {', '.join(context['languages'])}")
    if context.get("frameworks"):
        parts.append(f"Frameworks: {', '.join(context['frameworks'])}")
    if context.get("build_tools"):
        parts.append(f"Build tools: {', '.join(context['build_tools'])}")
    if context.get("infrastructure"):
        parts.append(f"Infrastructure: {', '.join(context['infrastructure'])}")
    if context.get("dependencies"):
        for tool, deps in context["dependencies"].items():
            parts.append(f"Dependencies ({tool}): {', '.join(deps[:15])}")
    if context.get("entry_points"):
        parts.append(f"Entry points: {', '.join(context['entry_points'])}")
    if context.get("structure"):
        parts.append("Structure:\n" + "\n".join(context["structure"][:30]))
    parts.append("--- END PROJECT CONTEXT ---")

    return "\n".join(parts)


# ============== AUTH & BILLING ENDPOINTS ==============

class RegisterRequest(BaseModel):
    email: str


@app.post("/register")
def register(req: RegisterRequest):
    """Register a new user and return their API token."""
    result = register_user(req.email)
    return result


class UsageRequest(BaseModel):
    token: str


@app.post("/usage")
def usage(req: UsageRequest):
    """Get usage stats for a user by token."""
    return get_usage_stats(req.token)


class UpgradeRequest(BaseModel):
    token: str
    plan: str


@app.post("/upgrade")
def upgrade(req: UpgradeRequest):
    """Upgrade user plan after Stripe payment."""
    return upgrade_plan(req.token, req.plan)


def _resolve_user_and_model(token: str | None, endpoint: str) -> tuple[str, str, str]:
    """Validate token, enforce rate limit, track usage. Returns (url, key, model)."""
    if not token:
        # Anonymous/free: use Groq defaults
        groq_key = GROQ_API_KEY or ""
        return GROQ_URL, groq_key, MODEL

    user = validate_token(token)
    if not user:
        raise HTTPException(status_code=401, detail="Invalid token. Run 'cortex register <email>' to get one.")

    # Check rate limit
    rate = check_rate_limit(user["id"], user["plan"])
    if not rate["allowed"]:
        plan = user["plan"]
        if plan == "free":
            raise HTTPException(
                status_code=429,
                detail=f"Free plan limit reached ({rate['limit']}/day). Run 'cortex upgrade' for Pro.",
            )
        else:
            raise HTTPException(status_code=429, detail=f"Daily limit reached ({rate['limit']}/day).")

    # Track usage
    track_usage(user["id"], endpoint)

    # Resolve model by plan
    plan_info = PLANS.get(user["plan"], PLANS["free"])
    if plan_info["provider"] == "openai":
        openai_key = os.getenv("OPENAI_API_KEY")
        if openai_key:
            return "https://api.openai.com/v1/chat/completions", openai_key, plan_info["model"]

    groq_key = GROQ_API_KEY or ""
    return GROQ_URL, groq_key, plan_info["model"]


# ============== END AUTH & BILLING ==============


async def call_agent(
    client: httpx.AsyncClient,
    agent: dict,
    messages: list[dict],
    lang: str,
    context: dict | None = None,
    task: str = "debate",
) -> str:
    """Call the best LLM for a task using intelligent routing."""
    lang_extra = LANG_INSTRUCTION.get(lang, f"You MUST respond entirely in the language with code '{lang}'.")
    context_block = _build_context_block(context)
    system_prompt = agent["system"] + (" " + lang_extra if lang_extra else "") + context_block

    user_message = messages[0]["content"] if messages else ""

    route = get_route(task)
    return await call_llm(route, system_prompt, user_message, temperature=0.8, max_tokens=300)


@app.post("/debate")
async def debate(req: DebateRequest):
    _resolve_user_and_model(req.token, "debate")

    num_rounds = max(1, min(req.rounds, 5))  # clamp between 1-5
    all_rounds = []

    # Use custom agents if provided, otherwise default
    active_agents = AGENTS
    custom = _load_custom_agents(req.agents_dir)
    if custom:
        active_agents = AGENTS + custom

    async with httpx.AsyncClient() as client:
        debate_history = ""

        for round_num in range(1, num_rounds + 1):
            round_results = []

            if round_num == 1:
                round_instruction = f"Debate this topic: {req.topic}"
            elif round_num < num_rounds:
                round_instruction = (
                    f"This is round {round_num} of the debate on: {req.topic}\n\n"
                    f"Here are the arguments from the previous round(s):\n{debate_history}\n\n"
                    "Now respond to the other agents' arguments. Challenge weak points, "
                    "acknowledge good arguments, and refine your position. "
                    "Address other agents by name when responding to them."
                )
            else:
                round_instruction = (
                    f"This is the FINAL round of the debate on: {req.topic}\n\n"
                    f"Here is the full debate so far:\n{debate_history}\n\n"
                    "Give your final verdict. You MUST start your response with exactly one of these words: "
                    "APPROVE, REJECT, or CONDITIONAL. Then explain your final position in 2-3 sentences. "
                    "Try to find common ground with other agents where possible."
                )

            for agent in active_agents:
                try:
                    messages = [{"role": "user", "content": round_instruction}]
                    argument = await call_agent(client, agent, messages, req.lang, req.context)
                    round_results.append({
                        "name": agent["name"],
                        "role": agent["role"],
                        "argument": argument,
                    })
                except Exception as e:
                    round_results.append({
                        "name": agent["name"],
                        "role": agent["role"],
                        "argument": f"Agent failed: {str(e)}",
                    })
                await asyncio.sleep(2.0)  # Avoid Groq rate limits

            # Build debate history for next round
            round_text = f"\n--- Round {round_num} ---\n"
            for r in round_results:
                round_text += f"\n[{r['name']}]: {r['argument']}\n"
            debate_history += round_text

            all_rounds.append({
                "round": round_num,
                "agents": round_results,
            })

        # Calculate consensus from final round
        final_round = all_rounds[-1]["agents"] if all_rounds else []
        votes = {"approve": 0, "reject": 0, "conditional": 0}

        approve_words = {"approve", "apruebo", "aprobado", "approuvé", "aprovado", "genehmigt", "approvato"}
        reject_words = {"reject", "rechazo", "rechazado", "rejeté", "rejeitado", "abgelehnt", "rifiutato"}
        conditional_words = {"conditional", "condicional", "conditionnel", "bedingt", "condizionale"}

        for agent_result in final_round:
            arg_lower = agent_result["argument"].lower().strip()
            first_word = arg_lower.split()[0].rstrip(".:,;") if arg_lower else ""

            if first_word in approve_words or arg_lower.startswith("approve"):
                votes["approve"] += 1
                agent_result["vote"] = "approve"
            elif first_word in reject_words or arg_lower.startswith("reject"):
                votes["reject"] += 1
                agent_result["vote"] = "reject"
            elif first_word in conditional_words or arg_lower.startswith("conditional"):
                votes["conditional"] += 1
                agent_result["vote"] = "conditional"
            else:
                # Try to find vote keyword anywhere in first 50 chars
                first_50 = arg_lower[:50]
                if any(w in first_50 for w in approve_words):
                    votes["approve"] += 1
                    agent_result["vote"] = "approve"
                elif any(w in first_50 for w in reject_words):
                    votes["reject"] += 1
                    agent_result["vote"] = "reject"
                elif any(w in first_50 for w in conditional_words):
                    votes["conditional"] += 1
                    agent_result["vote"] = "conditional"
                else:
                    agent_result["vote"] = "unknown"

    total = len(final_round)
    consensus = {
        "votes": votes,
        "total": total,
        "level": "unanimous" if votes["approve"] == total else
                 "strong" if votes["approve"] >= total * 0.75 else
                 "majority" if votes["approve"] + votes["conditional"] > total * 0.5 else
                 "divided",
    }

    return {
        "topic": req.topic,
        "rounds": all_rounds,
        "consensus": consensus,
    }


class InitRequest(BaseModel):
    path: str


@app.post("/init")
def init_project(req: InitRequest):
    context = scan_project(req.path)
    if "error" in context:
        raise HTTPException(status_code=400, detail=context["error"])
    return context


class AdrAgent(BaseModel):
    name: str
    role: str
    stance: str
    argument: str


class AdrRequest(BaseModel):
    topic: str
    agents: list[AdrAgent]
    lang: str = "en"
    token: str | None = None


ADR_SYSTEM = (
    "You are a technical writer that synthesizes architecture debates into "
    "Architecture Decision Records (ADR). You receive a topic and the arguments "
    "from multiple agents with different perspectives. "
    "Generate a well-structured ADR in markdown with these exact sections: "
    "# ADR-{number}: {title}, ## Status (always 'Proposed'), "
    "## Context (why this decision came up), "
    "## Positions (summarize each agent's argument with their name as bold label), "
    "## Decision (your synthesized recommendation based on all perspectives), "
    "## Consequences (what changes if this is accepted, both positive and negative). "
    "Write in normal sentence case. NEVER use all caps. "
    "Be concise but thorough. Use the ADR number provided."
)


@app.post("/generate-adr")
async def generate_adr(req: AdrRequest):
    _resolve_user_and_model(req.token, "generate-adr")

    lang_extra = LANG_INSTRUCTION.get(req.lang, f"You MUST respond entirely in the language with code '{req.lang}'.")
    system = ADR_SYSTEM + (" " + lang_extra if lang_extra else "")

    agents_summary = "\n\n".join(
        f"**{a.name}** ({a.role}):\n{a.argument}" for a in req.agents
    )
    user_msg = f"Topic: {req.topic}\n\nAgent arguments:\n{agents_summary}\n\nGenerate ADR number 1."

    route = get_route("adr")
    adr_content = await call_llm(route, system, user_msg, temperature=0.4, max_tokens=1000)

    return {"adr": adr_content}


class GenerateRequest(BaseModel):
    adr: str
    context: dict | None = None
    lang: str = "en"
    token: str | None = None


GENERATE_SYSTEM = (
    "You are an expert code generator. You receive an Architecture Decision Record (ADR) "
    "and a project context (languages, frameworks, structure, dependencies). "
    "Generate the implementation code needed to fulfill the ADR decision. "
    "RULES: "
    "- Follow the project's existing conventions, patterns, and naming style. "
    "- Use the same frameworks and libraries already in the project. "
    "- Output each file with its relative path as a markdown header: ## path/to/File.java "
    "- Include the full file content in a code block with the correct language tag. "
    "- Only generate files that are strictly necessary. "
    "- Add brief comments explaining key decisions. "
    "Write in normal sentence case. NEVER use all caps."
)


@app.post("/generate")
async def generate_code(req: GenerateRequest):
    _resolve_user_and_model(req.token, "generate")

    lang_extra = LANG_INSTRUCTION.get(req.lang, f"You MUST respond entirely in the language with code '{req.lang}'.")
    context_block = _build_context_block(req.context)
    system = GENERATE_SYSTEM + (" " + lang_extra if lang_extra else "") + context_block

    user_msg = f"Implement the following ADR decision. Generate all necessary code files.\n\n{req.adr}"

    route = get_route("generate")
    code_content = await call_llm(route, system, user_msg, temperature=0.3, max_tokens=2000, timeout=45.0)

    return {"code": code_content}


class ReviewRequest(BaseModel):
    file_path: str
    file_content: str
    context: dict | None = None
    lang: str = "en"
    agents_dir: str | None = None
    token: str | None = None


REVIEW_AGENTS = [
    {
        "name": "Architect",
        "role": "architect",
        "system": (
            "You are reviewing code as the Architect. Focus on: "
            "design patterns, SOLID violations, coupling, cohesion, naming conventions, "
            "and architectural consistency. Identify specific lines when possible. "
            "Give actionable suggestions, not vague advice. "
            "Write in normal sentence case. NEVER use all caps. "
            "Keep your review under 200 words."
        ),
    },
    {
        "name": "Pragmatic",
        "role": "pragmatic",
        "system": (
            "You are reviewing code as the Pragmatic developer. Focus on: "
            "code simplicity, readability, unnecessary complexity, dead code, "
            "and whether the code could be simpler. Challenge over-abstraction. "
            "If the code is fine, say so - don't invent problems. "
            "Write in normal sentence case. NEVER use all caps. "
            "Keep your review under 200 words."
        ),
    },
    {
        "name": "Security",
        "role": "security",
        "system": (
            "You are reviewing code as the Security expert. Focus on: "
            "SQL injection, XSS, CSRF, insecure deserialization, hardcoded secrets, "
            "missing input validation, improper error handling that leaks info, "
            "authentication/authorization flaws. Be specific about the vulnerability "
            "and the fix. Write in normal sentence case. NEVER use all caps. "
            "Keep your review under 200 words."
        ),
    },
    {
        "name": "DevOps",
        "role": "devops",
        "system": (
            "You are reviewing code as the DevOps engineer. Focus on: "
            "logging quality, error handling for production, configuration management, "
            "health check readiness, resource cleanup, connection pooling, "
            "and whether this code will be easy to debug in production. "
            "Write in normal sentence case. NEVER use all caps. "
            "Keep your review under 200 words."
        ),
    },
]


@app.post("/review")
async def review_code(req: ReviewRequest):
    _resolve_user_and_model(req.token, "review")

    lang_extra = LANG_INSTRUCTION.get(req.lang, f"You MUST respond entirely in the language with code '{req.lang}'.")
    context_block = _build_context_block(req.context)
    user_msg = f"Review this file: {req.file_path}\n\n```\n{req.file_content}\n```"

    # Use custom agents if provided, otherwise default
    active_review_agents = REVIEW_AGENTS
    custom = _load_custom_agents(req.agents_dir)
    if custom:
        active_review_agents = REVIEW_AGENTS + custom

    reviews = []
    for agent in active_review_agents:
        try:
            system = agent["system"] + (" " + lang_extra if lang_extra else "") + context_block
            route = get_route("review")
            review_text = await call_llm(route, system, user_msg, temperature=0.4, max_tokens=400)
            reviews.append({
                "name": agent["name"],
                "role": agent["role"],
                "review": review_text,
            })
        except Exception as e:
            reviews.append({
                "name": agent["name"],
                "role": agent["role"],
                "review": f"Review failed: {str(e)}",
            })
        await asyncio.sleep(2.0)  # Avoid rate limits

    return {"file": req.file_path, "reviews": reviews}


class HealthRequest(BaseModel):
    context: dict
    lang: str = "en"
    token: str | None = None


HEALTH_SYSTEM = (
    "You are a project health analyzer. You receive a project's context (languages, "
    "frameworks, dependencies, structure) and evaluate its health across these dimensions: "
    "security, maintainability, architecture, devops, testing. "
    "You MUST respond with ONLY valid JSON, no markdown, no explanation, just the JSON object. "
    "Format: "
    '{"scores": {"Security": {"score": 0-100, "detail": "brief reason"}, '
    '"Maintainability": {"score": 0-100, "detail": "brief reason"}, '
    '"Architecture": {"score": 0-100, "detail": "brief reason"}, '
    '"DevOps": {"score": 0-100, "detail": "brief reason"}, '
    '"Testing": {"score": 0-100, "detail": "brief reason"}}, '
    '"overall": 0-100, '
    '"recommendations": ["recommendation 1", "recommendation 2", "recommendation 3"]} '
    "Base scores on real evidence from the project context. Be fair but critical."
)


@app.post("/health-check")
async def health_check(req: HealthRequest):
    _resolve_user_and_model(req.token, "health-check")

    import json as json_module

    lang_extra = LANG_INSTRUCTION.get(req.lang, f"You MUST respond entirely in the language with code '{req.lang}'.")
    context_block = _build_context_block(req.context)
    system = HEALTH_SYSTEM + (" " + lang_extra if lang_extra else "")
    user_msg = f"Analyze this project's health:\n{context_block}"

    route = get_route("health")
    content = await call_llm(route, system, user_msg, temperature=0.3, max_tokens=800)

    # Try to parse JSON, handle markdown wrapping
    content = content.strip()
    if content.startswith("```"):
        content = content.split("\n", 1)[1] if "\n" in content else content
        content = content.rsplit("```", 1)[0]
        content = content.strip()

    try:
        result = json_module.loads(content)
    except json_module.JSONDecodeError:
        result = {
            "scores": {},
            "overall": 0,
            "recommendations": ["Could not parse health analysis. Try again."],
            "raw": content,
        }

    return result


class CreateRequest(BaseModel):
    prompt: str
    context: dict | None = None
    lang: str = "es"
    debate_context: dict | None = None
    provider: str = "groq"  # "groq" or "openai"
    model: str | None = None
    api_key: str | None = None
    token: str | None = None


CREATE_SYSTEM = (
    "You are an expert software engineer and code generator. "
    "The user will describe what they want to build. Generate complete, working code. "
    "RULES: "
    "- Generate ALL files needed for a working project. "
    "- For EACH file, use this EXACT format (no exceptions): "
    "FILE: path/to/FileName.java "
    "followed by a fenced code block with the correct language tag. "
    "- Example format: "
    "FILE: src/main/java/com/app/Main.java "
    "```java "
    "package com.app; "
    "public class Main {} "
    "``` "
    "- Include configuration files (pom.xml, package.json, requirements.txt, etc). "
    "- Make the code production-ready: proper error handling, validation, clean structure. "
    "- Every file MUST start with FILE: followed by the relative path. "
    "Write in normal sentence case. NEVER use all caps."
)


def _parse_files_from_response(content: str) -> list[dict]:
    """Parse file paths and code blocks from AI response. Handles multiple formats."""
    files = []
    lines = content.split("\n")
    current_path = None
    current_code = []
    in_code_block = False

    # Patterns that indicate a file path
    file_patterns = [
        re.compile(r"^FILE:\s*(.+)$", re.IGNORECASE),
        re.compile(r"^#{1,4}\s+`?([^\s`]+\.[a-zA-Z]+)`?\s*$"),
        re.compile(r"^#{1,4}\s+`?([^\s`]+/[^\s`]+)`?\s*$"),
        re.compile(r"^\*\*([^\s*]+\.[a-zA-Z]+)\*\*\s*$"),
        re.compile(r"^\*\*([^\s*]+/[^\s*]+)\*\*\s*$"),
        re.compile(r"^`([^\s`]+\.[a-zA-Z]+)`\s*$"),
        re.compile(r"^`([^\s`]+/[^\s`]+)`\s*$"),
    ]

    def extract_path(line: str) -> str | None:
        stripped = line.strip()
        for pattern in file_patterns:
            match = pattern.match(stripped)
            if match:
                path = match.group(1).strip()
                # Filter out non-path things
                if any(path.endswith(ext) for ext in [
                    ".java", ".py", ".js", ".ts", ".jsx", ".tsx", ".html", ".css",
                    ".json", ".xml", ".yml", ".yaml", ".toml", ".md", ".txt",
                    ".properties", ".cfg", ".conf", ".sh", ".bat", ".sql",
                    ".env", ".gitignore", ".dockerignore",
                    "Dockerfile", "Makefile", "Procfile",
                ]) or "/" in path:
                    return path
        return None

    for line in lines:
        stripped = line.strip()

        if stripped.startswith("```") and not in_code_block:
            in_code_block = True
            continue
        elif stripped == "```" and in_code_block:
            in_code_block = False
            continue
        elif in_code_block:
            current_code.append(line)
            continue

        # Try to detect a file path
        path = extract_path(stripped)
        if path:
            # Save previous file
            if current_path and current_code:
                files.append({
                    "path": current_path,
                    "content": "\n".join(current_code).strip(),
                })
            current_path = path
            current_code = []

    # Last file
    if current_path and current_code:
        files.append({
            "path": current_path,
            "content": "\n".join(current_code).strip(),
        })

    return files


@app.post("/create")
async def create_code(req: CreateRequest):
    if req.token:
        _resolve_user_and_model(req.token, "create")

    lang_extra = LANG_INSTRUCTION.get(req.lang, f"You MUST respond entirely in the language with code '{req.lang}'.")
    context_block = _build_context_block(req.context)
    system = CREATE_SYSTEM + (" " + lang_extra if lang_extra else "") + context_block

    prompt_lower = req.prompt.lower()
    is_fullstack = any(kw in prompt_lower for kw in [
        "mern", "mean", "full stack", "fullstack", "full-stack",
        "frontend y backend", "front y back", "react y node",
        "react y express", "angular y spring", "vue y express",
        "next.js", "nuxt",
    ])

    debate_info = ""
    if req.debate_context:
        rounds = req.debate_context.get("rounds", [])
        if rounds:
            debate_info = "\n\nPrevious architecture debate conclusions:\n"
            last_round = rounds[-1] if rounds else {}
            for agent in last_round.get("agents", []):
                debate_info += f"- {agent.get('name', 'Agent')}: {agent.get('argument', '')}\n"
            consensus = req.debate_context.get("consensus", {})
            if consensus:
                debate_info += f"\nConsensus: {consensus.get('level', 'unknown')} ({consensus.get('votes', {})})\n"
            debate_info += "\nUse these conclusions to guide your implementation.\n"

    all_code = ""

    # Intelligent routing: detect complexity from prompt
    route = get_route("create", req.prompt)

    try:
        if is_fullstack:
            # Split into two calls: backend first, then frontend
            for part, instruction in [
                ("BACKEND", f"Build ONLY the BACKEND for: {req.prompt}{debate_info}\nGenerate all server-side files: routes, controllers, models, config, package.json/pom.xml, etc."),
                ("FRONTEND", f"Build ONLY the FRONTEND for: {req.prompt}{debate_info}\nGenerate all client-side files inside a 'frontend/' directory: components, pages, styles, package.json, index.html, etc. All frontend paths must start with frontend/"),
            ]:
                part_code = await call_llm(route, system, instruction, temperature=0.3, max_tokens=4096)
                all_code += f"\n\n# === {part} ===\n\n"
                all_code += part_code
                await asyncio.sleep(3)  # Delay between backend and frontend calls
        else:
            # Single call for simpler projects
            all_code = await call_llm(
                route, system, f"Build this: {req.prompt}{debate_info}",
                temperature=0.3, max_tokens=4096,
            )
    except Exception as e:
        raise HTTPException(status_code=502, detail=f"LLM call failed: {str(e)}")

    parsed_files = _parse_files_from_response(all_code)
    return {"code": all_code, "files": parsed_files}


class AddRequest(BaseModel):
    instruction: str
    project_path: str
    files_context: list[dict] | None = None
    lang: str = "es"
    token: str | None = None
    provider: str = "groq"
    api_key: str | None = None
    model: str | None = None


ADD_SYSTEM = (
    "You are an expert software engineer modifying an existing project. "
    "The user will describe what to add or change. You receive the current project files as context. "
    "RULES: "
    "- ONLY output files that need to be CREATED or MODIFIED. "
    "- For EACH file, use this EXACT format: "
    "FILE: path/to/FileName.ext "
    "followed by a fenced code block with the correct language tag. "
    "- Include the FULL file content (not just the changed lines). "
    "- Keep existing functionality intact unless told otherwise. "
    "- Follow the project's existing patterns, naming conventions, and structure. "
    "Write in normal sentence case. NEVER use all caps."
)


@app.post("/add")
async def add_to_project(req: AddRequest):
    if req.token:
        user = validate_token(req.token)
        if user:
            rate = check_rate_limit(user["id"], user["plan"])
            if not rate["allowed"]:
                raise HTTPException(status_code=429, detail="Daily limit reached. Run 'cortex upgrade'.")
            track_usage(user["id"], "add")

    lang_extra = LANG_INSTRUCTION.get(req.lang, f"You MUST respond entirely in the language with code '{req.lang}'.")
    system = ADD_SYSTEM + (" " + lang_extra if lang_extra else "")

    # Build context from existing files
    files_context = ""
    if req.files_context:
        files_context = "\n\nCurrent project files:\n"
        for f in req.files_context[:20]:  # Limit to 20 files
            files_context += f"\n--- {f.get('path', 'unknown')} ---\n{f.get('content', '')[:2000]}\n"

    user_msg = f"Modify this project: {req.instruction}{files_context}"

    route = get_route("add")
    try:
        code_content = await call_llm(route, system, user_msg, temperature=0.3, max_tokens=4096)
    except Exception as e:
        raise HTTPException(status_code=502, detail=f"LLM call failed: {str(e)}")

    parsed_files = _parse_files_from_response(code_content)
    return {"code": code_content, "files": parsed_files}


class DiagramRequest(BaseModel):
    context: dict
    lang: str = "es"
    token: str | None = None


DIAGRAM_SYSTEM = (
    "You are an expert at creating ASCII architecture diagrams. "
    "You receive a project's context (languages, frameworks, dependencies, structure). "
    "Generate a clear ASCII diagram showing: "
    "- Main components/layers and how they connect "
    "- Databases, external services, APIs "
    "- Data flow direction with arrows (-->, <--) "
    "- Use box drawing characters: ┌ ┐ └ ┘ │ ─ ┬ ┴ ├ ┤ "
    "- Keep it under 30 lines wide and 20 lines tall "
    "- Add a brief legend if needed "
    "ONLY output the diagram, no explanations before or after. "
    "Write labels in normal sentence case."
)


@app.post("/diagram")
async def generate_diagram(req: DiagramRequest):
    if req.token:
        user = validate_token(req.token)
        if user:
            rate = check_rate_limit(user["id"], user["plan"])
            if not rate["allowed"]:
                raise HTTPException(status_code=429, detail="Daily limit reached.")
            track_usage(user["id"], "diagram")

    lang_extra = LANG_INSTRUCTION.get(req.lang, f"You MUST respond entirely in the language with code '{req.lang}'.")
    context_block = _build_context_block(req.context)
    system = DIAGRAM_SYSTEM + (" " + lang_extra if lang_extra else "")

    user_msg = f"Generate an ASCII architecture diagram for this project:{context_block}"

    route = get_route("diagram")
    diagram = await call_llm(route, system, user_msg, temperature=0.3, max_tokens=1000)
    return {"diagram": diagram}
