# День 33 — Ассистент поддержки пользователей

## Цель
Мини-сервис поддержки пользователей: AI-ассистент отвечает на вопросы о продукте, используя RAG (FAQ.md), и учитывает контекст пользователя/тикетов через MCP-инструменты (JSON-CRM).

---

## Решения из интервью

| Вопрос | Решение |
|---|---|
| Источник RAG | Новый `server/src/main/resources/support/faq.md` → отдельный `support_index.json` |
| CRM-хранилище | `users.json` + `tickets.json` + Repository + Mutex (паттерн Day 18) |
| MCP-инструменты | get_user, get_ticket, list_tickets, update_ticket_status (все 4) |
| Идентификация в UI | Dropdown с тест-пользователями из CRM |
| UI-лейаут | Split: чат слева + тикет-панель справа; dropdown выбора юзера вверху |
| История диалога | Multi-turn (как Day 25) — history: List<Message> передаётся в каждый запрос |
| Смена пользователя | История сохраняется (не сбрасывается при смене) |
| LLM-модель | `openai/gpt-4o-mini` (как в Day 31/32) |

---

## Архитектура

### Компонент 1 — FAQ-документация (RAG-источник)

**Файл:** `server/src/main/resources/support/faq.md`

Содержит ~15 Q&A по продукту AIChallenge:
- Авторизация / вход
- Оплата и тарифы
- Технические проблемы
- Сброс пароля
- Настройки аккаунта
- Функции приложения

**Индекс:** `support_index.json` (аналог `dev_docs_index.json` из Day 31)

---

### Компонент 2 — Server: пакет `support/`

```
server/.../support/
├── SupportModels.kt        # User, Ticket, CrmData, HTTP-модели
├── SupportRepository.kt    # JSON + Mutex (users.json + tickets.json)
├── SupportDocsIndexer.kt   # Индексирует faq.md → support_index.json
└── SupportRoutes.kt        # POST /support/chat, GET /support/users, инициализация
```

#### Модели данных

```kotlin
@Serializable
data class SupportUser(
    val id: String,         // "u1", "u2" ...
    val name: String,
    val email: String,
    val plan: String,       // "free" | "pro" | "enterprise"
    val registeredAt: String,
)

@Serializable
data class SupportTicket(
    val id: String,         // "T-001", "T-002" ...
    val userId: String,
    val subject: String,
    val description: String,
    val status: String,     // "open" | "in_progress" | "resolved"
    val createdAt: String,
    val messages: List<TicketMessage> = emptyList(),
)

@Serializable
data class TicketMessage(val role: String, val content: String, val createdAt: String)

@Serializable
data class CrmData(val users: List<SupportUser>, val tickets: List<SupportTicket>)
```

#### SupportRepository

- `loadCrm(): CrmData` — читает users.json + tickets.json (или seed-данные)
- `getUser(userId): SupportUser?`
- `getTicket(ticketId): SupportTicket?`
- `listTickets(userId): List<SupportTicket>`
- `updateTicketStatus(ticketId, status): Boolean` — Mutex + запись на диск

#### SupportDocsIndexer

- Аналог `DevDocsIndexer` из Day 31
- Читает `faq.md` из resources
- Использует `RagIndexer.search()` паттерн (cosine similarity)
- Хранит в `support_index.json`
- `search(query, k): List<SupportSearchResult>`

#### POST /support/chat

Request:
```json
{
  "userId": "u1",
  "query": "Почему не работает авторизация?",
  "history": [{"role": "user", "content": "..."}, {"role": "assistant", "content": "..."}]
}
```

Response:
```json
{
  "answer": "Здравствуйте, Alice! По вашему тикету #T-001...",
  "sources": [{"source": "faq.md", "section": "Авторизация", "preview": "..."}],
  "toolsUsed": ["get_user", "list_tickets"]
}
```

#### GET /support/users

Response: список всех пользователей (id + name) для Dropdown в UI.

---

### MCP-инструменты (tool-calling loop, паттерн из DevAssistantRoutes.kt)

```kotlin
fun executeSupportTool(name: String, input: Map<String, String>, repo: SupportRepository): String? = when (name) {
    "get_user"             -> repo.getUser(input["userId"] ?: "")?.let { Json.encodeToString(it) }
    "get_ticket"           -> repo.getTicket(input["ticketId"] ?: "")?.let { Json.encodeToString(it) }
    "list_tickets"         -> Json.encodeToString(repo.listTickets(input["userId"] ?: ""))
    "update_ticket_status" -> {
        val ok = repo.updateTicketStatus(input["ticketId"] ?: "", input["status"] ?: "open")
        if (ok) "Статус тикета обновлён" else "Тикет не найден"
    }
    else -> null
}
```

MAX_TOOL_ITERATIONS = 3 (как в Day 31).

---

### System prompt

```
Ты — ассистент поддержки пользователей продукта AIChallenge.
Используй инструменты для получения данных о пользователе и его тикетах.
Отвечай на вопросы, опираясь на FAQ ниже. Будь вежлив и конкретен.
При наличии активных тикетов — ссылайся на них.

=== FAQ ===
{rag_context}
===

Отвечай на русском языке.
```

---

### Компонент 3 — Seed-данные CRM

**users.json** (3 пользователя):
```json
[
  {"id": "u1", "name": "Alice", "email": "alice@example.com", "plan": "pro", "registeredAt": "2025-01-15"},
  {"id": "u2", "name": "Bob",   "email": "bob@example.com",   "plan": "free", "registeredAt": "2025-03-01"},
  {"id": "u3", "name": "Carol", "email": "carol@example.com", "plan": "enterprise", "registeredAt": "2024-11-20"}
]
```

**tickets.json** (4 тикета, у каждого юзера есть хотя бы один):
- T-001: Alice / "Не могу войти в аккаунт" / open
- T-002: Alice / "Ошибка при оплате" / resolved
- T-003: Bob / "Приложение падает на Android" / in_progress
- T-004: Carol / "Как экспортировать данные?" / open

---

### Компонент 4 — composeApp: пакет `day33/`

```
composeApp/.../day33/
├── Day33Contract.kt
├── Day33ViewModel.kt
└── Day33Screen.kt
```

#### State

```kotlin
data class State(
    val users: List<UserItem> = emptyList(),        // для Dropdown
    val selectedUserId: String? = null,
    val messages: List<ChatMessage> = emptyList(),  // история чата
    val tickets: List<TicketItem> = emptyList(),    // тикеты выбранного юзера
    val activeTicket: TicketItem? = null,           // показывается в панели
    val inputText: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
)

data class UserItem(val id: String, val name: String)
data class TicketItem(val id: String, val subject: String, val status: String)
data class ChatMessage(val role: String, val content: String)
```

#### Event

```kotlin
sealed interface Event {
    data class UserSelected(val userId: String) : Event
    data class InputChanged(val text: String) : Event
    data object SendMessage : Event
    data class TicketSelected(val ticketId: String) : Event
    data object ClearChat : Event
}
```

#### Effect

```kotlin
sealed interface Effect {
    data object ScrollToBottom : Effect
}
```

#### ViewModel

- `init` → загружает пользователей через `GET /support/users`
- `UserSelected` → обновляет `selectedUserId`, загружает тикеты через `list_tickets` (или GET /support/users/{id}/tickets)
- `SendMessage` → POST /support/chat с историей, добавляет ответ в messages
- История НЕ сбрасывается при смене пользователя

#### Day33Screen — UI

```
┌──────────────────────────────────────────────────────┐
│  Пользователь: [Alice ▼]                    [🗑 Чат] │
├──────────────────────────┬───────────────────────────┤
│  💬 Чат                  │  📋 Тикет                 │
│                          │  Нажми тикет слева        │
│  🤖 Здравствуйте, Alice! │                           │
│     По тикету #T-001...  │  [Список тикетов]         │
│                          │  • T-001 open             │
│  👤 Почему не работает   │  • T-002 resolved         │
│     авторизация?         │                           │
│                          │  ─────────────────────    │
│  🤖 Согласно FAQ...      │  📋 T-001 Активный        │
│                          │  Статус: open             │
│  ┌──────────────────────┐│  «Не могу войти»          │
│  │ Введите сообщение [→]││                           │
│  └──────────────────────┘│                           │
└──────────────────────────┴───────────────────────────┘
```

- Dropdown выбора пользователя (загружается при старте)
- Кнопка 🗑 — очищает историю чата
- Split: чат слева, тикет-панель справа
- Тикет-панель: список тикетов + детали выбранного
- Клик по тикету — показывает детали в нижней части панели

---

### Компонент 5 — shared: SupportAgent.kt

```
shared/.../data/agent/SupportAgent.kt
```

- `getUsers(): List<UserItem>`
- `chat(userId, query, history): SupportChatResponse`

---

## Затронутые файлы

**Новые:**
- `server/src/main/resources/support/faq.md`
- `server/.../support/SupportModels.kt`
- `server/.../support/SupportRepository.kt`
- `server/.../support/SupportDocsIndexer.kt`
- `server/.../support/SupportRoutes.kt`
- `shared/.../data/agent/SupportAgent.kt`
- `composeApp/.../day33/Day33Contract.kt`
- `composeApp/.../day33/Day33ViewModel.kt`
- `composeApp/.../day33/Day33Screen.kt`

**Изменения:**
- `server/.../Application.kt` — `installSupportRoutes()`
- `composeApp/.../App.kt` — таб «День 33» + `Day33Screen()`

**Seed-данные (создаются при первом запуске если файлов нет):**
- `users.json` (рабочая директория сервера)
- `tickets.json` (рабочая директория сервера)
- `support_index.json` (авто-индекс при старте)

---

## Паттерны из кодовой базы

| Паттерн | Откуда взять |
|---|---|
| Tool-calling loop | `DevAssistantRoutes.kt:234` — while loop + executeGitTool |
| JSON + Mutex Repository | `ScheduleRepository.kt` (Day 18) |
| Dropdown юзеров | `Day27Screen.kt` — Dropdown выбора модели |
| Split UI | `SchedulerScreen.kt` (Day 18) — Row { chatColumn + sidePanel } |
| Multi-turn history | `DevChatRequest.history` (Day 31) + `RagChatV3Request.history` (Day 25) |
| RAG search | `DevDocsIndexer.search()` → адаптировать для faq.md |
| Application.kt регистрация | Аналог `installDevAssistantRoutes()` |

---

## Риски и решения

| Риск | Решение |
|---|---|
| LLM вызывает update_ticket_status без подтверждения | Инструмент выполняется, но в UI статус тикета обновляется (обновить список тикетов после ответа) |
| faq.md не найден при индексации | Graceful fallback — ассистент отвечает без RAG-контекста |
| История чата растёт бесконечно | Передавать takeLast(10) как в DevAssistantRoutes.kt:222 |
| users.json/tickets.json отсутствуют | SupportRepository.loadCrm() возвращает seed-данные и записывает на диск |
| Dropdown пуст при ошибке сети | `State.error` отображает ошибку, Dropdown показывает placeholder |

---

## Последовательность реализации

1. `server/src/main/resources/support/faq.md` — FAQ-контент
2. `server/.../support/SupportModels.kt` — все модели данных
3. `server/.../support/SupportRepository.kt` — CRM репозиторий + seed
4. `server/.../support/SupportDocsIndexer.kt` — RAG по faq.md
5. `server/.../support/SupportRoutes.kt` — эндпоинты + tool-calling loop
6. `server/.../Application.kt` — регистрация маршрутов + авто-индекс
7. `shared/.../data/agent/SupportAgent.kt` — HTTP-клиент
8. `composeApp/.../day33/Day33Contract.kt` — State + Event + Effect
9. `composeApp/.../day33/Day33ViewModel.kt` — логика MVI
10. `composeApp/.../day33/Day33Screen.kt` — split UI
11. `composeApp/.../App.kt` — регистрация таба «День 33»
