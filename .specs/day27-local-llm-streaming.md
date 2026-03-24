# День 27 — Интеграция локальной LLM со стримингом

## Контекст
День 26 реализует базовый чат с Ollama (`stream=false`, хардкод `llama3.2:3b`, ждём весь ответ).
День 27 превращает его в «настоящее» приложение с потоковым выводом и выбором модели.

## Цели
1. Ответ от локальной LLM отображается **посимвольно** (streaming UX, курсор `▌`)
2. Список моделей загружается **динамически** из Ollama (`GET /api/tags`)
3. Работает **только без облака** — Cloud-режим отсутствует

## Архитектура

### Сервер (новые endpoints в `LocalLlmRoutes.kt`)

| Метод | Путь | Описание |
|-------|------|----------|
| GET | `/local/models` | Проксирует `GET http://localhost:11434/api/tags` → `List<String>` имён моделей |
| POST | `/local/stream` | Проксирует Ollama `stream=true` как SSE: `data: <token>\n\n`, финал `data: [DONE]\n\n` |

SSE-формат токенов — **plain text**, не JSON:
```
data: Hello\n\n
data:  world\n\n
data: [DONE]\n\n
```

### Shared (`LocalLlmStreamAgent.kt`)
- Принимает `messages: List<LocalChatMessageDto>`, `model: String`, `onChunk: (String) -> Unit`
- `GET /local/models` → `List<String>`
- `POST /local/stream` с `preparePost().execute { bodyAsChannel().readUTF8Line() }` (аналог `RouterAiApiService.streamMessages`)
- Парсит SSE-строки: `data: ` prefix → strip, `[DONE]` → break

### Presentation (`composeApp/.../day27/`)

**Day27Contract.kt**
```
State(
  messages: List<Message>,
  streamingText: String,      // накапливается во время стриминга
  isStreaming: Boolean,
  inputText: String,
  models: List<String>,
  selectedModel: String,
  isLoadingModels: Boolean,
  error: String?
)
Event: InputChanged | SendMessage | ClearHistory | SelectModel | LoadModels
Effect: ScrollToBottom
```

**Day27ViewModel.kt** — аналог `AgentViewModel`:
- `onStart` (или `init`) загружает список моделей (`LoadModels`)
- `SendMessage` → `agent.streamMessage(onChunk = { _state.update { it.copy(streamingText = it.streamingText + chunk) } })`
- По окончании стриминга — flush `streamingText` в `messages`, сброс флагов

**Day27Screen.kt**
- Header: название + DropdownMenu с моделями
- `StreamingBubble` — курсор `▌` с анимацией (как в `AgentScreen`)
- Только один бэкенд: Ollama local

### App.kt
Добавить `"День 27"` в конец `tabs` и `else -> Day27Screen()` в `when`.

## Файлы для изменения/создания

| Действие | Путь |
|----------|------|
| Изменить | `server/.../local/LocalLlmRoutes.kt` |
| Создать  | `shared/.../data/agent/LocalLlmStreamAgent.kt` |
| Создать  | `composeApp/.../day27/Day27Contract.kt` |
| Создать  | `composeApp/.../day27/Day27ViewModel.kt` |
| Создать  | `composeApp/.../day27/Day27Screen.kt` |
| Изменить | `composeApp/.../App.kt` |

## Паттерны из кодовой базы
- Стриминг: `RouterAiApiService.streamMessages()` → `preparePost().execute { bodyAsChannel() }`
- StreamingBubble + курсор: `AgentScreen.kt`
- ViewModel стриминг: `AgentViewModel.kt` (`streamingText` накапливается через `onChunk`)
- SSE сервер: `respondTextWriter(ContentType.Text.EventStream)` + `flush()` на каждый токен
