package ru.nikitaluga.aichallenge.data.agent

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import ru.nikitaluga.aichallenge.api.ChatMessage
import ru.nikitaluga.aichallenge.api.RouterAiApiService
import ru.nikitaluga.aichallenge.api.Usage
import ru.nikitaluga.aichallenge.data.storage.PlatformStorage

data class SlidingResult(val content: String, val usage: Usage?)

/**
 * Агент со скользящим окном контекста.
 *
 * Хранит полную историю в памяти и storage, но при каждом запросе
 * отправляет только последние [windowSize] сообщений. Остальные отбрасываются.
 */
class SlidingWindowAgent(
    private val apiService: RouterAiApiService,
    private val model: String = DEFAULT_MODEL,
    private val systemPrompt: String? = null,
    val windowSize: Int = 10,
    private val storageKey: String = "sliding_agent_history",
) {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
    private val _history = mutableListOf<ChatMessage>()

    val history: List<ChatMessage> get() = _history.toList()
    var lastUsage: Usage? = null
        private set

    init {
        loadFromStorage()
    }

    suspend fun sendMessage(text: String): SlidingResult {
        _history.add(ChatMessage(role = "user", content = text))
        saveToStorage()

        return try {
            val messages = buildList {
                systemPrompt?.let { add(ChatMessage(role = "system", content = it)) }
                addAll(_history.takeLast(windowSize))
            }
            val result = apiService.sendMessages(messages = messages, model = model)
            lastUsage = result.usage
            _history.add(ChatMessage(role = "assistant", content = result.content))
            saveToStorage()
            SlidingResult(content = result.content, usage = result.usage)
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

    private fun saveToStorage() {
        runCatching {
            val stored = _history.map { StoredMsg(it.role, it.content) }
            PlatformStorage.save(storageKey, json.encodeToString<List<StoredMsg>>(stored))
        }
    }

    private fun loadFromStorage() {
        runCatching {
            val encoded = PlatformStorage.load(storageKey) ?: return
            val stored = json.decodeFromString<List<StoredMsg>>(encoded)
            _history.addAll(stored.map { ChatMessage(role = it.role, content = it.content) })
        }
    }

    @Serializable
    private data class StoredMsg(val role: String, val content: String)

    companion object {
        private const val DEFAULT_MODEL = "deepseek/deepseek-v3.2"
    }
}
