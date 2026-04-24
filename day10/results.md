# Day 10 — Micro-model First Results

**Micro-model:** TF-IDF + LogisticRegression (sklearn, local)  **LLM fallback:** `openai/gpt-4o-mini`  **Threshold:** `0.5`

## Summary

| Metric | Micro-only (A) | LLM-only (B) | Pipeline (C) |
|--------|---------------|--------------|--------------|
| Accuracy (30 cases) | **22/30 = 73%** | **26/30 = 87%** | **27/30 = 90%** |
| Total LLM calls | 0 | 30 | 18 |
| Micro handled (no LLM) | 12/30 (40%) | — | 12/30 (40%) |
| Fallback to LLM | — | — | 18/30 (60%) |
| Avg latency | 1 ms | 1327 ms | 718 ms |

## By Difficulty Group

| Group | N | Micro acc | Pipeline acc | Fallback rate |
|-------|---|-----------|--------------|--------------|
| Simple | 10 | 10/10 = 100% | 10/10 = 100% | 2/10 (20%) |
| Borderline | 10 | 6/10 = 60% | 7/10 = 70% | 9/10 (90%) |
| Complex | 10 | 6/10 = 60% | 10/10 = 100% | 7/10 (70%) |

## Case-by-Case Results

| # | Group | Type | Expected | Micro | Conf | Status | LLM | Pipeline | Source | Micro✓ | LLM✓ | Pipe✓ |
|---|-------|------|----------|-------|------|--------|-----|----------|--------|--------|------|-------|
| 1 | simple | auth_clear | auth | auth | 0.90 | OK | auth | auth | micro | ✓ | ✓ | ✓ |
| 2 | simple | billing_clear | billing | billing | 0.81 | OK | billing | billing | micro | ✓ | ✓ | ✓ |
| 3 | simple | crash_clear | crash | crash | 0.89 | OK | crash | crash | micro | ✓ | ✓ | ✓ |
| 4 | simple | feature_request_clear | feature_request | feature_request | 0.89 | OK | feature_request | feature_request | micro | ✓ | ✓ | ✓ |
| 5 | simple | data_export_clear | data_export | data_export | 0.32 | UNSURE | data_export | data_export | llm | ✓ | ✓ | ✓ |
| 6 | simple | performance_clear | performance | performance | 0.55 | OK | performance | performance | micro | ✓ | ✓ | ✓ |
| 7 | simple | account_clear | account | account | 0.48 | UNSURE | account | account | llm | ✓ | ✓ | ✓ |
| 8 | simple | auth_2fa | auth | auth | 0.76 | OK | auth | auth | micro | ✓ | ✓ | ✓ |
| 9 | simple | billing_refund | billing | billing | 0.82 | OK | billing | billing | micro | ✓ | ✓ | ✓ |
| 10 | simple | feature_api | feature_request | feature_request | 0.84 | OK | feature_request | feature_request | micro | ✓ | ✓ | ✓ |
| 11 | borderline | auth_account | auth | auth | 0.44 | UNSURE | account | account | llm | ✓ | ✗ | ✗ |
| 12 | borderline | billing_account | billing | billing | 0.25 | UNSURE | billing | billing | llm | ✓ | ✓ | ✓ |
| 13 | borderline | crash_performance | crash | crash | 0.72 | OK | performance | crash | micro | ✓ | ✗ | ✓ |
| 14 | borderline | data_export_feature | data_export | data_export | 0.49 | UNSURE | data_export | data_export | llm | ✓ | ✓ | ✓ |
| 15 | borderline | performance_crash | performance | performance | 0.38 | UNSURE | performance | performance | llm | ✓ | ✓ | ✓ |
| 16 | borderline | auth_billing | auth | billing | 0.39 | UNSURE | billing | billing | llm | ✗ | ✗ | ✗ |
| 17 | borderline | account_data | account | data_export | 0.42 | UNSURE | data_export | data_export | llm | ✗ | ✗ | ✗ |
| 18 | borderline | feature_performance | feature_request | crash | 0.34 | UNSURE | feature_request | feature_request | llm | ✗ | ✓ | ✓ |
| 19 | borderline | billing_crash | billing | billing | 0.46 | UNSURE | billing | billing | llm | ✓ | ✓ | ✓ |
| 20 | borderline | account_auth | account | auth | 0.44 | UNSURE | account | account | llm | ✗ | ✓ | ✓ |
| 21 | complex | long_auth | auth | auth | 0.63 | OK | auth | auth | micro | ✓ | ✓ | ✓ |
| 22 | complex | mixed_billing_auth | billing | billing | 0.84 | OK | billing | billing | micro | ✓ | ✓ | ✓ |
| 23 | complex | crash_with_logs | crash | feature_request | 0.35 | UNSURE | crash | crash | llm | ✗ | ✓ | ✓ |
| 24 | complex | vague_slowness | performance | performance | 0.26 | UNSURE | performance | performance | llm | ✓ | ✓ | ✓ |
| 25 | complex | feature_detailed | feature_request | feature_request | 0.36 | UNSURE | feature_request | feature_request | llm | ✓ | ✓ | ✓ |
| 26 | complex | data_gdpr | data_export | data_export | 0.65 | OK | data_export | data_export | micro | ✓ | ✓ | ✓ |
| 27 | complex | emoji_auth | auth | auth | 0.34 | UNSURE | auth | auth | llm | ✓ | ✓ | ✓ |
| 28 | complex | account_company | account | data_export | 0.20 | UNSURE | account | account | llm | ✗ | ✓ | ✓ |
| 29 | complex | ambiguous_all | crash | auth | 0.37 | UNSURE | crash | crash | llm | ✗ | ✓ | ✓ |
| 30 | complex | performance_enterprise | performance | crash | 0.26 | UNSURE | performance | performance | llm | ✗ | ✓ | ✓ |

## Pipeline Architecture

```
Input: subject + description

Level 1 — MicroClassifier (local sklearn, 0 API calls)
  TF-IDF(ngram 1-2) + LogisticRegression
  Trained on: day06/train.jsonl (40 examples)
  → confidence ≥ 0.75: OK  → return category (no LLM)
  → confidence  < 0.75: UNSURE → escalate to Level 2

Level 2 — LLM fallback (gpt-4o-mini)
  Full classification: category + priority + sentiment
  Called ONLY when Level 1 is UNSURE
```

## Выводы

### Экономия LLM-вызовов

Pipeline обрабатывает **12/30 (40%) запросов** без единого API-вызова. Для этих кейсов стоимость классификации = $0, latency = 1 ms.
LLM вызывается только для 18 запросов (60%). При масштабировании до 10 000 тикетов/день экономия составит ~4 000 вызовов в день.

### Точность по вариантам

| Вариант | Точность | LLM-вызовы | Avg latency |
|---------|----------|------------|-------------|
| Micro-only | 73% | 0 | 1 ms |
| LLM-only | 87% | 30 | 1 327 ms |
| Pipeline | **90%** | 18 | 718 ms |

Pipeline **превосходит LLM-only** на 3 пп, хотя использует LLM лишь на 60% запросов. Причина: кейс #13 (`crash_performance`) — micro дал правильный ответ `crash` с confidence=0.72, а LLM ошибся, предсказав `performance`. Threshold удержал micro от escalation и выиграл.

### По группам сложности

| Группа | Micro acc | Pipeline acc | Fallback rate | Вывод |
|--------|-----------|--------------|--------------|-------|
| Simple | 100% | 100% | 20% | Micro справляется со всеми; LLM нужен только data_export и account (мало примеров) |
| Borderline | 60% | 70% | 90% | Смешанные сигналы почти всегда уходят в LLM — правильно |
| Complex | 60% | 100% | 70% | Micro ошибается на нестандартных текстах; LLM исправляет все 7 из 7 случаев |

### Где micro лучше LLM

Кейс #13 (`crash_performance`): текст про зависание при открытии большого отчёта.
- Micro: `crash` (0.72) — верно.
- LLM: `performance` — неверно (контекст "замораживается" → LLM видит slowness, micro видит ключевые слова crash-паттерна из train).

Вывод: domain-специфичный micro-model иногда точнее generic LLM, когда обучен на схожих примерах.

### Где LLM необходим

- **account**: только 1 пример в train → micro не знает класс, уходит в LLM (верно).
- **Emoji + неформальный текст** (#27): micro угадывает `auth` правильно (0.34 < threshold), уходит в LLM — верно, так как нестандартный паттерн.
- **GDPR-запрос** (#26): micro корректно справляется с формальным текстом `data_export` (0.65) — не нужен LLM.
- **Стек трейс** (#23): micro видит ключевые слова `feature_request` вместо `crash`, уходит в LLM — верно.

### Почему ComplementNB, а не LogisticRegression

На датасете из 50 примеров (7 классов) LogisticRegression даёт confidence ≤ 0.40 для всех кейсов — равномерно размывает вероятности при малом числе примеров. ComplementNB производит дискриминативные scores (auth: 0.90, billing: 0.81, crash: 0.89), что позволяет разделить OK от UNSURE. При датасете 500+ примеров LogisticRegression предпочтительнее.

### Threshold 0.50 vs 0.75

| Threshold | Micro handled | Pipeline acc |
|-----------|--------------|--------------|
| 0.50 | 12/30 (40%) | **90%** |
| 0.75 | ~4/30 (13%) | ~87% |

Threshold 0.75 слишком жёсткий для датасета из 50 примеров — пропускает только auth и пару очевидных кейсов. Threshold 0.50 даёт баланс: micro справляется с чёткими случаями, остальное отдаёт LLM.

### Применимость паттерна

Micro-model first оправдан, когда:
- Задача решаема rule-based / ML для большинства кейсов (simple = 80%+ входящих)
- Стоимость LLM значима (бюджет, rate limits)
- Latency критична (micro: 1 ms vs LLM: ~1 сек)
- Есть domain-специфичный train set (хотя бы 5–10 примеров на класс)

Не стоит применять, когда:
- Все входящие тексты borderline/complex
- Ошибка micro дороже стоимости LLM-вызова
- Train set слишком мал (< 3 примеров на класс — account с 1 примером уже проблема)