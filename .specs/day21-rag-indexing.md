# День 21 — RAG: Индексация документов

## Цель

Реализовать полноценный RAG-пайплайн:
1. **Индексирование** — chunking → embeddings → сохранение `rag_index.json`
2. **Поиск** — embedding запроса → cosine similarity → top-K чанков
3. **RAG-чат** — top-K чанков в системный промпт → ответ LLM

## Корпус документов

Файлы, которые индексируются:
- `.specs/*.md` — 9 spec-файлов (дни 11–20), ~300KB
- `CLAUDE.md` — архитектурная документация проекта
- `README.md` — описание проекта и команды сборки
- `shared/src/commonMain/**/*.kt` — Kotlin-файлы агентов и domain-слоя
- `docs/*.md` и `docs/*.pdf` — внешние документы, которые пользователь кладёт вручную в папку `docs/` рядом с сервером

Суммарно: >30 страниц текста/кода.

### PDF-парсинг

Для `.pdf` в папке `docs/` используем Apache PDFBox (JVM). Для `.md` и `.kt` — чистый текст.

## Архитектура

### Chunking стратегии

#### Стратегия 1: Fixed-size (по фиксированному размеру)

```
chunkSize: Int (токены, default 300)
overlap: Int   (токены, default 50)
```

Алгоритм:
1. Разбить текст на слова (split whitespace)
2. Сдвигающееся окно: `[i, i+chunkSize)`, следующее начинается с `i+chunkSize-overlap`
3. Каждый чанк — подряд идущие слова

Метаданные: `source`, `chunk_id`, `section=null`, `strategy="fixed"`

#### Стратегия 2: Structural (по структуре)

Для Markdown/specs:
- Разбить по заголовкам `## ` и `### `
- Каждая секция между заголовками = один чанк (или несколько, если секция >maxChunkSize слов)
- `section` = текст заголовка

Для Kotlin-файлов:
- Разбить по `fun `, `class `, `object `, `interface `
- Каждая функция/класс = один чанк
- `section` = имя функции/класса

Метаданные: `source`, `chunk_id`, `section`, `strategy="structural"`

### Индекс: `rag_index.json`

```json
{
  "version": 1,
  "model": "text-embedding-3-small",
  "chunkSize": 300,
  "overlap": 50,
  "createdAt": "2026-03-17T10:00:00Z",
  "strategies": {
    "fixed": {
      "chunkCount": 120,
      "avgChunkSize": 295
    },
    "structural": {
      "chunkCount": 85,
      "avgChunkSize": 412
    }
  },
  "chunks": [
    {
      "chunk_id": "fixed_0001",
      "source": "CLAUDE.md",
      "section": null,
      "strategy": "fixed",
      "text": "...",
      "embedding": [0.12, -0.34, ...]
    },
    ...
  ]
}
```

Хранится рядом с `schedules.json` в рабочей директории сервера. Загружается в память при старте.

### Embedding API

Добавить в `RouterAiApiService`:

```kotlin
suspend fun embed(texts: List<String>, model: String = "text-embedding-3-small"): List<List<Float>>
```

POST `https://routerai.ru/api/v1/embeddings`

```json
{"model": "text-embedding-3-small", "input": ["text1", "text2"]}
```

Response:
```json
{"data": [{"embedding": [...], "index": 0}, ...]}
```

Батчи по 100 текстов (ограничение API).

### Cosine similarity

```kotlin
fun cosineSimilarity(a: List<Float>, b: List<Float>): Float
```

Вычисляется в памяти на сервере при каждом запросе. FAISS не нужен — для ~200 чанков достаточно линейного прохода.

## Server: `RagRoutes.kt`

```
POST /rag/index          — запустить переиндексацию (ответ: статус + статистика)
GET  /rag/index/stats    — статистика текущего индекса (chunkCount, strategies)
POST /rag/search         — семантический поиск (query, k, strategy) → top-K чанков
POST /rag/chat           — RAG-чат (query, k, strategy) → LLM ответ + использованные чанки
```

### RagIndexer.kt (сервис)

Зависимости: `RouterAiApiService`, `RagRepository`

Методы:
- `buildIndex(chunkSize, overlap)` — читает все документы, чанкует двумя стратегиями, генерирует эмбеддинги, сохраняет JSON
- `search(query, k, strategy)` — embed(query) → cosine top-K

### RagRepository.kt

Аналогично `ScheduleRepository` (Mutex + JSON файл):
- `load(): RagIndex?`
- `save(index: RagIndex)`

## Shared: `RagAgent.kt`

`shared/.../data/agent/RagAgent.kt`

Методы:
- `buildIndex(chunkSize, overlap)` — POST /rag/index
- `getStats()` — GET /rag/index/stats
- `chat(query, k, strategy)` — POST /rag/chat → `RagChatResult`
- `search(query, k, strategy)` — POST /rag/search → `List<RagChunkResult>`

### Domain Models: `RagModels.kt`

```kotlin
enum class ChunkingStrategy { FIXED, STRUCTURAL }

data class RagChunkResult(
    val chunkId: String,
    val source: String,
    val section: String?,
    val strategy: ChunkingStrategy,
    val text: String,
    val score: Float,        // cosine similarity
)

data class RagChatResult(
    val answer: String,
    val usedChunks: List<RagChunkResult>,
)

data class RagIndexStats(
    val chunkCount: Int,
    val fixedCount: Int,
    val structuralCount: Int,
    val avgFixedSize: Int,
    val avgStructuralSize: Int,
    val model: String,
    val createdAt: String,
)

data class RagIndexConfig(
    val chunkSize: Int = 300,
    val overlap: Int = 50,
    val topK: Int = 5,
    val strategy: ChunkingStrategy = ChunkingStrategy.STRUCTURAL,
)
```

## composeApp: RagScreen

### Contract

```kotlin
State(
    // Режим просмотра (чат или сравнение)
    tab: RagTab = RagTab.CHAT,           // CHAT | COMPARE

    // Чат
    messages: ImmutableList<RagMessage> = persistentListOf(),
    inputText: String = "",
    isLoading: Boolean = false,

    // Конфигурация
    chunkSize: Int = 300,                // слайдер 100–1000
    overlap: Int = 50,                   // слайдер 0–200
    topK: Int = 5,                       // слайдер 1–10
    activeStrategy: ChunkingStrategy = ChunkingStrategy.STRUCTURAL,

    // Статистика индекса
    stats: RagIndexStats? = null,
    isIndexing: Boolean = false,

    // Compare tab
    compareStrategy: ChunkingStrategy = ChunkingStrategy.FIXED,

    errorMessage: String? = null,
)

sealed interface RagTab { object CHAT; object COMPARE }

data class RagMessage(
    val role: String,
    val content: String,
    val usedChunks: ImmutableList<RagChunkResult> = persistentListOf(),
)

Events: InputChanged, SendMessage, ClearHistory,
        BuildIndex, TabSelected, StrategyChanged,
        ChunkSizeChanged, OverlapChanged, TopKChanged,
        CompareStrategyChanged, DismissError

Effects: ScrollToBottom
```

### UI Layout

```
┌─────────────────────────────────────────┐
│  [💬 Чат]  [📊 Сравнение]    Индекс: ✓ │  ← top bar
├─────────────────────────────────────────┤
│  ┌ Настройки (collapsible card) ──────┐ │
│  │  Стратегия: [Fixed] [Structural]   │ │  ← ToggleButton
│  │  Chunk size: ──●──  300            │ │  ← Slider
│  │  Overlap:   ──●──   50             │ │  ← Slider
│  │  Top-K:     ──●──    5             │ │  ← Slider
│  │  [Переиндексировать]               │ │  ← кнопка
│  └────────────────────────────────────┘ │
├─────────────────────────────────────────┤
│  CHAT TAB:                              │
│  Чат-пузырьки (weight 1f)              │
│  ┌──────────────────────────────────┐  │
│  │ assistant: Ответ...              │  │
│  ├──────────────────────────────────┤  │
│  │ 📄 CLAUDE.md · MVI Rules   0.89 │  │  ← chunk badge
│  │ 📄 day20.md · Architecture 0.82 │  │
│  └──────────────────────────────────┘  │
├─────────────────────────────────────────┤
│  COMPARE TAB:                           │
│  [Fixed] [Structural]  ← таб-переключ. │
│  Чанков: 120   Средний размер: 295      │
│  ┌──────────────────────────────────┐  │
│  │ #1 CLAUDE.md — [текст чанка...] │  │
│  │ #2 day20.md  — [текст чанка...] │  │
│  │ #3 ...                          │  │
│  └──────────────────────────────────┘  │
├─────────────────────────────────────────┤
│  Input bar (text + → кнопка)            │
└─────────────────────────────────────────┘
```

### Chunk badge

```
📄 CLAUDE.md · MVI Rules   0.89
```
Цвет фона — `tertiaryContainer`. Показывается только у сообщений ассистента, если `usedChunks` не пустой.

### Статус индекса в top bar

- Нет индекса → `⚠️ Индекс не создан`
- Индексируется → `⏳ Индексирование...` + CircularProgressIndicator
- Готов → `✓ N чанков`

## App.kt

Добавить `"День 21"` в конец списка табов, `else → RagScreen()`.

## Инициализация в Application.kt

```kotlin
val ragRepository = RagRepository()
val ragIndexer = RagIndexer(ragRepository, RouterAiApiService())
installRagRoutes(ragRepository, ragIndexer)

launch {
    if (ragRepository.load() == null) {
        ragIndexer.buildIndex(chunkSize = 300, overlap = 50)
    }
}
```

## Тестовый сценарий

**Запрос:** «Как работает PipelineAgent и какие инструменты он использует?»

**Ожидаемый результат:**
- top-5 чанков из `day19-pipeline.md` и `PipelineAgent.kt`
- LLM отвечает на основе контекста из чанков
- В UI показаны 5 найденных чанков со score >0.7

## Затронутые файлы

**Новые:**
- `server/.../rag/RagModels.kt`
- `server/.../rag/RagRepository.kt`
- `server/.../rag/RagIndexer.kt`
- `server/.../rag/RagRoutes.kt`
- `shared/.../domain/model/RagModels.kt`
- `shared/.../data/agent/RagAgent.kt`
- `composeApp/.../rag/RagContract.kt`
- `composeApp/.../rag/RagViewModel.kt`
- `composeApp/.../rag/RagScreen.kt`

**Изменённые:**
- `shared/.../api/RouterAiApiService.kt` — добавить `embed()` метод
- `server/.../Application.kt` — `installRagRoutes()`
- `composeApp/.../App.kt` — новый таб + импорт

## Зависимости

**server/build.gradle.kts** — добавить Apache PDFBox для парсинга PDF:
```kotlin
implementation("org.apache.pdfbox:pdfbox:3.0.3")
```

## Паттерны из существующего кода

| Аспект | Источник паттерна |
|---|---|
| JSON + Mutex repository | `ScheduleRepository.kt` (День 18) |
| HTTP-роуты | `PipelineRoutes.kt` (День 19) |
| Agent-паттерн | `PipelineAgent.kt` (День 19) |
| Нижняя панель с настройками | `OrchestratorScreen.kt` (День 20) |
| Chunk badges | `PipelineScreen.kt` (День 19) |
| ImmutableList в State | Все экраны |
