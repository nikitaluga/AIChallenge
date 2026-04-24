#!/usr/bin/env python3
"""
Day 09 — Batch evaluation: monolithic vs multi-stage CV screening.

Runs 10 structured eval cases + 5 edge cases through both variants.
Writes comparison report to results.md.

Usage:
    cd day09
    python eval_inference.py
    python eval_inference.py --edge-only
"""

import argparse
import sys
import time
from pathlib import Path
from typing import Optional

sys.path.insert(0, str(Path(__file__).parent))
from inference import (
    CandidateResult,
    InferenceConfig,
    MonolithicResult,
    MultiStageResult,
    config_from_env,
    monolithic_screen,
    multistage_screen,
)

OUTPUT_FILE = Path(__file__).parent / "results.md"

# Cost estimates per call (rough: ~300 input + ~100 output tokens)
COST_FAST = 0.000_090    # gpt-4o-mini
COST_STRONG = 0.002_400  # gpt-4o

# ── Eval cases (10 synthetic CVs with ground truth) ───────────────────────────

EVAL_CASES = [
    {
        "id": 1,
        "type": "strong_match",
        "expected": "HIRE",
        "cv": (
            "Ivan Petrov — Senior Python Developer\n"
            "Experience: 7 years\n"
            "Skills: Python, FastAPI, PostgreSQL, Docker, Redis, Nginx\n"
            "Education: Bachelor in Computer Science\n"
            "Certifications: AWS Solutions Architect Associate\n"
            "Languages: Russian, English (B2)\n"
            "Recent roles: Senior Backend Engineer at Yandex (3yr), "
            "Python Developer at Sber (4yr)"
        ),
    },
    {
        "id": 2,
        "type": "clear_reject",
        "expected": "REJECT",
        "cv": (
            "Anna Sidorova — Frontend Developer\n"
            "Experience: 4 years\n"
            "Skills: React, TypeScript, HTML, CSS, Figma\n"
            "Education: Bachelor in Design\n"
            "Languages: Russian\n"
            "I am a passionate UI developer with no backend experience."
        ),
    },
    {
        "id": 3,
        "type": "moderate",
        "expected": "MAYBE",
        "cv": (
            "Dmitry Kozlov — Python Developer\n"
            "Experience: 3 years\n"
            "Skills: Python, Django, SQLite, Git\n"
            "Education: Bachelor in Applied Math\n"
            "Languages: Russian\n"
            "I have not worked with FastAPI or Docker yet but learning."
        ),
    },
    {
        "id": 4,
        "type": "strong_match_2",
        "expected": "HIRE",
        "cv": (
            "Sergey Volkov — Backend Engineer\n"
            "Experience: 6 years\n"
            "Skills: Python, FastAPI, Postgres, Docker, Kafka, gRPC\n"
            "Education: Master in Software Engineering\n"
            "Languages: Russian, English (C1)\n"
            "Built high-load microservices at Mail.ru, led team of 4."
        ),
    },
    {
        "id": 5,
        "type": "clear_reject_2",
        "expected": "REJECT",
        "cv": (
            "Maria Novikova — Java Developer\n"
            "Experience: 2 years\n"
            "Skills: Java, Spring Boot, MySQL\n"
            "Education: Bachelor in Computer Science\n"
            "Languages: Russian\n"
            "No Python experience. Interested in switching to Python."
        ),
    },
    {
        "id": 6,
        "type": "good_fit",
        "expected": "HIRE",
        "cv": (
            "Alexei Morozov — Python Backend\n"
            "Experience: 5 years\n"
            "Skills: Python, FastAPI, PostgreSQL, Docker, Celery, Redis\n"
            "Education: Master in CS\n"
            "Certifications: Docker Certified Associate\n"
            "Languages: Russian, English (B1)\n"
            "Worked on payment systems and REST APIs."
        ),
    },
    {
        "id": 7,
        "type": "borderline_reject",
        "expected": "REJECT",
        "cv": (
            "Pavel Orlov — Junior Python Developer\n"
            "Experience: 1 year\n"
            "Skills: Python, Flask, SQLite\n"
            "Education: Bachelor in Economics\n"
            "Languages: Russian\n"
            "Completed several pet projects. Looking for first serious role."
        ),
    },
    {
        "id": 8,
        "type": "moderate_2",
        "expected": "MAYBE",
        "cv": (
            "Elena Kuznetsova — Python Engineer\n"
            "Experience: 4 years\n"
            "Skills: Python, FastAPI, MySQL, Docker\n"
            "Education: Bachelor in CS\n"
            "Languages: Russian, English (A2)\n"
            "Good FastAPI and Docker skills, but no Postgres experience."
        ),
    },
    {
        "id": 9,
        "type": "strong_match_3",
        "expected": "HIRE",
        "cv": (
            "Nikolai Fedorov — Lead Python Developer\n"
            "Experience: 8 years\n"
            "Skills: Python, FastAPI, Postgres, Docker, Kafka, LLM, OpenAI API\n"
            "Education: PhD in Applied Math\n"
            "Languages: Russian, English (C2)\n"
            "Tech lead for AI platform at fintech startup."
        ),
    },
    {
        "id": 10,
        "type": "reject_no_exp",
        "expected": "REJECT",
        "cv": (
            "Oleg Stepanov — Self-taught Developer\n"
            "Experience: 0 years (self-study)\n"
            "Skills: Python (basics), some Flask tutorials\n"
            "Education: Bachelor in Mechanical Engineering\n"
            "Languages: Russian\n"
            "Completed an online Python course. No professional experience."
        ),
    },
]

# ── Edge cases (5 synthetic, no ground truth) ─────────────────────────────────

EDGE_CASES = [
    {
        "id": 11,
        "type": "overqualified",
        "expected": None,
        "cv": (
            "Viktor Andreev — Distinguished Engineer / Principal Architect\n"
            "Experience: 15 years\n"
            "Skills: Python, FastAPI, Postgres, Docker, Kafka, gRPC, Kubernetes, "
            "Terraform, Go, Rust, C++\n"
            "Education: PhD in Computer Science\n"
            "Languages: Russian, English (C2), German (B1)\n"
            "Former CTO, looking for IC role. Team of 40 engineers."
        ),
    },
    {
        "id": 12,
        "type": "career_change",
        "expected": None,
        "cv": (
            "Tatiana Romanova — Marketing Manager → ML Engineer\n"
            "Experience: 6 years marketing, 1 year ML self-study\n"
            "Skills: Python (1yr), scikit-learn, pandas, some FastAPI tutorials\n"
            "Education: Bachelor in Marketing\n"
            "Certifications: Coursera ML Specialization\n"
            "Languages: Russian, English (B2)\n"
            "Transitioned to ML after managing data-driven campaigns."
        ),
    },
    {
        "id": 13,
        "type": "noisy",
        "expected": None,
        "cv": (
            "💻 Artem K. 🚀 PYTHON DEV!!!\n"
            "Exp: ~5 yrs??? (maybe 4.5 lol)\n"
            "Skillz: python!!! FastAPi,, postgress (typo intentional), "
            "docker 🐳, kafkaa, grpc\n"
            "Educ: bachelor comp sci\n"
            "Langs: ru + en (sorta ok)\n"
            "I rlly luv coding 😍 pls hire me!!!!\n"
            "Previous: backend at some startups, can't say which bc NDA"
        ),
    },
    {
        "id": 14,
        "type": "fresh_grad",
        "expected": None,
        "cv": (
            "Kirill Smirnov — CS Graduate 2024\n"
            "Experience: 0 years professional. 2 internships (3 months each).\n"
            "Skills: Python, FastAPI (university project), PostgreSQL (coursework), "
            "Docker (tutorial)\n"
            "Education: Bachelor in CS, GPA 4.8/5.0\n"
            "Projects: built a FastAPI + Postgres REST API for diploma thesis, "
            "Docker-compose setup, unit tests. GitHub: 47 stars.\n"
            "Languages: Russian, English (B2)"
        ),
    },
    {
        "id": 15,
        "type": "ambiguous",
        "expected": None,
        "cv": (
            "Maxim Lebedev — Python Developer\n"
            "Experience: 3 years\n"
            "Skills: Python, FastAPI, MySQL (not Postgres), Docker\n"
            "Education: Master in CS\n"
            "Languages: Russian, English (B1)\n"
            "3yr Python with FastAPI, but MySQL only (no Postgres), "
            "Docker yes but no Kafka/gRPC. Level: mid-senior, maybe senior."
        ),
    },
]


# ── Run ───────────────────────────────────────────────────────────────────────

def run_case(case: dict, cfg: InferenceConfig) -> dict:
    mono_result = None
    multi_result = None
    mono_error = None
    multi_error = None

    t0 = time.monotonic()
    try:
        mono_result = monolithic_screen(case["cv"], cfg)
    except Exception as e:
        mono_error = str(e)

    try:
        multi_result = multistage_screen(case["cv"], cfg)
    except Exception as e:
        multi_error = str(e)

    return {
        "case": case,
        "mono": mono_result,
        "multi": multi_result,
        "mono_error": mono_error,
        "multi_error": multi_error,
        "wall_ms": int((time.monotonic() - t0) * 1000),
    }


def is_correct(result: Optional[CandidateResult], expected: Optional[str]) -> Optional[bool]:
    if expected is None or result is None:
        return None
    return result.decision == expected


# ── Report ────────────────────────────────────────────────────────────────────

def write_report(rows: list[dict], cfg: InferenceConfig) -> None:
    eval_rows = [r for r in rows if r["case"]["expected"] is not None]
    edge_rows = [r for r in rows if r["case"]["expected"] is None]

    mono_correct = sum(
        1 for r in eval_rows
        if r["mono"] and r["mono"].final.decision == r["case"]["expected"]
    )
    multi_correct = sum(
        1 for r in eval_rows
        if r["multi"] and r["multi"].final.decision == r["case"]["expected"]
    )
    n_eval = len(eval_rows)

    # Cost calculation
    total_mono_calls = sum(r["mono"].total_calls for r in rows if r["mono"])
    total_multi_calls = sum(r["multi"].total_calls for r in rows if r["multi"])
    strong_calls = sum(
        1 for r in rows if r["multi"] and r["multi"].model_used_stage2 == "strong"
    )
    fast_multi_calls = total_multi_calls - strong_calls
    cost_mono = total_mono_calls * COST_FAST
    cost_multi = fast_multi_calls * COST_FAST + strong_calls * COST_STRONG

    avg_mono_ms = int(sum(r["mono"].latency_ms for r in rows if r["mono"]) / max(1, sum(1 for r in rows if r["mono"])))
    avg_multi_ms = int(sum(r["multi"].latency_ms for r in rows if r["multi"]) / max(1, sum(1 for r in rows if r["multi"])))

    lines = [
        "# Day 09 — Multi-Stage Inference Results",
        "",
        f"**Fast model:** `{cfg.fast_model}`  **Strong model:** `{cfg.strong_model}`",
        "",
        "## Summary",
        "",
        "| Metric | Monolithic (A) | Multi-Stage (B) |",
        "|--------|----------------|-----------------|",
        f"| Decision accuracy (eval cases) | **{mono_correct}/{n_eval} = {mono_correct/n_eval:.0%}** | **{multi_correct}/{n_eval} = {multi_correct/n_eval:.0%}** |",
        f"| Total LLM calls | {total_mono_calls} (1× per case) | {total_multi_calls} (3–4× per case) |",
        f"| Stage 2 escalations (→ gpt-4o) | — | {strong_calls} |",
        f"| Estimated cost | ${cost_mono:.5f} | ${cost_multi:.5f} |",
        f"| Avg latency | {avg_mono_ms} ms | {avg_multi_ms} ms |",
        "",
        "## Decision Accuracy (eval cases)",
        "",
        "| # | Type | Expected | Mono | Multi | Mono✓ | Multi✓ | Mono score | Multi score |",
        "|---|------|----------|------|-------|-------|--------|------------|-------------|",
    ]

    for r in eval_rows:
        case = r["case"]
        exp = case["expected"]
        mono_d = r["mono"].final.decision if r["mono"] else "ERROR"
        multi_d = r["multi"].final.decision if r["multi"] else "ERROR"
        mono_ok = "✓" if mono_d == exp else "✗"
        multi_ok = "✓" if multi_d == exp else "✗"
        mono_score = r["mono"].final.score if r["mono"] else "—"
        multi_score = r["multi"].final.score if r["multi"] else "—"
        lines.append(
            f"| {case['id']} | {case['type']} | {exp} | {mono_d} | {multi_d} "
            f"| {mono_ok} | {multi_ok} | {mono_score} | {multi_score} |"
        )

    lines += [
        "",
        "## Edge Cases (no ground truth)",
        "",
        "| # | Type | Mono decision | Multi decision | Stage2 model | Multi gaps |",
        "|---|------|---------------|----------------|--------------|-----------|",
    ]

    for r in edge_rows:
        case = r["case"]
        mono_d = r["mono"].final.decision if r["mono"] else "ERROR"
        multi_d = r["multi"].final.decision if r["multi"] else "ERROR"
        stage2_m = r["multi"].model_used_stage2 if r["multi"] else "—"
        gaps = ", ".join(r["multi"].final.gaps[:3]) if r["multi"] else "—"
        lines.append(
            f"| {case['id']} | {case['type']} | {mono_d} | {multi_d} "
            f"| {stage2_m} | {gaps[:60]} |"
        )

    lines += [
        "",
        "## Multi-Stage Pipeline Detail (edge cases)",
        "",
    ]

    for r in edge_rows:
        case = r["case"]
        if not r["multi"]:
            lines.append(f"### Case {case['id']} ({case['type']}): ERROR — {r['multi_error']}")
            continue
        ms: MultiStageResult = r["multi"]
        fields = ms.stage1_fields
        scoring = ms.stage2_score
        lines += [
            f"### Case {case['id']} ({case['type']})",
            "",
            f"**Stage 1 extracted:** skills={fields.get('skills', [])[:5]}, "
            f"exp_years={fields.get('exp_years')}, "
            f"career_notes={fields.get('career_notes')}",
            "",
            f"**Stage 2 scoring ({ms.model_used_stage2}):** "
            f"score={scoring.get('score')}, match_level={scoring.get('match_level')}, "
            f"gaps={scoring.get('gaps', [])[:3]}",
            "",
            f"**Stage 3 final:** decision={ms.final.decision}, score={ms.final.score}",
            f"summary: _{ms.final.summary}_",
            "",
        ]

    lines += [
        "## Pipeline Architecture",
        "",
        "```",
        "Monolithic (A):",
        "  raw CV → [gpt-4o-mini: one big prompt] → {decision, score, gaps, summary}",
        "",
        "Multi-Stage (B):",
        "  raw CV → [Stage 1: gpt-4o-mini] → {skills, exp_years, career_notes, ...}",
        "         → [Stage 2: mini|gpt-4o*] → {score, match_level, gaps}",
        "         → [Stage 3: gpt-4o-mini]  → {decision, score, gaps, summary}",
        "",
        "  * Stage 2 escalates to gpt-4o when career_notes ≠ normal",
        "```",
        "",
        "Each stage has a short, focused prompt with strict JSON output.",
        "Stage 1 sees raw text. Stage 2 sees only structured fields, not raw CV.",
        "Stage 3 combines Stage 1 + Stage 2 outputs for final formatting.",
    ]

    OUTPUT_FILE.write_text("\n".join(lines), encoding="utf-8")
    print(f"\nReport saved: {OUTPUT_FILE}")


# ── Main ──────────────────────────────────────────────────────────────────────

def main() -> None:
    parser = argparse.ArgumentParser(description="Day 09 — Multi-stage inference eval")
    parser.add_argument("--edge-only", action="store_true", help="Run only 5 edge cases")
    parser.add_argument("--fast-model", default="openai/gpt-4o-mini")
    parser.add_argument("--strong-model", default="openai/gpt-4o")
    args = parser.parse_args()

    cfg = config_from_env()
    cfg.fast_model = args.fast_model
    cfg.strong_model = args.strong_model

    eval_cases = [] if args.edge_only else EVAL_CASES
    all_cases = eval_cases + EDGE_CASES

    print(f"=== Day 09 — Multi-Stage Inference ===")
    print(f"Fast: {cfg.fast_model}  Strong: {cfg.strong_model}")
    print(f"Cases: {len(all_cases)} ({len(eval_cases)} eval + {len(EDGE_CASES)} edge)\n")

    rows = []
    for case in all_cases:
        print(f"[{case['id']:02d}] {case['type']:16s} {case['cv'][:60]}...")
        row = run_case(case, cfg)
        rows.append(row)

        mono_d = row["mono"].final.decision if row["mono"] else f"ERROR:{row['mono_error']}"
        multi_d = row["multi"].final.decision if row["multi"] else f"ERROR:{row['multi_error']}"
        exp = case.get("expected", "?")

        mono_ok = ""
        multi_ok = ""
        if exp != "?":
            mono_ok = " ✓" if mono_d == exp else " ✗"
            multi_ok = " ✓" if multi_d == exp else " ✗"

        stage2_m = row["multi"].model_used_stage2 if row["multi"] else "—"
        print(
            f"       mono={mono_d}{mono_ok}  multi={multi_d}{multi_ok}  "
            f"stage2={stage2_m}  "
            f"mono_ms={row['mono'].latency_ms if row['mono'] else '?'}  "
            f"multi_ms={row['multi'].latency_ms if row['multi'] else '?'}"
        )

    write_report(rows, cfg)

    # Console summary
    eval_rows = [r for r in rows if r["case"]["expected"]]
    n = len(eval_rows)
    mono_ok = sum(1 for r in eval_rows if r["mono"] and r["mono"].final.decision == r["case"]["expected"])
    multi_ok = sum(1 for r in eval_rows if r["multi"] and r["multi"].final.decision == r["case"]["expected"])
    esc = sum(1 for r in rows if r["multi"] and r["multi"].model_used_stage2 == "strong")
    print(f"\nAccuracy: mono={mono_ok}/{n}={mono_ok/n:.0%}  multi={multi_ok}/{n}={multi_ok/n:.0%}  escalations={esc}")


if __name__ == "__main__":
    main()
