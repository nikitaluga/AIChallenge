---
name: Day 23 — RAG Reranking & Filter
description: Threshold filter + LLM query rewrite + triple comparison mode added to RAG
type: project
---

День 23 — RAG улучшен threshold-фильтрацией и query rewrite.

**Why:** Учебный день по улучшению RAG-пайплайна.

**How to apply:** Новый endpoint `/rag/compare/enhanced` возвращает тройное сравнение. Compare-таб расширен режимом "День 23: 3 режима".

## Что добавлено

- Server: `compareEnhanced()` в RagIndexer, `rewriteQuery()`, новые модели FilterStats/RagEnhancedCompare*
- Server: `POST /rag/compare/enhanced` в RagRoutes
- Shared: `FilterStats`, `RagTripleCompareResult` в RagModels; `compareEnhanced()` в RagAgent
- Presentation: новые State поля (threshold=0.35, topKBefore=20, rewriteEnabled=true, tripleCompareResult), Events (ThresholdChanged, TopKBeforeChanged, RewriteToggled, RunEnhancedCompare, SelectEnhancedQuestion)
- UI: Compare-таб с переключателем режимов, слайдер threshold, тройное сравнение (Без RAG / RAG базовый / RAG+Filter+Rewrite), воронка фильтрации
