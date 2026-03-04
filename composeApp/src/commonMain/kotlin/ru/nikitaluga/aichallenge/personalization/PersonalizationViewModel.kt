package ru.nikitaluga.aichallenge.personalization

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import ru.nikitaluga.aichallenge.api.ChatMessage
import ru.nikitaluga.aichallenge.api.RouterAiApiService
import ru.nikitaluga.aichallenge.data.agent.PersonalizedAgent
import ru.nikitaluga.aichallenge.data.storage.PlatformStorage
import ru.nikitaluga.aichallenge.domain.model.UserProfileConfig

private const val SYSTEM_PROMPT = "Ты персонализированный ассистент. Строго следуй характеристикам из профиля пользователя при каждом ответе."
private const val STORAGE_PROFILES_KEY = "day12_profiles"
private const val STORAGE_ACTIVE_ID_KEY = "day12_active_id"

val DEFAULT_PROFILES = listOf(
    UserProfileConfig(
        id = "demo_senior",
        name = "Senior Developer",
        fields = mapOf(
            "роль" to "Senior Software Engineer",
            "стиль ответа" to "краткий и технический, без воды",
            "формат" to "код без лишних объяснений, только ключевые детали",
            "экспертиза" to "backend, distributed systems, Kotlin, архитектура",
            "ограничения" to "не объяснять базовые концепции, без вступлений",
        ),
    ),
    UserProfileConfig(
        id = "demo_junior",
        name = "Junior / Студент",
        fields = mapOf(
            "роль" to "Student learning programming",
            "стиль ответа" to "подробный, дружелюбный, с примерами из жизни",
            "формат" to "пошаговые объяснения, аналогии, много комментариев в коде",
            "экспертиза" to "основы программирования, Python, начальный уровень",
            "ограничения" to "всегда объяснять 'почему', избегать жаргон без объяснений",
        ),
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
        ),
    ),
)

class PersonalizationViewModel : ViewModel() {

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
    private val apiService = RouterAiApiService()
    private val agent = PersonalizedAgent(
        apiService = apiService,
        baseSystemPrompt = SYSTEM_PROMPT,
        baseStorageKey = "personalized_day12",
    )

    private val _state = MutableStateFlow(PersonalizationContract.State())
    val state: StateFlow<PersonalizationContract.State> = _state.asStateFlow()

    private val _effects = Channel<PersonalizationContract.Effect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    init {
        loadProfilesFromStorage()
    }

    fun onEvent(event: PersonalizationContract.Event) {
        when (event) {
            is PersonalizationContract.Event.SelectProfile -> selectProfile(event.id)
            PersonalizationContract.Event.CreateProfile ->
                _state.update { it.copy(showDialog = true, editingProfile = null) }
            is PersonalizationContract.Event.EditProfile -> {
                val profile = _state.value.profiles.firstOrNull { it.id == event.id }
                _state.update { it.copy(showDialog = true, editingProfile = profile) }
            }
            is PersonalizationContract.Event.DeleteProfile -> deleteProfile(event.id)
            is PersonalizationContract.Event.SaveProfile -> saveProfile(event.profile)
            PersonalizationContract.Event.DismissDialog ->
                _state.update { it.copy(showDialog = false, editingProfile = null) }
            is PersonalizationContract.Event.InputChanged ->
                _state.update { it.copy(inputText = event.text) }
            PersonalizationContract.Event.SendMessage -> sendMessage()
            PersonalizationContract.Event.ClearHistory -> {
                agent.clearHistory()
                _state.update { it.copy(messages = emptyList(), showUsage = false) }
            }
        }
    }

    // ── Управление профилями ──────────────────────────────────────────────────

    private fun selectProfile(id: String) {
        val profile = _state.value.profiles.firstOrNull { it.id == id } ?: return
        agent.activeProfile = profile  // автоматически загружает историю этого профиля
        PlatformStorage.save(STORAGE_ACTIVE_ID_KEY, id)
        _state.update { it.copy(activeProfileId = id, messages = agent.history.toDisplay(profile)) }
    }

    private fun deleteProfile(id: String) {
        val current = _state.value.profiles
        if (current.size <= 1) return
        val updated = current.filter { it.id != id }
        val newActiveId = if (_state.value.activeProfileId == id) updated.first().id else _state.value.activeProfileId
        persistProfiles(updated)
        agent.activeProfile = updated.firstOrNull { it.id == newActiveId }
        PlatformStorage.save(STORAGE_ACTIVE_ID_KEY, newActiveId ?: "")
        _state.update { it.copy(profiles = updated, activeProfileId = newActiveId) }
    }

    private fun saveProfile(profile: UserProfileConfig) {
        val current = _state.value.profiles.toMutableList()
        val idx = current.indexOfFirst { it.id == profile.id }
        if (idx >= 0) current[idx] = profile else current.add(profile)
        persistProfiles(current)
        val activeId = _state.value.activeProfileId ?: profile.id
        agent.activeProfile = current.firstOrNull { it.id == activeId }
        _state.update {
            it.copy(
                profiles = current,
                showDialog = false,
                editingProfile = null,
                activeProfileId = if (it.activeProfileId == null) profile.id else it.activeProfileId,
            )
        }
    }

    // ── Чат ──────────────────────────────────────────────────────────────────

    private fun sendMessage() {
        val text = _state.value.inputText.trim()
        if (text.isEmpty() || _state.value.isLoading) return
        _state.update { it.copy(inputText = "", isLoading = true) }

        viewModelScope.launch {
            try {
                val result = agent.sendMessage(text)
                val activeProfile = _state.value.activeProfile
                _state.update {
                    it.copy(
                        isLoading = false,
                        messages = agent.history.toDisplay(activeProfile),
                        lastUsagePrompt = result.usage?.promptTokens ?: 0,
                        lastUsageCompletion = result.usage?.completionTokens ?: 0,
                        showUsage = result.usage != null,
                    )
                }
                _effects.send(PersonalizationContract.Effect.ScrollToBottom)
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false) }
            }
        }
    }

    // ── Хранилище ─────────────────────────────────────────────────────────────

    private fun loadProfilesFromStorage() {
        val stored = runCatching {
            PlatformStorage.load(STORAGE_PROFILES_KEY)?.let {
                json.decodeFromString<List<UserProfileConfig>>(it)
            }
        }.getOrNull()

        val profiles = if (stored.isNullOrEmpty()) DEFAULT_PROFILES else stored
        if (stored.isNullOrEmpty()) persistProfiles(profiles)

        val savedId = PlatformStorage.load(STORAGE_ACTIVE_ID_KEY)
        val activeId = profiles.firstOrNull { it.id == savedId }?.id ?: profiles.first().id
        val activeProfile = profiles.first { it.id == activeId }
        agent.activeProfile = activeProfile
        _state.update {
            it.copy(
                profiles = profiles,
                activeProfileId = activeId,
                messages = agent.history.toDisplay(activeProfile),
            )
        }
    }

    private fun persistProfiles(profiles: List<UserProfileConfig>) {
        runCatching {
            PlatformStorage.save(STORAGE_PROFILES_KEY, json.encodeToString<List<UserProfileConfig>>(profiles))
        }
    }

    private fun List<ChatMessage>.toDisplay(profile: UserProfileConfig? = null) =
        map { PersonalizationContract.DisplayMessage(role = it.role, content = it.content) }
}
