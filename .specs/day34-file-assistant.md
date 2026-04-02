# Day 34 — File Operations Assistant

## Цель

AI-ассистент, который самостоятельно работает с файлами проекта: читает, ищет по содержимому, предлагает изменения в виде unified diff и (по явному запросу) записывает результат на диск.

---

## Стек

- **Server**: Ktor JVM — новый роут `/files/chat`, отдельный от `/dev/chat`
- **Shared**: `FilesAgent.kt` — HTTP-клиент к `/files/chat`
- **composeApp**: `day34/` — `Day34Contract.kt`, `Day34ViewModel.kt`, `Day34Screen.kt` (chat-only UI)
- **Модель**: `openai/gpt-4o-mini` (как в Day 31/33)
- **Embedding**: не нужен (нет RAG — чистый tool-calling)

---

## Файловые инструменты (server-side, `/files/chat`)

### 1. `read_file`
- Параметр: `path` (относительный от корня проекта)
- Возвращает: содержимое файла (max 50 KB / 1500 строк)
- Sandbox: только внутри detectProjectRoot()

### 2. `search_in_files`
- Параметры: `pattern` (regex), `glob` (опц., напр. `**/*.kt`), `max_results` (опц., default 50)
- Движок: `File.walkTopDown()` + `Regex` (pure JVM, как `listProjectFiles` в Day 31)
- Возвращает: `file:line: совпавшая строка` (max 100 совпадений)
- Sandbox: только projectRoot

### 3. `write_file`
- Параметры: `path`, `content`
- **Dry-run по умолчанию**: всегда сначала генерирует unified diff (через `generate_diff`)
- Реальная запись только если в запросе пользователя есть: `сохрани`, `запиши`, `примени`, `сохрани файл`
- Sandbox: только projectRoot; запрещены пути `..`

### 4. `generate_diff`
- Параметры: `path`, `new_content`
- Читает текущий файл, генерирует unified diff (`--- a/path`, `+++ b/path`, `@@ ... @@`)
- Если файл не существует — diff показывает полностью новый контент (`+` строки)

---

## Сценарии (все 4 реализуются)

### Сценарий A: Найти все usages API/компонента
**Пример запроса**: "найди все места, где используется RouterAiApiService"
- LLM вызывает `search_in_files(pattern="RouterAiApiService", glob="**/*.kt")`
- Результат: список `файл:строка:фрагмент` в code-блоке в чате

### Сценарий B: Обновить CLAUDE.md
**Пример запроса**: "обнови CLAUDE.md — добавь описание Day 34"
- LLM: `read_file("CLAUDE.md")` → `read_file("Day34Screen.kt")` → `generate_diff("CLAUDE.md", new_content)`
- Показывает unified diff в чате; если пользователь пишет "сохрани" — вызывает `write_file`

### Сценарий C: Сгенерировать CHANGELOG
**Пример запроса**: "сгенерируй CHANGELOG на основе .specs/"
- LLM: `list_files(".specs")` → `read_file` каждого спека → `generate_diff("CHANGELOG.md", new_content)`
- Dry-run по умолчанию

### Сценарий D: Проверить MVI-инварианты
**Пример запроса**: "проверь все экраны на соответствие MVI-правилам"
- LLM: `list_files("composeApp/src/commonMain/kotlin/ru/nikitaluga/aichallenge")` → для каждого пакета `read_file` Contract/VM/Screen
- Проверяет:
  - В пакете ровно 3 файла: `*Contract.kt`, `*ViewModel.kt`, `*Screen.kt`
  - `State` объявлен как `data class`
  - `Event` объявлен как `sealed interface`
  - В `*Screen.kt` нет прямых вызовов репозиториев / use case
- Итог: таблица нарушений в чате

---

## Tool-calling loop

- Max итераций: **5** (больше, чем в Day 31/33, т.к. цепочки длиннее: list → read × N → diff)
- После 5 итераций — финальный вызов без инструментов

---

## Системный промпт ассистента

```
Ты — файловый ассистент проекта AIChallenge (KMP/Ktor/Compose Multiplatform).
Используй инструменты для работы с файлами: read_file, search_in_files, write_file, generate_diff.
ВАЖНО: при изменении файлов — сначала показывай diff, не записывай без явного подтверждения.
Отвечай на русском языке. Ссылайся на конкретные файлы и строки.
```

---

## UI — Day34Screen (chat-only)

- Структура как `Day31Screen`: `LazyColumn` с сообщениями + `TextField` + кнопка "Отправить"
- Сообщения с ролью `tool` (результаты инструментов) — отображаются как collapsible code-блоки
- Дополнительная панель с быстрыми примерами:
  - "Найди все usages RouterAiApiService"
  - "Проверь MVI-инварианты"
  - "Сгенерируй CHANGELOG"
  - "Обнови CLAUDE.md — добавь Day 34"
- `toolsUsed: List<String>` из ответа сервера — показывать как чипы под ответом ассистента

---

## Server: новые файлы

```
server/src/main/kotlin/ru/nikitaluga/aichallenge/files/
├── FilesAssistantRoutes.kt   # installFilesAssistantRoutes()
└── FilesService.kt           # executeFileTool(), buildFilesAnswer()
```

- Роут: `POST /files/chat` — принимает `FilesChatRequest(query, history, useDryRun=true)`
- Возвращает: `FilesChatResponse(answer, toolsUsed, diffs)`

---

## Shared: новый файл

```
shared/src/commonMain/kotlin/ru/nikitaluga/aichallenge/data/agent/
└── FilesAgent.kt
```

HTTP-клиент к `/files/chat`. Аналог `SupportAgent.kt`.

---

## composeApp: новые файлы

```
composeApp/src/commonMain/kotlin/ru/nikitaluga/aichallenge/day34/
├── Day34Contract.kt
├── Day34ViewModel.kt
└── Day34Screen.kt
```

- App.kt: добавить таб "День 34" + импорт `Day34Screen`

---

## Точки интеграции с существующим кодом

| Файл | Изменение |
|------|-----------|
| `server/.../Application.kt` | `installFilesAssistantRoutes(apiService)` |
| `composeApp/.../App.kt` | добавить "День 34" в tabs + `34 -> Day34Screen()` |
| Паттерн `detectProjectRoot()` | скопировать из `DevAssistantRoutes.kt:344` |
| `ToolDefinition` / `ToolFunction` | уже используется в Day 31 — переиспользовать |
| `ChatMessage` | переиспользовать из `api/` |

---

## Безопасность

- **Path traversal**: все пути нормализуются через `File(root, path).canonicalFile`, проверка что начинается с `root.canonicalPath`
- **write_file**: запрещён без ключевых слов-подтверждения; не пишет в `.git/`, `local.properties`, `*.xcconfig`
- **Размер файла**: `read_file` ограничен 50 KB
- **search_in_files**: max 100 совпадений, max глубина обхода 8 уровней

---

## Обратная совместимость

- Day 31 `/dev/chat` не меняется — Day 34 добавляет отдельный `/files/chat`
- Новый таб не меняет индексы существующих табов в App.kt (добавляется в конец)
