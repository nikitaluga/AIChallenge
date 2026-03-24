---
name: Day 27 — Local LLM Streaming
description: Streaming Ollama chat with dynamic model selection (День 27)
type: project
---

День 27 — SSE-стриминг токенов из Ollama + динамический выбор модели.

**Why:** Day 26 ждал весь ответ (stream=false). Day 27 делает чат живым — токены выводятся по мере генерации.

**New endpoints in LocalLlmRoutes.kt:**
- `GET /local/models` → proxies Ollama `/api/tags` → `List<String>`
- `POST /local/stream` → Ollama stream=true → SSE: `data: "<JSON-encoded token>"\n\n`, finish with `data: [DONE]\n\n`

**New files:**
- `shared/.../data/agent/LocalLlmStreamAgent.kt` — getModels() + streamChat() via SSE
- `composeApp/.../day27/Day27Contract.kt` — State(streamingText, models, selectedModel, isStreaming)
- `composeApp/.../day27/Day27ViewModel.kt` — loads models on init, streams via onChunk callback
- `composeApp/.../day27/Day27Screen.kt` — StreamingBubble with blinking cursor ▌ + ModelSelector dropdown

**How to apply:** Таб "День 27" — последний в App.kt. Требует запущенного `ollama serve` на localhost:11434.
