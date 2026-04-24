# Day 09 — Multi-Stage Inference (Decomposition)

## Goal

Demonstrate that decomposing a complex inference task into 3 short focused stages produces
**higher decision accuracy** than a single monolithic prompt, especially on edge cases.

Domain: **CV/Resume screening** for a fixed Senior Python Engineer vacancy.

---

## Problem with monolithic inference

A single large prompt (raw CV text → hire/reject decision) fails on:
- **Noisy input** (emojis, typos, mixed languages) — model spends attention on noise
- **Overqualified candidates** — model maps to HIRE without noticing mis-fit
- **Career-changers** — model misidentifies transferable skills
- **Ambiguous matches** — model picks a side without structured gap analysis

Multi-stage forces explicit intermediate representations, making each step auditable.

---

## Vacancy (static, embedded in Stage 2 system prompt)

```
Job: Senior Python Engineer
Required: Python 5yr+, FastAPI, Postgres, Docker
Nice-to-have: Kafka, gRPC, LLM experience
Level: Senior (5–10 yr). Junior/overqualified = MAYBE/REJECT.
```

---

## Variant A — Monolithic

**One prompt → one JSON answer**

```
System: You are a tech recruiter. Given a CV, return JSON:
  {"decision": "HIRE|REJECT|MAYBE", "score": 0-100, "gaps": [...], "summary": "..."}
  Vacancy: Senior Python Engineer. Required: Python 5yr+, FastAPI, Postgres, Docker.
User: <raw CV text>
```

Model: `openai/gpt-4o-mini`, temperature=0.1
Single call. No intermediate state.

---

## Variant B — Multi-Stage

### Stage 1 — Normalize/Extract (gpt-4o-mini, temp=0.0)

**Input:** raw CV text (may be noisy, multilingual, emoji-laden)
**Output:** strict JSON

```json
{
  "skills": ["Python", "FastAPI", "Docker"],
  "exp_years": 4,
  "education": "bachelor|master|phd|none",
  "certifications": ["AWS Solutions Architect"],
  "languages": ["Russian", "English"],
  "career_notes": "career_change|overqualified|fresh_grad|normal"
}
```

Short focused prompt: "Extract structured fields only. No decisions. No opinions."
Constraint: all fields must be present (default to empty list / 0 / "none").

### Stage 2 — Score/Decide (gpt-4o-mini → gpt-4o if escalate=true)

**Input:** Stage 1 JSON (structured fields)
**Output:** strict JSON

```json
{
  "score": 72,
  "match_level": "STRONG|MODERATE|WEAK",
  "matched_required": ["Python", "FastAPI"],
  "gaps": ["Postgres", "Docker (inferred from notes)"],
  "escalate": false
}
```

System prompt contains vacancy. Model sees **only Stage 1 output**, not raw CV.
If Stage 1 `career_notes` ≠ "normal" → set `escalate=true` → retry Stage 2 with gpt-4o.

### Stage 3 — Format Result (gpt-4o-mini, temp=0.1)

**Input:** Stage 1 + Stage 2 JSON
**Output:** final strict JSON

```json
{
  "decision": "HIRE|REJECT|MAYBE",
  "score": 82,
  "gaps": ["no Kafka experience"],
  "summary": "Strong Python/FastAPI match, missing distributed systems."
}
```

Decision rules embedded in system prompt:
- score ≥ 75 AND match_level=STRONG → HIRE
- score < 40 OR match_level=WEAK → REJECT
- otherwise → MAYBE

---

## Stage Model Assignment

| Stage | Model | Reason |
|-------|-------|--------|
| 1 normalize | gpt-4o-mini | Pure extraction, no judgment |
| 2 score | gpt-4o-mini (default) | Structured comparison |
| 2 score escalated | gpt-4o | Non-standard career paths |
| 3 format | gpt-4o-mini | Format conversion only |

---

## Files

```
day09/
├── inference.py       # monolithic_screen() + multistage_screen() + dataclasses + config
└── eval_inference.py  # batch eval, edge cases, markdown report → results.md
```

### `inference.py` exports

```python
@dataclass class CandidateResult:
    decision: str        # HIRE | REJECT | MAYBE
    score: int           # 0–100
    gaps: list[str]
    summary: str

@dataclass class MultiStageResult:
    final: CandidateResult
    stage1_fields: dict  # extracted fields
    stage2_score: dict   # scoring output
    model_used_stage2: str   # "fast" | "strong"
    total_calls: int
    latency_ms: int

@dataclass class MonolithicResult:
    final: CandidateResult
    total_calls: int
    latency_ms: int

def monolithic_screen(cv_text: str, config: InferenceConfig) -> MonolithicResult
def multistage_screen(cv_text: str, config: InferenceConfig) -> MultiStageResult
```

---

## Eval Cases

### Structured eval (10 synthetic CVs with ground truth decision)

```python
# In eval_inference.py
EVAL_CASES = [
    {"id": 1, "type": "strong_match",   "expected": "HIRE",   "cv": "..."},
    {"id": 2, "type": "clear_reject",   "expected": "REJECT", "cv": "..."},
    {"id": 3, "type": "moderate",       "expected": "MAYBE",  "cv": "..."},
    # ... 7 more
]
```

### Edge cases (5 synthetic, no ground truth)

```python
EDGE_CASES = [
    {"id": 11, "type": "overqualified",  "cv": "15yr Python, principal eng..."},
    {"id": 12, "type": "career_change",  "cv": "Marketing Manager → ML..."},
    {"id": 13, "type": "noisy",          "cv": "💻 Python dev 🚀 FastAPI !!!..."},
    {"id": 14, "type": "fresh_grad",     "cv": "0yr exp, 5 academic projects..."},
    {"id": 15, "type": "ambiguous",      "cv": "3yr Python, some FastAPI..."},
]
```

---

## Metrics (results.md)

- Decision accuracy: monolithic vs multi-stage on 10 eval cases
- Escalation rate (Stage 2: fast vs strong)
- Total LLM calls: mono=1×, multi=3–4×
- Cost: mono=$X, multi=$Y
- Avg latency: mono vs multi
- Per-case table: expected | mono_decision | multi_decision | mono_correct | multi_correct

---

## Integration Points

- `config_from_env()` — same pattern as day07/day08
- RouterAI `https://routerai.ru/api/v1`, prefix `openai/`
- `ROUTERAI_API_KEY` / `OPENAI_API_KEY` env vars
- `results.md` written by `eval_inference.py` (same pattern as day07/day08)

---

## Success Criteria

Multi-stage must outperform monolithic on at least 2 of 5 edge cases
(especially noisy and career_change where monolithic typically fails).
