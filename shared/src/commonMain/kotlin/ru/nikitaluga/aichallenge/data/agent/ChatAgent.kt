package ru.nikitaluga.aichallenge.data.agent

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import ru.nikitaluga.aichallenge.api.ChatMessage
import ru.nikitaluga.aichallenge.api.RouterAiApiService
import ru.nikitaluga.aichallenge.data.storage.PlatformStorage

/**
 * Stateful chat agent that manages conversation history, persists it across
 * sessions via [PlatformStorage], and delegates HTTP calls to [RouterAiApiService].
 *
 * Usage:
 * ```
 * val agent = ChatAgent(apiService)
 * val reply1 = agent.sendMessage("Привет, меня зовут Александр")
 * val reply2 = agent.sendMessage("Какое у меня имя?") // «помнит» имя
 * agent.clearHistory()
 * ```
 */
class ChatAgent(
    private val apiService: RouterAiApiService,
    private val model: String = DEFAULT_MODEL,
    private val systemPrompt: String? = null,
    /** Maximum number of messages kept in history before oldest are trimmed. */
    private val maxHistoryMessages: Int = 50,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val _history = mutableListOf<ChatMessage>()

    /** Read-only snapshot of the current conversation history. */
    val history: List<ChatMessage> get() = _history.toList()

    init {
        loadFromStorage()
    }

    /**
     * Add [userMessage] to history, call the API with the full history,
     * add the assistant reply to history, persist everything, and return
     * the reply text.
     */
    suspend fun sendMessage(userMessage: String): String {
        _history.add(ChatMessage(role = "user", content = userMessage))
        saveToStorage()

        return try {
            val response = apiService.sendMessages(
                messages = buildMessagesList(),
                model = model,
            )
            _history.add(ChatMessage(role = "assistant", content = response))
            trimIfNeeded()
            saveToStorage()
            response
        } catch (e: Exception) {
            _history.removeLast()
            throw e
        }
    }

    /**
     * Streaming variant: calls [onChunk] for every text fragment received,
     * then saves the complete assembled reply to history.
     *
     * @return The full assembled response text.
     */
    suspend fun streamMessage(
        userMessage: String,
        onChunk: (String) -> Unit,
    ): String {
        _history.add(ChatMessage(role = "user", content = userMessage))
        saveToStorage()

        val fullResponse = StringBuilder()
        return try {
            apiService.streamMessages(
                messages = buildMessagesList(),
                model = model,
                onChunk = { chunk ->
                    fullResponse.append(chunk)
                    onChunk(chunk)
                },
            )
            val responseText = fullResponse.toString()
            _history.add(ChatMessage(role = "assistant", content = responseText))
            trimIfNeeded()
            saveToStorage()
            responseText
        } catch (e: Exception) {
            _history.removeLast()
            throw e
        }
    }

    /** Clear conversation history in memory and storage. */
    fun clearHistory() {
        _history.clear()
        PlatformStorage.remove(HISTORY_KEY)
    }

    private fun buildMessagesList(): List<ChatMessage> {
        val system = systemPrompt
            ?.let { listOf(ChatMessage(role = "system", content = it)) }
            ?: emptyList()
        return system + _history
    }

    private fun trimIfNeeded() {
        while (_history.size > maxHistoryMessages) _history.removeAt(0)
    }

    private fun saveToStorage() {
        runCatching {
            PlatformStorage.save(HISTORY_KEY, json.encodeToString(_history))
        }
    }

    private fun loadFromStorage() {
        runCatching {
            val encoded = PlatformStorage.load(HISTORY_KEY) ?: return
            _history.addAll(json.decodeFromString<List<ChatMessage>>(encoded))
        }
    }

    companion object {
        private const val HISTORY_KEY = "chat_agent_history"
        private const val DEFAULT_MODEL = "deepseek/deepseek-v3.2"
    }
}
