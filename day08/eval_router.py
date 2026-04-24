#!/usr/bin/env python3
"""
Day 08 — Batch evaluation of model routing (gpt-4o-mini → gpt-4o).

Runs 10 eval cases from day06/eval.jsonl + 5 edge cases through route_classify()
and writes a comparison report to results.md.

Usage:
    cd day08
    python eval_router.py
    python eval_router.py --threshold 0.85 --min-words 12
"""

import argparse
import json
import sys
import time
from pathlib import Path
from typing import Optional

sys.path.insert(0, str(Path(__file__).parent))
from router import RouterConfig, RoutingResult, config_from_env, route_classify

sys.path.insert(0, str(Path(__file__).parent.parent / "day07"))
from confidence import Classification

EVAL_FILE = Path(__file__).parent.parent / "day06" / "eval.jsonl"
OUTPUT_FILE = Path(__file__).parent / "results.md"

# Cost estimates (USD per call, rough average ~200 input + ~80 output tokens)
COST_FAST = 0.000_078    # gpt-4o-mini
COST_STRONG = 0.002_100  # gpt-4o

# ── Edge cases ────────────────────────────────────────────────────────────────

EDGE_CASES = [
    {
        "index": 11,
        "subject": "Приложение сначала падало, потом списали деньги дважды",
        "description": "Вчера приложение начало вылетать при открытии. Сегодня обнаружил два одинаковых платежа за подписку.",
        "type": "ambiguous",
        "expected": None,
    },
    {
        "index": 12,
        "subject": "!!! ПОМОГИТЕ",
        "description": "!!! АААААА всё сломалось помогите немедленно!!! я уже третий раз пишу и никто не отвечает!!!!! SOS!!!!!",
        "type": "noisy",
        "expected": None,
    },
    {
        "index": 13,
        "subject": "Отличное приложение, но постоянно падает",
        "description": "В целом доволен функциональностью и дизайном. Но приложение вылетает при открытии настроек. Критично.",
        "type": "contradictory",
        "expected": None,
    },
    {
        "index": 14,
        "subject": "Ок",
        "description": "Не работает.",
        "type": "short",
        "expected": None,
    },
    {
        "index": 15,
        "subject": "Cannot login",
        "description": "I keep getting 'Invalid credentials' even though I just reset my password 5 minutes ago. Very frustrated.",
        "type": "multilingual",
        "expected": None,
    },
]


# ── Data loading ──────────────────────────────────────────────────────────────

def load_eval_cases() -> list[dict]:
    if not EVAL_FILE.exists():
        print(f"Warning: {EVAL_FILE} not found — running edge cases only")
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
            description = (
                user_content.split("\n\n", 1)[1].replace("Description: ", "").strip()
                if "\n\n" in user_content else ""
            )
            cases.append({
                "index": i,
                "subject": subject,
                "description": description,
                "type": "eval",
                "expected": expected,
            })
    return cases


# ── Run ───────────────────────────────────────────────────────────────────────

def run_case(case: dict, cfg: RouterConfig) -> dict:
    t0 = time.monotonic()
    try:
        result: RoutingResult = route_classify(case["subject"], case["description"], cfg)
    except Exception as e:
        return {"case": case, "result": None, "error": str(e), "wall_ms": int((time.monotonic() - t0) * 1000)}
    return {"case": case, "result": result, "error": None, "wall_ms": result.latency_ms}


def is_correct(case: dict, clf: Classification) -> Optional[bool]:
    expected = case.get("expected")
    if not expected:
        return None
    return (
        clf.category == expected.get("category")
        and clf.priority == expected.get("priority")
        and clf.sentiment == expected.get("sentiment")
    )


# ── Report ────────────────────────────────────────────────────────────────────

def write_report(rows: list[dict], cfg: RouterConfig) -> None:
    total = len(rows)
    errors = [r for r in rows if r["error"]]
    valid = [r for r in rows if r["result"]]
    escalated = [r for r in valid if r["result"].escalated]
    fast_only = [r for r in valid if not r["result"].escalated]

    total_calls = sum(r["result"].total_calls for r in valid)
    cost_routing = sum(
        (COST_FAST + (COST_STRONG if r["result"].escalated else 0)) for r in valid
    )
    cost_fast_only = len(valid) * COST_FAST
    cost_strong_only = len(valid) * COST_STRONG
    avg_latency = int(sum(r["wall_ms"] for r in valid) / len(valid)) if valid else 0

    # Accuracy — eval cases only
    eval_rows = [r for r in valid if r["case"]["expected"] is not None]

    def accuracy(subset: list[dict], field: str) -> str:
        if not eval_rows:
            return "—"
        n = sum(1 for r in subset if r in eval_rows and r["result"].classification.__dict__.get(field) == r["case"]["expected"].get(field))
        d = sum(1 for r in subset if r in eval_rows)
        return f"{n}/{d} = {n/d:.0%}" if d else "—"

    def acc_all(field: str) -> str:
        n = sum(1 for r in eval_rows if r["result"].classification.__dict__.get(field) == r["case"]["expected"].get(field))
        d = len(eval_rows)
        return f"{n}/{d} = {n/d:.0%}" if d else "—"

    # Escalation reason breakdown
    reasons: dict[str, int] = {}
    for r in escalated:
        key = r["result"].escalation_reason or "unknown"
        reasons[key] = reasons.get(key, 0) + 1

    lines = [
        "# Day 08 — Model Routing Results",
        "",
        f"**Fast:** `{cfg.fast_model}`  "
        f"**Strong:** `{cfg.strong_model}`  "
        f"**Threshold:** {cfg.confidence_threshold}  "
        f"**Min-words:** {cfg.min_reasoning_words}  "
        f"**Temp:** {cfg.temperature}",
        "",
        "## Routing Summary",
        "",
        "| Metric | Value |",
        "|--------|-------|",
        f"| Total cases | {total} |",
        f"| Stayed on gpt-4o-mini | **{len(fast_only)}** ({len(fast_only)/total:.0%}) |",
        f"| Escalated to gpt-4o | **{len(escalated)}** ({len(escalated)/total:.0%}) |",
        f"| Errors | {len(errors)} |",
        f"| Total LLM calls | {total_calls} (baseline 1×: {len(valid)}, strong-only 1×: {len(valid)}) |",
        f"| Avg latency | {avg_latency} ms |",
        "",
        "## Escalation Reasons",
        "",
    ]

    if reasons:
        lines += ["| Reason | Count |", "|--------|-------|"]
        for k, v in sorted(reasons.items(), key=lambda x: -x[1]):
            lines.append(f"| {k} | {v} |")
    else:
        lines.append("*No escalations.*")

    lines += [
        "",
        "## Cost Comparison",
        "",
        "| Strategy | Cost (USD) | vs routing |",
        "|----------|-----------|------------|",
        f"| Fast-only (gpt-4o-mini) | ${cost_fast_only:.5f} | — |",
        f"| **Routing (this script)** | **${cost_routing:.5f}** | baseline |",
        f"| Strong-only (gpt-4o) | ${cost_strong_only:.5f} | {cost_strong_only/cost_routing:.1f}× more |",
        "",
        "## Accuracy (eval cases only)",
        "",
        "| Field | Routing result |",
        "|-------|---------------|",
        f"| Category | {acc_all('category')} |",
        f"| Priority | {acc_all('priority')} |",
        f"| Sentiment | {acc_all('sentiment')} |",
        "",
        "*Baseline Day 06 (fast-only): category=90%, priority=50%, sentiment=70%*",
        "",
        "## Per-Case Results",
        "",
        "| # | Type | Fast conf | Escalated | Reason | Model | cat | pri | sen | ✓ |",
        "|---|------|-----------|-----------|--------|-------|-----|-----|-----|---|",
    ]

    for r in rows:
        case = r["case"]
        idx = case["index"]
        typ = case["type"]
        if r["error"]:
            lines.append(f"| {idx} | {typ} | — | ERROR | — | — | — | — | — | — |")
            continue
        res: RoutingResult = r["result"]
        clf = res.classification
        fast_conf = f"{res.fast_clf.confidence:.2f}"
        esc = "🔴 yes" if res.escalated else "🟢 no"
        reason = res.escalation_reason or ""
        model = "gpt-4o" if res.escalated else "mini"
        correct = is_correct(case, clf)
        tick = "✓" if correct is True else ("✗" if correct is False else "?")
        lines.append(
            f"| {idx} | {typ} | {fast_conf} | {esc} | {reason} | {model} "
            f"| {clf.category} | {clf.priority} | {clf.sentiment} | {tick} |"
        )

    lines += [
        "",
        "## Routing Logic",
        "",
        "```",
        "route_classify(subject, description):",
        "  1. Call gpt-4o-mini → {category, priority, sentiment, confidence, reasoning}",
        "  2. needs_escalation = (confidence < threshold) OR (len(reasoning.split()) < min_words)",
        "  3. if needs_escalation → call gpt-4o → return strong result",
        "     else               → return fast result",
        "```",
        "",
        f"Thresholds: confidence < {cfg.confidence_threshold}  |  reasoning < {cfg.min_reasoning_words} words",
    ]

    OUTPUT_FILE.write_text("\n".join(lines), encoding="utf-8")
    print(f"\nReport saved: {OUTPUT_FILE}")


# ── Main ──────────────────────────────────────────────────────────────────────

def main() -> None:
    parser = argparse.ArgumentParser(description="Day 08 — Model routing batch eval")
    parser.add_argument("--threshold", type=float, default=0.75)
    parser.add_argument("--min-words", type=int, default=8)
    parser.add_argument("--fast-model", default="openai/gpt-4o-mini")
    parser.add_argument("--strong-model", default="openai/gpt-4o")
    parser.add_argument("--temperature", type=float, default=0.3)
    parser.add_argument("--edge-only", action="store_true", help="Run only 5 edge cases")
    args = parser.parse_args()

    cfg = config_from_env()
    cfg.fast_model = args.fast_model
    cfg.strong_model = args.strong_model
    cfg.confidence_threshold = args.threshold
    cfg.min_reasoning_words = args.min_words
    cfg.temperature = args.temperature

    eval_cases = [] if args.edge_only else load_eval_cases()
    all_cases = eval_cases + EDGE_CASES

    print(f"=== Day 08 — Model Routing ===")
    print(f"Fast: {cfg.fast_model}  Strong: {cfg.strong_model}")
    print(f"Threshold: {cfg.confidence_threshold}  Min-words: {cfg.min_reasoning_words}  Cases: {len(all_cases)}\n")

    rows = []
    for case in all_cases:
        print(f"[{case['index']:02d}] {case['type']:14s} {case['subject'][:55]}")
        row = run_case(case, cfg)
        rows.append(row)

        if row["error"]:
            print(f"       ERROR: {row['error']}")
            continue

        res: RoutingResult = row["result"]
        clf = res.classification
        correct = is_correct(case, clf)
        tick = "✓" if correct is True else ("✗" if correct is False else "?")
        esc_label = f"→ {res.escalation_reason} → gpt-4o" if res.escalated else "→ mini (fast path)"
        print(
            f"       conf={res.fast_clf.confidence:.2f}  {esc_label}  {res.latency_ms}ms  "
            f"{clf.category}/{clf.priority}/{clf.sentiment}  {tick}"
        )

    write_report(rows, cfg)

    # Console summary
    valid = [r for r in rows if r["result"]]
    n_esc = sum(1 for r in valid if r["result"].escalated)
    n_fast = len(valid) - n_esc
    total_calls = sum(r["result"].total_calls for r in valid)
    print(f"\nFast-only={n_fast}  Escalated={n_esc}  Total calls={total_calls}")


if __name__ == "__main__":
    main()
