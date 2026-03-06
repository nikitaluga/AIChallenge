# День 14 — Инварианты и ограничения состояния

## Цель

Добавить к ассистенту механизм инвариантов: правил, которые ассистент никогда не имеет права нарушать. Инварианты хранятся отдельно от диалога, инжектируются в system prompt и валидируются после каждого ответа LLM отдельным LLM-вызовом. При нарушении — автоматический retry с указанием нарушений, результат показывается пользователю вместе с пометкой о нарушении.

---

## Архитектурные решения (из интервью)

| Аспект | Решение |
|---|---|
| Хранение инвариантов | PlatformStorage (персистентно, как профили в День 12) |
| При нарушении | Retry + показать нарушение пользователю |
| Валидация | Отдельный LLM-вызов параллельно с основным |
| UI | Демо-инварианты + CRUD (добавить/редактировать/удалить) |

---

## Доменные модели (`shared/domain/model/InvariantModels.kt`)

```kotlin
data class Invariant(
    val id: String,
    val name: String,           // "StackOnly"
    val rule: String,           // "Используй только Kotlin и Ktor, без Java и других фреймворков"
    val enabled: Boolean = true,
)

data class ValidationResult(
    val passed: Boolean,
    val violations: List<InvariantViolation>,
)

data class InvariantViolation(
    val invariantId: String,
    val invariantName: String,
    val explanation: String,    // "Предложено использовать Java + Spring Boot"
)

data class InvariantChatMessage(
    val role: String,
    val content: String,
    val violations: List<InvariantViolation> = emptyList(),  // непустой = retried ответ с нарушениями
    val wasRetried: Boolean = false,
)

data class InvariantAgentResult(
    val content: String,
    val violations: List<InvariantViolation>,
    val wasRetried: Boolean,
)
```

---

## Агент (`shared/data/agent/InvariantsAgent.kt`)

Extends паттерн PersonalizedAgent (инъекция контекста в system prompt) + паттерн MemoryAgent (параллельный LLM-вызов).

### Поток обработки сообщения

```
user message
    |
    +---> [основной запрос] (buildSystemPrompt с инвариантами)
    |
    v
LLM response
    |
    +---> [валидационный запрос] validateResponse(response, invariants) -> ValidationResult JSON
    |
    v
if Pass -> InvariantAgentResult(content, violations=[], wasRetried=false)
if Fail -> retry: buildRetryPrompt(original query, violations)
                    |
                    v
               LLM retry response
                    |
                    v
           InvariantAgentResult(content, violations, wasRetried=true)
```

### Ключевые методы

- `sendMessage(text): InvariantAgentResult` — основной метод
- `buildSystemPrompt(): String` — инжектирует инварианты в блоке `=== ИНВАРИАНТЫ ===`
- `validateResponse(response, invariants): ValidationResult` — параллельный LLM-вызов, возвращает JSON
- `buildRetryPrompt(original, violations): String` — составляет retry-запрос с указанием нарушений
- `clearHistory()`

### System prompt (инъекция инвариантов)

```
Ты полезный ассистент.

=== ОБЯЗАТЕЛЬНЫЕ ИНВАРИАНТЫ (НЕЛЬЗЯ НАРУШАТЬ) ===
[StackOnly] Используй только Kotlin и Ktor. Никаких Java, Spring, Python.
[MVI] Архитектура — строго MVI + Clean Architecture.
[NoSQL] Только PostgreSQL, никаких NoSQL баз данных.
===================================================

Строго соблюдай инварианты в каждом ответе. Если запрос противоречит инварианту — объясни это.
```

### Валидационный промпт

```
Проверь ответ ассистента на нарушение инвариантов.

=== ИНВАРИАНТЫ ===
[StackOnly] ...
[MVI] ...
=================

=== ОТВЕТ АССИСТЕНТА ===
{response}
========================

Верни ТОЛЬКО JSON:
{"passed": true/false, "violations": [{"invariantId":"...", "invariantName":"...", "explanation":"..."}]}
```

### Retry промпт

```
Предыдущий ответ нарушил инварианты:
- [StackOnly]: Предложено использовать Java + Spring Boot

Дай новый ответ, строго соблюдая инварианты. Исходный вопрос: {originalQuestion}
```

---

## Демо-инварианты (предустановлены, как DEFAULT_PROFILES в День 12)

```kotlin
val DEFAULT_INVARIANTS = listOf(
    Invariant(id="stack_kotlin", name="StackOnly",
              rule="Используй только Kotlin и Ktor. Никаких Java, Spring Boot, Python."),
    Invariant(id="arch_mvi", name="Architecture MVI",
              rule="Архитектура — строго MVI + Clean Architecture. Не предлагай MVVM, MVP, Redux."),
    Invariant(id="no_java", name="NoJava",
              rule="Категорически запрещено предлагать Java-код или Java-библиотеки."),
)
```

---

## Экран (`composeApp/.../invariants/`)

### Contract

```kotlin
State(
    invariants: List<Invariant>,
    messages: List<InvariantChatMessage>,
    inputText: String,
    isLoading: Boolean,
    showDialog: Boolean,
    editingInvariant: Invariant?,   // null = создание
)

Events: AddInvariant, EditInvariant(id), DeleteInvariant(id), ToggleInvariant(id),
        SaveInvariant(invariant), DismissDialog, InputChanged(text), SendMessage, ClearHistory

Effects: ScrollToBottom
```

### UI-компоненты

1. **Список инвариантов** (вверху/в drawer): каждый чип с именем, toggle включения, кнопки edit/delete
2. **Кнопка "+"**: открывает диалог создания инварианта (поля: name, rule)
3. **Чат-область**: список InvariantChatMessage
4. **Сообщение с нарушением**: отображается с красным баннером под текстом:
   ```
   [!] Нарушение: StackOnly — "Предложено использовать Java + Spring Boot" (ответ исправлен)
   ```
5. **Поле ввода + кнопка Send**

---

## Файлы для создания/изменения

| Файл | Действие |
|---|---|
| `shared/.../domain/model/InvariantModels.kt` | Создать |
| `shared/.../data/agent/InvariantsAgent.kt` | Создать |
| `composeApp/.../invariants/InvariantsContract.kt` | Создать |
| `composeApp/.../invariants/InvariantsViewModel.kt` | Создать |
| `composeApp/.../invariants/InvariantsScreen.kt` | Создать |
| `composeApp/.../App.kt` | Добавить Tab "День 14" + InvariantsScreen |

---

## Точки интеграции с существующим кодом

- `RouterAiApiService` — без изменений, те же `sendMessages()`
- `PlatformStorage` — без изменений, новые ключи `day14_invariants`
- Pattern: `PersonalizedAgent.buildSystemPrompt()` — аналогичный паттерн инъекции
- Pattern: `MemoryAgent.extractAndRouteFacts()` — аналогичный паттерн параллельного LLM-вызова
- `DEFAULT_MODEL = "deepseek/deepseek-v3.2"` — как во всех агентах

---

## Сценарии проверки

1. **Pass**: Спросить "как написать REST API?" → ответ про Ktor → нет нарушений
2. **Fail + Retry**: "Давай сделаем на Java + Spring Boot" → нарушение StackOnly → retry → ответ про Kotlin/Ktor + красный баннер "Нарушение: StackOnly"
3. **Toggle**: Выключить инвариант NoJava → "покажи Java-код" → нет нарушений
4. **CRUD**: Добавить инвариант "NoSQL: только PostgreSQL" → спросить про MongoDB → нарушение
