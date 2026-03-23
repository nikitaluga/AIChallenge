# День 26 — Локальная LLM через Ollama

## Цель
Запустить локальную LLM (llama3.2:3b через Ollama), интегрировать в проект через Ktor-прокси,
добавить вкладку «День 26» с чатом и переключателем Cloud (OpenAI) / Local (Ollama).

---

## Архитектурные решения (из интервью)

| Вопрос | Решение |
|--------|---------|
| Инструмент | Ollama |
| Железо | Apple Silicon (Metal GPU) |
| Модель | llama3.2:3b (1.9 GB) |
| Сервер | Ktor проксирует запросы к Ollama localhost:11434 |
| UI | Новый таб «День 26», чат + toggle Cloud/Local + latency |

---

## Шаг 0 — Установка Ollama (ручная, до запуска кода)

```bash
brew install ollama
ollama serve &          # запустить сервер (порт 11434)
ollama pull llama3.2:3b # скачать модель (~1.9 GB)

# Проверка:
ollama run llama3.2:3b "Привет! Кто ты?"
```

---

## Серверная часть (Ktor)

### Новый файл: `server/.../local/LocalLlmRoutes.kt`

**Endpoints:**

```
POST /local/chat
```

**Запрос `LocalChatRequest`:**
```json
{
  "messages": [
    {"role": "user", "content": "Привет!"}
  ],
  "model": "llama3.2:3b"
}
```

**Ответ `LocalChatResponse`:**
```json
{
  "reply": "Привет! Я языковая модель llama3.2...",
  "model": "llama3.2:3b",
  "latencyMs": 1234,
  "backend": "local"
}
```

### Логика обработки (Ktor → Ollama)
Ktor-сервер использует Ktor HttpClient для вызова Ollama native API:
```
POST http://localhost:11434/api/chat
Body: { "model": "llama3.2:3b", "messages": [...], "stream": false }
```

Ollama отвечает:
```json
{
  "message": { "role": "assistant", "content": "..." },
  "done": true
}
```

**`Application.kt`**: добавить `installLocalLlmRoutes()` в `module()`.

---

## Клиентская часть (shared)

### Новый файл: `shared/.../data/agent/LocalLlmAgent.kt`

```kotlin
class LocalLlmAgent {
    suspend fun chat(
        messages: List<LocalChatMessage>,
        model: String = "llama3.2:3b",
    ): LocalChatResult

    suspend fun chatCloud(
        messages: List<LocalChatMessage>,
    ): LocalChatResult  // Проксирует через Ktor к OpenAI gpt-4o-mini
}
```

### Доменные модели (`shared/domain/model/LocalLlmModels.kt`)
```kotlin
data class LocalChatMessage(val role: String, val content: String)
data class LocalChatResult(val reply: String, val latencyMs: Long, val backend: String)
```

---

## Presentation (composeApp)

### Пакет: `composeApp/.../day26/`

**Day26Contract.kt:**
```kotlin
data class State(
    val messages: ImmutableList<Day26Message> = persistentListOf(),
    val inputText: String = "",
    val isLoading: Boolean = false,
    val useLocal: Boolean = true,       // toggle
    val errorMessage: String? = null,
)

data class Day26Message(
    val role: String,
    val content: String,
    val latencyMs: Long? = null,
    val backend: String? = null,
)

sealed interface Event {
    data class InputChanged(val text: String) : Event
    data object SendMessage : Event
    data object ClearHistory : Event
    data class ToggleBackend(val useLocal: Boolean) : Event
    data object DismissError : Event
}

sealed interface Effect {
    data object ScrollToBottom : Effect
}
```

**Day26ViewModel.kt:**
- Держит `StateFlow<State>`
- В `sendMessage()` вызывает `LocalLlmAgent.chat()` (local) или `LocalLlmAgent.chatCloud()` (cloud) в зависимости от `state.useLocal`
- Передаёт историю из `state.messages`

**Day26Screen.kt:**
- Toggled header: `[☁ Cloud]  [💻 Local]`
- Каждое сообщение ассистента показывает `latencyMs` и `backend` под текстом
- Стандартный LazyColumn + TextField + Send

---

## 3 тестовых запроса (разная сложность)

| # | Сложность | Вопрос |
|---|-----------|--------|
| 1 | Простой | "Привет! Кто ты и что умеешь?" |
| 2 | Средний | "Объясни Kotlin Multiplatform в двух предложениях" |
| 3 | Сложный | "Напиши функцию на Kotlin для вычисления числа Фибоначчи с мемоизацией, объясни её временную и пространственную сложность" |

---

## Затронутые файлы

| Файл | Изменение |
|------|-----------|
| `server/.../local/LocalLlmRoutes.kt` | Новый — endpoint /local/chat |
| `server/.../Application.kt` | Добавить installLocalLlmRoutes() |
| `shared/.../domain/model/LocalLlmModels.kt` | Новый — доменные модели |
| `shared/.../data/agent/LocalLlmAgent.kt` | Новый — HTTP клиент к Ktor |
| `composeApp/.../day26/Day26Contract.kt` | Новый |
| `composeApp/.../day26/Day26ViewModel.kt` | Новый |
| `composeApp/.../day26/Day26Screen.kt` | Новый |
| `composeApp/.../App.kt` | Добавить таб «День 26» |
