---
name: Day 28 — Local LLM + RAG
description: Полностью локальный RAG-пайплайн с nomic-embed-text (Ollama) + side-by-side сравнение с облачным
type: project
---

Полностью локальный RAG-пайплайн: nomic-embed-text эмбеддинги через Ollama, генерация через Ollama chat. Side-by-side сравнение с облачным RAG.

**Why:** День 28 challenge — продемонстрировать полностью локальную RAG-систему и сравнить с облачной.

**How to apply:** При запросах на Local RAG или расширении — учитывать что локальный индекс в `local_rag_index.json` (768-dim, nomic-embed-text), облачный в `rag_index.json` (1536-dim, text-embedding-3-small).

## Новые файлы

### Server
- `server/.../rag/LocalRagServerModels.kt` — DTOs: LocalRagIndexFile, LocalRagIndexRequest/Response, LocalRagChatRequest/ResponseDto, LocalRagCompareRequest/Response, OllamaEmbedRequest/Response
- `server/.../rag/LocalRagRepository.kt` — хранит `local_rag_index.json`, аналог RagRepository
- `server/.../rag/LocalRagIndexer.kt` — Ollama /api/embed батчи (50 чанков), cosine similarity, chatLocal, compareLocalVsCloud (parallel async)
- `server/.../rag/LocalRagRoutes.kt` — GET /rag/local/index/stats, POST /rag/local/index|chat|compare

### Shared
- `shared/.../domain/model/LocalRagModels.kt` — LocalRagChatResult, LocalRagCompareResult, LocalRagStats
- `shared/.../data/agent/LocalRagAgent.kt` — HTTP клиент к /rag/local/** эндпоинтам

### composeApp
- `composeApp/.../day28/Day28Contract.kt` — State, Event, Effect, SAMPLE_QUESTIONS
- `composeApp/.../day28/Day28ViewModel.kt` — loadModels + loadStats, buildIndex, compare (параллельный async)
- `composeApp/.../day28/Day28Screen.kt` — IndexStatusCard + ModelDropdown + Side-by-side ResultCard

## Архитектурные детали
- `RagIndexer.buildRawChunks()` — добавлен internal метод для переиспользования чанков без эмбеддинга
- Ollama embed API: POST /api/embed с `input: List<String>` (batch)
- Ollama chat API: POST /api/chat с `stream: false`
- Cosine similarity вычисляется локально на сервере
- Для compare: coroutineScope + async для параллельного запуска local + cloud
- `private data class ErrorResponse` нельзя дублировать в разных файлах одного пакета на JVM (naming conflict) → использовать mapOf или уникальные имена
