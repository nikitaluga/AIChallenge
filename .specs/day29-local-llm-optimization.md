# День 29 — Оптимизация локальной LLM

## Цель

Оптимизировать локальную LLM (Ollama) под задачу KMP-ассистента: настроить параметры генерации, добавить специализированный system prompt, сравнить модели (llama3.2:3b vs phi3:mini) и оценить результаты с помощью LLM-judge.

---

## Контекст из кодовой базы

### Существующие точки интеграции

- **`LocalLlmRoutes.kt`** — POST `/local/stream` и `/local/chat`. Сейчас передаёт в Ollama только `messages` и `model`, без `options`. Ollama defaults: temperature=0.8, top_p=0.9, top_k=40, num_predict=-1, num_ctx=2048.
- **`Day27Contract.kt / Day27ViewModel.kt / Day27Screen.kt`** — existing streaming chat screen, default model `llama3.2:3b`.
- **`LocalLlmStreamAgent.kt`** — KMP-агент для SSE-стриминга. Request timeout 180s.
- **`App.kt`** — tab navigation; нужно добавить таб "День 29".

### Паттерны проекта

- MVI: `Contract.kt` (State/Event/Effect) + `ViewModel.kt` + `Screen.kt` в пакете `day29/`.
- Server Ktor routes: `install` в `Application.kt`.
- Все параметры в `LocalLlmServerModels.kt` / новые модели в `LocalRagServerModels.kt`.

---

## Что реализуем

### 1. Server: расширенные параметры генерации

**Обновить Ollama request** в `LocalLlmRoutes.kt`:
- Добавить `OllamaOptions(temperature, topP, topK, numPredict, numCtx)` в тело запроса к `/api/chat`.
- Принимать эти параметры из клиентского запроса (`LocalChatRequest`, `LocalStreamRequest`).
- System prompt передавать как отдельное сообщение `role="system"` перед историей диалога.

**Новый endpoint `/local/benchmark`** (POST):
- Принимает: `query`, `presetBefore` (default options), `presetAfter` (optimized options), `modelBefore`, `modelAfter`.
- Возвращает: ответ до и после с latency, затем отправляет оба ответа в GPT-4o-mini для LLM-judge оценки (1-5 + комментарий).

### 2. Server: LLM-judge

Endpoint `/local/judge` (POST):
- Принимает: `query`, `answerA`, `answerB`.
- Отправляет в GPT-4o-mini системный промпт-судья: оценить A и B по шкале 1-5 (accuracy, conciseness, helpfulness).
- Возвращает: `{ scoreA: Int, scoreB: Int, reasoning: String }`.

### 3. Клиент (shared): модели

Новые data-классы в `LocalLlmModels.kt`:
```kotlin
data class OllamaOptions(
    val temperature: Float = 0.8f,
    val topP: Float = 0.9f,
    val topK: Int = 40,
    val numPredict: Int = -1,
    val numCtx: Int = 2048,
)

data class OptimizedChatResult(
    val before: LocalChatResult,
    val after: LocalChatResult,
    val judgeScore: JudgeResult?,
)

data class JudgeResult(
    val scoreA: Int,
    val scoreB: Int,
    val reasoning: String,
)
```

Обновить `LocalLlmStreamAgent`:
- Добавить `options: OllamaOptions?` и `systemPrompt: String?` в `streamChat()`.

Новый `LocalOptimizationAgent`:
- `benchmark(query, beforeOptions, afterOptions, modelBefore, modelAfter)` → `OptimizedChatResult`
- `judge(query, answerA, answerB)` → `JudgeResult`

### 4. UI: Экран День 29

**Пакет**: `composeApp/.../day29/`

**Day29Contract.kt — State**:
```kotlin
data class State(
    // Параметры "До" (defaults)
    val beforeModel: String = "llama3.2:3b",
    val beforeOptions: OllamaOptions = OllamaOptions(),
    val beforeSystemPrompt: String = "",

    // Параметры "После" (optimized)
    val afterModel: String = "phi3:mini",
    val afterOptions: OllamaOptions = OllamaOptions(
        temperature = 0.3f,
        topP = 0.9f,
        topK = 40,
        numPredict = 512,
        numCtx = 4096,
    ),
    val afterSystemPrompt: String = KMP_SYSTEM_PROMPT,

    // Тестирование
    val query: String = "",
    val isRunning: Boolean = false,
    val beforeResult: LocalChatResult? = null,
    val afterResult: LocalChatResult? = null,
    val judgeResult: JudgeResult? = null,
    val isJudging: Boolean = false,

    // Модели
    val availableModels: List<String> = emptyList(),
    val error: String? = null,
)

const val KMP_SYSTEM_PROMPT = """You are an expert Kotlin Multiplatform developer.
Specialize in: KMP, Compose Multiplatform, Ktor, Coroutines.
Answer concisely and with code examples when relevant.
Respond in Russian."""
```

**Day29Contract.kt — Events**:
- `QueryChanged(text)`, `BeforeModelChanged(model)`, `AfterModelChanged(model)`
- `BeforeOptionsChanged(options)`, `AfterOptionsChanged(options)`
- `BeforeSystemPromptChanged(text)`, `AfterSystemPromptChanged(text)`
- `RunBenchmark`, `RunJudge`, `ClearResults`

**Day29Screen.kt — Layout**:

```
┌─────────────────────────────────────────────────────────┐
│ ДЕНЬ 29 — Оптимизация LLM                               │
├────────────────────────┬────────────────────────────────┤
│   ДО (baseline)        │   ПОСЛЕ (optimized)            │
│ ┌──────────────────┐   │ ┌────────────────────────────┐ │
│ │ Модель: [drop]   │   │ │ Модель: [drop]             │ │
│ │ Temp: [0.8] ─○── │   │ │ Temp: [0.3] ─○──           │ │
│ │ top_p: [0.9]     │   │ │ top_p: [0.9]               │ │
│ │ top_k: [40]      │   │ │ top_k: [40]                │ │
│ │ max_tokens: [-1] │   │ │ max_tokens: [512]          │ │
│ │ num_ctx: [2048]  │   │ │ num_ctx: [4096]            │ │
│ │ System prompt:   │   │ │ System prompt:             │ │
│ │ [текстовое поле] │   │ │ [KMP-промпт]               │ │
│ └──────────────────┘   │ └────────────────────────────┘ │
├────────────────────────┴────────────────────────────────┤
│ Запрос: [текстовое поле]           [Запустить тест]     │
├────────────────────────┬────────────────────────────────┤
│ Ответ ДО               │ Ответ ПОСЛЕ                    │
│ ─────────────────────  │ ────────────────────────────   │
│ [текст ответа]         │ [текст ответа]                 │
│ Latency: 1234 ms       │ Latency: 876 ms                │
├────────────────────────┴────────────────────────────────┤
│ LLM-Judge оценка              [Запустить судью]         │
│ ДО: ⭐⭐⭐  (3/5)   ПОСЛЕ: ⭐⭐⭐⭐⭐ (5/5)              │
│ "После-вариант лаконичнее и точнее..."                  │
└─────────────────────────────────────────────────────────┘
```

**Тестовые вопросы (chips)**:
```
"Что такое MVI?",
"Объясни Kotlin Coroutines",
"Напиши ViewModel для KMP",
"Чем collectAsStateWithLifecycle лучше collectAsState?",
"Что такое expect/actual в KMP?"
```

---

## Оптимальные параметры "После" (hardcoded presets)

| Параметр | До (default) | После (optimized) | Причина |
|---|---|---|---|
| temperature | 0.8 | 0.3 | Детерминированность для технических ответов |
| top_p | 0.9 | 0.9 | Без изменений |
| top_k | 40 | 40 | Без изменений |
| num_predict | -1 | 512 | Ограничение длины для лаконичности |
| num_ctx | 2048 | 4096 | Больше контекста для кода |
| system_prompt | нет | KMP-Expert | Специализация |
| model | llama3.2:3b | phi3:mini | Сравниваем |

---

## Затронутые файлы

### Server (изменения):
- `server/src/main/kotlin/ru/nikitaluga/aichallenge/local/LocalLlmRoutes.kt` — добавить `options` в Ollama request
- `server/src/main/kotlin/ru/nikitaluga/aichallenge/Application.kt` — зарегистрировать новые маршруты

### Server (новые файлы):
- `server/src/main/kotlin/ru/nikitaluga/aichallenge/local/LocalOptimizationRoutes.kt` — `/local/benchmark`, `/local/judge`

### Shared (изменения):
- `shared/.../domain/model/LocalLlmModels.kt` — добавить `OllamaOptions`, `OptimizedChatResult`, `JudgeResult`
- `shared/.../data/agent/LocalLlmStreamAgent.kt` — добавить `options` и `systemPrompt`

### Shared (новые):
- `shared/.../data/agent/LocalOptimizationAgent.kt` — `benchmark()`, `judge()`

### ComposeApp (новые):
- `composeApp/.../day29/Day29Contract.kt`
- `composeApp/.../day29/Day29ViewModel.kt`
- `composeApp/.../day29/Day29Screen.kt`

### ComposeApp (изменения):
- `composeApp/.../App.kt` — добавить таб "День 29"

---

## Риски и ограничения

1. **phi3:mini не установлен** — нужно `ollama pull phi3:mini` перед тестом. UI должен показывать ошибку если модель недоступна.
2. **LLM-judge стоит денег** — вызов GPT-4o-mini через `/local/judge`. Кнопка отдельная, не автоматически.
3. **Streaming для benchmark** — `/local/benchmark` использует non-streaming chat (ждёт полного ответа), чтобы измерить latency корректно.
4. **phi3:mini отвечает на английском** — system prompt должен явно указывать язык ответа (`Respond in Russian`).
