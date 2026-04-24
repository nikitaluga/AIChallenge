#!/usr/bin/env python3
"""
Day 10 — Batch evaluation: Micro-only vs LLM-only vs Pipeline.

30 test cases: 10 simple + 10 borderline + 10 complex.
Writes comparison report to results.md.

Usage:
    cd day10
    python eval_micro.py
    python eval_micro.py --threshold 0.8
    python eval_micro.py --no-llm    # skip LLM variants, micro stats only
"""

import argparse
import json
import os
import sys
import time
from dataclasses import dataclass
from pathlib import Path
from typing import Optional

sys.path.insert(0, str(Path(__file__).parent))
from micro import MicroClassifier
from pipeline import MicroFirstPipeline, PipelineConfig, PipelineResult, config_from_env

OUTPUT_FILE = Path(__file__).parent / "results.md"
TRAIN_PATH = Path(__file__).parent.parent / "day06" / "train.jsonl"

# ── Test Cases ────────────────────────────────────────────────────────────────
# Format: id, type, group, subject, description, expected_category

TEST_CASES = [
    # ── SIMPLE (10) ── clear single-category signals ──────────────────────────
    {
        "id": 1, "group": "simple", "type": "auth_clear",
        "expected": "auth",
        "subject": "Не могу войти в аккаунт",
        "description": "Ввожу правильный пароль, но получаю ошибку авторизации. Сбросил пароль — не помогло.",
    },
    {
        "id": 2, "group": "simple", "type": "billing_clear",
        "expected": "billing",
        "subject": "Списали деньги дважды",
        "description": "С моей карты списали оплату за подписку два раза за один месяц. Прошу вернуть.",
    },
    {
        "id": 3, "group": "simple", "type": "crash_clear",
        "expected": "crash",
        "subject": "Приложение вылетает при запуске",
        "description": "После обновления до версии 3.2 приложение падает сразу при открытии. Android 13.",
    },
    {
        "id": 4, "group": "simple", "type": "feature_request_clear",
        "expected": "feature_request",
        "subject": "Добавьте тёмную тему",
        "description": "Хотелось бы иметь тёмный режим в приложении. Очень не хватает для работы ночью.",
    },
    {
        "id": 5, "group": "simple", "type": "data_export_clear",
        "expected": "data_export",
        "subject": "Как выгрузить данные в Excel?",
        "description": "Мне нужно экспортировать отчёт за год в формате Excel. Не нашёл такой опции.",
    },
    {
        "id": 6, "group": "simple", "type": "performance_clear",
        "expected": "performance",
        "subject": "Страница загружается очень медленно",
        "description": "Дашборд открывается по 30 секунд. Интернет нормальный, другие сайты работают быстро.",
    },
    {
        "id": 7, "group": "simple", "type": "account_clear",
        "expected": "account",
        "subject": "Хочу удалить аккаунт",
        "description": "Прошу удалить мой аккаунт и все данные. Больше не пользуюсь сервисом.",
    },
    {
        "id": 8, "group": "simple", "type": "auth_2fa",
        "expected": "auth",
        "subject": "Код двухфакторной аутентификации не приходит",
        "description": "Включил 2FA, но SMS с кодом не приходит уже час. Телефон работает нормально.",
    },
    {
        "id": 9, "group": "simple", "type": "billing_refund",
        "expected": "billing",
        "subject": "Требую возврат средств за неиспользованный период",
        "description": "Отменил подписку 3 дня назад, но деньги за оставшиеся 27 дней не вернули.",
    },
    {
        "id": 10, "group": "simple", "type": "feature_api",
        "expected": "feature_request",
        "subject": "Нужен API для интеграции",
        "description": "Хотим интегрировать ваш сервис с нашей CRM. Есть ли REST API? Документация?",
    },
    # ── BORDERLINE (10) ── mixed signals, ambiguous ────────────────────────────
    {
        "id": 11, "group": "borderline", "type": "auth_account",
        "expected": "auth",
        "subject": "Не могу сменить email в профиле",
        "description": "При попытке изменить email в настройках аккаунта просит заново войти, но после входа снова просит — бесконечный цикл.",
    },
    {
        "id": 12, "group": "borderline", "type": "billing_account",
        "expected": "billing",
        "subject": "Хочу изменить тарифный план",
        "description": "Нашёл кнопку смены тарифа, нажал — ничего не происходит. Хочу перейти с Basic на Pro.",
    },
    {
        "id": 13, "group": "borderline", "type": "crash_performance",
        "expected": "crash",
        "subject": "Приложение зависает и перестаёт отвечать",
        "description": "При открытии большого отчёта (1000+ строк) интерфейс замораживается на несколько минут. Иногда приходится убивать процесс.",
    },
    {
        "id": 14, "group": "borderline", "type": "data_export_feature",
        "expected": "data_export",
        "subject": "Нет возможности выгрузить PDF",
        "description": "Экспорт в CSV есть, но PDF не поддерживается. Очень нужно для предоставления клиентам.",
    },
    {
        "id": 15, "group": "borderline", "type": "performance_crash",
        "expected": "performance",
        "subject": "Поиск работает очень долго",
        "description": "Поиск по базе занимает 2-3 минуты. Раньше было секунды. Иногда выдаёт ошибку таймаута.",
    },
    {
        "id": 16, "group": "borderline", "type": "auth_billing",
        "expected": "auth",
        "subject": "После оплаты премиум не активировался",
        "description": "Оплатил подписку, деньги списаны, но функции премиума не доступны. Попробовал выйти и войти — не помогло.",
    },
    {
        "id": 17, "group": "borderline", "type": "account_data",
        "expected": "account",
        "subject": "Хочу скачать все свои данные перед удалением аккаунта",
        "description": "Планирую удалить аккаунт. Могу ли я сначала скачать архив всех моих данных согласно GDPR?",
    },
    {
        "id": 18, "group": "borderline", "type": "feature_performance",
        "expected": "feature_request",
        "subject": "Кеширование результатов поиска",
        "description": "Предлагаю добавить кеш для часто используемых запросов. Сейчас каждый раз ждём полной загрузки.",
    },
    {
        "id": 19, "group": "borderline", "type": "billing_crash",
        "expected": "billing",
        "subject": "Ошибка при оплате — деньги списали но заказ не создан",
        "description": "Нажал оплатить, страница упала с ошибкой 500, но деньги со счёта ушли. Заказа в истории нет.",
    },
    {
        "id": 20, "group": "borderline", "type": "account_auth",
        "expected": "account",
        "subject": "Меня взломали, прошу заблокировать аккаунт",
        "description": "Замечаю подозрительные входы из другого города. Прошу временно заблокировать аккаунт до смены пароля.",
    },
    # ── COMPLEX (10) ── non-standard, emotional, multi-signal ─────────────────
    {
        "id": 21, "group": "complex", "type": "long_auth",
        "expected": "auth",
        "subject": "СРОЧНО! Не могу войти уже 3 дня!!!",
        "description": "Три дня не могу попасть в свой аккаунт. Пробовал: сброс пароля (письма нет), вход через Google (ошибка OAuth), звонок в поддержку (не отвечают). У меня там важные рабочие документы. Пожалуйста помогите СРОЧНО.",
    },
    {
        "id": 22, "group": "complex", "type": "mixed_billing_auth",
        "expected": "billing",
        "subject": "Продолжают списывать деньги после отмены подписки",
        "description": "Отменил подписку 2 месяца назад. Каждый месяц продолжают списывать 590 рублей. В поддержку писал, говорят 'всё отменено', но списания идут. Это мошенничество. Требую возврат за 2 месяца и объяснений.",
    },
    {
        "id": 23, "group": "complex", "type": "crash_with_logs",
        "expected": "crash",
        "subject": "NullPointerException в модуле отчётов",
        "description": "java.lang.NullPointerException at com.example.reports.ReportBuilder.generate(ReportBuilder.java:142). Воспроизводится при экспорте отчёта с фильтром по дате. Stack trace прикреплён. Version 4.1.2, Java 11.",
    },
    {
        "id": 24, "group": "complex", "type": "vague_slowness",
        "expected": "performance",
        "subject": "Всё тормозит",
        "description": "Последние дни что-то пошло не так. Открываю — долго. Нажимаю — долго. Сохраняю — тоже долго. Интернет норм проверил.",
    },
    {
        "id": 25, "group": "complex", "type": "feature_detailed",
        "expected": "feature_request",
        "subject": "Предложение по улучшению UX дашборда",
        "description": "Работаю с вашим продуктом уже 2 года. Хочу предложить несколько улучшений: 1) виджеты на главной должны быть настраиваемыми, 2) нужна кнопка 'Экспорт всего', 3) тёмная тема (очень прошу!). Готов участвовать в бета-тесте.",
    },
    {
        "id": 26, "group": "complex", "type": "data_gdpr",
        "expected": "data_export",
        "subject": "GDPR запрос на выгрузку персональных данных",
        "description": "В соответствии с GDPR (статья 20, право на переносимость данных) прошу предоставить все мои персональные данные в машиночитаемом формате в течение 30 дней. Если не получу ответ — обращусь в Роскомнадзор.",
    },
    {
        "id": 27, "group": "complex", "type": "emoji_auth",
        "expected": "auth",
        "subject": "помогите 😭😭😭 не заходит!!!",
        "description": "я уже всё перепробовала, пароль меняла, через телефон пробовала, через комп тоже, нииичего не работает, у меня там все фотки с прошлого года 😭 когда уже исправят?!",
    },
    {
        "id": 28, "group": "complex", "type": "account_company",
        "expected": "account",
        "subject": "Передача аккаунта при смене сотрудника",
        "description": "Наш системный администратор уволился. Как нам переоформить корпоративный аккаунт на нового администратора? Старый сотрудник не отвечает на звонки.",
    },
    {
        "id": 29, "group": "complex", "type": "ambiguous_all",
        "expected": "crash",
        "subject": "После обновления ничего не работает",
        "description": "Вчера автоматически обновилось приложение. Теперь: кнопка входа не нажимается (auth?), оплата выдаёт ошибку (billing?), отчёты не открываются (crash?). Что вообще произошло с обновлением?",
    },
    {
        "id": 30, "group": "complex", "type": "performance_enterprise",
        "expected": "performance",
        "subject": "Деградация производительности после миграции на облако",
        "description": "После перехода на облачную версию v2.0 время отклика API выросло с 120ms до 3400ms (p95). Нагрузка та же — ~500 RPS. Профилирование показывает узкое место в SQL-запросах. Можем предоставить Prometheus-метрики.",
    },
]


# ── LLM-only call ─────────────────────────────────────────────────────────────

def _llm_classify(subject: str, description: str, pipeline: MicroFirstPipeline) -> tuple[str, int]:
    """Returns (category, latency_ms). Uses pipeline's LLM client directly."""
    start = time.monotonic()
    category, _, _ = pipeline._call_llm(subject, description)
    return category, int((time.monotonic() - start) * 1000)


# ── Run ────────────────────────────────────────────────────────────────────────

@dataclass
class CaseRow:
    case: dict
    micro_category: str
    micro_confidence: float
    micro_status: str
    llm_category: str
    pipeline_category: str
    pipeline_source: str
    pipeline_llm_calls: int
    micro_latency_ms: int
    llm_latency_ms: int
    pipeline_latency_ms: int


def run_eval(cases: list[dict], pipeline: MicroFirstPipeline, skip_llm: bool = False) -> list[CaseRow]:
    rows = []
    for case in cases:
        subj = case["subject"]
        desc = case["description"]
        text = f"Subject: {subj}\n\nDescription: {desc}"

        # Micro-only
        t0 = time.monotonic()
        micro_result = pipeline._micro.predict(text)
        micro_ms = int((time.monotonic() - t0) * 1000)

        # LLM-only
        if skip_llm:
            llm_cat = "—"
            llm_ms = 0
        else:
            llm_cat, llm_ms = _llm_classify(subj, desc, pipeline)

        # Pipeline
        pipe_result = pipeline.classify(subj, desc)

        row = CaseRow(
            case=case,
            micro_category=micro_result.category,
            micro_confidence=micro_result.confidence,
            micro_status=micro_result.status,
            llm_category=llm_cat,
            pipeline_category=pipe_result.category,
            pipeline_source=pipe_result.source,
            pipeline_llm_calls=pipe_result.total_llm_calls,
            micro_latency_ms=micro_ms,
            llm_latency_ms=llm_ms,
            pipeline_latency_ms=pipe_result.latency_ms,
        )
        rows.append(row)

        exp = case["expected"]
        micro_ok = "✓" if micro_result.category == exp else "✗"
        llm_ok = "✓" if llm_cat == exp else ("—" if skip_llm else "✗")
        pipe_ok = "✓" if pipe_result.category == exp else "✗"
        print(
            f"[{case['id']:02d}] {case['type']:22s} "
            f"micro={micro_result.category:16s}({micro_result.confidence:.2f}){micro_ok}  "
            f"llm={llm_cat:16s}{llm_ok}  "
            f"pipe={pipe_result.category:16s}({pipe_result.source}){pipe_ok}"
        )
    return rows


# ── Report ─────────────────────────────────────────────────────────────────────

def write_report(rows: list[CaseRow], cfg: PipelineConfig, skip_llm: bool) -> None:
    n = len(rows)
    expected = [r.case["expected"] for r in rows]

    micro_correct = sum(1 for r in rows if r.micro_category == r.case["expected"])
    llm_correct = sum(1 for r in rows if not skip_llm and r.llm_category == r.case["expected"])
    pipe_correct = sum(1 for r in rows if r.pipeline_category == r.case["expected"])

    micro_handled = sum(1 for r in rows if r.micro_status == "OK")
    fallback_count = sum(1 for r in rows if r.pipeline_source == "llm")
    total_pipeline_llm_calls = sum(r.pipeline_llm_calls for r in rows)

    avg_micro_ms = int(sum(r.micro_latency_ms for r in rows) / n)
    avg_llm_ms = int(sum(r.llm_latency_ms for r in rows) / n) if not skip_llm else 0
    avg_pipe_ms = int(sum(r.pipeline_latency_ms for r in rows) / n)

    # By group
    groups = ["simple", "borderline", "complex"]
    group_stats: dict[str, dict] = {}
    for g in groups:
        g_rows = [r for r in rows if r.case["group"] == g]
        group_stats[g] = {
            "n": len(g_rows),
            "micro": sum(1 for r in g_rows if r.micro_category == r.case["expected"]),
            "pipe": sum(1 for r in g_rows if r.pipeline_category == r.case["expected"]),
            "fallback": sum(1 for r in g_rows if r.pipeline_source == "llm"),
        }

    lines = [
        "# Day 10 — Micro-model First Results",
        "",
        f"**Micro-model:** TF-IDF + ComplementNB (sklearn, local)  "
        f"**LLM fallback:** `{cfg.llm_model}`  "
        f"**Threshold:** `{cfg.confidence_threshold}`",
        "",
        "## Summary",
        "",
        "| Metric | Micro-only (A) | LLM-only (B) | Pipeline (C) |",
        "|--------|---------------|--------------|--------------|",
        f"| Accuracy (30 cases) | **{micro_correct}/{n} = {micro_correct/n:.0%}** | "
        f"{'**' + str(llm_correct) + '/' + str(n) + ' = ' + f'{llm_correct/n:.0%}' + '**' if not skip_llm else '—'} | "
        f"**{pipe_correct}/{n} = {pipe_correct/n:.0%}** |",
        f"| Total LLM calls | 0 | {n if not skip_llm else '—'} | {total_pipeline_llm_calls} |",
        f"| Micro handled (no LLM) | {micro_handled}/{n} ({micro_handled/n:.0%}) | — | {n - fallback_count}/{n} ({(n-fallback_count)/n:.0%}) |",
        f"| Fallback to LLM | — | — | {fallback_count}/{n} ({fallback_count/n:.0%}) |",
        f"| Avg latency | {avg_micro_ms} ms | {avg_llm_ms} ms | {avg_pipe_ms} ms |",
        "",
        "## By Difficulty Group",
        "",
        "| Group | N | Micro acc | Pipeline acc | Fallback rate |",
        "|-------|---|-----------|--------------|--------------|",
    ]
    for g in groups:
        s = group_stats[g]
        lines.append(
            f"| {g.capitalize()} | {s['n']} | {s['micro']}/{s['n']} = {s['micro']/s['n']:.0%} "
            f"| {s['pipe']}/{s['n']} = {s['pipe']/s['n']:.0%} "
            f"| {s['fallback']}/{s['n']} ({s['fallback']/s['n']:.0%}) |"
        )

    lines += [
        "",
        "## Case-by-Case Results",
        "",
        "| # | Group | Type | Expected | Micro | Conf | Status | LLM | Pipeline | Source | Micro✓ | LLM✓ | Pipe✓ |",
        "|---|-------|------|----------|-------|------|--------|-----|----------|--------|--------|------|-------|",
    ]

    for r in rows:
        exp = r.case["expected"]
        micro_ok = "✓" if r.micro_category == exp else "✗"
        llm_ok = "✓" if (not skip_llm and r.llm_category == exp) else ("—" if skip_llm else "✗")
        pipe_ok = "✓" if r.pipeline_category == exp else "✗"
        lines.append(
            f"| {r.case['id']} | {r.case['group']} | {r.case['type']} | {exp} "
            f"| {r.micro_category} | {r.micro_confidence:.2f} | {r.micro_status} "
            f"| {r.llm_category} | {r.pipeline_category} | {r.pipeline_source} "
            f"| {micro_ok} | {llm_ok} | {pipe_ok} |"
        )

    lines += [
        "",
        "## Pipeline Architecture",
        "",
        "```",
        "Input: subject + description",
        "",
        "Level 1 — MicroClassifier (local sklearn, 0 API calls)",
        "  TF-IDF(ngram 1-2) + ComplementNaiveBayes",
        "  Trained on: day06/train.jsonl + eval.jsonl (50 examples)",
        "  → confidence ≥ threshold: OK  → return category (no LLM)",
        "  → confidence  < threshold: UNSURE → escalate to Level 2",
        "",
        "Level 2 — LLM fallback (gpt-4o-mini)",
        "  Full classification: category + priority + sentiment",
        "  Called ONLY when Level 1 is UNSURE",
        "```",
        "",
        "## Выводы",
        "",
        f"### Экономия LLM-вызовов",
        f"Pipeline использует LLM только для {fallback_count}/{n} запросов ({fallback_count/n:.0%}). "
        f"Micro-model обрабатывает {n-fallback_count}/{n} ({(n-fallback_count)/n:.0%}) без единого API-вызова.",
        "",
        f"### Точность",
        f"- Micro-only: {micro_correct/n:.0%} — ограничена 40 примерами обучения",
        (f"- LLM-only: {llm_correct/n:.0%}" if not skip_llm else "- LLM-only: пропущен (--no-llm)"),
        f"- Pipeline: {pipe_correct/n:.0%} — LLM исправляет UNSURE кейсы",
        "",
        f"### По группам сложности",
        f"- Simple: micro справляется с большинством чётких кейсов",
        f"- Borderline: fallback-rate выше, LLM нужен для неоднозначных сигналов",
        f"- Complex: многосигнальные тексты требуют LLM чаще",
        "",
        f"### Латентность",
        f"- Micro: {avg_micro_ms} ms (локально, без сети)",
        f"- Pipeline avg: {avg_pipe_ms} ms (mix: {avg_micro_ms}ms local + ~1000ms LLM для {fallback_count} случаев)",
    ]

    OUTPUT_FILE.write_text("\n".join(lines), encoding="utf-8")
    print(f"\nReport saved: {OUTPUT_FILE}")


# ── Main ───────────────────────────────────────────────────────────────────────

def main() -> None:
    parser = argparse.ArgumentParser(description="Day 10 — Micro-model first eval")
    parser.add_argument("--threshold", type=float, default=0.50)
    parser.add_argument("--model", default="openai/gpt-4o-mini")
    parser.add_argument("--no-llm", action="store_true", help="Skip LLM-only variant (micro + pipeline only)")
    parser.add_argument("--group", choices=["simple", "borderline", "complex"], help="Run only one group")
    args = parser.parse_args()

    cfg = config_from_env()
    cfg.confidence_threshold = args.threshold
    cfg.llm_model = args.model

    pipeline = MicroFirstPipeline(cfg)

    cases = TEST_CASES
    if args.group:
        cases = [c for c in TEST_CASES if c["group"] == args.group]

    print(f"=== Day 10 — Micro-model First ===")
    print(f"Micro: TF-IDF+ComplementNB (sklearn, train.jsonl={TRAIN_PATH.name})")
    print(f"LLM fallback: {cfg.llm_model}  threshold={cfg.confidence_threshold}")
    print(f"Cases: {len(cases)}\n")

    rows = run_eval(cases, pipeline, skip_llm=args.no_llm)
    write_report(rows, cfg, skip_llm=args.no_llm)

    # Console summary
    n = len(rows)
    micro_ok = sum(1 for r in rows if r.micro_category == r.case["expected"])
    pipe_ok = sum(1 for r in rows if r.pipeline_category == r.case["expected"])
    handled = sum(1 for r in rows if r.pipeline_source == "micro")
    print(f"\nMicro: {micro_ok}/{n}={micro_ok/n:.0%}  Pipeline: {pipe_ok}/{n}={pipe_ok/n:.0%}  "
          f"LLM calls: {n - handled}  Micro handled: {handled}/{n}={handled/n:.0%}")


if __name__ == "__main__":
    main()
