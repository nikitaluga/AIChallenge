#!/usr/bin/env python3
"""
Day 09 — Multi-Stage Inference Decomposition.

CV screening for Senior Python Engineer vacancy.

Variant A: monolithic_screen()  — one big prompt → one JSON answer
Variant B: multistage_screen()  — Stage1: extract → Stage2: score → Stage3: format

Each stage uses a short, focused prompt with strict enum/JSON output.
Stage 2 escalates from gpt-4o-mini to gpt-4o for non-standard career paths.

Usage:
    from inference import InferenceConfig, monolithic_screen, multistage_screen, config_from_env
    cfg = config_from_env()
    result = multistage_screen(cv_text, cfg)
"""

import json
import os
import time
from dataclasses import dataclass, field
from typing import Optional

try:
    from openai import OpenAI
except ImportError:
    raise SystemExit("Run: pip install openai")


# ── Vacancy (static) ──────────────────────────────────────────────────────────

VACANCY = """\
Job: Senior Python Engineer
Required: Python 5yr+, FastAPI, Postgres, Docker
Nice-to-have: Kafka, gRPC, LLM experience
Level: Senior (5–10 yr). Junior candidates = MAYBE. Overqualified (10yr+, principal) = MAYBE.\
"""

# ── Valid values ──────────────────────────────────────────────────────────────

VALID_DECISIONS = {"HIRE", "REJECT", "MAYBE"}
VALID_MATCH_LEVELS = {"STRONG", "MODERATE", "WEAK"}
VALID_CAREER_NOTES = {"career_change", "overqualified", "fresh_grad", "normal"}
VALID_EDUCATION = {"bachelor", "master", "phd", "none"}

# ── Prompts ───────────────────────────────────────────────────────────────────

MONO_SYSTEM = f"""\
You are a tech recruiter. Review the CV and decide on the candidate.
{VACANCY}

Return ONLY valid JSON with exactly these keys:
- "decision": one of HIRE|REJECT|MAYBE
- "score": integer 0–100 (fit to vacancy)
- "gaps": list of strings (missing required skills/exp)
- "summary": 1 sentence

No markdown. No text outside JSON.\
"""

STAGE1_SYSTEM = """\
Extract structured fields from the CV. Return ONLY valid JSON:
- "skills": list of technical skills mentioned (strings, normalized)
- "exp_years": integer total years of professional experience (0 if none)
- "education": one of bachelor|master|phd|none
- "certifications": list of certifications (empty list if none)
- "languages": list of spoken languages
- "career_notes": one of career_change|overqualified|fresh_grad|normal
  career_change = background unrelated to software engineering
  overqualified = 10+ years or principal/staff/distinguished level
  fresh_grad = 0–1 year exp, mostly academic projects
  normal = standard software engineering background

No decisions. No opinions. Extract only what is explicitly stated or strongly implied.
No markdown. No text outside JSON.\
"""

STAGE2_SYSTEM = f"""\
You are a senior tech recruiter scoring a candidate profile.
{VACANCY}

You receive structured candidate fields (output of extraction stage).
Return ONLY valid JSON:
- "score": integer 0–100 (fit to required skills and experience level)
- "match_level": one of STRONG|MODERATE|WEAK
  STRONG = score ≥ 75, meets most required skills
  WEAK   = score < 40 or critical gap (Python < 3yr, no backend)
  MODERATE = everything else
- "matched_required": list of required skills the candidate has
- "gaps": list of required skills or experience the candidate lacks
- "escalate": boolean — true if career_notes ≠ normal (needs deeper judgment)

No markdown. No text outside JSON.\
"""

STAGE3_SYSTEM = """\
You are a recruiting coordinator. Given extraction and scoring results, produce the final decision.

Decision rules:
- HIRE  if score ≥ 75 AND match_level = STRONG
- REJECT if score < 40 OR match_level = WEAK
- MAYBE  otherwise (including overqualified, fresh_grad, career_change)

Return ONLY valid JSON:
- "decision": one of HIRE|REJECT|MAYBE
- "score": integer 0–100 (copy from scoring, adjust only if you see an error)
- "gaps": list of strings (copy gaps from scoring, add career gaps if any)
- "summary": 1 sentence explaining the decision

No markdown. No text outside JSON.\
"""


# ── Data classes ──────────────────────────────────────────────────────────────

@dataclass
class InferenceConfig:
    api_key: str
    base_url: Optional[str] = "https://routerai.ru/api/v1"
    fast_model: str = "openai/gpt-4o-mini"
    strong_model: str = "openai/gpt-4o"
    temperature_extract: float = 0.0
    temperature_decide: float = 0.1


@dataclass
class CandidateResult:
    decision: str         # HIRE | REJECT | MAYBE
    score: int            # 0–100
    gaps: list = field(default_factory=list)
    summary: str = ""

    def is_valid(self) -> bool:
        return self.decision in VALID_DECISIONS and 0 <= self.score <= 100


@dataclass
class MonolithicResult:
    final: CandidateResult
    total_calls: int
    latency_ms: int


@dataclass
class MultiStageResult:
    final: CandidateResult
    stage1_fields: dict
    stage2_score: dict
    model_used_stage2: str    # "fast" | "strong"
    total_calls: int
    latency_ms: int


# ── Monolithic (Variant A) ────────────────────────────────────────────────────

def monolithic_screen(cv_text: str, config: InferenceConfig) -> MonolithicResult:
    client = OpenAI(api_key=config.api_key, base_url=config.base_url)
    start = time.monotonic()

    raw = _chat(client, config.fast_model, config.temperature_decide,
                MONO_SYSTEM, cv_text)
    result = _parse_candidate_result(raw)

    return MonolithicResult(
        final=result,
        total_calls=1,
        latency_ms=_ms(start),
    )


# ── Multi-Stage (Variant B) ───────────────────────────────────────────────────

def multistage_screen(cv_text: str, config: InferenceConfig) -> MultiStageResult:
    client = OpenAI(api_key=config.api_key, base_url=config.base_url)
    start = time.monotonic()
    total_calls = 0

    # ── Stage 1: Extract fields ───────────────────────────────────────────────
    raw1 = _chat(client, config.fast_model, config.temperature_extract,
                 STAGE1_SYSTEM, cv_text)
    total_calls += 1
    fields = _parse_json(raw1)
    fields.setdefault("skills", [])
    fields.setdefault("exp_years", 0)
    fields.setdefault("education", "none")
    fields.setdefault("certifications", [])
    fields.setdefault("languages", [])
    fields.setdefault("career_notes", "normal")

    # ── Stage 2: Score against vacancy ───────────────────────────────────────
    stage2_input = json.dumps(fields, ensure_ascii=False)
    needs_strong = fields.get("career_notes", "normal") != "normal"
    stage2_model = config.strong_model if needs_strong else config.fast_model

    raw2 = _chat(client, stage2_model, config.temperature_decide,
                 STAGE2_SYSTEM, stage2_input)
    total_calls += 1
    scoring = _parse_json(raw2)
    scoring.setdefault("score", 50)
    scoring.setdefault("match_level", "MODERATE")
    scoring.setdefault("matched_required", [])
    scoring.setdefault("gaps", [])
    scoring.setdefault("escalate", False)

    # ── Stage 3: Format final result ─────────────────────────────────────────
    stage3_input = json.dumps({"extraction": fields, "scoring": scoring}, ensure_ascii=False)
    raw3 = _chat(client, config.fast_model, config.temperature_decide,
                 STAGE3_SYSTEM, stage3_input)
    total_calls += 1
    final = _parse_candidate_result(raw3)

    return MultiStageResult(
        final=final,
        stage1_fields=fields,
        stage2_score=scoring,
        model_used_stage2="strong" if needs_strong else "fast",
        total_calls=total_calls,
        latency_ms=_ms(start),
    )


# ── LLM helpers ───────────────────────────────────────────────────────────────

def _chat(client: OpenAI, model: str, temperature: float,
          system: str, user: str) -> str:
    resp = client.chat.completions.create(
        model=model,
        temperature=temperature,
        messages=[
            {"role": "system", "content": system},
            {"role": "user", "content": user},
        ],
    )
    return resp.choices[0].message.content or ""


def _parse_json(text: str) -> dict:
    raw = _extract_json(text)
    try:
        result = json.loads(raw)
        return result if isinstance(result, dict) else {}
    except (json.JSONDecodeError, ValueError):
        return {}


def _parse_candidate_result(text: str) -> CandidateResult:
    d = _parse_json(text)
    decision = str(d.get("decision", "")).upper()
    if decision not in VALID_DECISIONS:
        decision = "MAYBE"
    score_raw = d.get("score", 50)
    try:
        score = max(0, min(100, int(score_raw)))
    except (TypeError, ValueError):
        score = 50
    gaps = d.get("gaps", [])
    if not isinstance(gaps, list):
        gaps = []
    return CandidateResult(
        decision=decision,
        score=score,
        gaps=[str(g) for g in gaps],
        summary=str(d.get("summary", "")),
    )


def _extract_json(text: str) -> str:
    start = text.find("{")
    end = text.rfind("}")
    return text[start: end + 1] if start != -1 and end > start else text


def _ms(start: float) -> int:
    return int((time.monotonic() - start) * 1000)


# ── Config from env ───────────────────────────────────────────────────────────

def config_from_env() -> InferenceConfig:
    api_key = os.environ.get("OPENAI_API_KEY") or os.environ.get("ROUTERAI_API_KEY")
    if not api_key:
        raise SystemExit("Set OPENAI_API_KEY or ROUTERAI_API_KEY")
    base_url = os.environ.get("OPENAI_BASE_URL")
    if not base_url and os.environ.get("ROUTERAI_API_KEY"):
        base_url = "https://routerai.ru/api/v1"
    return InferenceConfig(api_key=api_key, base_url=base_url)
