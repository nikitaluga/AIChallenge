# День 6 — DataSet: Fine-tuning для классификации тикетов поддержки

## Задача

**Тип:** классификация  
**Цель:** дообучить `gpt-4o-mini` классифицировать входящие тикеты поддержки по трём осям одновременно:
- `category` — тип проблемы
- `priority` — срочность
- `sentiment` — тональность пользователя

## Категории и метки

| Ось | Значения |
|-----|----------|
| category | `auth`, `billing`, `crash`, `feature_request`, `data_export`, `performance`, `account` |
| priority | `low`, `medium`, `high`, `critical` |
| sentiment | `satisfied`, `neutral`, `frustrated`, `angry` |

## Формат примера

```jsonl
{"messages": [
  {"role": "system", "content": "Classify the support ticket. Return only valid JSON with keys: \"category\" (auth|billing|crash|feature_request|data_export|performance|account), \"priority\" (low|medium|high|critical), \"sentiment\" (satisfied|neutral|frustrated|angry)."},
  {"role": "user", "content": "Subject: Не могу войти в аккаунт\n\nDescription: Нажимаю кнопку «Войти», страница перезагружается."},
  {"role": "assistant", "content": "{\"category\": \"auth\", \"priority\": \"high\", \"sentiment\": \"frustrated\"}"}
]}
```

## Датасет

| Файл | Примеров | Назначение |
|------|----------|------------|
| `day06/train.jsonl` | 40 | Обучение модели |
| `day06/eval.jsonl` | 10 | Замер качества |

**Реальные примеры:** 10 из 50 (20%) — из `support_tickets.json` + ручные  
**AI-generated:** 40 из 50 (80%)

## Распределение по категориям (всего 50)

| Category | Train | Eval | Итого |
|----------|-------|------|-------|
| auth | 10 | 2 | 12 |
| billing | 8 | 2 | 10 |
| crash | 7 | 2 | 9 |
| feature_request | 7 | 1 | 8 |
| data_export | 4 | 1 | 5 |
| performance | 3 | 1 | 4 |
| account | 1 | 1 | 2 |
| **Итого** | **40** | **10** | **50** |

## Скрипты

| Файл | Назначение |
|------|------------|
| `day06/validate.py` | Валидация JSONL: формат, роли, пустые content |
| `day06/baseline.py` | Прогон 10 eval-примеров через gpt-4o-mini без файнтюна |
| `day06/finetune_client.py` | Загрузка файла + создание job + polling статуса |

## Baseline

**Модель:** `gpt-4o-mini` (без файнтюна)  
**Примеры:** 10 из eval.jsonl  
**Результаты:** `day06/baseline_results.md`

## Критерии "стало лучше"

1. **Точность категории** — % правильных `category` (baseline ожидается ~80%, цель ≥95%)
2. **Точность приоритета** — % правильных `priority` (baseline ~70%, цель ≥85%)
3. **Точность тональности** — % правильных `sentiment` (baseline ~75%, цель ≥90%)
4. **Формат ответа** — % ответов, являющихся валидным JSON (baseline ~95%, цель 100%)
5. **Скорость** — токены в ответе: файнтюн-модель должна быть короче (нет лишних объяснений)

## Точки интеграции с проектом

- Реальные данные из `support_tickets.json` (4 тикета)
- Тематически связан с Днём 33 (Support Assistant) — та же предметная область
- API ключ читается из переменной окружения `OPENAI_API_KEY`
- Модель после файнтюна можно использовать в `SupportRoutes.kt` для автоклассификации
