# День 22 — RAG: первый запрос и сравнение

## Цель

1. Подтвердить работающий пайплайн: вопрос → поиск чанков → контекст → LLM-ответ
2. Добавить comparison-режим: одновременный ответ с RAG и без RAG, side-by-side
3. Зафиксировать 10 контрольных вопросов прямо в UI в виде quick-chip кнопок

---

## Что уже реализовано (Day 21, трогать не нужно)

| Компонент | Статус |
|---|---|
| `RagIndexer.buildContextAndAnswer()` | ✓ полный RAG-пайплайн |
| `RagIndexer.compare()` | ✓ RAG + no-RAG ответы |
| `POST /rag/chat` | ✓ |
| `POST /rag/compare` | ✓ |
| `RagAgent.chat()` / `.compare()` | ✓ |
| `RagContract.RagTab.COMPARE` | ✓ таб есть |
| `RagDomainModels.RagCompareResult` | ✓ |

---

## Что меняем

### 1. `RagContract.kt` — расширить State + добавить события

**Добавить в `State`:**
```kotlin
// Compare tab
val compareInput: String = "",
val compareResult: RagCompareResult? = null,
val isComparing: Boolean = false,
```

Убрать `compareStrategy: ChunkingStrategy` — вкладка сравнения больше не показывает статистику чанков.

**Добавить события:**
```kotlin
data class CompareInputChanged(val text: String) : Event
data object RunCompare : Event
data class SelectControlQuestion(val question: String) : Event
```

**Добавить 10 контрольных вопросов как константы в `RagContract`:**
```kotlin
val CONTROL_QUESTIONS = listOf(
    "Как работает PipelineAgent и какие инструменты он использует?",
    "Что такое MVI и как он реализован в этом проекте?",
    "Какие стратегии chunking поддерживаются в RAG и чем они отличаются?",
    "Как устроен SchedulerAgent и как создаются расписания?",
    "Как добавить новый экран в приложение по правилам проекта?",
    "Какие MCP-серверы используются в оркестрации и как они взаимодействуют?",
    "Как работает коsinе similarity для поиска чанков?",
    "Что делает WeatherSchedulerService и как он восстанавливается после рестарта?",
    "Как устроена архитектура Clean Architecture в shared-модуле?",
    "Какой формат rag_index.json и что в нём хранится?",
)
```

---

### 2. `RagViewModel.kt` — добавить обработку compare-событий

В `onEvent()` добавить:
```kotlin
is RagContract.Event.CompareInputChanged ->
    _state.value = _state.value.copy(compareInput = event.text)

is RagContract.Event.RunCompare -> runCompare()

is RagContract.Event.SelectControlQuestion -> {
    _state.value = _state.value.copy(compareInput = event.question)
    runCompare()  // автоматически запускаем сравнение
}
```

Добавить `runCompare()`:
```kotlin
private fun runCompare() {
    val query = _state.value.compareInput.trim()
    if (query.isBlank() || _state.value.isComparing) return
    _state.value = _state.value.copy(isComparing = true, compareResult = null)
    val topK = _state.value.topK
    val strategy = _state.value.activeStrategy
    viewModelScope.launch {
        runCatching { agent.compare(query = query, k = topK, strategy = strategy) }
            .onSuccess { result ->
                _state.value = _state.value.copy(isComparing = false, compareResult = result)
            }
            .onFailure { e ->
                _state.value = _state.value.copy(
                    isComparing = false,
                    errorMessage = "Ошибка сравнения: ${e.message}",
                )
            }
    }
}
```

---

### 3. `RagScreen.kt` — заменить `RagCompareTab`

Удалить старый `RagCompareTab` (показывал статистику чанков Fixed/Structural).

**Новый `RagCompareTab`:**

```
┌──────────────────────────────────────────┐
│  Контрольные вопросы (горизонтальный     │
│  скролл chip-кнопок):                    │
│  [Как работает PipelineAgent?] [MVI?]... │
├──────────────────────────────────────────┤
│  Результат (если compareResult != null): │
│  ┌─────────────────┬────────────────┐   │
│  │  С RAG          │  Без RAG       │   │
│  │  ─────────────  │  ──────────── │   │
│  │  PipelineAgent  │  Не знаю       │   │
│  │  использует 3   │  деталей...    │   │
│  │  MCP инстру-    │                │   │
│  │  мента...       │                │   │
│  └─────────────────┴────────────────┘   │
│                                          │
│  Использованные чанки:                   │
│  📄 day19.md · Pipeline  91%            │
│  📄 PipelineAgent.kt     88%            │
├──────────────────────────────────────────┤
│  Загрузка... (если isComparing)         │
├──────────────────────────────────────────┤
│  [Введите вопрос для сравнения...]  [→] │
└──────────────────────────────────────────┘
```

**Детали реализации:**

- Chip-кнопки: `LazyRow` с `FilterChip` или `SuggestionChip` из Material3. При нажатии вызывает `SelectControlQuestion(question)`.
- Результат: два `Card` в `Row` с `weight(1f)` каждый. Заголовки "С RAG" (primary) и "Без RAG" (error/outline).
- Чанки: те же `RagChunkBadge` из чат-режима (переиспользовать существующий composable).
- isComparing: показывать `CircularProgressIndicator` + текст "RAG vs no-RAG...".
- `ScrollState` для вертикального прокрута всей вкладки.
- Input bar: переиспользовать `RagInputBar`, но с `compareInput` и `RunCompare` / `CompareInputChanged`.

---

## 10 контрольных вопросов

| # | Вопрос | Ожидаемый ответ содержит | Ожидаемые источники |
|---|---|---|---|
| 1 | Как работает PipelineAgent и какие инструменты он использует? | 3 инструмента MCP, get_weather/summarize_weather/save_to_file | `day19-pipeline.md`, `PipelineAgent.kt` |
| 2 | Что такое MVI и как он реализован в этом проекте? | State/Event/Effect, ViewModel, StateFlow | `CLAUDE.md`, `day11-memory-layers.md` |
| 3 | Какие стратегии chunking поддерживаются в RAG и чем они отличаются? | Fixed (sliding window, overlap), Structural (headings/functions) | `day21-rag-indexing.md`, `RagIndexer.kt` |
| 4 | Как устроен SchedulerAgent и как создаются расписания? | create_schedule, WeatherSchedulerService, coroutines | `day18-scheduler.md`, `SchedulerAgent.kt` |
| 5 | Как добавить новый экран в приложение по правилам проекта? | Contract/ViewModel/Screen, App.kt, viewModel<T>() | `CLAUDE.md` |
| 6 | Какие MCP-серверы используются в оркестрации? | 3 сервера, динамическое обнаружение, мульти-серверный флоу | `day20-orchestration-mcp.md` |
| 7 | Как вычисляется cosine similarity для поиска чанков? | dot product, нормализация, линейный проход | `day21-rag-indexing.md`, `RagIndexer.kt` |
| 8 | Что делает WeatherSchedulerService и как он восстанавливается? | coroutine, java.time, восстановление при рестарте | `day18-scheduler.md` |
| 9 | Как устроена архитектура Clean Architecture в shared-модуле? | domain/data/api слои, repository interfaces, use cases | `CLAUDE.md` |
| 10 | Какой формат rag_index.json и что в нём хранится? | version, model, chunks, embedding, strategy | `day21-rag-indexing.md` |

---

## Ожидаемый эффект сравнения

**С RAG:** конкретные детали из документации — названия функций, параметры, архитектурные решения.

**Без RAG:** общий ответ без деталей проекта. Модель честно говорит "не знаю специфики этого проекта" или даёт приблизительный ответ.

---

## Затронутые файлы

**Изменения:**
- `composeApp/.../rag/RagContract.kt` — State расширить, новые события, `CONTROL_QUESTIONS`
- `composeApp/.../rag/RagViewModel.kt` — обработка новых событий, `runCompare()`
- `composeApp/.../rag/RagScreen.kt` — заменить `RagCompareTab`, добавить `RagCompareInputBar`, chips

**Не трогаем:**
- Серверный код (все эндпоинты работают)
- `RagAgent.kt` (`.compare()` уже есть)
- `RagModels.kt` в shared (все модели есть)

---

## Паттерны из существующего кода

| Аспект | Источник |
|---|---|
| `RagChunkBadge` | Переиспользовать из того же `RagScreen.kt` |
| `RagInputBar` | Переиспользовать из того же `RagScreen.kt` |
| `FilterChip` / chips | Паттерн из других экранов |
| `compareResult: T?` → null когда нет данных | `stats: RagIndexStats?` в State |
