package ru.nikitaluga.aichallenge.data.agent

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import ru.nikitaluga.aichallenge.api.ChatMessage
import ru.nikitaluga.aichallenge.api.RouterAiApiService
import ru.nikitaluga.aichallenge.api.Usage
import ru.nikitaluga.aichallenge.data.storage.PlatformStorage
import ru.nikitaluga.aichallenge.domain.model.PersonalizedResult
import ru.nikitaluga.aichallenge.domain.model.UserProfileConfig

/**
 * День 12 — Персонализированный агент.
 *
 * При каждом запросе инжектирует активный профиль ([activeProfile]) в system-prompt.
 * Профиль задаётся снаружи из ViewModel — агент не меняет его автоматически.
 *
 * У каждого профиля **своя** история диалога, хранящаяся по отдельному ключу
 * `baseStorageKey_profileId`. Переключение профиля автоматически загружает его историю.
 *
 * История диалога — скользящее окно последних [windowSize] сообщений.
 */
class PersonalizedAgent(
    private val apiService: RouterAiApiService,
    private val model: String = DEFAULT_MODEL,
    private val baseSystemPrompt: String? = null,
    private val windowSize: Int = 20,
    private val baseStorageKey: String = "personalized_agent",
) {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
    private val _history = mutableListOf<ChatMessage>()

    val history: List<ChatMessage> get() = _history.toList()

    /** Ключ хранилища зависит от активного профиля — у каждого своя история. */
    private val storageKey: String get() = "${baseStorageKey}_${_activeProfile?.id ?: "default"}"

    private var _activeProfile: UserProfileConfig? = null

    /**
     * Активный профиль пользователя. При смене профиля (другой id) история
     * автоматически сохраняется (уже сохранена после каждого sendMessage) и
     * загружается история нового профиля.
     */
    var activeProfile: UserProfileConfig?
        get() = _activeProfile
        set(value) {
            if (value?.id != _activeProfile?.id) {
                // Переключение на другой профиль: загрузить его историю
                _activeProfile = value
                _history.clear()
                lastUsage = null
                loadFromStorage()
            } else {
                // Тот же профиль (например, отредактировали поля): просто обновить
                _activeProfile = value
            }
        }

    var lastUsage: Usage? = null
        private set

    suspend fun sendMessage(text: String): PersonalizedResult {
        _history.add(ChatMessage(role = "user", content = text))
        saveToStorage()

        return try {
            val result = apiService.sendMessages(
                messages = buildMessagesList(),
                model = model,
            )
            lastUsage = result.usage
            _history.add(ChatMessage(role = "assistant", content = result.content))
            trimIfNeeded()
            saveToStorage()
            PersonalizedResult(content = result.content, usage = result.usage)
        } catch (e: Exception) {
            _history.removeLast()
            saveToStorage()
            throw e
        }
    }

    fun clearHistory() {
        _history.clear()
        lastUsage = null
        PlatformStorage.remove(storageKey)
    }

    // ── Приватные методы ──────────────────────────────────────────────────────

    private fun buildMessagesList(): List<ChatMessage> {
        val systemMsg = ChatMessage(role = "system", content = buildSystemPrompt())
        return listOf(systemMsg) + _history.takeLast(windowSize)
    }

    private fun buildSystemPrompt(): String {
        val base = baseSystemPrompt ?: "Ты полезный ассистент."
        val profile = activeProfile ?: return base
        if (profile.fields.isEmpty()) return base
        val profileSection = buildString {
            appendLine("\n--- Профиль пользователя: ${profile.name} ---")
            profile.fields.forEach { (k, v) -> appendLine("$k: $v") }
            append("Учитывай характеристики профиля при каждом ответе. Адаптируй стиль, формат и уровень детализации.")
        }
        return base + profileSection
    }

    private fun trimIfNeeded() {
        while (_history.size > windowSize * 2) {
            _history.removeAt(0)
        }
    }

    private fun saveToStorage() {
        runCatching {
            val stored = _history.map { StoredMsg(it.role, it.content) }
            PlatformStorage.save(storageKey, json.encodeToString<List<StoredMsg>>(stored))
        }
    }

    private fun loadFromStorage() {
        runCatching {
            PlatformStorage.load(storageKey)?.let { encoded ->
                val stored = json.decodeFromString<List<StoredMsg>>(encoded)
                _history.addAll(stored.map { ChatMessage(role = it.role, content = it.content) })
            }
        }
    }

    @Serializable
    private data class StoredMsg(val role: String, val content: String)

    companion object {
        private const val DEFAULT_MODEL = "deepseek/deepseek-v3.2"
    }
}
