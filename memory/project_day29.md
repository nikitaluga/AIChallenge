---
name: Day 29 — Local LLM Optimization
description: Benchmark screen comparing baseline vs optimized local LLM (temperature, system prompt, model)
type: project
---

Оптимизация локальной LLM под задачу KMP-ассистента.

**Why:** Показать влияние параметров генерации и system prompt на качество ответов локальной модели.

**How to apply:** Если нужно работать с оптимизацией параметров Ollama — смотреть Day 29 файлы.

## Что реализовано

- **Server**: `/local/benchmark` — параллельный запуск двух конфигураций (до/после)
- **Server**: `/local/judge` — LLM-as-judge через GPT-4o-mini (1-5 оценка)
- **Server**: `LocalLlmRoutes.kt` обновлён — теперь принимает `options` (temperature/top_p/top_k/num_predict/num_ctx) и `system_prompt`
- **Shared**: `OllamaOptions`, `JudgeResult`, `BenchmarkResult` в `LocalLlmModels.kt`
- **Shared**: `LocalOptimizationAgent.kt` — `benchmark()` и `judge()`
- **ComposeApp**: `day29/` — Day29Contract/ViewModel/Screen

## Конфигурации

| Параметр | До (baseline) | После (optimized) |
|---|---|---|
| model | llama3.2:3b | phi3:mini |
| temperature | 0.8 | 0.3 |
| num_predict | -1 (∞) | 512 |
| num_ctx | 2048 | 4096 |
| system_prompt | нет | KMP Expert prompt |

## Затронутые файлы

- `server/.../local/LocalLlmRoutes.kt` — добавлены `LocalChatOptions`, `system_prompt`
- `server/.../local/LocalOptimizationRoutes.kt` — новый
- `server/.../Application.kt` — регистрация новых маршрутов
- `shared/.../domain/model/LocalLlmModels.kt` — добавлены модели
- `shared/.../data/agent/LocalOptimizationAgent.kt` — новый
- `composeApp/.../day29/` — новый пакет (3 файла)
- `composeApp/.../App.kt` — добавлен таб "День 29"
