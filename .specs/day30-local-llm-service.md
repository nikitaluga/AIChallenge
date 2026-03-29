# Day 30 — Локальная LLM как приватный сервис

## Цель

Превратить локальную LLM (Ollama) из хардкода `localhost:11434` в настоящий сетевой сервис с конфигурируемым URL, защитой от перегрузки (Semaphore), health check endpoint и UI для мониторинга + чата.

## Контекст кодовой базы

Существующие файлы, которые затрагивает фича:

| Файл | Что там сейчас |
|------|----------------|
| `server/.../local/LocalLlmRoutes.kt` | `/local/chat`, `/local/models`, `/local/stream`, `/local/cloud` — Ollama URL хардкодирован `http://localhost:11434` |
| `server/.../local/LocalOptimizationRoutes.kt` | `/local/benchmark`, `/local/judge` — тот же хардкод |
| `server/src/main/kotlin/.../Application.kt` | Регистрирует `installLocalLlmRoutes` и `installLocalOptimizationRoutes` |
| `shared/.../domain/model/LocalLlmModels.kt` | Модели для клиента Day 26-29 |
| `composeApp/.../App.kt` | Root nav — нужно добавить вкладку День 30 |

## Решения из интервью

| Вопрос | Решение |
|--------|---------|
| Где Ollama | Домашний Mac, доступ через **ngrok** (`ngrok http 11434`) |
| URL конфиг | ENV/JVM property **`OLLAMA_BASE_URL`**, дефолт `http://localhost:11434` |
| Rate limit | **Semaphore(2)** — max 2 параллельных запроса к Ollama |
| Health check | `GET /local/health` — статус Ollama + список моделей + active requests |
| Max context | ENV `OLLAMA_MAX_CTX` (лимит num_ctx, макс 4096) + обрезка истории до **20 сообщений** |
| Tunnel | ngrok, инструкции в UI |
| UI | Service Dashboard (health + stats) + чат с выбором модели |
| Тест | Android + iOS Simulator + curl |

## Серверная часть

### 1. OllamaConfig (новый файл)

```
server/.../local/OllamaConfig.kt
```

```kotlin
data class OllamaConfig(
    val baseUrl: String = System.getenv("OLLAMA_BASE_URL") ?: "http://localhost:11434",
    val maxConcurrent: Int = (System.getenv("OLLAMA_CONCURRENCY") ?: "2").toInt(),
    val maxCtx: Int = (System.getenv("OLLAMA_MAX_CTX") ?: "4096").toInt(),
    val maxHistoryMessages: Int = 20,
)
```

### 2. GET /local/health (новый endpoint в LocalLlmRoutes.kt)

Response:
```json
{
  "status": "ok" | "unavailable",
  "ollama_url": "http://localhost:11434",
  "models": ["llama3.2:3b", "phi3:mini"],
  "active_requests": 1,
  "max_concurrent": 2
}
```

- Делает `GET {ollamaBaseUrl}/api/tags` для проверки доступности
- `active_requests` — текущие permits taken у Semaphore

### 3. Рефакторинг: OllamaConfig + Semaphore

- `LocalLlmRoutes.kt` и `LocalOptimizationRoutes.kt` принимают `OllamaConfig` вместо хардкода
- Все HTTP-вызовы к Ollama оборачиваются в `semaphore.withPermit { ... }`
- `LocalChatOptions.numCtx` проверяется: `min(requested, config.maxCtx)`
- История в `/local/chat` обрезается до `config.maxHistoryMessages` последних сообщений

### 4. Application.kt

```kotlin
val ollamaConfig = OllamaConfig()
installLocalLlmRoutes(sharedApiService, ollamaConfig)
installLocalOptimizationRoutes(sharedApiService, ollamaConfig)
```

## UI — День 30 (composeApp)

### Структура пакета

```
composeApp/.../day30/
├── Day30Contract.kt   — State, Event, Effect
├── Day30ViewModel.kt  — loadHealth(), sendMessage()
└── Day30Screen.kt     — Dashboard + Chat
```

### State

```kotlin
data class State(
    val healthStatus: HealthStatus = HealthStatus.Loading,
    val ollamaUrl: String = "",
    val models: List<String> = emptyList(),
    val activeRequests: Int = 0,
    val maxConcurrent: Int = 2,
    val selectedModel: String = "llama3.2:3b",
    val messages: List<ChatEntry> = emptyList(),
    val inputText: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
)

enum class HealthStatus { Loading, Ok, Unavailable }
data class ChatEntry(val role: String, val content: String)
```

### Events

```kotlin
sealed interface Event {
    object RefreshHealth : Event
    data class SelectModel(val model: String) : Event
    data class InputChanged(val text: String) : Event
    object SendMessage : Event
    object ClearChat : Event
}
```

### UI Layout

```
┌──────────────────────────────────────┐
│ 🟢 Ollama: http://localhost:11434    │
│ Models: llama3.2:3b, phi3:mini       │
│ Active: 1/2   [↻ Refresh]            │
├──────────────────────────────────────┤
│ [Model ▾ llama3.2:3b]                │
│                                      │
│  user: Hello                         │
│  assistant: Hi there...              │
│                                      │
│ [________________________] [Send]    │
└──────────────────────────────────────┘
```

- HealthStatus.Ok → зелёный индикатор, статус "Ollama online"
- HealthStatus.Unavailable → красный + hint "Run: ngrok http 11434"
- HealthStatus.Loading → прогресс

## Shared-слой

### LocalLlmModels.kt — добавить модели для health

```kotlin
@Serializable
data class ServiceHealthResponse(
    val status: String,         // "ok" | "unavailable"
    @SerialName("ollama_url") val ollamaUrl: String,
    val models: List<String>,
    @SerialName("active_requests") val activeRequests: Int,
    @SerialName("max_concurrent") val maxConcurrent: Int,
)
```

### LocalLlmAgent или прямой вызов из ViewModel

ViewModel вызывает `LocalLlmRepository` (уже существующий паттерн из Days 26-29):
- `getHealth(): Result<ServiceHealthResponse>`
- `chat(messages, model): Result<LocalChatResponse>`

## Ngrok — инструкция для пользователя

Показывается в UI когда `status == "unavailable"`:

```
Для доступа по сети:
1. brew install ngrok
2. ngrok http 11434
3. Скопируй URL в ENV OLLAMA_BASE_URL
4. Перезапусти сервер
```

## Затронутые файлы

### Изменяются

- `server/.../local/LocalLlmRoutes.kt` — OllamaConfig, Semaphore, /health, max context/history
- `server/.../local/LocalOptimizationRoutes.kt` — OllamaConfig, Semaphore
- `server/.../Application.kt` — OllamaConfig construction + passing
- `shared/.../domain/model/LocalLlmModels.kt` — ServiceHealthResponse
- `composeApp/.../App.kt` — новая вкладка "День 30"

### Создаются

- `server/.../local/OllamaConfig.kt`
- `composeApp/.../day30/Day30Contract.kt`
- `composeApp/.../day30/Day30ViewModel.kt`
- `composeApp/.../day30/Day30Screen.kt`

## Сценарии тестирования

### curl (базовый)
```bash
# Health check
curl http://localhost:8080/local/health

# Chat
curl -X POST http://localhost:8080/local/chat \
  -H "Content-Type: application/json" \
  -d '{"messages":[{"role":"user","content":"Hello"}],"model":"llama3.2:3b"}'

# Параллельные запросы (Semaphore тест)
for i in 1 2 3; do
  curl -s -X POST http://localhost:8080/local/chat \
    -d '{"messages":[{"role":"user","content":"Count to 10"}],"model":"llama3.2:3b"}' \
    -H "Content-Type: application/json" &
done
wait
```

### Android + iOS
1. Сервер запущен, Android/iOS подключены к той же сети или через ngrok
2. Открыть вкладку "День 30"
3. Dashboard показывает зелёный статус
4. Чат отвечает с правильной моделью
5. При 3 параллельных запросах — только 2 идут одновременно

## Риски

| Риск | Митигация |
|------|-----------|
| Ollama недоступна | /health возвращает `status: "unavailable"`, UI показывает ngrok-инструкцию |
| Большой контекст OOM | maxCtx cap через ENV + обрезка истории до 20 сообщений |
| Долгий ответ > 120s | HttpTimeout уже настроен на 120s в LocalLlmRoutes |
| ngrok URL меняется при рестарте | Пользователь обновляет ENV и рестартует сервер |
