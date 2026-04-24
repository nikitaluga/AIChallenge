#!/usr/bin/env python3
"""
Day 07 — Batch evaluation of the confidence pipeline.

Runs all eval.jsonl cases + 3 boundary cases through classify_with_confidence()
and writes a comparison report to results.md.

Usage:
    cd day07
    python eval_confidence.py
    python eval_confidence.py --threshold 0.85
"""

import argparse
import json
import sys
import time
from pathlib import Path
from typing import Optional

sys.path.insert(0, str(Path(__file__).parent))
from confidence import (
    Classification,
    Config,
    PipelineResult,
    classify_with_confidence,
    config_from_env,
)

EVAL_FILE = Path(__file__).parent.parent / "day06" / "eval.jsonl"
OUTPUT_FILE = Path(__file__).parent / "results.md"

# Cost estimate: gpt-4o-mini ~$0.15/1M input tokens, ~$0.60/1M output tokens
# Rough: ~200 input + ~80 output tokens per call → ~$0.000078 per call
COST_PER_CALL_USD = 0.000_078

# ── Boundary cases ─────────────────────────────────────────────────────────────

BOUNDARY_CASES = [
    {
        "index": 11,
        "subject": "Приложение сначала падало, а потом и деньги списались дважды",
        "description": "Вчера приложение начало вылетать при открытии. Сегодня обнаружил, что с карты дважды списалась сумма за подписку.",
        "type": "ambiguous",
        "expected": None,
    },
    {
        "index": 12,
        "subject": "!!! ПОМОГИТЕ",
        "description": "!!! АААААА всё сломалось помогите немедленно аааа!!!! я уже третий раз пишу и никто не отвечает!!!!! SOS!!!!!",
        "type": "noisy",
        "expected": None,
    },
    {
        "index": 13,
        "subject": "Отличное приложение, но постоянно падает",
        "description": "В целом я доволен функциональностью и дизайном, всё очень удобно. Но приложение постоянно вылетает при попытке открыть раздел настроек. Это прям критично.",
        "type": "contradictory",
        "expected": None,
    },
]


# ── Data loading ───────────────────────────────────────────────────────────────

def load_eval_cases() -> list[dict]:
    if not EVAL_FILE.exists():
        print(f"Warning: {EVAL_FILE} not found — running boundary cases only")
        return []
    cases = []
    with open(EVAL_FILE, encoding="utf-8") as f:
        for i, line in enumerate(f, start=1):
            line = line.strip()
            if not line:
                continue
            obj = json.loads(line)
            messages = obj["messages"]
            user_content = next(m["content"] for m in messages if m["role"] == "user")
            assistant_content = next(m["content"] for m in messages if m["role"] == "assistant")
            expected = json.loads(assistant_content)
            subject = user_content.split("\n\n")[0].replace("Subject: ", "").strip()
            description = user_content.split("\n\n", 1)[1].replace("Description: ", "").strip() if "\n\n" in user_content else ""
            cases.append({
                "index": i,
                "subject": subject,
                "description": description,
                "type": "eval",
                "expected": expected,
            })
    return cases


# ── Run ────────────────────────────────────────────────────────────────────────

def run_case(case: dict, config: Config) -> dict:
    t0 = time.monotonic()
    try:
        result: PipelineResult = classify_with_confidence(
            case["subject"], case["description"], config
        )
    except Exception as e:
        return {
            "case": case,
            "result": None,
            "error": str(e),
            "wall_ms": int((time.monotonic() - t0) * 1000),
        }
    return {
        "case": case,
        "result": result,
        "error": None,
        "wall_ms": result.latency_ms,
    }


def compute_correct(case: dict, clf: Classification) -> Optional[bool]:
    expected = case.get("expected")
    if not expected:
        return None
    return (
        clf.category == expected.get("category")
        and clf.priority == expected.get("priority")
        and clf.sentiment == expected.get("sentiment")
    )


# ── Report ─────────────────────────────────────────────────────────────────────

def write_report(rows: list[dict], config: Config) -> None:
    total = len(rows)
    errors = [r for r in rows if r["error"]]
    ok_rows = [r for r in rows if r["result"] and r["result"].status == "OK"]
    fail_rows = [r for r in rows if r["result"] and r["result"].status == "FAIL"]
    retried = [r for r in rows if r["result"] and len(r["result"].steps) > 1]

    total_calls = sum(r["result"].total_calls for r in rows if r["result"])
    avg_latency = int(sum(r["wall_ms"] for r in rows) / total) if total else 0
    est_cost = total_calls * COST_PER_CALL_USD

    eval_rows = [r for r in rows if r["case"]["expected"] is not None and r["result"]]
    cat_ok = sum(1 for r in eval_rows if r["result"].classification.category == r["case"]["expected"]["category"])
    pri_ok = sum(1 for r in eval_rows if r["result"].classification.priority == r["case"]["expected"]["priority"])
    sen_ok = sum(1 for r in eval_rows if r["result"].classification.sentiment == r["case"]["expected"]["sentiment"])
    n_eval = len(eval_rows)

    # Only-OK eval accuracy
    ok_eval = [r for r in eval_rows if r["result"].status == "OK"]
    cat_ok_only = sum(1 for r in ok_eval if r["result"].classification.category == r["case"]["expected"]["category"])
    pri_ok_only = sum(1 for r in ok_eval if r["result"].classification.priority == r["case"]["expected"]["priority"])
    sen_ok_only = sum(1 for r in ok_eval if r["result"].classification.sentiment == r["case"]["expected"]["sentiment"])
    n_ok_eval = len(ok_eval)

    lines = [
        "# Day 07 — Confidence Control Results",
        "",
        f"**Model:** `{config.model}`  **Threshold:** {config.threshold}  **Temp:** {config.temperature}",
        "",
        "## Summary",
        "",
        "| Metric | Value |",
        "|--------|-------|",
        f"| Total cases | {total} |",
        f"| Accepted (OK) | {len(ok_rows)} ({len(ok_rows)/total:.0%}) |",
        f"| Rejected (FAIL) | {len(fail_rows)} ({len(fail_rows)/total:.0%}) |",
        f"| Required retry | {len(retried)} ({len(retried)/total:.0%}) |",
        f"| Errors | {len(errors)} |",
        f"| Total LLM calls | {total_calls} (baseline would be {total}) |",
        f"| Extra LLM calls | {total_calls - total} ({(total_calls - total)/total:.1f}× overhead) |",
        f"| Avg latency | {avg_latency} ms |",
        f"| Estimated cost | ${est_cost:.4f} USD |",
        "",
        "## Accuracy (eval cases only)",
        "",
        "| Metric | All cases | OK-only cases |",
        "|--------|-----------|---------------|",
    ]
    if n_eval:
        lines += [
            f"| Category | {cat_ok}/{n_eval} = {cat_ok/n_eval:.0%} | {cat_ok_only}/{n_ok_eval} = {cat_ok_only/n_ok_eval:.0%} |",
            f"| Priority | {pri_ok}/{n_eval} = {pri_ok/n_eval:.0%} | {pri_ok_only}/{n_ok_eval} = {pri_ok_only/n_ok_eval:.0%} |",
            f"| Sentiment | {sen_ok}/{n_eval} = {sen_ok/n_eval:.0%} | {sen_ok_only}/{n_ok_eval} = {sen_ok_only/n_ok_eval:.0%} |",
        ]
    lines += [
        "",
        f"*Baseline (Day 06): category=90%, priority=50%, sentiment=70%*",
        "",
        "## Per-Case Results",
        "",
        "| # | Type | Status | Steps | Calls | ms | Category | Priority | Sentiment | Conf | ✓ | Reasoning |",
        "|---|------|--------|-------|-------|----|----------|----------|-----------|------|---|-----------|",
    ]

    for r in rows:
        case = r["case"]
        idx = case["index"]
        typ = case["type"]
        if r["error"]:
            lines.append(f"| {idx} | {typ} | ERROR | — | — | {r['wall_ms']} | — | — | — | — | — | {r['error'][:60]} |")
            continue
        res: PipelineResult = r["result"]
        clf = res.classification
        steps_str = "→".join(res.steps)
        correct = compute_correct(case, clf)
        tick = "✓" if correct is True else ("✗" if correct is False else "?")
        color = "🟢" if res.status == "OK" else "🔴"
        reasoning = clf.reasoning[:60].replace("|", "\\|")
        lines.append(
            f"| {idx} | {typ} | {color} {res.status} | {steps_str} | {res.total_calls} | {res.latency_ms} "
            f"| {clf.category} | {clf.priority} | {clf.sentiment} | {clf.confidence:.2f} | {tick} | {reasoning} |"
        )

    lines += [
        "",
        "## Pipeline Design",
        "",
        "```",
        "Scoring (1 call)  →  score ≥ 0.75  →  OK",
        "                  →  score < 0.75",
        "Self-check (1 call)  →  score ≥ 0.75  →  OK",
        "                     →  score < 0.75",
        "Redundancy (2 parallel calls)  →  majority vote  →  OK",
        "                               →  no majority    →  FAIL",
        "```",
        "",
        "Constraint check after every LLM call: valid JSON + allowed enum values + confidence ∈ [0,1].",
    ]

    OUTPUT_FILE.write_text("\n".join(lines), encoding="utf-8")
    print(f"\nReport saved: {OUTPUT_FILE}")


# ── Main ───────────────────────────────────────────────────────────────────────

def main() -> None:
    parser = argparse.ArgumentParser(description="Day 07 — Confidence pipeline batch eval")
    parser.add_argument("--threshold", type=float, default=0.75)
    parser.add_argument("--model", default="openai/gpt-4o-mini")
    parser.add_argument("--temperature", type=float, default=0.3)
    parser.add_argument("--boundary-only", action="store_true", help="Run only 3 boundary cases")
    args = parser.parse_args()

    config = config_from_env()
    config.threshold = args.threshold
    config.model = args.model
    config.temperature = args.temperature

    eval_cases = [] if args.boundary_only else load_eval_cases()
    all_cases = eval_cases + BOUNDARY_CASES

    print(f"=== Day 07 — Confidence Control ===")
    print(f"Model: {config.model}  Threshold: {config.threshold}  Cases: {len(all_cases)}\n")

    rows = []
    for case in all_cases:
        print(f"[{case['index']:02d}] {case['type']:14s} {case['subject'][:55]}...")
        row = run_case(case, config)
        rows.append(row)

        if row["error"]:
            print(f"       ERROR: {row['error']}")
            continue

        res: PipelineResult = row["result"]
        clf = res.classification
        correct = compute_correct(case, clf)
        tick = "✓" if correct is True else ("✗" if correct is False else "?")
        steps = "→".join(res.steps)
        print(
            f"       {res.status:4s} [{steps}]  calls={res.total_calls}  {res.latency_ms}ms  "
            f"conf={clf.confidence:.2f}  {clf.category}/{clf.priority}/{clf.sentiment}  {tick}"
        )

    write_report(rows, config)

    # Console summary
    ok = sum(1 for r in rows if r["result"] and r["result"].status == "OK")
    fail = sum(1 for r in rows if r["result"] and r["result"].status == "FAIL")
    retried = sum(1 for r in rows if r["result"] and len(r["result"].steps) > 1)
    total_calls = sum(r["result"].total_calls for r in rows if r["result"])
    print(f"\nOK={ok}  FAIL={fail}  retried={retried}  total_calls={total_calls}")


if __name__ == "__main__":
    main()
