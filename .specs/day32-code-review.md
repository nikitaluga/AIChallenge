# День 32 — Автоматизация ревью кода

## Цель
Пайплайн, в котором AI-ассистент анализирует PR: получает полный git diff, использует архитектурный контекст проекта (CLAUDE.md + .specs/), и возвращает структурированное ревью — баги, архитектурные проблемы, рекомендации.

---

## Решения из интервью

| Вопрос | Решение |
|---|---|
| Вывод ревью | Комментарий в GitHub PR через GitHub API |
| Где LLM-вызов | Напрямую из GitHub Action (Python script) |
| Глубина diff | Полный patch (с +/- строками) |
| RAG-контекст в CI | Читать CLAUDE.md + .specs/*.md напрямую в prompt |
| Ktor-эндпоинт | Да, POST /review/pr + Day32Screen |
| Секрет | GitHub Secrets: OPENROUTER_API_KEY |

---

## Архитектура

### Компонент 1 — GitHub Action

**Файл:** `.github/workflows/ai-review.yml`

Триггер: `pull_request` (типы: opened, synchronize, reopened)

**Шаги:**
1. `actions/checkout@v4` с `fetch-depth: 0`
2. Получить diff: `git diff origin/master...HEAD` (полный patch)
3. Обрезать до 8000 символов (token limit)
4. Прочитать `CLAUDE.md` и все `.specs/*.md` как архитектурный контекст
5. Вызвать OpenRouter API (model: `openai/gpt-4o-mini`) через Python-скрипт
6. Разобрать JSON-ответ со структурой `{ bugs, architecture, recommendations, summary }`
7. Сформировать Markdown-комментарий и запостить через `gh pr comment`

**Секреты:**
- `OPENROUTER_API_KEY` — ключ OpenRouter
- `GITHUB_TOKEN` — предоставляется автоматически

**Python-скрипт:** `.github/scripts/ai_review.py`
- Принимает diff и контекст
- Формирует system prompt с архитектурными правилами
- Вызывает OpenRouter `/chat/completions` с `response_format: json_object`
- Возвращает JSON с полями: bugs, architecture, recommendations, summary

---

### Компонент 2 — Ktor Server

**Новый пакет:** `server/.../review/`

```
server/.../review/
├── ReviewRoutes.kt      # POST /review/pr endpoint
└── ReviewService.kt     # Логика: RAG + LLM call
```

**POST /review/pr**

Request:
```json
{
  "diff": "string (полный git diff patch)",
  "title": "string? (заголовок PR)",
  "description": "string? (описание PR)",
  "maxDiffLength": 8000
}
```

Response:
```json
{
  "bugs": ["..."],
  "architecture": ["..."],
  "recommendations": ["..."],
  "summary": "...",
  "model": "openai/gpt-4o-mini",
  "diffLength": 4231
}
```

`ReviewService.kt`:
- Читает `dev_docs_index.json` через `DevDocsRepository` (уже есть в Day 31)
- Поиск топ-5 чанков по diff через `DevDocsIndexer.search()`
- Формирует system prompt с RAG-контекстом
- Вызывает `RouterAiApiService.sendMessages()` с `response_format: json_object`
- Парсит JSON-ответ → `ReviewResponse`

---

### Компонент 3 — composeApp

**Новый пакет:** `composeApp/.../day32/`

```
composeApp/.../day32/
├── Day32Contract.kt
├── Day32ViewModel.kt
└── Day32Screen.kt
```

**State:**
```kotlin
data class State(
    val diffInput: String = "",
    val prTitle: String = "",
    val isLoading: Boolean = false,
    val review: ReviewResult? = null,
    val error: String? = null,
)

data class ReviewResult(
    val bugs: List<String>,
    val architecture: List<String>,
    val recommendations: List<String>,
    val summary: String,
    val diffLength: Int,
)
```

**Event:**
```kotlin
sealed interface Event {
    data class DiffChanged(val text: String) : Event
    data class TitleChanged(val text: String) : Event
    data object SubmitReview : Event
    data object ClearResult : Event
    data object InsertSampleDiff : Event
}
```

**Effect:** `ScrollToResult`

**UI — Day32Screen:**
- Поле ввода `prTitle` (однострочное)
- Большое multiline поле для `diffInput` (diff paste)
- Кнопка "Вставить пример" — подставляет короткий sample diff
- Кнопка "Анализировать" — вызывает POST /review/pr
- Результат в трёх карточках: 🐛 Баги / 🏗 Архитектура / 💡 Рекомендации
- `summary` — отдельная секция вверху результата

---

### Shared — новый файл

`shared/.../data/agent/ReviewAgent.kt`
- HTTP-клиент к POST /review/pr
- Принимает diff + title, возвращает `ReviewResult`

---

## System Prompt для LLM

```
Ты — эксперт по code review Kotlin Multiplatform проектов.
Проект использует MVI + Clean Architecture, Compose Multiplatform, Ktor.

Архитектурные правила из документации проекта:
{rag_context}

Проанализируй следующий git diff и верни ТОЛЬКО валидный JSON:
{
  "bugs": ["описание потенциального бага 1", ...],
  "architecture": ["архитектурная проблема 1", ...],
  "recommendations": ["рекомендация 1", ...],
  "summary": "Краткое резюме (2-3 предложения)"
}

Правила:
- bugs: null pointer, race condition, утечки памяти, неправильная обработка ошибок
- architecture: нарушение MVI, Clean Architecture, KMP-специфичные проблемы
- recommendations: улучшения кода, производительности, читаемости
- Если нет проблем в категории — пустой массив []
- Отвечай на русском языке
- НЕ добавляй ничего кроме JSON
```

---

## Затронутые файлы

**Новые:**
- `.github/workflows/ai-review.yml`
- `.github/scripts/ai_review.py`
- `server/.../review/ReviewRoutes.kt`
- `server/.../review/ReviewService.kt`
- `shared/.../data/agent/ReviewAgent.kt`
- `composeApp/.../day32/Day32Contract.kt`
- `composeApp/.../day32/Day32ViewModel.kt`
- `composeApp/.../day32/Day32Screen.kt`

**Изменения:**
- `server/.../Application.kt` — `installReviewRoutes()`
- `composeApp/.../App.kt` — таб «День 32» + `Day32Screen()`

---

## Риски и решения

| Риск | Решение |
|---|---|
| Diff слишком большой для контекста LLM | Обрезать до 8000 символов, сохранить заголовки файлов |
| LLM не вернул валидный JSON | `runCatching` + fallback response с error message |
| GitHub Action без OPENROUTER_API_KEY | Workflow проверяет наличие секрета, выводит warning вместо падения |
| .specs/ пустой или не существует | Graceful fallback — только CLAUDE.md |
| PR из fork (нет доступа к secrets) | Документировать ограничение, пропускать шаг review |

---

## Последовательность реализации

1. `.github/workflows/ai-review.yml` + `.github/scripts/ai_review.py`
2. `server/.../review/ReviewService.kt` + `ReviewRoutes.kt`
3. `server/Application.kt` — регистрация маршрутов
4. `shared/.../data/agent/ReviewAgent.kt`
5. `composeApp/.../day32/` — Contract + ViewModel + Screen
6. `composeApp/.../App.kt` — регистрация таба
