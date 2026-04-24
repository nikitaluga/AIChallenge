#!/usr/bin/env python3
"""
Day 07 — Confidence Control Pipeline.

Three-stage quality gate for ticket classification (no fine-tuning):
  1. Scoring     — model classifies + reports confidence
  2. Self-check  — model critiques its own answer
  3. Redundancy  — 2 independent calls + majority vote

Constraint check at every stage (JSON validity + allowed enum values).

Usage:
    from confidence import classify_with_confidence, Config
    result = classify_with_confidence("Subject: ...", "Description: ...", config)
"""

import json
import os
import time
from concurrent.futures import ThreadPoolExecutor, as_completed
from dataclasses import dataclass, field
from typing import Optional

try:
    from openai import OpenAI
except ImportError:
    raise SystemExit("Run: pip install openai")

# ── Allowed values ─────────────────────────────────────────────────────────────

VALID_CATEGORIES = {"auth", "billing", "crash", "feature_request", "data_export", "performance", "account"}
VALID_PRIORITIES = {"low", "medium", "high", "critical"}
VALID_SENTIMENTS = {"satisfied", "neutral", "frustrated", "angry"}

# ── Prompts ────────────────────────────────────────────────────────────────────

SCORING_SYSTEM = """\
Classify the support ticket. Return ONLY valid JSON with exactly these keys:
- "category": one of auth|billing|crash|feature_request|data_export|performance|account
- "priority": one of low|medium|high|critical
- "sentiment": one of satisfied|neutral|frustrated|angry
- "confidence": float 0.0–1.0 (your certainty in this exact classification)
- "reasoning": 1–2 sentences explaining your choice

No markdown. No text outside JSON."""

SELF_CHECK_SYSTEM = """\
You previously classified a support ticket as:
{prev}

Critically review:
- Could the category be different? Any signals for multiple categories?
- Does the priority match the described urgency?
- Does the sentiment truly reflect the user's tone?

Return updated classification as JSON with the same keys (category, priority, sentiment, confidence, reasoning).
Lower confidence if uncertain. No markdown. No text outside JSON."""

# ── Data classes ───────────────────────────────────────────────────────────────

@dataclass
class Config:
    api_key: str
    base_url: Optional[str] = "https://routerai.ru/api/v1"
    model: str = "openai/gpt-4o-mini"
    temperature: float = 0.3
    threshold: float = 0.75


@dataclass
class Classification:
    category: str = ""
    priority: str = ""
    sentiment: str = ""
    confidence: float = 0.0
    reasoning: str = ""

    def is_valid(self) -> bool:
        return (
            self.category in VALID_CATEGORIES
            and self.priority in VALID_PRIORITIES
            and self.sentiment in VALID_SENTIMENTS
            and 0.0 <= self.confidence <= 1.0
        )


@dataclass
class PipelineResult:
    classification: Classification
    status: str                    # OK | FAIL
    steps: list[str]               # which stages ran
    total_calls: int
    latency_ms: int
    fail_reason: Optional[str] = None


# ── Core pipeline ──────────────────────────────────────────────────────────────

def classify_with_confidence(subject: str, description: str, config: Config) -> PipelineResult:
    """
    Run the full confidence pipeline.
    Returns PipelineResult with status=OK (accepted) or FAIL (rejected).
    """
    client = OpenAI(api_key=config.api_key, base_url=config.base_url)
    start = time.monotonic()
    steps: list[str] = []
    total_calls = 0

    # ── Stage 1: Scoring ──────────────────────────────────────────────────────
    steps.append("scoring")
    total_calls += 1
    clf = _call_classify(client, config, subject, description)

    if not clf.is_valid():
        return PipelineResult(clf, "FAIL", steps, total_calls, _ms(start), "constraint_violation")
    if clf.confidence >= config.threshold:
        return PipelineResult(clf, "OK", steps, total_calls, _ms(start))

    # ── Stage 2: Self-check ───────────────────────────────────────────────────
    steps.append("self_check")
    total_calls += 1
    clf = _call_self_check(client, config, subject, description, clf)

    if not clf.is_valid():
        return PipelineResult(clf, "FAIL", steps, total_calls, _ms(start), "constraint_violation")
    if clf.confidence >= config.threshold:
        return PipelineResult(clf, "OK", steps, total_calls, _ms(start))

    # ── Stage 3: Redundancy (2 parallel) ─────────────────────────────────────
    steps.append("redundancy")
    total_calls += 2
    r1, r2 = _call_parallel(client, config, subject, description)

    candidates = [c for c in (clf, r1, r2) if c.is_valid()]
    winner = _majority_vote(candidates)

    if winner is None:
        return PipelineResult(clf, "FAIL", steps, total_calls, _ms(start), "no_majority")

    winner.reasoning = f"Majority vote ({len(candidates)}/3): {winner.reasoning}"
    return PipelineResult(winner, "OK", steps, total_calls, _ms(start))


# ── LLM calls ─────────────────────────────────────────────────────────────────

def _call_classify(client: OpenAI, config: Config, subject: str, description: str) -> Classification:
    text = client.chat.completions.create(
        model=config.model,
        temperature=config.temperature,
        messages=[
            {"role": "system", "content": SCORING_SYSTEM},
            {"role": "user", "content": f"Subject: {subject}\nDescription: {description}"},
        ],
    ).choices[0].message.content or ""
    return _parse(text)


def _call_self_check(
    client: OpenAI, config: Config, subject: str, description: str, prev: Classification
) -> Classification:
    prev_json = json.dumps(
        {"category": prev.category, "priority": prev.priority, "sentiment": prev.sentiment,
         "confidence": prev.confidence, "reasoning": prev.reasoning},
        ensure_ascii=False,
    )
    text = client.chat.completions.create(
        model=config.model,
        temperature=config.temperature,
        messages=[
            {"role": "system", "content": SELF_CHECK_SYSTEM.format(prev=prev_json)},
            {"role": "user", "content": f"Subject: {subject}\nDescription: {description}"},
        ],
    ).choices[0].message.content or ""
    return _parse(text)


def _call_parallel(
    client: OpenAI, config: Config, subject: str, description: str
) -> tuple[Classification, Classification]:
    results: list[Classification] = [Classification(), Classification()]
    with ThreadPoolExecutor(max_workers=2) as ex:
        futures = {ex.submit(_call_classify, client, config, subject, description): i for i in range(2)}
        for f in as_completed(futures):
            results[futures[f]] = f.result()
    return results[0], results[1]


# ── Majority vote ─────────────────────────────────────────────────────────────

def _majority_vote(candidates: list[Classification]) -> Optional[Classification]:
    if not candidates:
        return None

    def most_common(values: list[str]) -> tuple[str, int]:
        counts: dict[str, int] = {}
        for v in values:
            counts[v] = counts.get(v, 0) + 1
        best = max(counts, key=lambda k: counts[k])
        return best, counts[best]

    cat, cat_n = most_common([c.category for c in candidates])
    pri, pri_n = most_common([c.priority for c in candidates])
    sen, sen_n = most_common([c.sentiment for c in candidates])

    if cat_n < 2 or pri_n < 2 or sen_n < 2:
        return None  # no majority on at least one axis

    avg_conf = sum(c.confidence for c in candidates) / len(candidates)
    return Classification(
        category=cat, priority=pri, sentiment=sen,
        confidence=round(avg_conf, 3),
        reasoning=f"{cat}/{pri}/{sen}",
    )


# ── JSON helpers ───────────────────────────────────────────────────────────────

def _parse(text: str) -> Classification:
    raw = _extract_json(text)
    try:
        d = json.loads(raw)
        return Classification(
            category=str(d.get("category", "")),
            priority=str(d.get("priority", "")),
            sentiment=str(d.get("sentiment", "")),
            confidence=float(d.get("confidence", 0.0)),
            reasoning=str(d.get("reasoning", "")),
        )
    except (json.JSONDecodeError, ValueError, TypeError):
        return Classification(reasoning=f"parse_error: {text[:80]}")


def _extract_json(text: str) -> str:
    start = text.find("{")
    end = text.rfind("}")
    return text[start : end + 1] if start != -1 and end > start else text


def _ms(start: float) -> int:
    return int((time.monotonic() - start) * 1000)


# ── Build config from env ──────────────────────────────────────────────────────

def config_from_env() -> Config:
    api_key = os.environ.get("OPENAI_API_KEY") or os.environ.get("ROUTERAI_API_KEY")
    if not api_key:
        raise SystemExit("Set OPENAI_API_KEY or ROUTERAI_API_KEY")
    base_url = os.environ.get("OPENAI_BASE_URL")
    if not base_url and os.environ.get("ROUTERAI_API_KEY"):
        base_url = "https://routerai.ru/api/v1"
    return Config(api_key=api_key, base_url=base_url)
