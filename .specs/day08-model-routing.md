# День 8 — Model Routing: gpt-4o-mini → gpt-4o

## Задача
Routing запросов между дешёвой (gpt-4o-mini) и сильной (gpt-4o) моделями.
Эскалация при низком confidence или коротком reasoning.
Python-скрипты в `day08/` (не KMP, аналогично day06/day07).

## Контекст кодовой базы
- `day07/confidence.py` — `classify_with_confidence()`, `Config`, `Classification` — переиспользуется
- `day06/eval.jsonl` — 10 кейсов с ground truth для batch-теста
- RouterAI: `https://routerai.ru/api/v1`, OpenAI-совместимый API

## Эвристики эскалации (комбо — ИЛИ)
1. **Confidence** — `fast_confidence < 0.75`
2. **Длина reasoning** — `len(reasoning.split()) < 8` слов

## Модели
| Tier | Model | ~Cost/call |
|------|-------|-----------|
| Fast | `openai/gpt-4o-mini` | $0.000_078 |
| Strong | `openai/gpt-4o` | $0.002_100 |

## Файлы
- `day08/router.py` — `RouterConfig`, `RoutingResult`, `route_classify()` (importable)
- `day08/eval_router.py` — batch runner (10 eval + 5 edge cases) → `results.md`
- `day08/results.md` — создаётся скриптом

## Структура RoutingResult
```python
@dataclass
class RoutingResult:
    classification: Classification   # финальный ответ
    model_used: str                  # "fast" | "strong"
    escalated: bool
    escalation_reason: str | None    # "low_confidence" | "short_reasoning" | "both" | None
    fast_clf: Classification         # что ответил gpt-4o-mini
    total_calls: int                 # 1 или 2
    latency_ms: int
```

## Метрики отчёта
- Количество запросов: mini-only vs escalated
- Accuracy (category/priority/sentiment) — fast-only vs routing vs baseline Day06
- Cost: fast-only vs routing vs strong-only
- Per-case таблица: subject, fast_conf, escalated?, final_model, correct?

## Тестовые данные
10 кейсов из `day06/eval.jsonl` + 5 edge-cases в коде (ambiguous, noisy, contradictory, short, multilingual).
