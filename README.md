<div align="center">

```
   ██████╗ ██████╗ ██████╗ ████████╗███████╗██╗  ██╗
  ██╔════╝██╔═══██╗██╔══██╗╚══██╔══╝██╔════╝╚██╗██╔╝
  ██║     ██║   ██║██████╔╝   ██║   █████╗   ╚███╔╝
  ██║     ██║   ██║██╔══██╗   ██║   ██╔══╝   ██╔██╗
  ╚██████╗╚██████╔╝██║  ██║   ██║   ███████╗██╔╝ ██╗
   ╚═════╝ ╚═════╝ ╚═╝  ╚═╝   ╚═╝   ╚══════╝╚═╝  ╚═╝
```

# Cortex — AI Architecture Decision Engine

**Multiple AI agents with opposing personalities debate your technical decisions, generate Architecture Decision Records, and produce context-aware code.**

[![Java](https://img.shields.io/badge/Java-21-orange?logo=openjdk)](https://openjdk.org/)
[![Python](https://img.shields.io/badge/Python-3.13-blue?logo=python)](https://python.org/)
[![FastAPI](https://img.shields.io/badge/FastAPI-0.115-green?logo=fastapi)](https://fastapi.tiangolo.com/)
[![Groq](https://img.shields.io/badge/Groq-LLM-purple)](https://groq.com/)

</div>

---

## What Makes Cortex Different?

Unlike Cursor, Copilot, or ChatGPT, Cortex doesn't give you a single AI opinion. It creates a **multi-agent debate** where agents with fundamentally different priorities argue, challenge each other, and reach consensus — just like a real architecture review meeting.

| Feature | Cursor/Copilot | ChatGPT | **Cortex** |
|---------|---------------|---------|------------|
| Single AI assistant | ✅ | ✅ | ❌ |
| Multiple opposing agents | ❌ | ❌ | ✅ |
| Multi-round debate | ❌ | ❌ | ✅ |
| Agents respond to each other | ❌ | ❌ | ✅ |
| Consensus voting | ❌ | ❌ | ✅ |
| Context-aware (scans your project) | Partial | ❌ | ✅ |
| ADR generation | ❌ | ❌ | ✅ |
| Code generation from ADR | ❌ | ❌ | ✅ |
| Custom agent personas | ❌ | ❌ | ✅ |
| Multi-agent code review | ❌ | ❌ | ✅ |
| Project health scoring | ❌ | ❌ | ✅ |

## Built-in Agents

| Agent | Perspective | Focus |
|-------|------------|-------|
| **Architect** | Long-term design | Patterns, SOLID, scalability, clean architecture |
| **Pragmatic** | Ship fast | YAGNI, simplicity, real-world tradeoffs |
| **Security** | Defense in depth | Attack surface, validation, auth, secrets |
| **DevOps** | Operations | CI/CD, monitoring, deployment, infra cost |

## Commands

### `cortex init <path>`
Scans your project and generates `.architect/context.json` with detected languages, frameworks, dependencies, and structure.

```bash
cortex init /path/to/my-project
```

### `cortex debate "<topic>"`
Multi-round debate with AI agents. Each round, agents read previous arguments and challenge each other. Final round: each agent votes APPROVE, REJECT, or CONDITIONAL.

```bash
# Basic debate (3 rounds, Spanish)
cortex debate "REST vs GraphQL para una app mobile"

# Context-aware debate (agents see your real code)
cortex debate --project /path/to/project "should we add Redis caching"

# Debate + generate ADR document
cortex debate --project /path --adr "microservices vs monolith"

# Custom rounds and language
cortex debate --rounds 5 --lang en "event sourcing vs CRUD"

# With custom agents
cortex debate --agents ./my-agents/ "serverless vs containers"
```

### `cortex review <file>`
Multi-agent code review. Each agent reviews the same file from their unique perspective.

```bash
cortex review --project /path src/main/java/com/example/UserService.java
```

### `cortex health`
AI-powered project health analysis with scores across 5 dimensions.

```bash
cortex health --project /path/to/project
```

### `cortex generate --from-adr <file>`
Generates implementation code based on an ADR decision, following your project's conventions.

```bash
cortex generate --project /path --from-adr ADR-001.md
```

### `cortex context`
Shows the current project context in a formatted view.

```bash
cortex context --project /path/to/project
```

## Custom Agents

Create YAML files in `.architect/agents/` (auto-detected) or any directory:

```yaml
# .architect/agents/cost-optimizer.yaml
name: "Cost Optimizer"
role: "cost"
color: "#FF6B00"
personality: |
  You obsess over cloud costs and resource efficiency.
  You challenge any decision that increases monthly spend
  without clear ROI. You know AWS/GCP/Azure pricing.
```

See `examples/agents/` for more templates (Performance, UX Advocate).

## Architecture

```
cortex/
├── src/main/java/com/cortex/cli/    ← Java 21 CLI (PicoCLI)
│   ├── CortexCLI.java               ← Main + gradient banner
│   ├── DebateCommand.java           ← Multi-round debate engine
│   ├── InitCommand.java             ← Project scanner trigger
│   ├── ReviewCommand.java           ← Multi-agent code review
│   ├── HealthCommand.java           ← Project health scorer
│   ├── GenerateCommand.java         ← Code gen from ADR
│   ├── ContextCommand.java          ← Context viewer
│   ├── Agent.java                   ← Agent model
│   ├── DebateResponse.java          ← Multi-round response
│   ├── RoundResult.java             ← Single round model
│   └── Consensus.java               ← Voting result model
│
├── ai-service/                       ← Python FastAPI
│   ├── main.py                       ← All endpoints
│   ├── scanner/                      ← Project analyzer
│   │   ├── __init__.py
│   │   └── analyzer.py              ← Language/framework detection
│   └── requirements.txt
│
├── examples/agents/                  ← Custom agent templates
│   ├── cost-optimizer.yaml
│   ├── performance.yaml
│   └── ux-advocate.yaml
│
└── pom.xml                           ← Maven config
```

## Quick Start

### Prerequisites
- Java 21+
- Python 3.11+
- [Groq API key](https://console.groq.com/) (free tier available)

### Setup

```bash
# Clone
git clone https://github.com/nacho995/cortex.git
cd cortex

# Python AI service
cd ai-service
pip install -r requirements.txt
echo "GROQ_API_KEY=your_key_here" > .env
uvicorn main:app --port 8000 &

# Java CLI (in another terminal)
cd ..
mvn -q compile exec:java -Dexec.mainClass="com.cortex.cli.CortexCLI"
```

### First Run

```bash
# 1. Scan your project
cortex init /path/to/your/project

# 2. Run a debate
cortex debate --project /path "should we use microservices"

# 3. Review code
cortex review --project /path src/main/java/App.java

# 4. Check project health
cortex health --project /path
```

## Tech Stack

| Layer | Technology | Purpose |
|-------|-----------|---------|
| CLI | Java 21, PicoCLI 4.7.6 | Command parsing, display, orchestration |
| AI Service | Python 3.13, FastAPI | Agent logic, LLM calls, project scanning |
| LLM | Groq (Llama 3.1 8B) | Fast inference for multi-agent debate |
| Serialization | Gson 2.11.0 | JSON parsing in Java |
| HTTP | Java HttpClient | Built-in, no dependencies |

## Author

**Ignacio Dalesio** — [GitHub](https://github.com/nacho995)

Built as part of a full-stack learning journey covering Java, Spring Boot, Angular, Python, and AI integration.

## License

MIT
