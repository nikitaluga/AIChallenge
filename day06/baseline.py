#!/usr/bin/env python3
"""Run 10 eval examples through gpt-4o-mini (no fine-tuning) and record baseline."""

import json
import os
import sys
from pathlib import Path

try:
    from openai import OpenAI
except ImportError:
    print("Error: openai package not installed. Run: pip install openai")
    sys.exit(1)

EVAL_FILE = Path(__file__).parent / "eval.jsonl"
OUTPUT_FILE = Path(__file__).parent / "baseline_results.md"
MODEL = "gpt-4o-mini"
MAX_EXAMPLES = 10
# RouterAI is an OpenAI-compatible proxy used throughout this project
ROUTERAI_BASE_URL = "https://routerai.ru/api/v1"


def load_eval_examples(path: Path, limit: int) -> list[dict]:
    examples = []
    with open(path, encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if line:
                examples.append(json.loads(line))
                if len(examples) >= limit:
                    break
    return examples


def run_baseline(client: OpenAI, model: str, example: dict) -> tuple[str, dict | None]:
    messages = example["messages"][:-1]  # exclude assistant (ground truth)
    response = client.chat.completions.create(
        model=model,
        messages=messages,
        temperature=0,
        max_tokens=100,
    )
    raw = response.choices[0].message.content or ""
    try:
        parsed = json.loads(raw)
    except json.JSONDecodeError:
        parsed = None
    return raw, parsed


def score(predicted: dict | None, expected: dict) -> dict[str, bool]:
    if predicted is None:
        return {"category": False, "priority": False, "sentiment": False, "valid_json": False}
    return {
        "category": predicted.get("category") == expected.get("category"),
        "priority": predicted.get("priority") == expected.get("priority"),
        "sentiment": predicted.get("sentiment") == expected.get("sentiment"),
        "valid_json": True,
    }


def main():
    # Support both direct OpenAI and RouterAI proxy (OpenAI-compatible)
    api_key = os.environ.get("OPENAI_API_KEY") or os.environ.get("ROUTERAI_API_KEY")
    base_url = os.environ.get("OPENAI_BASE_URL") or (
        ROUTERAI_BASE_URL if os.environ.get("ROUTERAI_API_KEY") else None
    )
    if not api_key:
        print("Error: set OPENAI_API_KEY or ROUTERAI_API_KEY environment variable")
        sys.exit(1)

    client = OpenAI(api_key=api_key, base_url=base_url)
    # RouterAI requires "openai/gpt-4o-mini" prefix; direct OpenAI uses plain "gpt-4o-mini"
    model = f"openai/{MODEL}" if base_url and "routerai" in base_url else MODEL
    examples = load_eval_examples(EVAL_FILE, MAX_EXAMPLES)
    print(f"Loaded {len(examples)} eval examples. Running baseline with {model}...\n")

    results = []
    for i, example in enumerate(examples, start=1):
        messages = example["messages"]
        expected_str = next(m["content"] for m in messages if m["role"] == "assistant")
        expected = json.loads(expected_str)
        user_content = next(m["content"] for m in messages if m["role"] == "user")

        raw, predicted = run_baseline(client, model, example)
        scores = score(predicted, expected)

        result = {
            "n": i,
            "user": user_content[:80].replace("\n", " "),
            "expected": expected,
            "predicted_raw": raw,
            "predicted": predicted,
            "scores": scores,
        }
        results.append(result)

        status = "✓" if all(scores.values()) else "✗"
        print(f"[{i:02d}] {status} expected={expected_str} | got={raw[:60]}")

    # Compute aggregate scores
    n = len(results)
    agg = {k: sum(r["scores"][k] for r in results) / n for k in ["category", "priority", "sentiment", "valid_json"]}

    # Write markdown report
    lines = [
        "# Day 06 — Baseline Results",
        "",
        f"**Model:** `{model}` (no fine-tuning)  ",
        f"**Examples:** {n} from eval.jsonl  ",
        "",
        "## Aggregate Scores",
        "",
        "| Metric | Score |",
        "|--------|-------|",
        f"| Valid JSON | {agg['valid_json']:.0%} |",
        f"| Category accuracy | {agg['category']:.0%} |",
        f"| Priority accuracy | {agg['priority']:.0%} |",
        f"| Sentiment accuracy | {agg['sentiment']:.0%} |",
        "",
        "## Per-Example Results",
        "",
        "| # | Input (truncated) | Expected | Predicted | Cat | Pri | Sen |",
        "|---|-------------------|----------|-----------|-----|-----|-----|",
    ]

    for r in results:
        exp = r["expected"]
        pred = r["predicted"] or {}
        s = r["scores"]

        def check(ok): return "✓" if ok else "✗"

        exp_str = f"cat={exp.get('category')} pri={exp.get('priority')} sen={exp.get('sentiment')}"
        pred_str = f"cat={pred.get('category','?')} pri={pred.get('priority','?')} sen={pred.get('sentiment','?')}"
        lines.append(
            f"| {r['n']} | {r['user'][:50]}... | {exp_str} | {pred_str} "
            f"| {check(s['category'])} | {check(s['priority'])} | {check(s['sentiment'])} |"
        )

    lines += [
        "",
        "## Criteria for Improvement",
        "",
        "After fine-tuning, the model should achieve:",
        "",
        "| Metric | Baseline | Target |",
        "|--------|----------|--------|",
        f"| Valid JSON | {agg['valid_json']:.0%} | 100% |",
        f"| Category accuracy | {agg['category']:.0%} | ≥95% |",
        f"| Priority accuracy | {agg['priority']:.0%} | ≥85% |",
        f"| Sentiment accuracy | {agg['sentiment']:.0%} | ≥90% |",
        "| Response length | baseline | shorter (no explanations) |",
    ]

    OUTPUT_FILE.write_text("\n".join(lines), encoding="utf-8")
    print(f"\nBaseline report saved to {OUTPUT_FILE}")
    print(f"\nSummary: category={agg['category']:.0%} priority={agg['priority']:.0%} sentiment={agg['sentiment']:.0%}")


if __name__ == "__main__":
    main()
