# День 17 — MCP Weather Tool

## Цель
Реализовать MCP-сервер вокруг / API и подключить его к агенту в мобильном приложении.
В UI — переключатель Connect/Disconnect для демонстрации разницы в поведении агента.

## Архитектурные решения (из интервью)

| Вопрос | Решение |
|--------|---------|
| MCP-транспорт | HTTP/REST (POST /mcp/tools/call, GET /mcp/tools/list) на Ktor port 8080 |
| Агент вызывает инструмент | Tool-calling через LLM (OpenAI function-calling format) |
| Поведение при disconnect | Fallback — LLM отвечает из общих знаний без реальных данных |
| URL сервера | Хардкод `http://10.0.2.2:8080` (localhost эмулятора Android) |

## Новый экран

Вкладка **"День 17"** (МСП Погода) добавляется последней в `App.kt`.

### UX
- Кнопка **Connect / Disconnect** вверху экрана с индикатором статуса
- Чат для ввода вопросов о погоде
- Когда MCP ON: агент отправляет инструмент в LLM → LLM вызывает tool → агент идёт к MCP-серверу → результат возвращается LLM → итоговый ответ
- Когда MCP OFF: LLM отвечает из общих знаний без инструмента

---

## Компоненты

### 1. Ktor Server — MCP endpoints

**Файлы:**
- `server/src/main/kotlin/.../mcp/WeatherService.kt` — вызов OpenWeatherMap
- `server/src/main/kotlin/.../mcp/McpRoutes.kt` — REST-эндпоинты

**Эндпоинты:**
```
GET  /mcp/tools/list   → { tools: [{ name, description, inputSchema }] }
POST /mcp/tools/call   → body: { name: "get_weather", input: { city: "Moscow" } }
                       ← body: { result: "City: Moscow\nTemperature: -5°C\n..." }
```

**WeatherService**: вызывает `https://api.openweathermap.org/data/2.5/weather?q={city}&appid=3c0820600da5b2cc86aa8c18cec34061&units=metric&lang=ru`

### 2. shared — Tool-calling модели и агент

**RouterAiModels.kt** — добавить:
- `ToolDefinition`, `ToolFunction`, `ToolParameters`, `ToolProperty`
- `ToolCall`, `ToolCallFunction` (ответ LLM с вызовом инструмента)
- `ToolCallResult` — обёртка для sendMessagesWithTools
- `ChatMessage.content: String? = null` — поддержка null при tool_calls
- `ChatRequest.tools: List<ToolDefinition>? = null`

**RouterAiApiService.kt** — добавить:
- `sendMessagesWithTools(messages, tools, model)` → `ToolCallResult`

**McpModels.kt** (новый, domain/model):
- `McpChatMessage(role, content, toolCallMade, toolName, toolResult)`
- `McpAgentResult(content, toolCallMade, toolName, toolInput, toolResult)`

**McpWeatherAgent.kt** (новый, data/agent):
- Параметры: `apiService`, `mcpBaseUrl`
- `sendMessage(text, isMcpConnected): McpAgentResult`
- Собственный Ktor HttpClient для вызова MCP сервера

### 3. composeApp — новый экран `mcp/`

**McpContract.kt:**
```
State(isMcpConnected, messages, inputText, isLoading, errorMessage)
Event: Connect, Disconnect, InputChanged, SendMessage, ClearHistory, DismissError
Effect: ScrollToBottom
```

**McpViewModel.kt:** создаёт `McpWeatherAgent`, обрабатывает события

**McpScreen.kt:** Connect/Disconnect chip + чат

---

## Затронутые файлы

| Файл | Изменение |
|------|-----------|
| `gradle/libs.versions.toml` | + `ktor-serverContentNegotiation` |
| `server/build.gradle.kts` | + serialization plugin, + content-negotiation dep |
| `server/.../Application.kt` | + install ContentNegotiation, + McpRoutes |
| `shared/.../api/RouterAiModels.kt` | + tool-calling models |
| `shared/.../api/RouterAiApiService.kt` | + sendMessagesWithTools() |
| `shared/.../domain/model/McpModels.kt` | новый |
| `shared/.../data/agent/McpWeatherAgent.kt` | новый |
| `composeApp/.../mcp/McpContract.kt` | новый |
| `composeApp/.../mcp/McpViewModel.kt` | новый |
| `composeApp/.../mcp/McpScreen.kt` | новый |
| `composeApp/.../App.kt` | + "День 17" вкладка |

---

## Данные о погоде (OpenWeatherMap)

API KEY: `3c0820600da5b2cc86aa8c18cec34061`
Endpoint: `https://api.openweathermap.org/data/2.5/weather?q={city}&appid={key}&units=metric&lang=ru`

Возвращаемые поля: `name`, `main.temp`, `main.feels_like`, `main.humidity`, `weather[0].description`, `wind.speed`
