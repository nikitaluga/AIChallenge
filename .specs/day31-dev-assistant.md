# День 31 — Ассистент разработчика

## Цель
Ассистент, понимающий структуру и контекст проекта: отвечает на вопросы о коде через RAG по документации + использует MCP-инструменты для получения live-контекста (git branch/status/diff, список файлов).

---

## Решения из интервью
| Вопрос | Решение |
|---|---|
| RAG-индекс | Отдельный `dev_docs_index.json` (README, docs/, CLAUDE.md, .specs/) |
| MCP-инструменты | Все 4: git_branch, git_status, git_diff, list_files |
| Режим UI | Всегда RAG+MCP, кнопка /help вставляет пример запроса |
| История | Поддерживается (как Day 25, без TaskMemory) |

---

## Архитектура

### Server — новые файлы
```
server/.../
├── dev/
│   ├── DevDocsIndexer.kt       # Индексирует README.md, CLAUDE.md, docs/, .specs/
│   ├── DevDocsRepository.kt    # load/save dev_docs_index.json (аналог RagRepository)
│   └── DevAssistantRoutes.kt   # /dev/** endpoints
```

### Endpoints
| Метод | Путь | Описание |
|---|---|---|
| GET | /dev/docs/stats | Статистика dev_docs_index.json |
| POST | /dev/docs/index | Перестроить индекс |
| GET | /dev/mcp/tools | Список MCP-инструментов (git_branch, git_status, git_diff, list_files) |
| POST | /dev/mcp/call | Выполнить MCP-инструмент |
| POST | /dev/chat | Чат: RAG + LLM tool-calling loop + история |

### MCP-инструменты (сервер выполняет через ProcessBuilder)
- **git_branch** — `git rev-parse --abbrev-ref HEAD` → текущая ветка
- **git_status** — `git status --short` → изменённые файлы
- **git_diff** — `git diff --stat HEAD` → stat последних изменений (без patch)
- **list_files** — `find <path> -maxdepth 2 -type f` → файлы в директории

### POST /dev/chat Request/Response
```json
// Request
{
  "query": "Как устроена архитектура MVI?",
  "history": [{"role": "user", "content": "..."}, {"role": "assistant", "content": "..."}],
  "k": 5,
  "useMcp": true
}

// Response
{
  "answer": "...",
  "sources": [{"source": "CLAUDE.md", "section": "MVI Rules", "preview": "..."}],
  "toolsUsed": ["git_branch", "git_status"]
}
```

### Shared — новый файл
```
shared/.../data/agent/DevAssistantAgent.kt
```
HTTP-клиент к /dev/chat + /dev/docs/stats + /dev/docs/index. Хранит историю в памяти.

### composeApp — новый пакет
```
composeApp/.../day31/
├── Day31Contract.kt
├── Day31ViewModel.kt
└── Day31Screen.kt
```

**State:**
- messages: List\<ChatEntry\>
- inputText: String
- isLoading: Boolean
- indexStats: DevIndexStats? (totalChunks, createdAt, hasIndex)
- isIndexing: Boolean
- error: String?

**Events:** InputChanged, SendMessage, ClearHistory, BuildIndex, InsertHelpExample

**Effect:** ScrollToBottom

**UI:**
- Верхняя панель: статус индекса (чанков, дата) + кнопка "Переиндексировать"
- Чат (пузырьки user/assistant, источники RAG под ответом)
- Кнопка `/help` вставляет "Расскажи об архитектуре проекта"
- Поле ввода + Send

---

## Источники для RAG-индекса
```
README.md
CLAUDE.md
docs/**/*.md
.specs/**/*.md
```
Используется тот же `RagIndexer`-подобный механизм (structural chunking по заголовкам), модель `text-embedding-3-small`.

---

## Системный промпт ассистента
```
Ты ассистент разработчика проекта AIChallenge (Kotlin Multiplatform).
Используй инструменты для получения актуального git-контекста проекта.
При ответе опирайся на документацию из контекста — приводи название файла-источника.
Отвечай на русском языке.
```

---

## Затронутые файлы
- **Новые:** `server/.../dev/DevDocsIndexer.kt`, `DevDocsRepository.kt`, `DevAssistantRoutes.kt`
- **Новые:** `shared/.../data/agent/DevAssistantAgent.kt`
- **Новые:** `composeApp/.../day31/Day31Contract.kt`, `Day31ViewModel.kt`, `Day31Screen.kt`
- **Изменения:** `server/.../Application.kt` — установить `installDevAssistantRoutes()`
- **Изменения:** `composeApp/.../App.kt` — добавить таб "День 31" + `Day31Screen()`

---

## Риски и решения
| Риск | Решение |
|---|---|
| git команды не найдены в PATH на сервере | Используем `ProcessBuilder` с явным PATH, fallback — пустая строка |
| dev_docs_index.json ещё не построен при первом запросе | Авто-сборка при старте сервера (как в основном RAG) |
| LLM вызывает несуществующий инструмент | Ответ 404 с сообщением "Инструмент не найден" |
| .specs/ содержит много файлов → медленная индексация | Structural chunking только по заголовкам, без fixed-чанков |