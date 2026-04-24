# Day 10 — Micro-model First: проверка перед LLM

## Задача

Классификация тикетов поддержки (домен day07). Level 1 — локальный sklearn TF-IDF + LogisticRegression (нулевые API-вызовы). Level 2 — LLM fallback только при UNSURE.

## Домен

Поле `category` из day07: `auth | billing | crash | feature_request | data_export | performance | account`
Поля `priority` и `sentiment` — всегда от LLM (micro-model не классифицирует их).

## Архитектура

```
Level 1 — MicroClassifier (local sklearn, no API)
  text → TF-IDF → LogisticRegression.predict_proba
  confidence = max(proba) → если ≥ 0.75: return OK(category, confidence)
                          → если  < 0.75: return UNSURE

Level 2 — LLM fallback (gpt-4o-mini)
  только если Level 1 вернул UNSURE
  полная классификация: category + priority + sentiment
  промпт из day07 (SCORING_SYSTEM без confidence/reasoning)
```

## Обучающая выборка

Источник: `day06/train.jsonl` (40 примеров, JSONL формат).
Парсинг: `messages[1].content` (user) → текст; `messages[2].content` (assistant) → JSON → `category`.

## Файлы

| Файл | Назначение |
|------|-----------|
| `day10/micro.py` | `MicroClassifier`: fit/predict, возвращает `MicroResult(category, confidence, status)` |
| `day10/pipeline.py` | `MicroFirstPipeline`: Level1 → если UNSURE → Level2 LLM. `PipelineResult` со всеми метриками |
| `day10/eval_micro.py` | Batch runner: 30 test cases (10 simple + 10 borderline + 10 complex), 3-way сравнение |
| `day10/results.md` | Генерируется `eval_micro.py` |

## Структуры данных

```python
@dataclass
class MicroResult:
    category: str       # предсказание
    confidence: float   # max(proba), 0.0–1.0
    status: str         # "OK" | "UNSURE"

@dataclass  
class PipelineResult:
    category: str
    priority: str       # "" если micro обработал (micro не классифицирует)
    sentiment: str      # "" если micro обработал
    confidence: float
    source: str         # "micro" | "llm"
    micro_result: MicroResult
    total_llm_calls: int  # 0 или 1
    latency_ms: int
```

## Eval: 30 тест-кейсов

- **10 simple**: чёткие одиночные категории (auth-только, crash-только)
- **10 borderline**: смешанные сигналы (auth + account, performance + crash)
- **10 complex**: нестандартные, многословные, эмоциональные тексты

Ground truth для всех 30 задан вручную (expected_category).

## Метрики в results.md

| Метрика | Описание |
|---------|----------|
| Micro-handled | % запросов обработан без LLM |
| Fallback rate | % ушедших в LLM |
| LLM calls total | для 30 тест-кейсов |
| Accuracy (3 варианта) | Micro-only / LLM-only / Pipeline |
| Avg latency | Micro / LLM / Pipeline (ms) |

## Сравнение (3-way)

- **Variant A (Micro-only)**: только sklearn, без LLM (включая UNSURE → best_guess)
- **Variant B (LLM-only)**: только gpt-4o-mini на все 30 кейсов
- **Variant C (Pipeline)**: micro-first, LLM только при UNSURE

## Точки интеграции с существующим кодом

- Читает `day06/train.jsonl` — парсинг через `json.loads`
- Переиспользует промпт-паттерн из `day07/confidence.py` (`SCORING_SYSTEM`)
- `config_from_env()` — тот же паттерн (OPENAI_API_KEY / ROUTERAI_API_KEY)
- RouterAI API: `https://routerai.ru/api/v1`, модель `openai/gpt-4o-mini`

## Зависимости

```
pip install scikit-learn openai
```

sklearn только для `MicroClassifier`. openai — для LLM fallback.

## Пороги

- `confidence_threshold = 0.75` — ниже → UNSURE → LLM fallback
- LLM temperature = 0.0 (детерминированность)
- `max_iter = 1000` для LogisticRegression

## Ограничения micro-model

- Micro-model предсказывает только `category` (7 классов)
- `priority` и `sentiment` — всегда LLM или `""` (в Micro-only варианте)
- Нет дообучения онлайн — fit один раз при инициализации
