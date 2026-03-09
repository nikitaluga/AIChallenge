# День 13 — Task State Machine (Конечный автомат задачи)

## Цель
Реализовать агента с формализованным состоянием задачи как детерминированного конечного автомата.
Переходы между стадиями — строго в коде. Каждая стадия имеет свой system prompt, артефакт и триггер перехода (явный Approve от пользователя).

---

## Подход: Детерминированный (вариант 3)

Переходы между стадиями — в коде, не в LLM.
- Каждая стадия: отдельная история диалога + отдельный system prompt
- Артефакт стадии: пользователь видит и аппрувает его → машина переходит дальше
- Пауза/возобновление: персистируется весь стейт через PlatformStorage

---

## Стадии автомата

```
planning → execution → validation → done
```

| Стадия | Цель | Artifact | Триггер перехода |
|--------|------|----------|-----------------|
| `planning` | Детализировать задачу, разбить на шаги | Текст плана (строка) | Пользователь нажимает "Утвердить план" |
| `execution` | Выполнить задачу по плану | Текст результата | Пользователь нажимает "Утвердить результат" |
| `validation` | Проверить соответствие результата плану | Отчёт валидации | Пользователь нажимает "Завершить" |
| `done` | Задача завершена | — | Кнопка "Новая задача" |

---

## System Prompts

### Planning
```
Ты планировщик задачи. Пользователь опишет задачу — твоя цель: задать уточняющие вопросы,
детализировать требования и сформулировать чёткий план выполнения.
Когда план готов — предложи его в виде структурированного текста.
Не выполняй задачу — только планируй.
```

### Execution
```
Ты исполнитель задачи. Работай строго по плану:

=== ПЛАН ===
{plan}
============

Выполняй шаги последовательно. Отмечай прогресс. Если нужны уточнения — спроси.
Когда задача выполнена — предоставь итоговый результат.
```

### Validation
```
Ты валидатор. Проверь, соответствует ли результат плану.

=== ПЛАН ===
{plan}
============

=== РЕЗУЛЬТАТ ===
{result}
================

Составь отчёт: что выполнено корректно, что отклоняется от плана, итоговый вердикт.
Будь объективным и конкретным.
```

---

## Модели данных

**Путь**: `shared/.../domain/model/TaskStateMachineModels.kt`

```kotlin
enum class TaskStage { PLANNING, EXECUTION, VALIDATION, DONE }

data class TaskStateSnapshot(
    val stage: TaskStage = TaskStage.PLANNING,
    val taskDescription: String = "",       // первоначальное описание задачи
    val planArtifact: String = "",          // артефакт Planning
    val executionArtifact: String = "",     // артефакт Execution
    val validationArtifact: String = "",    // артефакт Validation
)

data class TaskStateMachineResult(
    val content: String,
    val newStage: TaskStage,
    val artifactReady: Boolean,             // LLM сигнализирует, что артефакт сформирован
)
```

---

## Агент: `TaskStateMachineAgent`

**Путь**: `shared/.../data/agent/TaskStateMachineAgent.kt`

### Параметры конструктора
```kotlin
class TaskStateMachineAgent(
    private val apiService: RouterAiApiService,
    private val model: String = DEFAULT_MODEL,
    private val storageKey: String = "day13_task_state_machine",
)
```

### Состояние (в памяти + персистируется)
```kotlin
private var snapshot: TaskStateSnapshot
private val stagingHistories: Map<TaskStage, MutableList<ChatMessage>>
    // у каждой стадии — своя история диалога
```

### Публичный API
```kotlin
val currentSnapshot: TaskStateSnapshot
val currentHistory: List<ChatMessage>      // история текущей стадии

suspend fun startTask(description: String): String  // начать с Planning
suspend fun sendMessage(text: String): String        // продолжить диалог в текущей стадии
fun setArtifact(artifact: String)                    // пользователь указывает артефакт стадии (или берём последний ответ)
fun advance(): TaskStage                             // детерминированный переход к следующей стадии
fun reset()                                          // сбросить всё
```

### Логика advance()
```kotlin
fun advance(): TaskStage {
    snapshot = when (snapshot.stage) {
        PLANNING -> snapshot.copy(stage = EXECUTION)
        EXECUTION -> snapshot.copy(stage = VALIDATION)
        VALIDATION -> snapshot.copy(stage = DONE)
        DONE -> snapshot.copy(stage = PLANNING, planArtifact = "", executionArtifact = "", validationArtifact = "")
    }
    saveToStorage()
    return snapshot.stage
}
```

### Логика buildSystemPrompt()
Зависит от текущей `snapshot.stage`. Инжектирует артефакты предыдущих стадий.

---

## MVI: Contract

**Путь**: `composeApp/.../taskstate/TaskStateContract.kt`

```kotlin
data class State(
    val stage: TaskStage = TaskStage.PLANNING,
    val messages: List<DisplayMessage> = emptyList(),
    val inputText: String = "",
    val isLoading: Boolean = false,
    val taskDescription: String = "",
    val planArtifact: String = "",
    val executionArtifact: String = "",
    val validationArtifact: String = "",
    val showArtifactInput: Boolean = false,   // поле для правки артефакта перед утверждением
    val pendingArtifact: String = "",         // временный артефакт для редактирования
)

sealed interface Event {
    data class InputChanged(val text: String) : Event
    data object SendMessage : Event
    data class ArtifactChanged(val text: String) : Event
    data object ApproveArtifact : Event       // утвердить артефакт и advance()
    data object RejectArtifact : Event        // отклонить, продолжить диалог
    data object NewTask : Event               // сброс
}

sealed interface Effect {
    data object ScrollToBottom : Effect
}
```

---

## UI: `TaskStateScreen`

**Путь**: `composeApp/.../taskstate/TaskStateScreen.kt`

### Layout
```
┌──────────────────────────────────────────┐
│ [Planning] → [Execution] → [Validation] → [Done] │  ← stepper
│  Текущий шаг: Планирование задачи        │  ← подпись
│  Ожидаемое действие: Опишите задачу      │
├──────────────────────────────────────────┤
│                                          │
│   [Список сообщений — пузыри]           │
│                                          │
├──────────────────────────────────────────┤
│  [Артефакт стадии — текстовое поле]     │  ← появляется, когда есть артефакт
│  [✓ Утвердить]  [✗ Отклонить/Доработать]│
├──────────────────────────────────────────┤
│  [Ввод сообщения...]  [→ Отправить]     │
│  [🔄 Новая задача]                       │
└──────────────────────────────────────────┘
```

### Stepper — визуальный индикатор
Четыре чипа/кнопки: Planning / Execution / Validation / Done.
Текущая стадия — выделена (filled). Прошедшие — отмечены галкой. Будущие — серые.

### Поведение Pause/Resume
- При закрытии приложения — состояние сохранено в PlatformStorage
- При возврате — автоматически загружается стадия + история
- Сообщение в чате НЕ дублируется: history хранится в агенте, не переспрашивается

---

## Пауза и продолжение без повторных объяснений

Ключ: у каждой стадии **своя история + сохранённые артефакты предыдущих стадий**.
При resume агент загружает из PlatformStorage:
- Текущую стадию
- Историю диалога текущей стадии
- Артефакты предыдущих стадий

Execution и Validation сразу получают артефакты в system prompt — объяснять повторно не нужно.

---

## PlatformStorage ключи

- `day13_snapshot` — JSON `TaskStateSnapshot`
- `day13_history_planning` — JSON `List<StoredMsg>`
- `day13_history_execution` — JSON `List<StoredMsg>`
- `day13_history_validation` — JSON `List<StoredMsg>`

---

## Интеграция в App.kt

Новый таб `"День 13"`, индекс `11`:
```kotlin
val tabs = listOf("Чат", "День 2", ..., "День 12", "День 13")
11 -> PersonalizationScreen()   // был else
else -> TaskStateScreen()       // День 13
```

---

## Затронутые файлы

| Файл | Действие |
|------|----------|
| `shared/.../domain/model/TaskStateMachineModels.kt` | Создать |
| `shared/.../data/agent/TaskStateMachineAgent.kt` | Создать |
| `composeApp/.../taskstate/TaskStateContract.kt` | Создать |
| `composeApp/.../taskstate/TaskStateViewModel.kt` | Создать |
| `composeApp/.../taskstate/TaskStateScreen.kt` | Создать |
| `composeApp/.../App.kt` | Добавить таб + routing |

---

## Трейдоффы

1. **Артефакт = последний ответ ассистента** по умолчанию, но пользователь может его отредактировать перед утверждением — компромисс между автоматизмом и контролем.
2. **Отдельные истории по стадиям** вместо единой: гарантирует, что execution не "помнит" детали планирования вне артефакта — чистые контексты.
3. **Нет автоматического advance**: только явное действие пользователя — максимальная предсказуемость.
4. **Reject не откатывает стадию**: просто продолжает диалог в текущей стадии — пользователь доработает артефакт.