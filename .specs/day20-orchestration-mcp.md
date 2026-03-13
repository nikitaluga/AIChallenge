# День 20 — Orchestration MCP

## Цель

Продемонстрировать оркестрацию нескольких MCP-серверов: агент динамически обнаруживает инструменты каждого сервера, автономно выбирает нужный и корректно маршрутизирует запросы через длинный multi-server флоу.

## Архитектура: 3 MCP-сервера

Все три сервера живут в одном Ktor-процессе (порт 8080) под разными маршрутами — имитируют независимые MCP-эндпоинты.

```
Server 1: /mcp/weather  — get_weather(city)                      [reuse WeatherService]
Server 2: /mcp/notes    — create_note, list_notes, read_note     [notes.json + Mutex]
Server 3: /mcp/calc     — convert_units, calculate               [pure Kotlin logic]
```

Каждый сервер предоставляет два эндпоинта (паттерн из День 17):
- `GET /mcp/{server}/tools/list` — список инструментов с JSON Schema
- `POST /mcp/{server}/tools/call` — выполнить инструмент

## Server 1: Weather MCP `/mcp/weather`

### Инструменты
- **get_weather(city: String)** — текущая погода через OWM. Reuses `WeatherService.getWeather()`.

### Реализация
`WeatherMcpRoutes.kt` — аналог существующего `McpRoutes.kt`, но под `/mcp/weather/`.
Существующий `/mcp/tools` (День 17) не трогаем — оставляем для обратной совместимости.

## Server 2: Notes MCP `/mcp/notes`

### Инструменты
- **create_note(title: String, content: String)** — создать/перезаписать заметку
- **list_notes()** — список всех заметок (title + preview первых 50 символов)
- **read_note(title: String)** — полное содержимое заметки

### Хранилище
`NoteRepository.kt` — аналог `ScheduleRepository`:
```kotlin
data class Note(val title: String, val content: String, val createdAt: String)
// notes.json рядом с schedules.json и jar
// Mutex для thread-safety
```

### Реализация
`NotesMcpRoutes.kt`

## Server 3: Calculator MCP `/mcp/calc`

### Инструменты
- **convert_units(value: String, from_unit: String, to_unit: String)** — конвертация:
  - Температура: celsius ↔ fahrenheit ↔ kelvin
  - Расстояние: km ↔ miles ↔ meters
  - Вес: kg ↔ pounds ↔ grams
- **calculate(expression: String)** — арифметика (+−×÷, скобки). Парсим вручную (no eval/ScriptEngine) через простой рекурсивный парсер выражений.

### Реализация
`CalcMcpRoutes.kt`

## OrchestratorAgent (shared)

`shared/.../data/agent/OrchestratorAgent.kt`

### Принцип работы

**1. Регистр серверов** — список `McpServerConfig`:
```kotlin
data class McpServerConfig(
    val id: String,        // "weather", "notes", "calc"
    val displayName: String,
    val baseUrl: String,   // "http://10.0.2.2:8080/mcp/weather"
    val emoji: String,     // "🌤", "📝", "⚙️"
)
```

**2. Динамическое обнаружение** — `discoverTools()`:
```
для каждого сервера → GET {baseUrl}/tools/list
собрать все инструменты + запомнить map: toolName → serverId
```

**3. Цикл оркестрации** (max 8 итераций — 3-4+ tool calls):
```
repeat(8):
  sendMessagesWithTools(history, allTools)
  if finishReason == "tool_calls":
    toolCall = result.toolCalls.first()
    serverId = toolToServerMap[toolCall.function.name]
    result = POST {serverBaseUrl}/tools/call {name, input}
    toolSteps.add(OrchestratorToolStep(serverName, toolName, input, result))
    history.add(assistant + tool messages)
    continue
  else:
    return OrchestratorResult(content, toolSteps)
```

**4. Маршрутизация** — по имени инструмента из `toolToServerMap` (заполняется при discoverTools).

### Domain Models

```kotlin
data class OrchestratorToolStep(
    val serverId: String,      // "weather", "notes", "calc"
    val serverName: String,    // "Weather MCP", "Notes MCP"
    val serverEmoji: String,   // "🌤", "📝", "⚙️"
    val toolName: String,
    val toolInput: String,     // raw JSON
    val toolResult: String,
)

data class OrchestratorMessage(
    val role: String,
    val content: String,
    val toolSteps: List<OrchestratorToolStep> = emptyList(),
)

data class OrchestratorResult(
    val content: String,
    val toolSteps: List<OrchestratorToolStep>,
)

data class McpServerInfo(
    val id: String,
    val displayName: String,
    val emoji: String,
    val toolCount: Int,
    val isOnline: Boolean,
)
```

## OrchestratorScreen (composeApp)

### Contract
```kotlin
State(
    messages: ImmutableList<OrchestratorMessage> = persistentListOf(),
    servers: ImmutableList<McpServerInfo> = persistentListOf(),   // для панели
    inputText: String = "",
    isLoading: Boolean = false,
    isDiscovering: Boolean = false,  // идёт GET /tools/list
    errorMessage: String? = null,
)

Events: InputChanged, SendMessage, ClearHistory, RefreshServers, DismissError
Effects: ScrollToBottom
```

### ViewModel
- `init { discoverServers() }` — запрашивает /tools/list каждого сервера, обновляет `servers`
- `sendMessage()` — стандартный MVI-паттерн
- `RefreshServers` → повторный `discoverServers()`

### UI Layout

```
┌────────────────────────────────┐
│  Чат (weight 1f)               │
│  ┌────────────────────────┐    │
│  │ user: Погода в Москве  │    │
│  └────────────────────────┘    │
│  ┌────────────────────────────┐│
│  │ assistant: Готово!         ││
│  ├────────────────────────────┤│
│  │ 🌤 weather · get_weather   ││
│  │ 🌤 weather · get_weather   ││
│  │ ⚙️ calc · convert_units    ││
│  │ 📝 notes · create_note     ││
│  └────────────────────────────┘│
├────────────────────────────────┤
│  Input bar (text + → + Сброс)  │
├────────────────────────────────┤
│  HorizontalDivider             │
├────────────────────────────────┤
│  ServerRegistry Panel          │
│  Servers (3)          Refresh  │
│  🌤 Weather MCP  ✓ 1 tool      │
│  📝 Notes MCP    ✓ 3 tools     │
│  ⚙️  Calc MCP     ✓ 2 tools    │
└────────────────────────────────┘
```

### Tool Step Badge
```
🌤 weather · get_weather
📝 notes · create_note
⚙️ calc · convert_units
```
Цвет фона — `secondaryContainer` (как в PipelineScreen).

### ServerRegistry Panel
- Аналогична `SchedulesPanel` из День 18 (heightIn max 200dp, verticalScroll)
- Каждая строка: emoji + название + статус (✓ online / ✗ offline) + кол-во tools
- При `isDiscovering` — показать CircularProgressIndicator вместо заголовка

## App.kt

Добавить `"День 20"` в список табов, `else → OrchestratorScreen()`.

## Тестовый сценарий (длинный флоу)

**Запрос:** «Погода в Москве и Лондоне. Переведи температуру Москвы в Фаренгейт. Сохрани сравнение в заметку weather_comparison.»

**Ожидаемый флоу:**
1. `[weather]` get_weather(Москва) → «5°C, облачно»
2. `[weather]` get_weather(Лондон) → «12°C, дождь»
3. `[calc]` convert_units(5, celsius, fahrenheit) → «41°F»
4. `[notes]` create_note(weather_comparison, «Москва: 5°C (41°F)... Лондон: 12°C...»)

**Итог:** 4 tool calls, 3 разных сервера, 8 итераций цикла достаточно.

## Затронутые файлы

**Новые:**
- `server/.../mcp/WeatherMcpRoutes.kt`
- `server/.../notes/NoteRepository.kt`
- `server/.../notes/NotesMcpRoutes.kt`
- `server/.../calc/CalcMcpRoutes.kt`
- `shared/.../domain/model/OrchestratorModels.kt`
- `shared/.../data/agent/OrchestratorAgent.kt`
- `composeApp/.../orchestrator/OrchestratorContract.kt`
- `composeApp/.../orchestrator/OrchestratorViewModel.kt`
- `composeApp/.../orchestrator/OrchestratorScreen.kt`

**Изменённые:**
- `server/.../Application.kt` — `installWeatherMcpRoutes()`, `installNotesMcpRoutes()`, `installCalcMcpRoutes()`
- `composeApp/.../App.kt` — новый таб + импорт + when-branch

## Паттерны из существующего кода

| Аспект | Источник паттерна |
|---|---|
| MCP routes (list + call) | `McpRoutes.kt` (День 17) |
| JSON + Mutex repository | `ScheduleRepository.kt` (День 18) |
| Multi-step tool loop | `PipelineAgent.kt` (День 19) |
| Нижняя панель | `SchedulesPanel` (День 18) |
| Tool step badges | `PipelineScreen.kt` (День 19) |
| ImmutableList в State | Все экраны после рефакторинга |
