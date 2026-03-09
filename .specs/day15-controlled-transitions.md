# День 15 — Контролируемые переходы состояний (Profiled Task Agent)

## Цель

Ассистент с контролируемым жизненным циклом задачи: несколько профилей задач с разными стадиями,
авто-классификация по первому сообщению, жёсткий запрет на "прыжки" между стадиями
с визуальным красным баннером при попытке недопустимого перехода.

---

## Отличия от Дня 13

| Аспект | День 13 | День 15 |
|--------|---------|---------|
| Профили | Один жёсткий (PLANNING→...→DONE) | 3 профиля с разными стадиями |
| Классификация | Нет | Авто-LLM-классификация + подтверждение пользователем |
| Блокировка | Кнопка `advance()` просто не вызывается | Явный `tryAdvance()` + красный баннер в чате |
| Тест перехода | Нет | Demo-кнопка «Попробовать перепрыгнуть» |

---

## Профили задач

```
DevTask:      CLASSIFY → PLANNING → EXECUTION → VALIDATION → DONE
ResearchTask: CLASSIFY → RESEARCH → SYNTHESIS → DONE
SimpleTask:   CLASSIFY → DONE  (прямой ответ без стадий)
```

| ProfileStage | Описание | System prompt focus |
|---|---|---|
| `PLANNING` | Планирование разработки | Задай вопросы, составь пронумерованный план |
| `EXECUTION` | Реализация по плану | Строго следуй плану, отмечай шаги |
| `VALIDATION` | Проверка результата | Сравни результат с планом, вынеси вердикт |
| `RESEARCH` | Сбор информации | Задавай уточняющие вопросы, собирай факты |
| `SYNTHESIS` | Формирование итогов | Систематизируй факты, выдай структурированный итог |
| `DONE` | Завершено | — |

---

## Доменные модели

**Путь**: `shared/.../domain/model/TaskProfileModels.kt`

```kotlin
enum class TaskProfile(val displayName: String, val stages: List<ProfileStage>) {
    DEV_TASK("Разработка", listOf(ProfileStage.PLANNING, ProfileStage.EXECUTION, ProfileStage.VALIDATION, ProfileStage.DONE)),
    RESEARCH_TASK("Исследование", listOf(ProfileStage.RESEARCH, ProfileStage.SYNTHESIS, ProfileStage.DONE)),
    SIMPLE_TASK("Простой вопрос", listOf(ProfileStage.DONE)),
}

enum class ProfileStage(val label: String, val actionHint: String) {
    PLANNING("Планирование", "Составляем план разработки"),
    EXECUTION("Выполнение", "Реализуем по плану"),
    VALIDATION("Валидация", "Проверяем соответствие результата"),
    RESEARCH("Исследование", "Собираем информацию"),
    SYNTHESIS("Синтез", "Формируем структурированный итог"),
    DONE("Готово", "Задача завершена"),
}

@Serializable
data class ClassificationResult(
    val profile: TaskProfile,
    val reason: String,    // объяснение LLM
)

@Serializable
data class ProfiledSnapshot(
    val profile: TaskProfile? = null,
    val classificationConfirmed: Boolean = false,
    val currentStageIndex: Int = 0,
    val taskDescription: String = "",
    val artifacts: Map<String, String> = emptyMap(),  // ProfileStage.name -> artifact text
)

data class InvalidTransitionAttempt(
    val fromStage: ProfileStage,
    val toStage: ProfileStage,
    val reason: String,    // "план не утверждён" / "результат не утверждён"
)
```

---

## Агент: `ProfiledTaskAgent`

**Путь**: `shared/.../data/agent/ProfiledTaskAgent.kt`

### Конструктор
```kotlin
class ProfiledTaskAgent(
    private val apiService: RouterAiApiService,
    private val model: String = DEFAULT_MODEL,
    private val storageKey: String = "day15_profiled_task",
)
```

### Публичный API
```kotlin
val currentSnapshot: ProfiledSnapshot
val currentHistory: List<ChatMessage>        // история текущей стадии
val currentStage: ProfileStage               // computed из snapshot
val allowedNextStage: ProfileStage?          // следующая по профилю, null если DONE или нет профиля

// Шаг 1: LLM классифицирует задачу
suspend fun classifyTask(description: String): ClassificationResult

// Шаг 2: Пользователь подтверждает/меняет профиль
fun confirmProfile(profile: TaskProfile)     // → переводит в первую стадию

// Шаг 3: Диалог внутри стадии
suspend fun sendMessage(text: String): String

// Шаг 4: Попытка перехода — может вернуть ошибку
fun tryAdvance(artifact: String): InvalidTransitionAttempt?  // null = OK, advance выполнен

fun reset()
```

### Логика tryAdvance()
```kotlin
fun tryAdvance(artifact: String): InvalidTransitionAttempt? {
    val snap = _snapshot
    val profile = snap.profile ?: return InvalidTransitionAttempt(
        fromStage = currentStage,
        toStage = ProfileStage.DONE,
        reason = "Профиль задачи не определён — сначала подтвердите профиль",
    )
    if (!snap.classificationConfirmed) return InvalidTransitionAttempt(
        fromStage = currentStage,
        toStage = ProfileStage.DONE,
        reason = "Классификация задачи не подтверждена",
    )
    val stages = profile.stages
    val nextIndex = snap.currentStageIndex + 1
    if (nextIndex >= stages.size) return null  // уже DONE

    // Сохраняем артефакт и переходим
    val updatedArtifacts = snap.artifacts + (currentStage.name to artifact)
    _snapshot = snap.copy(
        currentStageIndex = nextIndex,
        artifacts = updatedArtifacts,
    )
    // Очищаем историю новой стадии (она начинается чисто)
    saveToStorage()
    return null
}
```

### Логика classifyTask()
```kotlin
suspend fun classifyTask(description: String): ClassificationResult {
    val prompt = """
        Classify the following task into one of these profiles:
        - DEV_TASK: software development, coding, architecture, debugging
        - RESEARCH_TASK: information gathering, research, analysis, comparison
        - SIMPLE_TASK: simple question, quick answer, no complex workflow needed

        Task: $description

        Reply ONLY with JSON: {"profile":"DEV_TASK","reason":"..."}
    """.trimIndent()
    val response = apiService.sendMessages(
        messages = listOf(ChatMessage("user", prompt)),
        model = model,
        maxTokens = 200,
        temperature = 0.2,
    )
    return json.decodeFromString<ClassificationResult>(response.content)
}
```

### Логика buildSystemPrompt() (по стадии)
- `PLANNING`: Планировщик, задаёт вопросы, составляет план. Не выполняет задачу.
- `EXECUTION`: Исполнитель, работает строго по плану (инжектируется планArtifact). Отмечает шаги.
- `VALIDATION`: Валидатор, сравнивает результат (executionArtifact) с планом (planArtifact). Вердикт.
- `RESEARCH`: Аналитик, задаёт уточняющие вопросы, собирает факты по теме.
- `SYNTHESIS`: Синтетизатор, систематизирует все собранные факты (researchArtifact), выдаёт итог.
- `DONE`: "Задача завершена."

---

## MVI Contract

**Путь**: `composeApp/.../taskprofile/TaskProfileContract.kt`

```kotlin
data class State(
    val profile: TaskProfile? = null,
    val classificationResult: ClassificationResult? = null,
    val showClassificationPanel: Boolean = false,
    val isClassifying: Boolean = false,
    val stages: List<ProfileStage> = emptyList(),
    val currentStageIndex: Int = 0,
    val messages: List<DisplayMessage> = emptyList(),
    val inputText: String = "",
    val isLoading: Boolean = false,
    val pendingArtifact: String = "",
    val showArtifactPanel: Boolean = false,
    val invalidTransition: InvalidTransitionAttempt? = null,
    val errorMessage: String? = null,
) {
    val currentStage: ProfileStage get() =
        stages.getOrElse(currentStageIndex) { ProfileStage.DONE }
    val isProfileConfirmed: Boolean get() =
        profile != null && !showClassificationPanel
}

data class DisplayMessage(
    val role: String,
    val content: String,
    val stage: ProfileStage,
    val isSystemNotice: Boolean = false,   // красные баннеры о недопустимых переходах
)

sealed interface Event {
    data class InputChanged(val text: String) : Event
    data object SendMessage : Event
    data class ConfirmProfile(val profile: TaskProfile) : Event  // подтвердить классификацию
    data object DismissClassificationPanel : Event
    data class ArtifactChanged(val text: String) : Event
    data object ShowArtifactPanel : Event
    data object ApproveArtifact : Event         // advance() → следующая стадия
    data object DismissArtifactPanel : Event
    data object TryInvalidTransition : Event    // DEMO: попытка перепрыгнуть
    data object DismissInvalidTransition : Event
    data object DismissError : Event
    data object NewTask : Event
}

sealed interface Effect {
    data object ScrollToBottom : Effect
}
```

---

## ViewModel

**Путь**: `composeApp/.../taskprofile/TaskProfileViewModel.kt`

### Ключевые методы

**SendMessage**:
1. Если профиль не подтверждён → `agent.classifyTask(text)` → показать ClassificationPanel
2. Если профиль подтверждён → `agent.sendMessage(text)`

**ConfirmProfile(profile)**:
1. `agent.confirmProfile(profile)` → первая стадия
2. Если `SimpleTask` → `agent.sendMessage("Отвечай на вопрос напрямую: $taskDescription")`

**ApproveArtifact**:
1. `agent.tryAdvance(pendingArtifact)` → если `null` (успех) → следующая стадия
2. Если `InvalidTransitionAttempt` → показать баннер (не должно происходить при нормальном флоу)
3. Если новая стадия — EXECUTION или VALIDATION или SYNTHESIS → autoStartStage()

**TryInvalidTransition** (demo):
1. Принудительно вызываем `agent.tryAdvanceTo(stage_after_next)` — прыжок на 2 вперёд
2. Добавляем в сообщения DisplayMessage с `isSystemNotice = true`
3. Показываем `state.invalidTransition`

---

## UI: `TaskProfileScreen`

**Путь**: `composeApp/.../taskprofile/TaskProfileScreen.kt`

### Layout
```
┌──────────────────────────────────────────────┐
│ [Профиль: DevTask] [Разработка]               │  ← профиль чип, серый если не определён
│ [Planning] → [Execution] → [Validation] → [Done] │  ← stepper (показывается после confirm)
├──────────────────────────────────────────────┤
│  [Панель классификации — появляется после 1 сообщения]:
│  ┌─ Определил профиль: DevTask ──────────────┐
│  │ Стадии: Planning → Execution → Validation  │
│  │ Причина: задача о разработке              │
│  │ [✓ Подтвердить] [DevTask ↓] [Research] [Simple]│
│  └────────────────────────────────────────────┘
├──────────────────────────────────────────────┤
│  [Список сообщений с красными баннерами ⛔]  │
├──────────────────────────────────────────────┤
│  [Артефакт + Утвердить/Отклонить — как в Дне 13]│
├──────────────────────────────────────────────┤
│  [Ввод] [→] [🔄 Новая]  [⛔ Попробовать прыжок] │
└──────────────────────────────────────────────┘
```

### Компоненты

1. **ProfileChip** — вверху, показывает профиль/стадию, серый пока нет профиля
2. **Stepper** — горизонтальный ряд стадий профиля (аналог Дня 13), только после подтверждения
3. **ClassificationPanel** — Card с результатом классификации + кнопки [Подтвердить] + ряд кнопок смены профиля
4. **ChatList** — список сообщений, красные `SystemNoticeBubble` для `isSystemNotice = true`
5. **SystemNoticeBubble** — ⛔ красный Surface с текстом блокировки
6. **ArtifactPanel** — как в Дне 13
7. **InputBar** — поле ввода + кнопка Отправить + кнопка "Новая задача" + кнопка "⛔ Прыжок" (demo)

### InvalidTransitionBubble (красный баннер в чате)
```
┌─ ⛔ Недопустимый переход ──────────────────┐
│ Текущая стадия: PLANNING                    │
│ Запрошено: EXECUTION                        │
│ Причина: план не утверждён пользователем    │
│ Продолжите диалог или нажмите «Утвердить»  │
└────────────────────────────────────────────┘
```

---

## PlatformStorage ключи

- `day15_snapshot` — JSON `ProfiledSnapshot`
- `day15_history_PLANNING` — JSON `List<StoredMsg>`
- `day15_history_EXECUTION`
- `day15_history_VALIDATION`
- `day15_history_RESEARCH`
- `day15_history_SYNTHESIS`

---

## Интеграция в App.kt

Новый таб `"День 15"`, индекс `12`:
```kotlin
val tabs = listOf(..., "День 13", "День 14", "День 15")
12 -> TaskProfileScreen()
```

---

## Затронутые файлы

| Файл | Действие |
|------|----------|
| `shared/.../domain/model/TaskProfileModels.kt` | Создать |
| `shared/.../data/agent/ProfiledTaskAgent.kt` | Создать |
| `composeApp/.../taskprofile/TaskProfileContract.kt` | Создать |
| `composeApp/.../taskprofile/TaskProfileViewModel.kt` | Создать |
| `composeApp/.../taskprofile/TaskProfileScreen.kt` | Создать |
| `composeApp/.../App.kt` | Добавить таб + routing |

---

## Сценарии проверки

1. **DevTask**: "Напиши REST API на Ktor" → LLM определяет DevTask → [Подтвердить] → Planning → диалог → [Утвердить план] → Execution (autostart) → [Утвердить результат] → Validation (autostart) → [Завершить] → Done
2. **ResearchTask**: "Сравни PostgreSQL и MySQL" → LLM определяет ResearchTask → [Подтвердить] → Research → диалог → [Утвердить] → Synthesis (autostart) → [Завершить] → Done
3. **SimpleTask**: "Сколько байт в мегабайте?" → LLM определяет SimpleTask → [Подтвердить] → Done (прямой ответ)
4. **Смена профиля**: LLM определил DevTask, пользователь нажимает [Research] → профиль меняется до подтверждения
5. **Недопустимый переход**: Кнопка "⛔ Прыжок" на стадии Planning → красный баннер в чате ⛔
6. **Pause/Resume**: Закрыть приложение на Execution → вернуться → стадия восстановлена, история на месте

---

## Трейдоффы

1. **Отдельные истории по стадиям** — как в Дне 13, каждая стадия получает чистый контекст + артефакты предыдущих через system prompt.
2. **Классификация JSON `temperature=0.2`** — детерминированный результат, минимальная вариативность.
3. **SimpleTask без стадий**: пользователь не видит stepper, ответ приходит сразу — UX как обычный чат.
4. **Demo-кнопка прыжка** всегда доступна, чтобы явно продемонстрировать блокировку.