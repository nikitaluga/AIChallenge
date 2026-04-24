#!/usr/bin/env python3
"""
Day 08 — Model Routing: gpt-4o-mini → gpt-4o.

Heuristics triggering escalation (either condition is sufficient):
  1. fast model confidence < threshold
  2. reasoning word count < min_reasoning_words

Usage:
    from router import RouterConfig, route_classify, config_from_env
    config = config_from_env()
    result = route_classify("Subject: ...", "Description: ...", config)
"""

import os
import sys
import time
from dataclasses import dataclass, field
from pathlib import Path
from typing import Optional

try:
    from openai import OpenAI
except ImportError:
    raise SystemExit("Run: pip install openai")

sys.path.insert(0, str(Path(__file__).parent.parent / "day07"))
from confidence import Classification, Config, _call_classify  # noqa: E402

# ── Config ────────────────────────────────────────────────────────────────────

@dataclass
class RouterConfig:
    api_key: str
    base_url: Optional[str] = "https://routerai.ru/api/v1"
    fast_model: str = "openai/gpt-4o-mini"
    strong_model: str = "openai/gpt-4o"
    temperature: float = 0.3
    confidence_threshold: float = 0.75
    min_reasoning_words: int = 8


# ── Result ────────────────────────────────────────────────────────────────────

@dataclass
class RoutingResult:
    classification: Classification
    model_used: str                   # "fast" | "strong"
    escalated: bool
    escalation_reason: Optional[str]  # "low_confidence" | "short_reasoning" | "both" | None
    fast_clf: Classification
    total_calls: int
    latency_ms: int


# ── Core routing ──────────────────────────────────────────────────────────────

def route_classify(subject: str, description: str, cfg: RouterConfig) -> RoutingResult:
    client = OpenAI(api_key=cfg.api_key, base_url=cfg.base_url)
    start = time.monotonic()

    # Stage 1 — fast model
    fast_config = _make_config(cfg, cfg.fast_model)
    fast_clf = _call_classify(client, fast_config, subject, description)
    total_calls = 1

    # Evaluate escalation heuristics
    low_conf = fast_clf.confidence < cfg.confidence_threshold
    short_reasoning = len(fast_clf.reasoning.split()) < cfg.min_reasoning_words
    needs_escalation = low_conf or short_reasoning

    if not needs_escalation:
        return RoutingResult(
            classification=fast_clf,
            model_used="fast",
            escalated=False,
            escalation_reason=None,
            fast_clf=fast_clf,
            total_calls=total_calls,
            latency_ms=_ms(start),
        )

    # Determine reason label
    if low_conf and short_reasoning:
        reason = "both"
    elif low_conf:
        reason = "low_confidence"
    else:
        reason = "short_reasoning"

    # Stage 2 — strong model
    strong_config = _make_config(cfg, cfg.strong_model)
    strong_clf = _call_classify(client, strong_config, subject, description)
    total_calls += 1

    return RoutingResult(
        classification=strong_clf,
        model_used="strong",
        escalated=True,
        escalation_reason=reason,
        fast_clf=fast_clf,
        total_calls=total_calls,
        latency_ms=_ms(start),
    )


# ── Helpers ───────────────────────────────────────────────────────────────────

def _make_config(cfg: RouterConfig, model: str) -> Config:
    return Config(
        api_key=cfg.api_key,
        base_url=cfg.base_url,
        model=model,
        temperature=cfg.temperature,
        threshold=cfg.confidence_threshold,
    )


def _ms(start: float) -> int:
    return int((time.monotonic() - start) * 1000)


def config_from_env() -> RouterConfig:
    api_key = os.environ.get("OPENAI_API_KEY") or os.environ.get("ROUTERAI_API_KEY")
    if not api_key:
        raise SystemExit("Set OPENAI_API_KEY or ROUTERAI_API_KEY")
    base_url = os.environ.get("OPENAI_BASE_URL")
    if not base_url and os.environ.get("ROUTERAI_API_KEY"):
        base_url = "https://routerai.ru/api/v1"
    return RouterConfig(api_key=api_key, base_url=base_url)
