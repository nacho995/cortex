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
                if response.status_code != 200:
                    error_body = response.text
                    print(f"[ANTHROPIC ERROR] Status: {response.status_code}, Body: {error_body[:500]}")
                response.raise_for_status()
                data = response.json()
                return data["content"][0]["text"]
            except httpx.HTTPStatusError as e:
                if e.response.status_code == 429 and attempt < 2:
                    await asyncio.sleep((attempt + 1) * 5)
                    continue
                raise
    raise Exception("Rate limited after retries")


async def stream_llm(
    route: dict,
    system: str,
    user_message: str,
    temperature: float = 0.7,
    max_tokens: int = 1000,
    timeout: float = 60.0,
):
    """Stream tokens from any LLM provider. Yields text chunks."""
    provider = route["provider"]
    model = route["model"]
    url = route["url"]
    key = route["key"]

    if provider == "anthropic":
        async for chunk in _stream_anthropic(url, key, model, system, user_message, temperature, max_tokens, timeout):
            yield chunk
    elif provider == "google":
        # Google doesn't have easy streaming, yield full response
        try:
            result = await _call_google(url, key, model, system, user_message, temperature, max_tokens, timeout)
            yield result
        except Exception:
            # Fallback to Groq if Google fails
            import os
            groq_key = os.getenv("GROQ_API_KEY", "")
            async for chunk in _stream_openai_compatible(
                "https://api.groq.com/openai/v1/chat/completions",
                groq_key, "llama-3.3-70b-versatile",
                system, user_message, temperature, max_tokens, timeout
            ):
                yield chunk
    else:
        async for chunk in _stream_openai_compatible(url, key, model, system, user_message, temperature, max_tokens, timeout):
            yield chunk


async def _stream_openai_compatible(url, key, model, system, user_message, temperature, max_tokens, timeout):
    """Stream from OpenAI-compatible APIs (OpenAI, Groq)."""
    import json as json_mod
    async with httpx.AsyncClient() as client:
        async with client.stream(
            "POST",
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
                "stream": True,
            },
            timeout=timeout,
        ) as response:
            response.raise_for_status()
            async for line in response.aiter_lines():
                if line.startswith("data: "):
                    data = line[6:]
                    if data.strip() == "[DONE]":
                        break
                    try:
                        chunk = json_mod.loads(data)
                        delta = chunk.get("choices", [{}])[0].get("delta", {})
                        content = delta.get("content", "")
                        if content:
                            yield content
                    except (json_mod.JSONDecodeError, IndexError, KeyError):
                        continue


async def _stream_anthropic(url, key, model, system, user_message, temperature, max_tokens, timeout):
    """Stream from Anthropic Claude API."""
    import json as json_mod
    async with httpx.AsyncClient() as client:
        async with client.stream(
            "POST",
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
                "stream": True,
            },
            timeout=timeout,
        ) as response:
            if response.status_code != 200:
                error_body = await response.aread()
                print(f"[ANTHROPIC STREAM ERROR] Status: {response.status_code}, Body: {error_body.decode()[:500]}")
            response.raise_for_status()
            async for line in response.aiter_lines():
                if line.startswith("data: "):
                    try:
                        data = json_mod.loads(line[6:])
                        if data.get("type") == "content_block_delta":
                            text = data.get("delta", {}).get("text", "")
                            if text:
                                yield text
                    except (json_mod.JSONDecodeError, KeyError):
                        continue


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
