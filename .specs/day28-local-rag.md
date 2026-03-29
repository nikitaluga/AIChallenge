# День 28 — Local LLM + RAG (полностью локальный RAG-пайплайн)

## Цель
RAG-система, полностью работающая локально: локальные эмбеддинги (nomic-embed-text через Ollama) + локальная генерация (Ollama chat). Side-by-side сравнение с облачным RAG.

---

## Архитектурные решения

### Эмбеддинги
- Существующий `rag_index.json` использует `text-embedding-3-small` (OpenAI, 1536-dim).
- Для полностью локального пайплайна нужен **отдельный индекс** `local_rag_index.json` с эмбеддингами от **nomic-embed-text** (Ollama, 768-dim).
- Ollama Embeddings API: `POST http://localhost:11434/api/embeddings` → `{"model":"nomic-embed-text","prompt":"..."}`

### Retrieval
- Запрос пользователя эмбеддируется через Ollama nomic-embed-text.
- Cosine similarity против local_rag_index.json.
- Тот же threshold-фильтр, что в RagIndexer (v2).

### Генерация
- Контекст передаётся в Ollama chat (модель выбирается пользователем, default llama3.2:3b).
- Endpoint: `POST /local/chat` уже есть — используем его.

### Сравнение
- Новый endpoint `POST /rag/local/compare`:
  - Параллельно: local RAG (nomic embed + Ollama generate) vs cloud RAG (openai embed + GPT).
  - Возвращает оба ответа + latency каждого.

---

## Новые Endpoints (Server)

### `POST /rag/local/index`
Строит `local_rag_index.json` с nomic-embed-text эмбеддингами.
```json
Request: { "chunkSize": 300, "overlap": 50 }
Response: { "message": "...", "chunkCount": 580, "model": "nomic-embed-text" }
```

### `POST /rag/local/chat`
Полностью локальный RAG-чат.
```json
Request: { "query": "...", "k": 5, "model": "llama3.2:3b" }
Response: { "answer": "...", "sources": [...], "latencyMs": 1234 }
```

### `POST /rag/local/compare`
Side-by-side: local vs cloud.
```json
Request: { "query": "...", "k": 5, "localModel": "llama3.2:3b" }
Response: {
  "local": { "answer": "...", "sources": [...], "latencyMs": ... },
  "cloud": { "answer": "...", "sources": [...], "latencyMs": ... }
}
```

### `GET /rag/local/index/stats`
Статистика локального индекса.

---

## Новые файлы (Server)

### `server/.../rag/LocalRagIndexer.kt`
- `buildLocalIndex(chunkSize, overlap)` — те же документы, что в RagIndexer, но embed через Ollama.
- `searchLocal(query, k)` — embed query через Ollama, cosine similarity.
- `chatLocal(query, k, model)` — search + ollama chat.
- `compareLocalVsCloud(query, k, localModel)` — параллельный coroutines запуск.

### `server/.../rag/LocalRagRepository.kt`
- Хранит `local_rag_index.json`, аналогично RagRepository.

### `server/.../rag/LocalRagRoutes.kt`
- Регистрирует 4 endpoint'а выше.

---

## Новые файлы (Shared)

### `shared/.../domain/model/LocalRagModels.kt`
```kotlin
data class LocalRagChatResult(val answer: String, val sources: List<RagSource>, val latencyMs: Long)
data class LocalRagCompareResult(val local: LocalRagChatResult, val cloud: LocalRagChatResult)
data class LocalRagIndexStats(val chunkCount: Int, val model: String, val createdAt: String?)
```

### `shared/.../data/agent/LocalRagAgent.kt`
- `buildLocalIndex(chunkSize, overlap)` → String
- `chatLocal(query, k, model)` → LocalRagChatResult
- `compareLocalVsCloud(query, k, localModel)` → LocalRagCompareResult
- `getLocalIndexStats()` → LocalRagIndexStats

---

## Новые файлы (composeApp)

### `composeApp/.../day28/`

**Day28Contract.kt**
```kotlin
data class State(
    val isIndexBuilt: Boolean,
    val isIndexing: Boolean,
    val indexStats: LocalRagIndexStats?,
    val query: String,
    val isLoading: Boolean,
    val localResult: LocalRagChatResult?,
    val cloudResult: LocalRagChatResult?,
    val selectedModel: String,
    val availableModels: List<String>,
    val error: String?
)
sealed interface Event {
    data object BuildIndex : Event
    data class QueryChanged(val query: String) : Event
    data object Compare : Event
    data class ModelSelected(val model: String) : Event
}
```

**Day28ViewModel.kt**
- `init` → `loadModels()` + `getLocalIndexStats()`
- `onEvent(BuildIndex)` → `localRagAgent.buildLocalIndex()` → обновить stats
- `onEvent(Compare)` → параллельно local + cloud via `compareLocalVsCloud()`

**Day28Screen.kt**
- Панель вверху: статус индекса + кнопка "Построить локальный индекс" + dropdown модели
- Поле ввода запроса + кнопка Compare
- Side-by-side: LocalRAG | CloudRAG
  - Заголовок с latency
  - Ответ
  - Sources (список файлов)

---

## Wiring

### `server/.../Application.kt`
- Добавить инициализацию `LocalRagRepository`, `LocalRagIndexer`
- Зарегистрировать `LocalRagRoutes`

### `composeApp/.../App.kt`
- Добавить вкладку "День 28" → `Day28Screen()`

---

## Интеграция с существующим кодом

| Что переиспользуем | Где |
|---|---|
| `RagIndexer.collectDocuments()` | Те же документы для локального индекса |
| `RagIndexer.cosineSimilarity()` | Та же функция |
| `LocalLlmRoutes.kt` `/local/models` | Список моделей для dropdown |
| `RagAgent.kt` | Cloud RAG в compare |
| `LocalLlmStreamAgent` | Можно расширить для streaming local RAG (опционально) |

---

## Ограничения и риски

1. **Первая индексация медленная**: 580 чанков × Ollama embed ≈ 5-10 мин без GPU.
2. **Размер local_rag_index.json**: nomic-embed-text 768-dim × 580 чанков ≈ ~3-4 MB (вдвое меньше).
3. **Качество nomic-embed-text**: хуже text-embedding-3-small для русского языка — это ожидаемо, демонстрируем разницу.
4. **Ollama должен быть запущен** — показываем ошибку если недоступен.

---

## Затронутые файлы

### Новые
- `server/.../rag/LocalRagIndexer.kt`
- `server/.../rag/LocalRagRepository.kt`
- `server/.../rag/LocalRagRoutes.kt`
- `shared/.../domain/model/LocalRagModels.kt`
- `shared/.../data/agent/LocalRagAgent.kt`
- `composeApp/.../day28/Day28Contract.kt`
- `composeApp/.../day28/Day28ViewModel.kt`
- `composeApp/.../day28/Day28Screen.kt`

### Изменённые
- `server/.../Application.kt` — регистрация LocalRagRoutes, инициализация
- `composeApp/.../App.kt` — добавление вкладки "День 28"
