"""Unified LLM client supporting multiple providers."""

import httpx
import asyncio


async def call_llm(
    route: dict,
    system: str,
    user_message: str,
    temperature: float = 0.7,
    max_tokens: int = 1000,
    timeout: float = 60.0,
) -> str:
    """Call any LLM provider and return the response text."""

    provider = route["provider"]
    model = route["model"]
    url = route["url"]
    key = route["key"]

    if provider == "anthropic":
        return await _call_anthropic(url, key, model, system, user_message, temperature, max_tokens, timeout)
    elif provider == "google":
        return await _call_google(url, key, model, system, user_message, temperature, max_tokens, timeout)
    else:
        # OpenAI-compatible (Groq, OpenAI)
        return await _call_openai_compatible(url, key, model, system, user_message, temperature, max_tokens, timeout)


async def _call_openai_compatible(
    url: str,
    key: str,
    model: str,
    system: str,
    user_message: str,
    temperature: float,
    max_tokens: int,
    timeout: float,
) -> str:
    """Call OpenAI-compatible APIs (OpenAI, Groq)."""
    async with httpx.AsyncClient() as client:
        for attempt in range(3):
            try:
                response = await client.post(
                    url,
                    headers={
                        "Authorization": f"Bearer {key}",
                        "Content-Type": "application/json",
                    },
                    json={
                        "model": model,
                        "messages": [
                            {"role": "system", "content": system},
                            {"role": "user", "content": user_message},
                        ],
                        "temperature": temperature,
                        "max_tokens": max_tokens,
                    },
                    timeout=timeout,
                )
                if response.status_code == 429:
                    await asyncio.sleep((attempt + 1) * 5)
                    continue
                response.raise_for_status()
                data = response.json()
                return data["choices"][0]["message"]["content"]
            except httpx.HTTPStatusError as e:
                if e.response.status_code == 429 and attempt < 2:
                    await asyncio.sleep((attempt + 1) * 5)
                    continue
                raise
    raise Exception("Rate limited after retries")


async def _call_anthropic(
    url: str,
    key: str,
    model: str,
    system: str,
    user_message: str,
    temperature: float,
    max_tokens: int,
    timeout: float,
) -> str:
    """Call Anthropic Claude API."""
    async with httpx.AsyncClient() as client:
        for attempt in range(3):
            try:
                response = await client.post(
                    url,
                    headers={
                        "x-api-key": key,
                        "anthropic-version": "2023-06-01",
                        "Content-Type": "application/json",
                    },
                    json={
                        "model": model,
                        "max_tokens": max_tokens,
                        "system": system,
                        "messages": [
                            {"role": "user", "content": user_message},
                        ],
                        "temperature": temperature,
                    },
                    timeout=timeout,
                )
                if response.status_code == 429:
                    await asyncio.sleep((attempt + 1) * 5)
                    continue
                response.raise_for_status()
                data = response.json()
                return data["content"][0]["text"]
            except httpx.HTTPStatusError as e:
                if e.response.status_code == 429 and attempt < 2:
                    await asyncio.sleep((attempt + 1) * 5)
                    continue
                raise
    raise Exception("Rate limited after retries")


async def _call_google(
    url: str,
    key: str,
    model: str,
    system: str,
    user_message: str,
    temperature: float,
    max_tokens: int,
    timeout: float,
) -> str:
    """Call Google Gemini API."""
    api_url = url.replace("{model}", model) + f"?key={key}"

    async with httpx.AsyncClient() as client:
        for attempt in range(3):
            try:
                response = await client.post(
                    api_url,
                    headers={"Content-Type": "application/json"},
                    json={
                        "system_instruction": {"parts": [{"text": system}]},
                        "contents": [
                            {"parts": [{"text": user_message}]},
                        ],
                        "generationConfig": {
                            "temperature": temperature,
                            "maxOutputTokens": max_tokens,
                        },
                    },
                    timeout=timeout,
                )
                if response.status_code == 429:
                    await asyncio.sleep((attempt + 1) * 5)
                    continue
                response.raise_for_status()
                data = response.json()
                return data["candidates"][0]["content"]["parts"][0]["text"]
            except httpx.HTTPStatusError as e:
                if e.response.status_code == 429 and attempt < 2:
                    await asyncio.sleep((attempt + 1) * 5)
                    continue
                raise
    raise Exception("Rate limited after retries")
