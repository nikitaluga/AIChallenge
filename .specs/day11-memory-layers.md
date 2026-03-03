# День 11 — Модель памяти ассистента (Memory Layers)

## Цель
Реализовать агента с тремя явными слоями памяти: краткосрочная (текущий диалог), рабочая (данные текущей задачи), долговременная (профиль пользователя). Каждый слой хранится отдельно, пользователь видит содержимое каждого слоя.

---

## Архитектура памяти

### Слой 1 — Краткосрочная (Short-term / Dialog)
- **Что хранится**: последние N сообщений текущего диалога (скользящее окно)
- **Когда сбрасывается**: при нажатии «Новая задача» (вместе с рабочей памятью)
- **Персистентность**: `PlatformStorage`, ключ `memory_agent_short`
- **Размер**: последние 10 сообщений (как в SlidingWindowAgent)

### Слой 2 — Рабочая (Working memory / Task context)
- **Что хранится**: контекст текущей задачи
  - Фиксированное поле `goal: String` (цель задачи, извлекается LLM)
  - Произвольные параметры `params: Map<String, String>` (этапы, ограничения, прогресс)
- **Когда сбрасывается**: явная кнопка «Новая задача» → очищается диалог + рабочая память
- **Персистентность**: `PlatformStorage`, ключ `memory_agent_working`
- **Формат**: `WorkingMemory(goal: String, params: Map<String, String>)`

### Слой 3 — Долговременная (Long-term / Profile)
- **Что хранится**: профиль пользователя, накапливается автоматически
  - `name` — имя/ник пользователя
  - `techStack` — предпочитаемые языки, фреймворки, инструменты
  - `goals` — долгосрочные цели и проекты
  - `communicationStyle` — предпочтения к стилю ответов
- **Когда обновляется**: после каждого сообщения, параллельно с основным запросом (как в FactsAgent)
- **Когда сбрасывается**: только явная кнопка «Очистить профиль»
- **Персистентность**: `PlatformStorage`, ключ `memory_agent_long`
- **Формат**: `UserProfile(name, techStack, goals, communicationStyle)`

---

## Новый агент: `MemoryAgent`

**Путь**: `shared/src/commonMain/kotlin/ru/nikitaluga/aichallenge/data/agent/MemoryAgent.kt`

### Параметры конструктора
```kotlin
class MemoryAgent(
    private val apiService: RouterAiApiService,
    private val model: String = DEFAULT_MODEL,
    private val systemPrompt: String? = null,
    private val windowSize: Int = 10,
    private val storageKey: String = "memory_agent",
)
```

### Публичный API
```kotlin
suspend fun sendMessage(text: String): MemoryResult
fun startNewTask()           // очищает short-term + working, профиль остаётся
fun clearProfile()           // очищает только долговременную память
val shortTermHistory: List<ChatMessage>
val workingMemory: WorkingMemory
val userProfile: UserProfile
var lastUsage: Usage? = null
```

### Логика `sendMessage`
1. Добавить сообщение в short-term историю
2. Параллельно запустить 2 запроса:
   a. **Основной**: system + профиль как контекст + рабочая память + скользящее окно → ответ LLM
   b. **Обновление памяти**: LLM обновляет `UserProfile` и `WorkingMemory` из последнего сообщения
3. Обновить все слои, сохранить в `PlatformStorage`

### Построение промпта
System prompt включает:
```
[Системный промпт]

Профиль пользователя:
- Имя: {name}
- Стек: {techStack}
- Цели: {goals}
- Стиль: {communicationStyle}

Текущая задача:
- Цель: {goal}
- Параметры: {params}
```
Затем — последние `windowSize` сообщений из `shortTermHistory`.

---

## Модели данных

**Путь**: `shared/src/commonMain/kotlin/ru/nikitaluga/aichallenge/domain/model/`

```kotlin
// MemoryModels.kt
data class WorkingMemory(
    val goal: String = "",
    val params: Map<String, String> = emptyMap(),
)

data class UserProfile(
    val name: String = "",
    val techStack: String = "",
    val goals: String = "",
    val communicationStyle: String = "",
)

data class MemoryResult(
    val content: String,
    val usage: Usage?,
    val workingMemory: WorkingMemory,
    val userProfile: UserProfile,
)
```

---

## UI: `MemoryScreen` (День 11)

**Путь**: `composeApp/src/commonMain/kotlin/ru/nikitaluga/aichallenge/memory/`

### Компоненты
- `MemoryContract.kt` — State, Event, Effect
- `MemoryViewModel.kt` — ViewModel
- `MemoryScreen.kt` — Composable

### Layout
```
┌────────────────────────────────┐
│  💬 Диалог │ 📋 Задача │ 👤 Профиль │  ← TabRow
├────────────────────────────────┤
│                                │
│  [содержимое выбранной вкладки]│
│                                │
│  [Список сообщений / данные]   │
│                                │
├────────────────────────────────┤
│  [🗑️ Новая задача]  [Ввод...]  [Отправить] │
└────────────────────────────────┘
```

### Вкладка «Диалог»
- Список сообщений (user/assistant пузыри)
- Счётчик: «в памяти: N из {windowSize}»

### Вкладка «Задача» (рабочая память)
- Поле `Цель`: текст или «не определена»
- Список `params` в виде key → value строк
- Кнопка «Новая задача» → Event.StartNewTask

### Вкладка «Профиль» (долговременная)
- 4 поля: Имя, Стек, Цели, Стиль
- Кнопка «Очистить профиль» → Event.ClearProfile
- Статус: «обновлено N сообщений назад»

### События (Event)
```kotlin
data class InputChanged(val text: String) : Event
data object SendMessage : Event
data class SwitchTab(val tab: MemoryTab) : Event
data object StartNewTask : Event
data object ClearProfile : Event
```

---

## Интеграция в App.kt

Добавить таб «День 11» и `MemoryScreen()` как `else` ветку (текущий `else` — ContextScreen — станет `8 ->`).

---

## Затронутые файлы

| Файл | Действие |
|------|----------|
| `shared/.../data/agent/MemoryAgent.kt` | Создать |
| `shared/.../domain/model/MemoryModels.kt` | Создать |
| `composeApp/.../memory/MemoryContract.kt` | Создать |
| `composeApp/.../memory/MemoryViewModel.kt` | Создать |
| `composeApp/.../memory/MemoryScreen.kt` | Создать |
| `composeApp/.../App.kt` | Добавить таб + routing |

---

## Ключевые трейдоффы

1. **Параллельные запросы**: обновление профиля и рабочей памяти выполняется параллельно с основным запросом (как в FactsAgent). Если LLM-обновление упадёт — диалог не прерывается.
2. **Рабочая память vs FactsAgent**: `FactsAgent` хранит один словарь; новый агент разделяет «постоянный профиль» и «временный контекст задачи» — это ключевое отличие.
3. **Хранилище**: всё в `PlatformStorage` (SharedPreferences/UserDefaults). Не требует БД.
4. **Обновление профиля**: LLM видит текущий профиль + последнее сообщение и решает, что обновить. Не меняет то, чего не было в сообщении.
