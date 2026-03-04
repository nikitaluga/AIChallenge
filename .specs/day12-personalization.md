# День 12 — Персонализация ассистента

## Цель
Добавить поверх модели памяти (День 11) жёстко заданные профили пользователя. Каждый профиль — это набор характеристик (свободный `Map<String, String>`), который инжектируется в system-prompt при каждом запросе. Пользователь может переключаться между профилями, создавать, редактировать и удалять их. 3 профиля захардкожены как демо.

---

## Контекст: отличие от Дня 11

| Аспект | День 11 | День 12 |
|--------|---------|---------|
| Профиль | Динамический — LLM извлекает факты | Статический — задан пользователем |
| Источник | Диалог (автоматически) | Ручное создание/редактирование |
| Хранение | `memory_agent_profile` | `day12_profiles` + `day12_active_id` |
| Изменение | LLM предлагает → пользователь подтверждает | Пользователь редактирует напрямую |

---

## Модели данных

**Путь**: `shared/src/commonMain/kotlin/ru/nikitaluga/aichallenge/domain/model/PersonalizationModels.kt`

```kotlin
data class UserProfileConfig(
    val id: String,           // UUID или timestamp string
    val name: String,         // отображаемое имя профиля
    val fields: Map<String, String>,  // произвольные поля key→value
)

data class PersonalizedResult(
    val content: String,
    val usage: Usage?,
)
```

---

## Демо-профили (3 штуки, захардкожены)

Загружаются при первом старте, если `PlatformStorage` пуст.

```kotlin
val DEFAULT_PROFILES = listOf(
    UserProfileConfig(
        id = "demo_senior",
        name = "Senior Developer",
        fields = mapOf(
            "роль" to "Senior Software Engineer",
            "стиль ответа" to "краткий и технический, без воды",
            "формат" to "код без объяснений, только ключевые детали",
            "экспертиза" to "backend, distributed systems, Kotlin, Go, архитектура",
            "ограничения" to "не объяснять базовые концепции, не добавлять вступления",
        )
    ),
    UserProfileConfig(
        id = "demo_junior",
        name = "Junior / Студент",
        fields = mapOf(
            "роль" to "Student learning programming",
            "стиль ответа" to "подробный, дружелюбный, с примерами из жизни",
            "формат" to "пошаговые объяснения, аналогии, много комментариев в коде",
            "экспертиза" to "основы программирования, Python, начальный уровень",
            "ограничения" to "избегать жаргон без объяснений, всегда объяснять 'почему'",
        )
    ),
    UserProfileConfig(
        id = "demo_pm",
        name = "Менеджер / PM",
        fields = mapOf(
            "роль" to "Product Manager",
            "стиль ответа" to "деловой, структурированный, ориентированный на результат",
            "формат" to "bullet points, TLDR в начале, максимум 5 пунктов",
            "экспертиза" to "управление продуктом, приоритизация, метрики, OKR",
            "ограничения" to "без технических деталей реализации, только бизнес-импакт",
        )
    ),
)
```

---

## Новый агент: `PersonalizedAgent`

**Путь**: `shared/src/commonMain/kotlin/ru/nikitaluga/aichallenge/data/agent/PersonalizedAgent.kt`

### Параметры конструктора
```kotlin
class PersonalizedAgent(
    private val apiService: RouterAiApiService,
    private val model: String = DEFAULT_MODEL,
    private val baseSystemPrompt: String? = null,
    private val windowSize: Int = 20,
)
```

### Публичный API
```kotlin
var activeProfile: UserProfileConfig? = null   // устанавливается из ViewModel
val history: List<ChatMessage>                 // текущая история диалога
var lastUsage: Usage? = null

suspend fun sendMessage(text: String): PersonalizedResult
fun clearHistory()
```

### Логика `sendMessage`
1. Добавить user-сообщение в историю
2. Построить system-prompt: `baseSystemPrompt` + секция профиля (если `activeProfile != null`)
3. Взять последние `windowSize` сообщений из истории
4. Вызвать `apiService.sendMessages()`
5. Добавить ответ ассистента в историю
6. Вернуть `PersonalizedResult`

### Построение промпта профиля
```
--- Профиль пользователя ---
{key1}: {value1}
{key2}: {value2}
...
Учитывай эти характеристики при каждом ответе.
```

---

## Управление профилями

Профили хранит **ViewModel** через `PlatformStorage`. Агент получает активный профиль через `agent.activeProfile = ...`.

### PlatformStorage ключи
- `day12_profiles` — JSON-сериализованный `List<UserProfileConfig>`
- `day12_active_id` — ID текущего активного профиля

### CRUD-операции (в ViewModel)
- **Create** — добавить `UserProfileConfig` в список, сохранить, переключиться на него
- **Read** — загрузить список при старте (или вернуть DEFAULT_PROFILES)
- **Update** — обновить поля существующего профиля по `id`
- **Delete** — удалить из списка; если удалили активный → переключиться на первый

### Защита от удаления: нельзя удалить единственный профиль

---

## UI: `PersonalizationScreen` (День 12)

**Путь**: `composeApp/src/commonMain/kotlin/ru/nikitaluga/aichallenge/personalization/`

### Файлы
- `PersonalizationContract.kt` — State, Event, Effect
- `PersonalizationViewModel.kt` — ViewModel с CRUD-профилей + агент
- `PersonalizationScreen.kt` — Composable

### Layout
```
┌─────────────────────────────────────┐
│  [Senior Dev ✓] [Junior] [PM] [+]   │  ← горизонтальный скролл профилей
│  ✏️ Редактировать   🗑️ Удалить       │  ← кнопки для активного профиля
├─────────────────────────────────────┤
│  Активен: Senior Developer          │  ← инфо-чип
│  роль: Senior Software Engineer     │  ← краткий превью полей (2-3 строки)
├─────────────────────────────────────┤
│                                     │
│  [Список сообщений — пузыри]        │
│                                     │
├─────────────────────────────────────┤
│  [🗑️ Сброс диалога]  [Ввод...]  [→] │
└─────────────────────────────────────┘
```

### ProfileEditDialog (модальный диалог)
Открывается при создании нового профиля или редактировании:
```
┌── Профиль ──────────────────────────┐
│  Имя: [________________]           │
│                                     │
│  Поля:                              │
│  [роль         ] [Senior Dev      ] │
│  [стиль ответа ] [краткий...      ] │
│  [+ Добавить поле]                  │
│                                     │
│  [Отмена]           [Сохранить]    │
└─────────────────────────────────────┘
```

### Events
```kotlin
sealed interface Event {
    data class SelectProfile(val id: String) : Event
    data object CreateProfile : Event                   // открыть диалог создания
    data class EditProfile(val id: String) : Event      // открыть диалог редактирования
    data class DeleteProfile(val id: String) : Event
    data class SaveProfile(val profile: UserProfileConfig) : Event  // сохранить из диалога
    data class InputChanged(val text: String) : Event
    data object SendMessage : Event
    data object ClearHistory : Event
    data object DismissDialog : Event
}
```

### State
```kotlin
data class State(
    val profiles: List<UserProfileConfig> = emptyList(),
    val activeProfileId: String? = null,
    val messages: List<DisplayMessage> = emptyList(),
    val inputText: String = "",
    val isLoading: Boolean = false,
    val showDialog: Boolean = false,
    val editingProfile: UserProfileConfig? = null,   // null = новый, non-null = редактирование
    val lastUsagePrompt: Int = 0,
    val lastUsageCompletion: Int = 0,
    val showUsage: Boolean = false,
)
```

---

## Интеграция в App.kt

Добавить таб **«День 12»** как новый `else` (текущий `else -> MemoryScreen()` станет `9 ->`).

```kotlin
val tabs = listOf("Чат", "День 2", ..., "День 11", "День 12")
// ...
9 -> MemoryScreen()     // День 11
else -> PersonalizationScreen()  // День 12
```

---

## Затронутые файлы

| Файл | Действие |
|------|----------|
| `shared/.../domain/model/PersonalizationModels.kt` | Создать |
| `shared/.../data/agent/PersonalizedAgent.kt` | Создать |
| `composeApp/.../personalization/PersonalizationContract.kt` | Создать |
| `composeApp/.../personalization/PersonalizationViewModel.kt` | Создать |
| `composeApp/.../personalization/PersonalizationScreen.kt` | Создать |
| `composeApp/.../App.kt` | Добавить таб + routing |

---

## Трейдоффы и решения

1. **Map vs структура**: Используем `Map<String, String>` как в День 11 — гибкость важнее типобезопасности для демо-упражнения. Структуру легко добавить позже.
2. **Хранение в ViewModel vs Repository**: Для простоты CRUD-логика прямо в ViewModel. При росте — выносить в UseCase.
3. **Агент без 3 слоёв памяти**: PersonalizedAgent намеренно проще — только история + профиль. День 11 показывает сложную память, День 12 — персонализацию. Смешивать не нужно.
4. **Демо-профили**: Загружаются один раз при первом старте (проверка `PlatformStorage.load("day12_profiles") == null`). После — из хранилища.
5. **Удаление с защитой**: Минимум 1 профиль должен оставаться. Кнопка Delete неактивна при `profiles.size == 1`.
6. **Диалог редактирования**: Один и тот же `ProfileEditDialog` для создания и редактирования — `editingProfile == null` означает новый.
