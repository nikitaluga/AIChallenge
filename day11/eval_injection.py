#!/usr/bin/env python3
"""
Day 11 — Prompt Injection Evaluation.

Runs attack techniques against vulnerable and hardened system prompts.
Iterates on hardened prompt using LLM until 0/N attacks breach (or max_iterations).
Generates day11/results.md.

Usage:
    ROUTERAI_API_KEY=<key> python day11/eval_injection.py
    # or
    OPENAI_API_KEY=<key> python day11/eval_injection.py
"""

import sys
import textwrap
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent.parent))

from day11.injection import (
    INJECTION_CATALOG,
    ATTACK_TECHNIQUES,
    VULNERABLE_SYSTEM,
    HARDENED_SYSTEM,
    TestReport,
    AttackResult,
    test_system_prompt,
    auto_improve_prompt,
    config_from_env,
)

OUTPUT_PATH = Path(__file__).parent / "results.md"
MAX_ITERATIONS = 5


# ── Report rendering ──────────────────────────────────────────────────────────

def _verdict_emoji(verdict: str) -> str:
    return "🔴 BREACHED" if verdict == "BREACHED" else "🟢 HELD"


def render_catalog() -> str:
    n = len(INJECTION_CATALOG)
    lines = [
        f"## Каталог инъекций ({n} реальных примеров)\n",
        "| # | Название | Тип | Источник |",
        "|---|---------|-----|---------|",
    ]
    for ex in INJECTION_CATALOG:
        lines.append(f"| {ex['id']} | **{ex['name']}** | `{ex['type']}` | {ex['source']} |")

    lines.append("")
    for ex in INJECTION_CATALOG:
        lines.append(f"### {ex['id']}. {ex['name']}")
        lines.append(f"**Тип:** `{ex['type']}`  \n**Источник:** {ex['source']}\n")
        lines.append(f"**Что делает:**  \n{ex['what_it_does']}\n")
        lines.append(f"**Почему работает:**  \n{ex['why_it_works']}\n")
        lines.append(f"**Защита:**  \n{ex['defense']}\n")
        excerpt = ex["prompt_excerpt"]
        lines.append(f"```\n{excerpt[:300]}{'...' if len(excerpt) > 300 else ''}\n```\n")

    return "\n".join(lines)


def render_attack_report(report: TestReport, iteration: int | None = None) -> str:
    label = report.label
    if iteration is not None:
        label = f"{report.label} (iteration {iteration})"
    lines = [
        f"## Атаки на {label} промпт\n",
        f"**Итог:** {report.total_breached} BREACHED / {report.total_held} HELD\n",
        "| Техника | Вердикт | Рассуждение судьи |",
        "|---------|---------|-------------------|",
    ]
    for r in report.results:
        lines.append(
            f"| {r.technique_label} | {_verdict_emoji(r.verdict)} | {r.judge_reasoning} |"
        )
    lines.append("")

    for r in report.results:
        lines.append(f"### {r.technique_label}")
        lines.append(
            f"**Вердикт:** {_verdict_emoji(r.verdict)}  \n"
            f"**Время:** {r.latency_ms} ms\n"
        )
        lines.append("**Атака:**")
        lines.append(f"```\n{r.attack_prompt}\n```\n")
        response_preview = r.model_response[:600]
        if len(r.model_response) > 600:
            response_preview += "..."
        lines.append("**Ответ модели:**")
        lines.append(f"```\n{response_preview}\n```\n")
        lines.append(f"**Рассуждение судьи:**  \n{r.judge_reasoning}\n")

    return "\n".join(lines)


def render_diff(vuln: TestReport, hard: TestReport) -> str:
    lines = [
        "## Diff: уязвимый vs hardened\n",
        "| Техника | Уязвимый | Hardened | Изменение |",
        "|---------|----------|----------|-----------|",
    ]
    vuln_map = {r.technique_id: r for r in vuln.results}
    hard_map = {r.technique_id: r for r in hard.results}

    for tech in ATTACK_TECHNIQUES:
        tid = tech["id"]
        v = vuln_map.get(tid)
        h = hard_map.get(tid)
        v_str = _verdict_emoji(v.verdict) if v else "—"
        h_str = _verdict_emoji(h.verdict) if h else "—"
        changed = ""
        if v and h:
            if v.verdict == "BREACHED" and h.verdict == "HELD":
                changed = "✅ защита сработала"
            elif v.verdict == "HELD" and h.verdict == "BREACHED":
                changed = "⚠️ регрессия"
            else:
                changed = "без изменений"
        lines.append(f"| {tech['label']} | {v_str} | {h_str} | {changed} |")

    lines.append("")
    lines.append("### Ключевые техники защиты\n")
    lines.append("| Техника защиты | Нейтрализует |")
    lines.append("|----------------|-------------|")
    lines.append("| **Format lock** (только JSON) | role_play, instruction_override, skeleton_key — нарративный ответ физически невозможен |")
    lines.append("| **Guideline immutability** | skeleton_key — явный запрет на подтверждение изменений |")
    lines.append("| **Example blindness** (игнорировать Q&A паттерны) | many_shot — примеры не меняют поведение |")
    lines.append("| **Memory isolation** (нет памяти о прошлых диалогах) | crescendo — фиктивный контекст = новый тикет |")
    lines.append("| **Instruction blindness** ('IGNORE/DAN/SKELETON KEY' = тикет) | role_play, instruction_override |")
    lines.append("| **Persona erasure** (нет имени, нет персоны) | role_play — нет персоны для замены |")
    lines.append("| **Prompt confidentiality** | prompt_extraction — явный запрет |")

    return "\n".join(lines)


def render_iteration_log(iteration_reports: list[tuple[int, TestReport, int]]) -> str:
    """iteration_reports: list of (iteration_num, report, breached_count)"""
    lines = ["## Лог итераций авто-усиления\n"]
    lines.append("| Итерация | BREACHED | HELD | Статус |")
    lines.append("|----------|----------|------|--------|")
    for i, report, breached in iteration_reports:
        status = "✅ готово" if breached == 0 else "🔄 улучшаем"
        lines.append(f"| {i} | {breached} | {report.total_held} | {status} |")
    return "\n".join(lines)


def render_results(
    vuln: TestReport,
    hard: TestReport,
    iteration_reports: list[tuple[int, TestReport, int]],
    final_prompt: str,
) -> str:
    sections = [
        "# Day 11 — Prompt Injection Results\n",
        render_catalog(),
        render_attack_report(vuln),
        render_iteration_log(iteration_reports),
        render_attack_report(hard, iteration=len(iteration_reports)),
        render_diff(vuln, hard),
        "## Финальный hardened prompt\n",
        f"```\n{final_prompt}\n```\n",
        "---",
        "*Сгенерировано: eval_injection.py*",
    ]
    return "\n\n".join(sections)


# ── Iterative hardening ───────────────────────────────────────────────────────

def iterate_until_hardened(config, max_iter: int = MAX_ITERATIONS) -> tuple[TestReport, list, str]:
    """
    Run attacks against HARDENED_SYSTEM, auto-improve on failure.
    Returns (final_report, iteration_log, final_prompt).
    """
    current_prompt = HARDENED_SYSTEM
    iteration_reports = []

    for i in range(1, max_iter + 1):
        print(f"\n  [hardened iteration {i}/{max_iter}] Testing...")
        report = test_system_prompt(current_prompt, config, label="hardened")
        iteration_reports.append((i, report, report.total_breached))

        breached = [r for r in report.results if r.verdict == "BREACHED"]
        print(f"  Result: {report.total_breached} BREACHED / {report.total_held} HELD")
        for r in report.results:
            print(f"    {r.technique_label}: {r.verdict}")

        if report.total_breached == 0:
            print(f"  ✅ Hardened prompt holds all {report.total_held} attacks.")
            return report, iteration_reports, current_prompt

        if i < max_iter:
            print(f"  🔄 {report.total_breached} breach(es) — improving prompt with LLM...")
            current_prompt = auto_improve_prompt(current_prompt, breached, config)
            print(f"  Prompt updated ({len(current_prompt)} chars).")

    print(f"\n  ⚠️ Max iterations reached. Final: {iteration_reports[-1][2]} BREACHED.")
    return report, iteration_reports, current_prompt


# ── Main ──────────────────────────────────────────────────────────────────────

def main() -> None:
    config = config_from_env()

    print("Day 11 — Prompt Injection Evaluation")
    print("=" * 50)
    print(f"Attacks: {len(ATTACK_TECHNIQUES)} | Catalog: {len(INJECTION_CATALOG)} | Max iterations: {MAX_ITERATIONS}")

    print("\n[1/2] Testing VULNERABLE prompt...")
    vuln_report = test_system_prompt(VULNERABLE_SYSTEM, config, label="vulnerable")
    print(f"  Vulnerable: {vuln_report.total_breached} BREACHED / {vuln_report.total_held} HELD")
    for r in vuln_report.results:
        print(f"    {r.technique_label}: {r.verdict}")

    print("\n[2/2] Testing HARDENED prompt (iterative until 0 BREACHED)...")
    hard_report, iteration_reports, final_prompt = iterate_until_hardened(config)

    print(f"\nWriting {OUTPUT_PATH}...")
    OUTPUT_PATH.write_text(
        render_results(vuln_report, hard_report, iteration_reports, final_prompt),
        encoding="utf-8",
    )
    print(f"Done. Results: {OUTPUT_PATH}")

    print("\n" + "=" * 50)
    print("SUMMARY")
    print("=" * 50)
    print(f"Attacks total:   {len(ATTACK_TECHNIQUES)}")
    print(f"Vulnerable:      {vuln_report.total_breached}/{len(ATTACK_TECHNIQUES)} attacks succeeded")
    print(f"Hardened final:  {hard_report.total_breached}/{len(ATTACK_TECHNIQUES)} attacks succeeded")
    print(f"Iterations used: {len(iteration_reports)}")
    improvement = vuln_report.total_breached - hard_report.total_breached
    if improvement > 0:
        print(f"Improvement:     {improvement} attack(s) neutralized")
    elif hard_report.total_breached == 0:
        print("Result:          ✅ ALL attacks neutralized")
    else:
        print(f"Remaining:       {hard_report.total_breached} attack(s) not neutralized after {len(iteration_reports)} iterations")


if __name__ == "__main__":
    main()
