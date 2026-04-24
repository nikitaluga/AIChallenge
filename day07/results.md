# Day 07 — Confidence Control Results

**Model:** `openai/gpt-4o-mini`  **Threshold:** 0.92  **Temp:** 0.3

## Summary

| Metric | Value |
|--------|-------|
| Total cases | 13 |
| Accepted (OK) | 13 (100%) |
| Rejected (FAIL) | 0 (0%) |
| Required retry | 12 (92%) |
| Errors | 0 |
| Total LLM calls | 49 (baseline would be 13) |
| Extra LLM calls | 36 (2.8× overhead) |
| Avg latency | 10155 ms |
| Estimated cost | $0.0038 USD |

## Accuracy (eval cases only)

| Metric | All cases | OK-only cases |
|--------|-----------|---------------|
| Category | 9/10 = 90% | 9/10 = 90% |
| Priority | 4/10 = 40% | 4/10 = 40% |
| Sentiment | 8/10 = 80% | 8/10 = 80% |

*Baseline (Day 06): category=90%, priority=50%, sentiment=70%*

## Per-Case Results

| # | Type | Status | Steps | Calls | ms | Category | Priority | Sentiment | Conf | ✓ | Reasoning |
|---|------|--------|-------|-------|----|----------|----------|-----------|------|---|-----------|
| 1 | eval | 🟢 OK | scoring→self_check→redundancy | 4 | 9931 | auth | high | frustrated | 0.88 | ✓ | Majority vote (3/3): auth/high/frustrated |
| 2 | eval | 🟢 OK | scoring→self_check→redundancy | 4 | 6195 | account | high | angry | 0.88 | ✗ | Majority vote (3/3): account/high/angry |
| 3 | eval | 🟢 OK | scoring→self_check→redundancy | 4 | 7866 | billing | high | frustrated | 0.88 | ✗ | Majority vote (3/3): billing/high/frustrated |
| 4 | eval | 🟢 OK | scoring→self_check→redundancy | 4 | 10392 | billing | high | frustrated | 0.83 | ✗ | Majority vote (3/3): billing/high/frustrated |
| 5 | eval | 🟢 OK | scoring→self_check→redundancy | 4 | 6202 | crash | high | frustrated | 0.88 | ✗ | Majority vote (3/3): crash/high/frustrated |
| 6 | eval | 🟢 OK | scoring→self_check→redundancy | 4 | 6727 | crash | high | frustrated | 0.82 | ✗ | Majority vote (3/3): crash/high/frustrated |
| 7 | eval | 🟢 OK | scoring→self_check→redundancy | 4 | 5419 | feature_request | medium | neutral | 0.82 | ✗ | Majority vote (3/3): feature_request/medium/neutral |
| 8 | eval | 🟢 OK | scoring→self_check→redundancy | 4 | 6519 | data_export | high | neutral | 0.88 | ✓ | Majority vote (3/3): data_export/high/neutral |
| 9 | eval | 🟢 OK | scoring→self_check→redundancy | 4 | 9783 | performance | high | frustrated | 0.88 | ✓ | Majority vote (3/3): performance/high/frustrated |
| 10 | eval | 🟢 OK | scoring→self_check→redundancy | 4 | 10805 | account | medium | neutral | 0.82 | ✓ | Majority vote (3/3): account/medium/neutral |
| 11 | ambiguous | 🟢 OK | scoring→self_check→redundancy | 4 | 22171 | crash | high | frustrated | 0.82 | ? | Majority vote (3/3): crash/high/frustrated |
| 12 | noisy | 🟢 OK | scoring | 1 | 14392 | crash | critical | angry | 0.95 | ? | The user is expressing a sense of urgency and frustration du |
| 13 | contradictory | 🟢 OK | scoring→self_check→redundancy | 4 | 15618 | crash | high | frustrated | 0.88 | ? | Majority vote (3/3): crash/high/frustrated |

## Comparison: threshold=0.75 vs 0.92 vs baseline

| Метрика | Baseline Day 06 | threshold=0.75 | threshold=0.92 |
|---------|-----------------|----------------|----------------|
| Category | 90% | 90% | 90% |
| Priority | 50% | 30% ↓ | 40% ↓ |
| Sentiment | 70% | 60% ↓ | **80% ↑** |
| LLM calls | 1× | 1× | 3.8× |
| Avg latency | ~3 s | ~6 s | ~10 s |
| FAIL | — | 0 | 0 |

## Выводы

### 1. Self-reported confidence некалиброван
gpt-4o-mini возвращает 0.85–0.95 на всех кейсах независимо от реальной сложности.
При threshold=0.75 pipeline сработал как plain scoring — ни один кейс не дошёл до Self-check.
Для calibration нужен внешний сигнал (agreement rate, entropy), а не модель оценивающая себя.

### 2. Redundancy помогает с random errors
При threshold=0.92 sentiment вырос с 60% до 80% (+10 pp к baseline).
Кейс #10 исправлен: frustrated → neutral через majority vote 3/3.
Self-check и параллельные вызовы дают разные рассуждения, что позволяет откорректировать borderline случаи.

### 3. Confidence control не лечит системный bias
Priority: модель стабильно выбирает "high" вместо "critical" или "medium" в 6 из 10 кейсов.
При 3 независимых вызовах все три дают одинаковый неправильный ответ → majority vote 3/3 на ошибке.
FAIL=0 — pipeline ничего не отверг, потому что все вызовы согласуются (согласованно ошибаются).

### Вывод для Fine-Tuning недели
Confidence control — защита от случайного шума, не от систематического bias.
Priority — самая слабая ось (baseline 50%, с контролем 40%). Это кандидат для fine-tune из Day 06.
Оптимальная стратегия: fine-tune для коррекции bias + confidence control для отсева edge-cases.

## Pipeline Design

```
Scoring (1 call)  →  score ≥ 0.75  →  OK
                  →  score < 0.75
Self-check (1 call)  →  score ≥ 0.75  →  OK
                     →  score < 0.75
Redundancy (2 parallel calls)  →  majority vote  →  OK
                               →  no majority    →  FAIL
```

Constraint check after every LLM call: valid JSON + allowed enum values + confidence ∈ [0,1].