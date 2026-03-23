# День 25 — RAG + Memory Chat (production-like)

## Цель
Мини-чат с историей диалога, RAG-контекстом и памятью задачи (TaskMemory). Верифицировать на 2 сценариях по 10–15 сообщений — ассистент не теряет цель и всегда возвращает источники.

## Пользовательский сценарий
1. Пользователь открывает вкладку «День 25»
2. Панель TaskMemory пуста (goal/terms/constraints не заданы)
3. Пользователь задаёт вопросы — по каждому ответу LLM:
   - Возвращает ответ с sources + citations
   - Обновляет TaskMemory (goal, terms, constraints) на основе диалога
4. Панель TaskMemory отображается над чатом и обновляется после каждого хода
5. История диалога накапливается в ViewModel и передаётся на сервер с каждым новым вопросом

---

## Архитектурные решения (из интервью)

| Вопрос | Решение |
|--------|---------|
| Размещение | Новая вкладка «День 25» в App.kt |
| История | Хранится в `Day25ViewModel`, передаётся в `/rag/chat/v3` |
| TaskMemory | LLM извлекает автоматически, возвращается в каждом ответе |
| UI сообщений | Sources + Citations + TaskMemory панель + below-threshold индикатор |
| Endpoint | `POST /rag/chat/v3` (новый, сервер) |

---

## Серверная часть

### Новый endpoint: `POST /rag/chat/v3`

**Запрос `RagChatV3Request`:**
```json
{
  "query": "текущий вопрос пользователя",
  "history": [
    {"role": "user", "content": "..."},
    {"role": "assistant", "content": "..."}
  ],
  "taskMemory": {
    "goal": "Изучить RAG архитектуру",
    "terms": ["chunking", "cosine sim"],
    "constraints": ["только Kotlin"]
  },
  "k": 5,
  "strategy": "structural",
  "threshold": 0.35
}
```

**Ответ `RagChatV3Response`:**
```json
{
  "answer": "...",
  "usedChunks": [...],
  "sources": [{"chunkId": "...", "source": "...", "section": "..."}],
  "citations": [{"text": "...", "chunkId": "..."}],
  "belowThreshold": false,
  "taskMemory": {
    "goal": "Изучить RAG архитектуру",
    "terms": ["chunking", "cosine sim", "новый термин"],
    "constraints": ["только Kotlin"]
  }
}
```

### Логика `RagIndexer.buildContextAndAnswerV3()`

1. **Threshold check** — если `maxScore < threshold` → вернуть "не знаю" (без вызова LLM для ответа, но TaskMemory всё равно обновить)
2. **System prompt** (один JSON-вызов):
   - Контекст из RAG (top-K чанков)
   - История диалога (последние N сообщений, например 20)
   - Текущее состояние TaskMemory
   - Инструкция: вернуть JSON `{answer, sources, citations, taskMemory}`
3. **Разбор ответа** — аналогично `parseV2Response`, добавить поле `taskMemory`
4. **Fallback** — если JSON не парсится, вернуть raw-ответ с пустыми sources/citations и прежним taskMemory

### System prompt шаблон
```
Ты — ассистент с памятью задачи и доступом к базе знаний.

ТЕКУЩАЯ ПАМЯТЬ ЗАДАЧИ:
Цель: {goal или "не определена"}
Термины: {terms.joinToString()}
Ограничения: {constraints.joinToString()}

ИСТОРИЯ ДИАЛОГА:
{history.last(20).format()}

БАЗА ЗНАНИЙ (релевантные фрагменты):
{chunks.format()}

ПРАВИЛА:
1. Отвечай на основе базы знаний и истории диалога.
2. Верни строго JSON без markdown-блоков:
   {
     "answer":"...",
     "sources":[{"chunkId":"...","source":"...","section":"..."}],
     "citations":[{"text":"...","chunkId":"..."}],
     "taskMemory":{
       "goal":"...цель диалога...",
       "terms":["термин1","термин2"],
       "constraints":["ограничение1"]
     }
   }
3. В taskMemory.goal — сформулируй цель пользователя из диалога (1 предложение).
4. В taskMemory.terms — список ключевых терминов/понятий, упомянутых пользователем.
5. В taskMemory.constraints — явные ограничения/требования пользователя.
6. Если фрагменты не содержат ответа — answer="Я не знаю...", sources=[], citations=[].
7. НЕ придумывай информацию.
```

---

## Shared слой

### Новые файлы/изменения

**`shared/domain/model/RagModels.kt`** — добавить:
```kotlin
@Immutable
data class TaskMemory(
    val goal: String = "",
    val terms: List<String> = emptyList(),
    val constraints: List<String> = emptyList(),
)

@Immutable
data class RagChatV3Result(
    val answer: String,
    val usedChunks: List<RagChunkResult>,
    val sources: List<RagSource>,
    val citations: List<RagCitation>,
    val belowThreshold: Boolean,
    val taskMemory: TaskMemory,
)
```

**`shared/data/agent/RagAgent.kt`** — добавить `chatV3()`:
```kotlin
suspend fun chatV3(
    query: String,
    history: List<RagHistoryMessage>,
    taskMemory: TaskMemory,
    k: Int = 5,
    strategy: ChunkingStrategy = ChunkingStrategy.STRUCTURAL,
    threshold: Float = 0.35f,
): RagChatV3Result
```

**`shared/api/RagDtos.kt`** (или расширить существующий) — новые DTO для v3.

---

## ComposeApp слой

### Новые файлы

```
composeApp/.../day25/
├── Day25Contract.kt
├── Day25ViewModel.kt
└── Day25Screen.kt
```

### `Day25Contract.kt`

```kotlin
object Day25Contract {
    data class State(
        val messages: ImmutableList<Day25Message> = persistentListOf(),
        val inputText: String = "",
        val isLoading: Boolean = false,
        val taskMemory: TaskMemory = TaskMemory(),
        // конфигурация
        val topK: Int = 5,
        val threshold: Float = 0.35f,
        val strategy: ChunkingStrategy = ChunkingStrategy.STRUCTURAL,
    )

    @Immutable
    data class Day25Message(
        val role: String,           // "user" | "assistant"
        val content: String,
        val sources: ImmutableList<RagSource> = persistentListOf(),
        val citations: ImmutableList<RagCitation> = persistentListOf(),
        val belowThreshold: Boolean = false,
    )

    sealed interface Event {
        data class InputChanged(val text: String) : Event
        data object SendMessage : Event
        data object ClearHistory : Event
        data class ThresholdChanged(val value: Float) : Event
        data class TopKChanged(val k: Int) : Event
    }

    sealed interface Effect {
        data object ScrollToBottom : Effect
    }
}
```

### `Day25ViewModel.kt`

Ключевая логика `sendMessage()`:
- Добавить user-сообщение в state.messages
- Передать в `agent.chatV3(query, history, taskMemory, k, strategy, threshold)`
  - `history` = все предыдущие messages (role + content)
- Обновить state: добавить assistant-сообщение + обновить `taskMemory`
- Emit `ScrollToBottom`

### `Day25Screen.kt`

**Компоновка экрана:**
```
┌─────────────────────────────────────────┐
│  ПАНЕЛЬ TASK MEMORY (Card, раскрываемая)│
│  Цель: "..."                            │
│  Термины: [chip][chip]                  │
│  Ограничения: [chip]                    │
├─────────────────────────────────────────┤
│  LazyColumn — сообщения                 │
│  ┌─ user bubble ───────────────────┐    │
│  │ Текст вопроса                   │    │
│  └─────────────────────────────────┘    │
│  ┌─ assistant bubble ──────────────┐    │
│  │ Ответ                           │    │
│  │ ─────────────────────────────── │    │
│  │ 📄 sources: CLAUDE.md · MVI     │    │
│  │ ❝ citation text ❞               │    │
│  └─────────────────────────────────┘    │
├─────────────────────────────────────────┤
│  [TextField] [Send]                     │
└─────────────────────────────────────────┘
```

**TaskMemoryCard** — `AnimatedVisibility` если `taskMemory != empty`:
- Показывает goal (если непустой)
- Terms как `FilterChip` (read-only)
- Constraints как `AssistChip` (read-only)
- Кнопка "Очистить историю" (ClearHistory event)

**Day25MessageBubble** — аналог `RagChatBubble` из Day 24:
- Для assistant: показывает sources + citations + ⚠ если belowThreshold

---

## Точки интеграции

| Файл | Изменение |
|------|-----------|
| `server/.../rag/RagServerModels.kt` | Добавить `TaskMemoryDto`, `RagChatV3Request`, `RagChatV3Response` |
| `server/.../rag/RagIndexer.kt` | Добавить `buildContextAndAnswerV3()` |
| `server/.../rag/RagRoutes.kt` | Добавить `post("/rag/chat/v3")` |
| `shared/domain/model/RagModels.kt` | Добавить `TaskMemory`, `RagChatV3Result`, `RagHistoryMessage` |
| `shared/data/agent/RagAgent.kt` | Добавить `chatV3()` |
| `composeApp/.../App.kt` | Добавить вкладку «День 25» |
| `composeApp/.../day25/` | 3 новых файла (Contract, ViewModel, Screen) |

---

## Проверка (2 сценария)

### Сценарий 1: «Изучение RAG архитектуры» (10 сообщений)
- Вопросы про chunking, embedding, cosine similarity, threshold, query rewrite
- Ожидаем: к 5-му сообщению `goal` заполнен, `terms` содержит ключевые термины
- Ожидаем: все ответы имеют sources

### Сценарий 2: «MVI + Clean Architecture» (10 сообщений)
- Вопросы про State/Event/Effect, ViewModel, Use Cases, dependency rules
- Ожидаем: goal = «изучение MVI паттерна», constraints = «только KMP/commonMain»
- Ожидаем: ассистент не теряет контекст при 10+ сообщениях

---

## Ограничения и риски

- **Размер контекста**: история 20 сообщений + RAG-чанки может быть большой. Ограничить `history.takeLast(20)` на сервере.
- **JSON parsing**: LLM иногда добавляет markdown-блоки. Использовать существующий `extractJsonObject()`.
- **TaskMemory drift**: LLM может переопределить goal неверно. Принимать новый taskMemory из каждого ответа без ручной коррекции (per дизайн-решению из интервью).
- **belowThreshold + TaskMemory**: даже при "не знаю" — обновлять TaskMemory на основе истории (отдельный минимальный JSON-вызов или возвращать прежний taskMemory).
