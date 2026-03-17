---
name: Day 21 RAG Indexing
description: День 21 — полный RAG-пайплайн с индексированием документов и RAG-чатом
type: project
---

День 21 реализован и компилируется.

**Why:** Учебное задание по RAG: эмбеддинги → поиск по индексу → LLM с контекстом.

**How to apply:** При работе с RAG-компонентами смотри на эти файлы как референс.

## Что реализовано

- **Корпус**: .specs/*.md + CLAUDE.md + README.md + shared/**/*.kt + docs/ (user files)
- **Эмбеддинги**: routerai.ru /v1/embeddings, модель text-embedding-3-small, батчи по 100
- **Хранилище**: rag_index.json (JSON, рядом с schedules.json)
- **Chunking**: 2 стратегии — Fixed (sliding window) и Structural (по заголовкам/функциям)
- **Поиск**: cosine similarity в памяти, top-K
- **RAG-чат**: embed query → top-K chunks → system prompt with context → LLM

## Ключевые файлы

- `server/.../rag/RagIndexer.kt` — chunking + embedding + search
- `server/.../rag/RagRepository.kt` — JSON storage
- `server/.../rag/RagRoutes.kt` — POST /rag/index, GET /rag/index/stats, POST /rag/search, POST /rag/chat
- `shared/.../domain/model/RagModels.kt` — domain models
- `shared/.../data/agent/RagAgent.kt` — HTTP client
- `composeApp/.../rag/` — MVI screen (Contract, ViewModel, Screen)
- `shared/.../api/RouterAiModels.kt` — добавлен EmbeddingRequest/Response/Data
- `shared/.../api/RouterAiApiService.kt` — добавлен метод embed()
- `server/build.gradle.kts` — добавлен PDFBox 3.0.3

## Авто-индексация

При старте сервера: если rag_index.json не существует — автоматически запускает buildIndex(300, 50).
