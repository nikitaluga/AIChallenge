# День 24 — Цитаты, источники и анти-галлюцинации

## Цель

Доработать RAG так, чтобы каждый ответ включал структурированные источники и цитаты, а при низкой релевантности — явный ответ "не знаю".

---

## Решения из интервью

| Вопрос | Решение |
|---|---|
| Формат ответа | Новый endpoint `/rag/chat/v2`, не ломает существующий `/rag/chat` |
| Извлечение цитат | LLM выделяет цитаты через JSON-промпт |
| Режим "не знаю" | threshold в `/rag/chat/v2`; если все chunks < threshold → ответ "не знаю" |
| Валидация | Новая вкладка "День 24" в UI с 10 вопросами и иконками ✓/✗ |

---

## Серверная часть

### Новые модели (`RagServerModels.kt`)

```kotlin
@Serializable
data class RagSource(
    val chunkId: String,
    val source: String,
    val section: String? = null
)

@Serializable
data class RagCitation(
    val text: String,    // фрагмент текста, выделенный LLM
    val chunkId: String  // к какому чанку относится
)

@Serializable
data class RagChatV2Request(
    val query: String,
    val k: Int = 5,
    val strategy: String = "structural",
    val threshold: Float = 0.35f
)

@Serializable
data class RagChatV2Response(
    val answer: String,
    val usedChunks: List<RagSearchResult>,
    val sources: List<RagSource>,
    val citations: List<RagCitation>,
    val belowThreshold: Boolean = false  // true если сработал режим "не знаю"
)
```

### Системный промпт (LLM → JSON)

```
Ты — ассистент с доступом к базе знаний. Тебе предоставлены фрагменты документации (chunks).
Отвечай ТОЛЬКО на основе этих фрагментов.

ПРАВИЛА:
1. Верни ответ строго в формате JSON (без markdown, без пояснений вне JSON):
{
  "answer": "...",
  "sources": [{"chunkId": "...", "source": "...", "section": "..."}],
  "citations": [{"text": "...", "chunkId": "..."}]
}
2. В "sources" перечисли все chunkId, которые использовал для ответа.
3. В "citations" приведи дословные короткие фрагменты (1-2 предложения) из chunks, подтверждающие ответ. Обязательно укажи chunkId цитируемого chunk.
4. Если предоставленные фрагменты не содержат ответа на вопрос — answer должен быть "Я не знаю. Пожалуйста, уточните вопрос или расширьте базу знаний.", sources=[], citations=[].
5. НЕ придумывай информацию, которой нет в chunks.
```

### Логика endpoint `/rag/chat/v2`

1. Выполнить поиск top-K chunks
2. Если `max(scores) < threshold` → вернуть `RagChatV2Response(answer="Я не знаю...", usedChunks=[], sources=[], citations=[], belowThreshold=true)`
3. Иначе: сформировать контекст из chunks с их chunkId/source/section, вызвать LLM с JSON-промптом
4. Распарсить JSON-ответ LLM → `RagChatV2Response`
5. Если парсинг не удался (LLM вернула не-JSON) → fallback: `answer` = сырой текст, `sources` = чанки из `usedChunks`, `citations` = []

### Новый route в `RagRoutes.kt`

```
POST /rag/chat/v2
```

---

## Shared-модуль

### Новые доменные модели (`RagModels.kt`)

```kotlin
data class RagSource(val chunkId: String, val source: String, val section: String? = null)
data class RagCitation(val text: String, val chunkId: String)
data class RagChatV2Result(
    val answer: String,
    val usedChunks: List<RagChunkResult>,
    val sources: List<RagSource>,
    val citations: List<RagCitation>,
    val belowThreshold: Boolean
)
```

### `RagAgent.kt`

Добавить метод:
```kotlin
suspend fun chatV2(query: String, k: Int = 5, strategy: String = "structural", threshold: Float = 0.35f): RagChatV2Result
```

---

## Presentation-слой (`composeApp/`)

### `RagContract.kt` — новый таб

```kotlin
enum class RagTab { CHAT, COMPARE, DAY24 }

// В State добавить:
val day24Results: List<Day24QuestionResult> = emptyList(),
val isDay24Running: Boolean = false,

data class Day24QuestionResult(
    val question: String,
    val answer: String,
    val hasSources: Boolean,
    val hasCitations: Boolean,
    val belowThreshold: Boolean,
    val sourcesCount: Int,
    val citationsCount: Int
)

// Events
object RunDay24Test : Event
```

### `RagViewModel.kt`

Метод `handleRunDay24Test()`:
- Итерирует 10 вопросов (переиспользовать `controlQuestions` из Compare-таба)
- Для каждого вызывает `ragAgent.chatV2(question)`
- Маппит в `Day24QuestionResult` с проверкой `hasSources = sources.isNotEmpty()`, `hasCitations = citations.isNotEmpty()`
- Обновляет State после каждого вопроса (live-обновление)

### `RagScreen.kt` — таб "День 24"

```
┌─────────────────────────────────────────────────────┐
│  [Запустить 10 вопросов]    ProgressBar (если running)|
├─────────────────────────────────────────────────────┤
│  #1  "Как работает PipelineAgent?"                  │
│      [✓ Источники: 3] [✓ Цитаты: 2]                 │
│      Ответ: "PipelineAgent использует 3 инструмента" │
│                                                     │
│  #2  "Вопрос вне контекста?"                        │
│      [⚠ Ниже порога] [✗ Источники: 0] [✗ Цитаты: 0] │
│      Ответ: "Я не знаю..."                          │
└─────────────────────────────────────────────────────┘
```

- Зелёный `✓` если `hasSources && hasCitations`
- Красный `✗` если нет источников или цитат
- Жёлтый `⚠` если `belowThreshold`

---

## 10 тестовых вопросов

1. "Как работает PipelineAgent и какие инструменты он использует?"
2. "Что такое MVI и как он реализован в этом проекте?"
3. "Какие стратегии chunking поддерживаются в RAG и чем они отличаются?"
4. "Как устроен SchedulerAgent и как создаются расписания?"
5. "Как добавить новый экран в приложение по правилам проекта?"
6. "Какие MCP-серверы используются в оркестрации?"
7. "Как вычисляется cosine similarity для поиска чанков?"
8. "Что делает WeatherSchedulerService и как он восстанавливается?"
9. "Как устроена архитектура Clean Architecture в shared-модуле?"
10. "Какой формат rag_index.json и что в нём хранится?"

---

## Затронутые файлы

| Файл | Изменение |
|---|---|
| `server/.../rag/RagServerModels.kt` | + `RagSource`, `RagCitation`, `RagChatV2Request`, `RagChatV2Response` |
| `server/.../rag/RagIndexer.kt` | + `chatV2()` метод с JSON-промптом и threshold |
| `server/.../rag/RagRoutes.kt` | + `POST /rag/chat/v2` |
| `shared/.../domain/model/RagModels.kt` | + `RagSource`, `RagCitation`, `RagChatV2Result` |
| `shared/.../data/agent/RagAgent.kt` | + `chatV2()` |
| `composeApp/.../rag/RagContract.kt` | + `DAY24` таб, `Day24QuestionResult`, `RunDay24Test` |
| `composeApp/.../rag/RagViewModel.kt` | + `handleRunDay24Test()` |
| `composeApp/.../rag/RagScreen.kt` | + Day24 таб UI |

---

## Обратная совместимость

- `/rag/chat` — не изменяется (backward compatible)
- `RagChatResponse` — не изменяется
- Новый функционал только через `/rag/chat/v2`
