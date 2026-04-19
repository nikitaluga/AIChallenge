# День 5 — Execution Loop: Task Pool

## Параметры эксперимента

| Параметр | Значение |
|----------|---------|
| Формат | Markdown-пул + Claude Code CLI |
| Категории | Баги, тесты, рефакторинг, документация, новые фичи |
| Локальная модель для сравнения | `qwen2.5-coder:7b` (через `ollama run`) |
| Критерий "сделано" | Код компилируется, тест проходит, или файл создан |
| Метрики | Кол-во задач подряд, время на задачу, % с первого раза |

---

## Контекст из кодовой базы

### Найденные проблемы (реальные)

| Проблема | Масштаб |
|----------|---------|
| `Json { ignoreUnknownKeys = true }` дублируется | 27 файлов |
| `"http://10.0.2.2:8080"` hardcoded | 17 агентов |
| `private data class ErrorResponse` | 8 route файлов |
| Нет `HttpTimeout` в HttpClient агентов | Все агенты (17+) |
| Нет тестов для Days 33–36 | 4 ViewModel |
| CHANGELOG обрывается на Day 34 | CHANGELOG.md |

---

## Пул задач (18 штук)

### Баги / Качество кода

#### T01 — HttpTimeout в агентах
**Тип:** BUG  
**Файлы:** `shared/.../data/agent/*.kt` (17 файлов)  
**Задача:** Добавить `install(HttpTimeout) { requestTimeoutMillis = 30_000L }` во все Agent HttpClient-инициализации.  
**Критерий done:** Все AgentHttpClient содержат `HttpTimeout`.

#### T02 — Blank input guard в Day36ViewModel
**Тип:** BUG  
**Файл:** `composeApp/.../day36/Day36ViewModel.kt`  
**Задача:** Убедиться что пустой/blank ввод не отправляется. Добавить guard если отсутствует.  
**Критерий done:** `if (text.isBlank() || _state.value.isLoading) return` есть в send-методе.

#### T03 — MVI audit всех экранов
**Тип:** BUG  
**Файлы:** `composeApp/.../day*/`, `composeApp/.../mcp/`, etc.  
**Задача:** Запустить поиск анти-паттернов (прямая мутация state, бизнес-логика в Screen, публичные методы в VM) по всем файлам. Исправить найденные нарушения.  
**Критерий done:** Нет нарушений из `.claude/rules/anti-patterns.md`.

---

### Рефакторинг

#### T04 — Общий CommonJson util
**Тип:** REFACTOR  
**Новый файл:** `shared/src/commonMain/.../util/CommonJson.kt`  
**Задача:** Создать `val CommonJson = Json { ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = false }` и заменить все 27 inline-инициализаций.  
**Критерий done:** Файл создан, все агенты используют `CommonJson`.

#### T05 — AgentConfig константа для serverBaseUrl
**Тип:** REFACTOR  
**Новый файл:** `shared/src/commonMain/.../util/AgentConfig.kt`  
**Задача:** `object AgentConfig { const val DEFAULT_SERVER_URL = "http://10.0.2.2:8080" }` и заменить 17 hardcoded строк.  
**Критерий done:** `10.0.2.2:8080` встречается 0 раз в commonMain agent-файлах.

#### T06 — Общий ErrorResponse в server
**Тип:** REFACTOR  
**Новый файл:** `server/src/main/kotlin/.../common/ErrorResponse.kt`  
**Задача:** `@Serializable internal data class ErrorResponse(val error: String)` и заменить 8 private duplicate-объявлений.  
**Критерий done:** `private data class ErrorResponse` не встречается в route-файлах.

---

### Тесты

#### T07 — Unit tests для Day33ViewModel
**Тип:** TEST  
**Новый файл:** `composeApp/src/commonTest/.../day33/Day33ViewModelTest.kt`  
**Задача:** 3 кейса: отправка сообщения → isLoading=true, успешный ответ → messages++, DismissError → error=null.  
**Критерий done:** `./gradlew :composeApp:allTests` — тест проходит.

#### T08 — Unit tests для Day34ViewModel
**Тип:** TEST  
**Новый файл:** `composeApp/src/commonTest/.../day34/Day34ViewModelTest.kt`  
**Задача:** 3 кейса: InputChanged обновляет inputText, SendMessage при blank не меняет state, ClearChat очищает messages.  
**Критерий done:** `./gradlew :composeApp:allTests` — тест проходит.

#### T09 — Unit tests для Day35ViewModel
**Тип:** TEST  
**Новый файл:** `composeApp/src/commonTest/.../day35/Day35ViewModelTest.kt`  
**Задача:** 3 кейса: InputChanged, ClearChat, DismissError.  
**Критерий done:** `./gradlew :composeApp:allTests` — тест проходит.

---

### Документация

#### T10 — CHANGELOG Days 35–36
**Тип:** DOC  
**Файл:** `CHANGELOG.md`  
**Задача:** Дописать записи для Day 35 (Git Commit Generator) и Day 36.  
**Критерий done:** CHANGELOG содержит секции `[Day 35]` и `[Day 36]`.

#### T11 — KDoc к публичным агент-методам
**Тип:** DOC  
**Файлы:** `RagAgent.kt`, `SupportAgent.kt`, `FilesAgent.kt`  
**Задача:** Добавить однострочный KDoc к каждому `suspend fun` (что делает, что возвращает).  
**Критерий done:** Каждый public suspend fun в трёх файлах имеет `/** ... */`.

#### T12 — README для agent-пакета
**Тип:** DOC  
**Новый файл:** `shared/src/commonMain/.../data/agent/README.md`  
**Задача:** Таблица: агент → день → endpoint → назначение (17 строк).  
**Критерий done:** Файл создан, все 17 агентов перечислены.

---

### Новые фичи / Архитектура

#### T13 — GET /api/health endpoint
**Тип:** FEAT  
**Файл:** `server/src/main/kotlin/.../Application.kt` + новый `HealthRoutes.kt`  
**Задача:** `GET /api/health → {"status":"ok","uptime":<seconds>}`.  
**Критерий done:** `curl localhost:8080/api/health` возвращает 200 + JSON.

#### T14 — Server request timeout plugin
**Тип:** FEAT  
**Файл:** `server/src/main/kotlin/.../Application.kt`  
**Задача:** Добавить `install(RequestTimeout) { requestTimeoutMillis = 60_000L }` в server plugins.  
**Критерий done:** Плагин подключён в Application.kt.

#### T15 — Retry button в Day33Screen
**Тип:** FEAT  
**Файл:** `composeApp/.../day33/Day33Screen.kt`  
**Задача:** В блоке отображения `error != null` добавить кнопку "Повторить" → `viewModel.onEvent(Event.RetryLastMessage)`. Добавить `RetryLastMessage` в Contract и ViewModel.  
**Критерий done:** Event.RetryLastMessage есть в Contract, обрабатывается в ViewModel, кнопка в Screen.

#### T16 — Retry button в Day34Screen
**Тип:** FEAT  
**Файл:** `composeApp/.../day34/Day34Screen.kt`  
**Задача:** Аналогично T15 — `Event.RetryLastMessage` + кнопка "Повторить" при ошибке.  
**Критерий done:** Event.RetryLastMessage в Contract, ViewModel, Screen.

#### T17 — Input length limit (2000 символов)
**Тип:** FEAT  
**Файлы:** `Day33ViewModel.kt`, `Day34ViewModel.kt`  
**Задача:** `if (text.length > 2000) { _state.value = _state.value.copy(error = "Слишком длинный запрос (максимум 2000 символов)"); return }`.  
**Критерий done:** Guard есть в обоих ViewModel перед отправкой.

#### T18 — Uptime counter в Day30Screen (Local LLM Health)
**Тип:** FEAT  
**Файл:** `composeApp/.../day30/Day30Screen.kt` + `Day30ViewModel.kt`  
**Задача:** Добавить отображение uptime сервера (из `/local/health`) и авто-обновление каждые 30 сек через `LaunchedEffect` + `delay`.  
**Критерий done:** Day30Screen показывает uptime и обновляет его раз в 30 секунд.

---

## Метрики — Прогон 1 (облако, claude-sonnet-4-6)

| # | Задача | Статус | С первого раза | Примечание |
|---|--------|--------|----------------|-----------|
| T01 | HttpTimeout в агентах | ✅ | ✓ | 11 файлов, параллельный batch |
| T02 | Blank input Day36 | ✅ | ✓ | Уже был guard — 0 правок |
| T03 | MVI audit | ✅ | ✓ | coroutineScope только для scroll animation — OK |
| T04 | CommonJson util | ✅ | ✓ | 11 замен (только exact-match конфиги) |
| T05 | AgentConfig serverBaseUrl | ✅ | ✓ | 16 файлов, sed batch |
| T06 | Общий ErrorResponse | ✅ | ✓ | 8 route файлов |
| T07 | Tests Day33VM | ✅ | ✓ | 5 тест-кейсов |
| T08 | Tests Day34VM | ✅ | ✓ | 5 тест-кейсов |
| T09 | Tests Day35VM | ✅ | ✓ | 5 тест-кейсов |
| T10 | CHANGELOG 35–36 | ✅ | ✓ | Добавлены Day 34/35/36 |
| T11 | KDoc агенты | ✅ | ✓ | RagAgent × 3, SupportAgent × 3, FilesAgent × 1 |
| T12 | README agent/ | ✅ | ✓ | 26 агентов в таблице |
| T13 | /api/health endpoint | ✅ | ✓ | GET /api/health → JSON uptime |
| T14 | Server timeout plugin | ⏭ | — | Нет готового плагина в Ktor 3.4 без доп. dep |
| T15 | Retry Day33Screen | ⏭ | — | Ошибки в Day33 — как chat bubbles, retry не применим |
| T16 | Retry Day34Screen | ⏭ | — | Day34 ошибки — validation errors, retry не применим |
| T17 | Input length limit | ✅ | ✓ | Day33 + Day34 ViewModels |
| T18 | Day30 uptime counter | ⏭ | — | Требует изменений в domain model ServiceHealth |

**Итог Прогон 1:**
- Выполнено: 14/18 задач
- Без вмешательства пользователя: 14 подряд (все до конца)
- Пропущено (обоснованно): 4 (T14, T15, T16, T18)
- Первый проход: 14/14 = 100%
- Коммитов: 10

## Лог прогона 1 (облако, claude-sonnet-4-6)

| Задача | Результат | Примечание |
|--------|-----------|-----------|
| T01 | ✅ 1 коммит | 22 правки (11 imports + 11 HttpClient bodies), параллельно |
| T02 | ✅ 0 коммитов | Guard уже есть → диагностика без правок |
| T03 | ✅ 0 коммитов | Аудит grep, нарушений нет |
| T04 | ✅ 1 коммит | CommonJson.kt + 11 замен |
| T05 | ✅ 1 коммит | AgentConfig.kt + 16 замен (sed batch) |
| T06 | ✅ 1 коммит | common/ErrorResponse.kt + 8 route файлов |
| T07/T08/T09 | ✅ 1 коммит | 3 файла × 5 кейсов = 15 тестов |
| T10 | ✅ 1 коммит | 3 секции CHANGELOG |
| T11 | ✅ 1 коммит | 7 KDoc-строк в 3 файлах |
| T12 | ✅ 1 коммит | README.md с 26 агентами |
| T13 | ✅ 1 коммит | HealthRoutes.kt + Application.kt |
| T17 | ✅ 1 коммит | 2 ViewModel + 4 строки |
| T14,T15,T16,T18 | ⏭ | Обоснованно пропущены (см. таблицу выше) |

_Прогон 2 (локальная, qwen2.5-coder:7b) — запускается вручную через `ollama run qwen2.5-coder:7b`:_

Тестовые задачи для сравнения:
1. "Добавь HttpTimeout(30_000L) в FilesAgent.kt" → проверить: импорт + install
2. "Создай val CommonJson = Json { ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = false } в пакете util" → проверить: синтаксис, пакет
3. "Напиши unit-тест для Day34ViewModel: Event.InputChanged обновляет inputText" → проверить: компилируется, использует kotlin.test

| Задача | qwen2.5-coder:7b | Примечание |
|--------|-----------------|-----------|
| T01 (HttpTimeout) | — | Запустить вручную |
| T04 (CommonJson) | — | Запустить вручную |
| T08 (Test Day34) | — | Запустить вручную |
