"""Intelligent model routing — picks the best AI for each task."""

import os
import re

# Provider configurations
PROVIDERS = {
    "groq": {
        "url": "https://api.groq.com/openai/v1/chat/completions",
        "key_env": "GROQ_API_KEY",
    },
    "anthropic": {
        "url": "https://api.anthropic.com/v1/messages",
        "key_env": "ANTHROPIC_API_KEY",
    },
    "google": {
        "url": "https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent",
        "key_env": "GOOGLE_API_KEY",
    },
    "openai": {
        "url": "https://api.openai.com/v1/chat/completions",
        "key_env": "OPENAI_API_KEY",
    },
}

# Task → default model routing
TASK_ROUTING = {
    "debate": {"provider": "groq", "model": "llama-3.3-70b-versatile"},
    "review": {"provider": "groq", "model": "llama-3.1-8b-instant"},
    "health": {"provider": "groq", "model": "llama-3.1-8b-instant"},
    "diagram": {"provider": "groq", "model": "llama-3.1-8b-instant"},
    "create_simple": {"provider": "anthropic", "model": "claude-sonnet-4-20250514"},
    "create_complex": {"provider": "anthropic", "model": "claude-opus-4-20250514"},
    "add": {"provider": "anthropic", "model": "claude-sonnet-4-20250514"},
    "fix": {"provider": "anthropic", "model": "claude-sonnet-4-20250514"},
    "generate": {"provider": "anthropic", "model": "claude-sonnet-4-20250514"},
    "adr": {"provider": "groq", "model": "llama-3.1-8b-instant"},
}

# Keywords that indicate a complex project
COMPLEX_KEYWORDS = [
    "microservic", "full stack", "fullstack", "full-stack", "mern", "mean",
    "next.js", "nuxt", "monorepo", "kubernetes", "k8s", "docker compose",
    "graphql", "websocket", "event sourcing", "cqrs", "saga pattern",
    "distributed", "scalab", "enterprise", "production-ready",
    "authentication", "authorization", "oauth", "stripe", "payment",
    "real-time", "realtime", "microservice",
]


def detect_complexity(prompt: str) -> str:
    """Detect if a create prompt is simple or complex."""
    prompt_lower = prompt.lower()
    matches = sum(1 for kw in COMPLEX_KEYWORDS if kw in prompt_lower)
    if matches >= 2:
        return "create_complex"
    return "create_simple"


def get_route(task: str, prompt: str = "") -> dict:
    """Get the best provider/model for a task."""
    if task == "create":
        task = detect_complexity(prompt)

    route = TASK_ROUTING.get(task, TASK_ROUTING["review"])
    provider = route["provider"]
    model = route["model"]

    # Check if the provider's API key is available
    provider_info = PROVIDERS.get(provider, {})
    key = os.getenv(provider_info.get("key_env", ""))

    # Fallback chain: if preferred provider not available, fall back
    if not key:
        # Try OpenAI
        openai_key = os.getenv("OPENAI_API_KEY")
        if openai_key:
            return {
                "provider": "openai",
                "model": "gpt-4o-mini" if task in ("review", "health", "diagram", "adr") else "gpt-4o",
                "url": PROVIDERS["openai"]["url"],
                "key": openai_key,
            }
        # Fall back to Groq (always available)
        groq_key = os.getenv("GROQ_API_KEY")
        return {
            "provider": "groq",
            "model": "llama-3.1-8b-instant",
            "url": PROVIDERS["groq"]["url"],
            "key": groq_key or "",
        }

    return {
        "provider": provider,
        "model": model,
        "url": provider_info["url"],
        "key": key,
    }
