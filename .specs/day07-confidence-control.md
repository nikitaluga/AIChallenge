# День 7 — Confidence Control: Оценка уверенности при классификации тикетов

## Контекст
Часть недели Fine Tuning (дни 6–10). Python-скрипты в `day07/`. Не KMP.  
Задача из Day 6: классификация тикетов (category + priority + sentiment).  
Ошибка недопустима — pipeline отклоняет ненадёжные результаты.

## Pipeline (3 подхода)

```
Scoring (1 вызов)
  → score ≥ 0.75: OK, вернуть
  → score < 0.75
Self-check (1 вызов — модель критикует себя)
  → score ≥ 0.75: OK, вернуть
  → score < 0.75
Redundancy (2 параллельных вызова + majority vote)
  → 2/3 согласны: OK, вернуть
  → нет majority: FAIL
```

Constraint-based проверка на каждом шаге:
- JSON валиден
- category/priority/sentiment в допустимых множествах
- confidence ∈ [0.0, 1.0]

## Параметры
- model: `openai/gpt-4o-mini` через RouterAI
- threshold: 0.75
- temperature: 0.3 (детерминизм)

## Данные
- `../day06/eval.jsonl` (10 кейсов с ground truth)
- 3 boundary кейса в коде: ambiguous, noisy, contradictory

## Файлы
- `day07/confidence.py` — pipeline (importable)
- `day07/eval_confidence.py` — batch runner + report
- `day07/results.md` — создаётся скриптом

## Метрики
- rejected count, retry count, extra LLM calls
- avg latency, estimated cost
- accuracy (category/priority/sentiment) — только OK-кейсы vs все
