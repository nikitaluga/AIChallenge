#!/usr/bin/env python3
"""
AI Code Review Script — День 32
Читает CLAUDE.md + .specs/*.md как архитектурный контекст,
вызывает OpenRouter API, генерирует структурированное ревью.
"""

import os
import json
import sys
import requests
from pathlib import Path

OPENROUTER_API_KEY = os.environ.get("OPENROUTER_API_KEY", "")
PR_TITLE = os.environ.get("PR_TITLE", "PR без заголовка")
PR_DIFF = os.environ.get("PR_DIFF", "")
GITHUB_OUTPUT = os.environ.get("GITHUB_OUTPUT", "")

MODEL = "openai/gpt-4o-mini"
MAX_DIFF_LENGTH = 8000
MAX_CONTEXT_LENGTH = 6000


def read_architecture_context() -> str:
    """Read CLAUDE.md + top .specs files as architecture context."""
    parts = []
    root = Path(".")

    claude_md = root / "CLAUDE.md"
    if claude_md.exists():
        content = claude_md.read_text(encoding="utf-8")[:3000]
        parts.append(f"=== CLAUDE.md ===\n{content}")

    specs_dir = root / ".specs"
    if specs_dir.exists():
        spec_files = sorted(specs_dir.glob("*.md"))[-5:]  # last 5 specs
        for spec_file in spec_files:
            content = spec_file.read_text(encoding="utf-8")[:500]
            parts.append(f"=== {spec_file.name} ===\n{content}")

    return "\n\n".join(parts)[:MAX_CONTEXT_LENGTH]


def call_openrouter(diff: str, context: str, title: str) -> dict:
    """Call OpenRouter API and return parsed review dict."""
    system_prompt = f"""Ты — эксперт по code review Kotlin Multiplatform проектов.
Проект использует MVI + Clean Architecture, Compose Multiplatform, Ktor.

Архитектурные правила из документации проекта:
{context}

Проанализируй git diff и верни ТОЛЬКО валидный JSON без markdown-блоков:
{{
  "bugs": ["описание потенциального бага 1"],
  "architecture": ["архитектурная проблема 1"],
  "recommendations": ["рекомендация 1"],
  "summary": "Краткое резюме (2-3 предложения)"
}}

Правила анализа:
- bugs: null pointer, race condition, утечки ресурсов, неправильная обработка Result/ошибок
- architecture: нарушение MVI (изменение State вне ViewModel), нарушение Clean Architecture (UI импортирует data-слой), KMP-специфичные проблемы (platform-зависимый код в commonMain)
- recommendations: улучшения читаемости, производительности, соответствия паттернам проекта
- Если нет проблем в категории — пустой массив []
- Отвечай на русском языке"""

    user_message = f"PR: {title}\n\n```diff\n{diff[:MAX_DIFF_LENGTH]}\n```"

    response = requests.post(
        "https://openrouter.ai/api/v1/chat/completions",
        headers={
            "Authorization": f"Bearer {OPENROUTER_API_KEY}",
            "Content-Type": "application/json",
            "HTTP-Referer": "https://github.com/AIChallenge",
        },
        json={
            "model": MODEL,
            "messages": [
                {"role": "system", "content": system_prompt},
                {"role": "user", "content": user_message},
            ],
            "response_format": {"type": "json_object"},
            "temperature": 0.2,
        },
        timeout=60,
    )
    response.raise_for_status()
    content = response.json()["choices"][0]["message"]["content"]
    return json.loads(content)


def format_markdown(review: dict, title: str, diff_len: int) -> str:
    """Format review as GitHub PR comment markdown."""
    bugs = review.get("bugs", [])
    architecture = review.get("architecture", [])
    recommendations = review.get("recommendations", [])
    summary = review.get("summary", "")

    lines = [
        "## 🤖 AI Code Review",
        "",
        f"> **PR:** {title}  ",
        f"> Проанализировано: {min(diff_len, MAX_DIFF_LENGTH)} символов diff",
        "",
    ]

    if summary:
        lines += [f"**Резюме:** {summary}", ""]

    if bugs:
        lines.append("### 🐛 Потенциальные баги")
        for bug in bugs:
            lines.append(f"- {bug}")
        lines.append("")

    if architecture:
        lines.append("### 🏗 Архитектурные проблемы")
        for issue in architecture:
            lines.append(f"- {issue}")
        lines.append("")

    if recommendations:
        lines.append("### 💡 Рекомендации")
        for rec in recommendations:
            lines.append(f"- {rec}")
        lines.append("")

    if not bugs and not architecture and not recommendations:
        lines += ["### ✅ Проблем не обнаружено", ""]

    lines.append("---")
    lines.append("*Сгенерировано [AI Review](/.github/workflows/ai-review.yml) · День 32*")

    return "\n".join(lines)


def set_output(name: str, value: str) -> None:
    """Write GitHub Actions output variable."""
    if not GITHUB_OUTPUT:
        print(f"[output] {name}={value[:200]}")
        return
    with open(GITHUB_OUTPUT, "a", encoding="utf-8") as f:
        f.write(f"{name}<<EOF\n{value}\nEOF\n")


def main() -> None:
    if not OPENROUTER_API_KEY:
        print("⚠️  OPENROUTER_API_KEY не задан — пропускаем AI review")
        set_output("review_markdown", "")
        return

    if not PR_DIFF.strip():
        print("ℹ️  Diff пустой (возможно, нет изменений в .kt/.gradle файлах)")
        set_output("review_markdown", "")
        return

    print(f"📖 Читаем архитектурный контекст...")
    context = read_architecture_context()
    print(f"   Контекст: {len(context)} символов")

    print(f"🔍 Анализируем diff ({len(PR_DIFF)} символов)...")
    try:
        review = call_openrouter(diff=PR_DIFF, context=context, title=PR_TITLE)
    except Exception as e:
        print(f"❌ Ошибка вызова LLM: {e}", file=sys.stderr)
        set_output("review_markdown", "")
        sys.exit(0)  # не падаем CI из-за ошибки ревью

    markdown = format_markdown(review, PR_TITLE, len(PR_DIFF))
    print("✅ Ревью готово")
    print(markdown[:500])

    set_output("review_markdown", markdown)


if __name__ == "__main__":
    main()
