import os
import sqlite3
import uuid
import hashlib
from datetime import datetime, date
from pathlib import Path

DB_PATH = os.getenv("CORTEX_DB_PATH", "/data/cortex.db")

# Stripe payment links
STRIPE_LINKS = {
    "pro": "https://buy.stripe.com/5kQ6oI5uEcHW4hZerC0VO00",
    "enterprise": "https://buy.stripe.com/7sYeVee1a23i3dVerC0VO01",
}

# Plan limits
PLANS = {
    "free": {
        "daily_limit": 10,
        "model": "llama-3.1-8b-instant",
        "provider": "groq",
    },
    "pro": {
        "daily_limit": 200,
        "model": "gpt-4o-mini",
        "provider": "openai",
    },
    "enterprise": {
        "daily_limit": 999999,
        "model": "gpt-4o",
        "provider": "openai",
    },
}


def get_db():
    """Get database connection, creating tables if needed."""
    db_dir = Path(DB_PATH).parent
    db_dir.mkdir(parents=True, exist_ok=True)

    conn = sqlite3.connect(DB_PATH)
    conn.row_factory = sqlite3.Row
    conn.execute("""
        CREATE TABLE IF NOT EXISTS users (
            id TEXT PRIMARY KEY,
            email TEXT UNIQUE NOT NULL,
            token TEXT UNIQUE NOT NULL,
            plan TEXT DEFAULT 'free',
            stripe_customer_id TEXT,
            created_at TEXT DEFAULT CURRENT_TIMESTAMP
        )
    """)
    conn.execute("""
        CREATE TABLE IF NOT EXISTS usage (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            user_id TEXT NOT NULL,
            endpoint TEXT NOT NULL,
            tokens_used INTEGER DEFAULT 0,
            created_at TEXT DEFAULT CURRENT_TIMESTAMP,
            FOREIGN KEY (user_id) REFERENCES users(id)
        )
    """)
    conn.commit()
    return conn


def generate_token() -> str:
    """Generate a unique API token."""
    raw = uuid.uuid4().hex + uuid.uuid4().hex
    return "ctx_" + raw[:40]


def register_user(email: str) -> dict:
    """Register a new user and return their token."""
    conn = get_db()

    # Check if email exists
    existing = conn.execute("SELECT token, plan FROM users WHERE email = ?", (email,)).fetchone()
    if existing:
        conn.close()
        return {"token": existing["token"], "plan": existing["plan"], "message": "Account already exists. Here is your token."}

    user_id = uuid.uuid4().hex[:16]
    token = generate_token()

    conn.execute(
        "INSERT INTO users (id, email, token, plan) VALUES (?, ?, ?, 'free')",
        (user_id, email, token)
    )
    conn.commit()
    conn.close()

    return {"token": token, "plan": "free", "message": "Account created. Save your token!"}


def validate_token(token: str) -> dict | None:
    """Validate an API token and return user info."""
    if not token:
        return None
    conn = get_db()
    user = conn.execute("SELECT id, email, plan FROM users WHERE token = ?", (token,)).fetchone()
    conn.close()
    if user:
        return {"id": user["id"], "email": user["email"], "plan": user["plan"]}
    return None


def check_rate_limit(user_id: str, plan: str) -> dict:
    """Check if user has remaining calls today."""
    conn = get_db()
    today = date.today().isoformat()

    row = conn.execute(
        "SELECT COUNT(*) as count FROM usage WHERE user_id = ? AND created_at >= ?",
        (user_id, today)
    ).fetchone()

    used = row["count"] if row else 0
    limit = PLANS.get(plan, PLANS["free"])["daily_limit"]
    remaining = max(0, limit - used)

    conn.close()
    return {"used": used, "limit": limit, "remaining": remaining, "allowed": remaining > 0}


def track_usage(user_id: str, endpoint: str, tokens_used: int = 0):
    """Record a usage event."""
    conn = get_db()
    conn.execute(
        "INSERT INTO usage (user_id, endpoint, tokens_used) VALUES (?, ?, ?)",
        (user_id, endpoint, tokens_used)
    )
    conn.commit()
    conn.close()


def get_usage_stats(token: str) -> dict:
    """Get usage stats for a user."""
    conn = get_db()
    user = conn.execute("SELECT id, email, plan FROM users WHERE token = ?", (token,)).fetchone()
    if not user:
        conn.close()
        return {"error": "Invalid token"}

    today = date.today().isoformat()
    today_count = conn.execute(
        "SELECT COUNT(*) as count FROM usage WHERE user_id = ? AND created_at >= ?",
        (user["id"], today)
    ).fetchone()["count"]

    total_count = conn.execute(
        "SELECT COUNT(*) as count FROM usage WHERE user_id = ?",
        (user["id"],)
    ).fetchone()["count"]

    plan_info = PLANS.get(user["plan"], PLANS["free"])

    conn.close()
    return {
        "email": user["email"],
        "plan": user["plan"],
        "today": today_count,
        "daily_limit": plan_info["daily_limit"],
        "remaining_today": max(0, plan_info["daily_limit"] - today_count),
        "total_calls": total_count,
        "model": plan_info["model"],
        "upgrade_links": STRIPE_LINKS if user["plan"] == "free" else {},
    }


def upgrade_plan(token: str, plan: str) -> dict:
    """Upgrade user plan (called after Stripe payment)."""
    if plan not in ("pro", "enterprise"):
        return {"error": "Invalid plan"}
    conn = get_db()
    conn.execute("UPDATE users SET plan = ? WHERE token = ?", (plan, token))
    conn.commit()
    conn.close()
    return {"message": f"Plan upgraded to {plan}!", "plan": plan}
