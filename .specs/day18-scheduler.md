# День 18 — Планировщик и фоновые задачи

## Цель
Добавить MCP-инструмент с периодическим выполнением: агент-планировщик погоды, который сохраняет
расписание в JSON на сервере, запускает фоновые coroutine по расписанию, генерирует LLM-summary
и отдаёт агрегированный результат. Пользователь управляет расписаниями через чат на русском языке.

---

## Архитектурные решения (из интервью)

| Вопрос | Решение |
|--------|---------|
| Новый экран или расширение Дня 17 | Новая вкладка «День 18» — пакет `scheduler/` |
| UX управления расписанием | Чат с агентом (LLM tool-calls) |
| Layout экрана | Чат сверху + список активных расписаний снизу с кнопкой [×] |
| Хранилище | JSON-файл `schedules.json` на Ktor-сервере |
| Планировщик | Kotlin coroutines loop с `delay()` до следующего срабатывания |
| Восстановление после перезапуска | При старте сервера читаем `schedules.json`, запускаем coroutine для каждого |
| Агент: механизм | LLM tool-calls (3 инструмента) + REST-вызовы из агента |
| Генерация summary | WeatherService → LLM → краткий текстовый отчёт |
| Лимит истории | 7 последних отчётов на schedule (старые автоочищаются) |

---

## Компоненты

### 1. Server — Scheduler

#### Модели (`server/.../scheduler/ScheduleModels.kt`)
```kotlin
@Serializable
data class ScheduleEntry(
    val id: String,              // UUID
    val city: String,
    val hour: Int,               // 0-23
    val minute: Int,             // 0-59
    val reports: List<WeatherReport> = emptyList(), // последние 7
)

@Serializable
data class WeatherReport(
    val ts: Long,                // unix epoch seconds
    val summary: String,
)

// REST request/response
@Serializable data class CreateScheduleRequest(val city: String, val hour: Int, val minute: Int)
@Serializable data class ScheduleListResponse(val schedules: List<ScheduleEntry>)
@Serializable data class ScheduleReportsResponse(val reports: List<WeatherReport>)
```

#### REST-эндпоинты (`server/.../scheduler/SchedulerRoutes.kt`)

```
POST /scheduler/create      body: CreateScheduleRequest
                            → 201 ScheduleEntry

GET  /scheduler/list        → 200 ScheduleListResponse

DELETE /scheduler/{id}      → 204

GET  /scheduler/{id}/reports → 200 ScheduleReportsResponse (последние 7)
```

#### Репозиторий (`server/.../scheduler/ScheduleRepository.kt`)
- Хранит `MutableList<ScheduleEntry>` в памяти + синхронизирует с `schedules.json`
- `load()` — читает из файла при старте, возвращает список
- `save()` — атомарная запись (write-then-rename или просто write под mutex)
- `addSchedule(entry)`, `removeSchedule(id)`, `addReport(id, report)` — обновляют память и сбрасывают в JSON
- Под `Mutex` для thread-safety в корутинах

#### Сервис планировщика (`server/.../scheduler/WeatherSchedulerService.kt`)
```
WeatherSchedulerService(
    repo: ScheduleRepository,
    weatherService: WeatherService,    // из server/../mcp/
    apiService: RouterAiApiService,    // из shared
    scope: CoroutineScope,
)
```
- `startAll(schedules)` — при старте сервера восстанавливает coroutine для каждого
- `startSchedule(entry)` — запускает `scope.launch { while(true) { delay(msUntilNext); runReport(entry) } }`
- `cancelSchedule(id)` — отменяет Job, удаляет из map
- `runReport(entry)`:
  1. `WeatherService.getWeather(city)` → rawWeather
  2. `RouterAiApiService.sendMessages(...)` с prompt: "Кратко (1-2 предложения) опиши погоду: $rawWeather"
  3. `repo.addReport(id, WeatherReport(ts=now, summary=llmResponse))` (ротация: max 7)

**Расчёт delay:**
```kotlin
fun msUntilNext(hour: Int, minute: Int): Long {
    val now = Clock.System.now().toLocalDateTime(TimeZone.UTC)
    val todayFire = LocalDateTime(now.date, LocalTime(hour, minute))
    val nextFire = if (now < todayFire) todayFire else todayFire + 1.days
    return (nextFire.toInstant(TimeZone.UTC) - Clock.System.now()).inWholeMilliseconds
}
```

#### Изменения в `Application.kt`
```kotlin
fun Application.module() {
    // ... existing ...
    val schedulerService = WeatherSchedulerService(...)
    installSchedulerRoutes(schedulerService)
    // Восстановление расписаний
    launch { schedulerService.startAll(repo.load()) }
}
```

---

### 2. Shared — Agent и Модели

#### `shared/.../domain/model/SchedulerModels.kt`
```kotlin
data class ScheduleInfo(val id: String, val city: String, val hour: Int, val minute: Int, val lastReport: String?)
data class SchedulerAgentResult(val content: String, val toolCallMade: Boolean, val toolName: String? = null)
```

#### `shared/.../data/agent/SchedulerAgent.kt`
Аналог `McpWeatherAgent` — tool-calling агент с 3 инструментами:

**Инструменты для LLM:**
- `create_schedule(city: String, hour: Int, minute: Int)` — создать расписание
- `list_schedules()` — показать все активные
- `delete_schedule(id: String)` — удалить расписание

**Поведение:**
1. `sendMessage(text): SchedulerAgentResult`
2. Отправить в LLM с 3 tool-definitions
3. Если `finishReason == "tool_calls"`:
   - Вызвать соответствующий REST-эндпоинт сервера
   - Добавить tool-result в историю
   - Финальный LLM-вызов → текстовый ответ пользователю
4. Иначе — вернуть ответ LLM напрямую

**Системный промпт:**
```
Ты ассистент-планировщик погодных отчётов. Ты можешь создавать, просматривать и удалять расписания.
Используй инструменты для выполнения запросов пользователя. Отвечай на русском языке.
```

**REST-клиент в агенте:** собственный Ktor HttpClient, baseUrl = `http://10.0.2.2:8080`.

---

### 3. ComposeApp — Новый экран

#### `composeApp/.../scheduler/SchedulerContract.kt`
```kotlin
data class State(
    val messages: List<SchedulerChatMessage> = emptyList(),
    val schedules: List<ScheduleInfo> = emptyList(),
    val inputText: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
)

sealed interface Event {
    data class InputChanged(val text: String) : Event
    object SendMessage : Event
    data class DeleteSchedule(val id: String) : Event
    object RefreshSchedules : Event
    object ClearHistory : Event
    object DismissError : Event
}

sealed interface Effect {
    object ScrollToBottom : Effect
}

data class SchedulerChatMessage(
    val role: String,         // "user" / "assistant"
    val content: String,
    val toolCallMade: Boolean = false,
    val toolName: String? = null,
)
```

#### `composeApp/.../scheduler/SchedulerViewModel.kt`
- Создаёт `SchedulerAgent` и Ktor-клиент для прямых REST-запросов (GET /scheduler/list)
- `init` — загружает список расписаний (`GET /scheduler/list`)
- `onEvent(DeleteSchedule(id))` — вызывает `DELETE /scheduler/{id}`, затем перезагружает список
- `onEvent(SendMessage)` — вызывает `agent.sendMessage(...)`, после ответа перезагружает список
- `onEvent(RefreshSchedules)` — перезагружает список

#### `composeApp/.../scheduler/SchedulerScreen.kt`
```
┌──────────────────────────────────────┐
│  [Чат с агентом-планировщиком]        │  (weight = 1f)
│  User: хочу отчёт по Москве в 15:00  │
│  Agent: Готово! Расписание создано ✅  │
│  [tool badge: create_schedule]        │
│                                       │
│  [InputBar: текст + кнопки →/Сброс]   │
├──────────────────────────────────────┤
│  📋 Активные расписания (N)           │  (wrapContentHeight)
│  ┌──────────────────────────────┐     │
│  │ 🕒 Москва · 15:00 ежедневно  │     │
│  │ Последний: +5°C, ясно        │ [×] │
│  └──────────────────────────────┘     │
└──────────────────────────────────────┘
```

- Список расписаний — `LazyColumn` с max-height или фиксированная секция
- Кнопка `[×]` → `Event.DeleteSchedule(id)` + confirm диалог (опционально)
- Если список пустой → текст-заглушка «Нет активных расписаний. Напишите агенту!»
- Кнопка «Обновить» рядом с заголовком секции → `Event.RefreshSchedules`

#### `composeApp/App.kt`
```kotlin
val tabs = listOf(..., "День 17", "День 18")
// ...
else -> SchedulerScreen()  // День 18
```

---

## Затронутые файлы

| Файл | Изменение |
|------|-----------|
| `server/.../scheduler/ScheduleModels.kt` | новый |
| `server/.../scheduler/ScheduleRepository.kt` | новый |
| `server/.../scheduler/WeatherSchedulerService.kt` | новый |
| `server/.../scheduler/SchedulerRoutes.kt` | новый |
| `server/.../Application.kt` | + installSchedulerRoutes(), + startAll() при старте |
| `server/build.gradle.kts` | + kotlinx-datetime если нет |
| `shared/.../domain/model/SchedulerModels.kt` | новый |
| `shared/.../data/agent/SchedulerAgent.kt` | новый |
| `composeApp/.../scheduler/SchedulerContract.kt` | новый |
| `composeApp/.../scheduler/SchedulerViewModel.kt` | новый |
| `composeApp/.../scheduler/SchedulerScreen.kt` | новый |
| `composeApp/.../App.kt` | + «День 18» вкладка |

---

## Потоки данных

### Создание расписания
```
User types → SchedulerViewModel.onEvent(SendMessage)
  → SchedulerAgent.sendMessage(text)
    → LLM (с 3 tool-definitions)
    ← finishReason="tool_calls", tool=create_schedule(city=Москва, hour=15, minute=0)
    → POST http://10.0.2.2:8080/scheduler/create {city, hour, minute}
    ← {id, city, hour, minute, reports=[]}
    → LLM (tool result) → "Готово! Расписание создано: Москва в 15:00"
  ← SchedulerAgentResult
→ SchedulerViewModel обновляет messages, вызывает GET /scheduler/list
→ UI показывает новую карточку расписания
```

### Фоновый запуск отчёта (сервер)
```
WeatherSchedulerService coroutine просыпается в 15:00
  → WeatherService.getWeather("Москва")
  ← "Температура: +5°C, ясно, ветер 3 м/с..."
  → RouterAiApiService.sendMessages([system, user: "Кратко опиши: {raw}"])
  ← "Москва: +5°C, ясно, лёгкий ветер. Комфортная погода."
  → ScheduleRepository.addReport(id, WeatherReport(ts=now, summary=...))
  → schedules.json обновлён (7 последних отчётов)
```

### Просмотр накопленных отчётов
```
User: "покажи последние отчёты по Москве"
  → LLM вызывает list_schedules()
  → GET /scheduler/list → [{id, city, ..., reports:[...]}]
  → LLM формирует текстовую сводку из reports
  ← "За последние 7 дней в Москве: пн +2°C, вт +5°C..."
```

---

## Граничные случаи

- **Дублирующиеся расписания**: сервер не блокирует, может быть несколько для одного города в разное время
- **Ошибка LLM при summary**: сохраняем raw строку от WeatherService как fallback
- **Ошибка сети при REST из агента**: `runCatching { ... }.getOrElse { "Ошибка: ${it.message}" }` → возвращается как tool-result в LLM
- **schedules.json не существует**: `ScheduleRepository.load()` возвращает пустой список, файл создаётся при первом сохранении
- **Обновление списка в UI**: после каждого tool-call агента ViewModel делает `GET /scheduler/list`
- **kotlinx-datetime**: если нет в server/build.gradle.kts — добавить `libs.kotlinx.datetime`

---

## API Key на сервере

Сервер использует `RouterAiApiService` из `shared`. На JVM-таргете уже есть `ApiKeyProvider.jvm.kt` с реализацией `actual fun getApiKey()`. Сервер компилируется под JVM — всё работает без изменений.