#!/usr/bin/env python3
"""
Day 10 — Micro-model First Pipeline.

Two-level inference:
  Level 1: MicroClassifier (sklearn TF-IDF+LR, zero API calls)
    → OK(confidence ≥ 0.75): done, no LLM
    → UNSURE(confidence < 0.75): escalate to Level 2

  Level 2: LLM fallback (gpt-4o-mini)
    full classification: category + priority + sentiment

Usage:
    from pipeline import MicroFirstPipeline, PipelineConfig, config_from_env
    cfg = config_from_env()
    pipeline = MicroFirstPipeline(cfg)
    result = pipeline.classify("Subject: ...", "Description: ...")
"""

import json
import os
import time
from dataclasses import dataclass, field
from pathlib import Path
from typing import Optional

try:
    from openai import OpenAI
except ImportError:
    raise SystemExit("Run: pip install openai")

from micro import MicroClassifier, MicroResult

VALID_CATEGORIES = {"auth", "billing", "crash", "feature_request", "data_export", "performance", "account"}
VALID_PRIORITIES = {"low", "medium", "high", "critical"}
VALID_SENTIMENTS = {"satisfied", "neutral", "frustrated", "angry"}

DEFAULT_TRAIN_PATH = Path(__file__).parent.parent / "day06" / "train.jsonl"
DEFAULT_EVAL_PATH = Path(__file__).parent.parent / "day06" / "eval.jsonl"

LLM_SYSTEM = """\
Classify the support ticket. Return ONLY valid JSON with exactly these keys:
- "category": one of auth|billing|crash|feature_request|data_export|performance|account
- "priority": one of low|medium|high|critical
- "sentiment": one of satisfied|neutral|frustrated|angry

No markdown. No text outside JSON."""


# ── Config ─────────────────────────────────────────────────────────────────────

@dataclass
class PipelineConfig:
    api_key: str
    base_url: Optional[str] = "https://routerai.ru/api/v1"
    llm_model: str = "openai/gpt-4o-mini"
    temperature: float = 0.0
    confidence_threshold: float = 0.50
    train_path: Path = field(default_factory=lambda: DEFAULT_TRAIN_PATH)
    eval_path: Optional[Path] = field(default_factory=lambda: DEFAULT_EVAL_PATH)


# ── Results ────────────────────────────────────────────────────────────────────

@dataclass
class PipelineResult:
    category: str
    priority: str       # "" when micro handled (micro doesn't classify priority)
    sentiment: str      # "" when micro handled
    confidence: float
    source: str         # "micro" | "llm"
    micro_result: MicroResult
    total_llm_calls: int   # 0 or 1
    latency_ms: int

    def is_valid_category(self) -> bool:
        return self.category in VALID_CATEGORIES


# ── Pipeline ──────────────────────────────────────────────────────────────────

class MicroFirstPipeline:

    def __init__(self, config: PipelineConfig) -> None:
        self.config = config
        self._micro = MicroClassifier(threshold=config.confidence_threshold)
        paths = [config.train_path]
        if config.eval_path and config.eval_path.exists():
            paths.append(config.eval_path)
        self._micro.fit_from_jsonl(*paths)
        self._client = OpenAI(api_key=config.api_key, base_url=config.base_url)

    def classify(self, subject: str, description: str) -> PipelineResult:
        text = f"Subject: {subject}\n\nDescription: {description}"
        start = time.monotonic()

        # Level 1 — micro-model
        micro_result = self._micro.predict(text)

        if micro_result.status == "OK":
            return PipelineResult(
                category=micro_result.category,
                priority="",
                sentiment="",
                confidence=micro_result.confidence,
                source="micro",
                micro_result=micro_result,
                total_llm_calls=0,
                latency_ms=_ms(start),
            )

        # Level 2 — LLM fallback
        llm_category, priority, sentiment = self._call_llm(subject, description)
        return PipelineResult(
            category=llm_category,
            priority=priority,
            sentiment=sentiment,
            confidence=1.0,   # LLM result treated as confident
            source="llm",
            micro_result=micro_result,
            total_llm_calls=1,
            latency_ms=_ms(start),
        )

    def _call_llm(self, subject: str, description: str) -> tuple[str, str, str]:
        resp = self._client.chat.completions.create(
            model=self.config.llm_model,
            temperature=self.config.temperature,
            messages=[
                {"role": "system", "content": LLM_SYSTEM},
                {"role": "user", "content": f"Subject: {subject}\n\nDescription: {description}"},
            ],
        )
        raw = resp.choices[0].message.content or ""
        d = _parse_json(raw)
        category = str(d.get("category", "")).lower()
        if category not in VALID_CATEGORIES:
            category = "account"
        priority = str(d.get("priority", "")).lower()
        if priority not in VALID_PRIORITIES:
            priority = "medium"
        sentiment = str(d.get("sentiment", "")).lower()
        if sentiment not in VALID_SENTIMENTS:
            sentiment = "neutral"
        return category, priority, sentiment


# ── Helpers ───────────────────────────────────────────────────────────────────

def _parse_json(text: str) -> dict:
    start = text.find("{")
    end = text.rfind("}")
    if start == -1 or end <= start:
        return {}
    try:
        return json.loads(text[start: end + 1])
    except (json.JSONDecodeError, ValueError):
        return {}


def _ms(start: float) -> int:
    return int((time.monotonic() - start) * 1000)


def config_from_env() -> PipelineConfig:
    api_key = os.environ.get("OPENAI_API_KEY") or os.environ.get("ROUTERAI_API_KEY")
    if not api_key:
        raise SystemExit("Set OPENAI_API_KEY or ROUTERAI_API_KEY")
    base_url = os.environ.get("OPENAI_BASE_URL")
    if not base_url and os.environ.get("ROUTERAI_API_KEY"):
        base_url = "https://routerai.ru/api/v1"
    return PipelineConfig(api_key=api_key, base_url=base_url)
