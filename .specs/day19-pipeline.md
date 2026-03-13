# День 19 — MCP Tool Composition Pipeline

## Цель

Реализовать автоматический пайплайн из 3 MCP-инструментов, в котором LLM сама решает когда и в какой последовательности вызывать каждый инструмент:

1. `get_weather(city)` — получить данные о погоде из OWM API
2. `summarize_weather(weather_data)` — сформировать компактную сводку из сырых данных
3. `save_to_file(filename, content)` — сохранить сводку в файл на сервере

## Архитектура

### Server: `PipelineRoutes.kt`

- `POST /pipeline/tools/call` — выполнить инструмент (dispatch по `name`)
- `GET /pipeline/tools/list` — список доступных инструментов (для info)
- `GET /pipeline/files` — список сохранённых файлов (`reports/` folder)
- `GET /pipeline/files/{filename}` — содержимое файла

Инструменты:
- `get_weather(city: String)` — reuses `WeatherService.getWeather()`
- `summarize_weather(weather_data: String)` — парсит многострочный вывод WeatherService в одну строку формата `"Москва: 5°C, пасмурно, влажность 80%, ветер 3 м/с"`
- `save_to_file(filename: String, content: String)` — создаёт `reports/` если нет, пишет файл, возвращает `"Сохранено: reports/{filename}"`

Хранилище: папка `reports/` рядом с `schedules.json`.

### Shared: `PipelineAgent.kt`

Многошаговый цикл tool-calling (макс. 5 итераций):
```
loop:
  sendMessagesWithTools(history, [3 tools])
  → if finishReason == "tool_calls":
      executeToolCall() → получить результат
      добавить в history assistant + tool сообщения
      continue loop
  → else:
      history.add(assistant answer)
      return PipelineAgentResult
```

Возвращает `PipelineAgentResult(content, toolSteps: List<PipelineToolStep>)`.

### Domain Models: `PipelineModels.kt`

```kotlin
data class PipelineToolStep(
    val toolName: String,
    val toolInput: String,   // raw JSON args
    val toolResult: String,
)

data class PipelineChatMessage(
    val role: String,
    val content: String,
    val toolSteps: List<PipelineToolStep> = emptyList(),
)

data class PipelineAgentResult(
    val content: String,
    val toolSteps: List<PipelineToolStep> = emptyList(),
)

data class SavedFileInfo(
    val filename: String,
    val sizeBytes: Long,
)
```

### composeApp: Pipeline Screen

**Contract:**
- State: `messages`, `savedFiles: List<SavedFileInfo>`, `inputText`, `isLoading`, `errorMessage`
- Events: `InputChanged`, `SendMessage`, `ClearHistory`, `RefreshFiles`, `DismissError`
- Effects: `ScrollToBottom`

**ViewModel:**
- `sendMessage()` → вызывает `PipelineAgent.sendMessage()`, после успеха обновляет `savedFiles`
- `loadFiles()` → `GET /pipeline/files`
- `onEvent(RefreshFiles)` → `loadFiles()`

**Screen:**
- Чат с пузырьками (как в SchedulerScreen)
- Каждое сообщение ассистента показывает вертикальный список pipeline-шагов:
  - `[🔍 get_weather: Moscow]`
  - `[📝 summarize_weather]`
  - `[💾 save_to_file: moscow.txt]`
- Нижняя панель `Сохранённые файлы` (аналогично `SchedulesPanel`)
  - filename + размер, кнопка Обновить
- Input bar (идентичен SchedulerInputBar с другим placeholder)

### App.kt

Добавить `"День 19"` в конец списка табов, `else` перейдёт на `PipelineScreen`.

## Затронутые файлы

**Новые:**
- `server/.../pipeline/PipelineRoutes.kt`
- `shared/.../data/agent/PipelineAgent.kt`
- `shared/.../domain/model/PipelineModels.kt`
- `composeApp/.../pipeline/PipelineContract.kt`
- `composeApp/.../pipeline/PipelineViewModel.kt`
- `composeApp/.../pipeline/PipelineScreen.kt`

**Изменённые:**
- `server/.../Application.kt` — `installPipelineRoutes()`
- `composeApp/.../App.kt` — новый таб + импорт + when-branch
