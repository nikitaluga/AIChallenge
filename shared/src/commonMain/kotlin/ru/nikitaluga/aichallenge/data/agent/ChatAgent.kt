package ru.nikitaluga.aichallenge.data.agent

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import ru.nikitaluga.aichallenge.api.ChatMessage
import ru.nikitaluga.aichallenge.api.RouterAiApiService
import ru.nikitaluga.aichallenge.data.storage.PlatformStorage
import ru.nikitaluga.aichallenge.domain.model.TokenStats

/**
 * Stateful chat agent that manages conversation history, persists it across
 * sessions via [PlatformStorage], and delegates HTTP calls to [RouterAiApiService].
 *
 * Token tracking is done via [TokenCounter] (character-based heuristic for commonMain).
 * For non-streaming calls the API-reported [Usage] is preferred when available.
 *
 * Overflow strategy: when estimated context tokens exceed [contextWindowLimit],
 * the oldest user+assistant message pairs are removed automatically.
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
    /** Simulated context window size in tokens. Oldest message pairs are removed when exceeded. */
    val contextWindowLimit: Int = 4096,
    /** Storage key for history persistence — use distinct keys for separate agent instances. */
    private val storageKey: String = DEFAULT_HISTORY_KEY,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val _history = mutableListOf<ChatMessage>()

    /** Read-only snapshot of the current conversation history. */
    val history: List<ChatMessage> get() = _history.toList()

    // ── Token tracking ────────────────────────────────────────────────────────
    private var lastRequestTokens = 0
    private var lastResponseTokens = 0
    private var totalUserTokens = 0
    private var totalAssistantTokens = 0

    init {
        loadFromStorage()
    }

    // ── Public token API ──────────────────────────────────────────────────────

    /** Estimate token count for an arbitrary [text] string. */
    fun countTokens(text: String): Int = TokenCounter.countTokens(text)

    /** Estimate total tokens for an explicit [messages] list. */
    fun countMessagesTokens(messages: List<ChatMessage>): Int =
        TokenCounter.countMessagesTokens(messages)

    /** Estimate tokens currently occupying the context window (history + system prompt). */
    fun getCurrentContextTokens(): Int = TokenCounter.countMessagesTokens(buildMessagesList())

    /** Return a snapshot of token statistics for the current session. */
    fun getTokenStats(): TokenStats = TokenStats(
        lastRequestTokens = lastRequestTokens,
        lastResponseTokens = lastResponseTokens,
        totalUserTokens = totalUserTokens,
        totalAssistantTokens = totalAssistantTokens,
        currentContextTokens = getCurrentContextTokens(),
        contextWindowLimit = contextWindowLimit,
        messageCount = _history.size,
    )

    // ── Messaging ─────────────────────────────────────────────────────────────

    /**
     * Add [userMessage] to history, call the API with the full history,
     * add the assistant reply to history, persist everything, and return
     * the reply text.
     *
     * Actual API token usage is used when the server returns it; otherwise
     * falls back to the [TokenCounter] estimate.
     */
    suspend fun sendMessage(userMessage: String): String {
        val userTokens = TokenCounter.countTokens(userMessage)
        _history.add(ChatMessage(role = "user", content = userMessage))
        lastRequestTokens = userTokens
        totalUserTokens += userTokens
        saveToStorage()

        return try {
            val result = apiService.sendMessages(
                messages = buildMessagesList(),
                model = model,
            )
            val responseText = result.content
            val responseTokens = result.usage?.completionTokens
                ?: TokenCounter.countTokens(responseText)
            lastResponseTokens = responseTokens
            totalAssistantTokens += responseTokens

            _history.add(ChatMessage(role = "assistant", content = responseText))
            trimIfNeeded()
            saveToStorage()
            responseText
        } catch (e: Exception) {
            _history.removeLast()
            totalUserTokens -= userTokens
            lastRequestTokens = 0
            throw e
        }
    }

    /**
     * Streaming variant: calls [onChunk] for every text fragment received,
     * then saves the complete assembled reply to history.
     *
     * Token counts are estimated via [TokenCounter] since the streaming SSE
     * protocol does not include usage metadata mid-stream.
     *
     * @return The full assembled response text.
     */
    suspend fun streamMessage(
        userMessage: String,
        onChunk: (String) -> Unit,
    ): String {
        val userTokens = TokenCounter.countTokens(userMessage)
        _history.add(ChatMessage(role = "user", content = userMessage))
        lastRequestTokens = userTokens
        totalUserTokens += userTokens
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
            val responseTokens = TokenCounter.countTokens(responseText)
            lastResponseTokens = responseTokens
            totalAssistantTokens += responseTokens

            _history.add(ChatMessage(role = "assistant", content = responseText))
            trimIfNeeded()
            saveToStorage()
            responseText
        } catch (e: Exception) {
            _history.removeLast()
            totalUserTokens -= userTokens
            lastRequestTokens = 0
            throw e
        }
    }

    /** Clear conversation history in memory and storage, and reset token counters. */
    fun clearHistory() {
        _history.clear()
        lastRequestTokens = 0
        lastResponseTokens = 0
        totalUserTokens = 0
        totalAssistantTokens = 0
        PlatformStorage.remove(storageKey)
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private fun buildMessagesList(): List<ChatMessage> {
        val system = systemPrompt
            ?.let { listOf(ChatMessage(role = "system", content = it)) }
            ?: emptyList()
        return system + _history
    }

    /**
     * Trim history if it exceeds [maxHistoryMessages] or pushes context tokens
     * above [contextWindowLimit].  Removes oldest user+assistant pairs to keep
     * the history coherent.
     */
    private fun trimIfNeeded() {
        while (_history.size > maxHistoryMessages) _history.removeAt(0)
        while (
            _history.size >= 2 &&
            TokenCounter.countMessagesTokens(buildMessagesList()) > contextWindowLimit
        ) {
            _history.removeAt(0) // oldest user message
            _history.removeAt(0) // oldest assistant message
        }
    }

    private fun saveToStorage() {
        runCatching {
            PlatformStorage.save(storageKey, json.encodeToString(_history))
        }
    }

    private fun loadFromStorage() {
        runCatching {
            val encoded = PlatformStorage.load(storageKey) ?: return
            _history.addAll(json.decodeFromString<List<ChatMessage>>(encoded))
            // Reconstruct cumulative token totals from persisted history
            for (msg in _history) {
                val t = TokenCounter.countTokens(msg.effectiveContent)
                when (msg.role) {
                    "user" -> totalUserTokens += t
                    "assistant" -> totalAssistantTokens += t
                }
            }
        }
    }

    companion object {
        private const val DEFAULT_HISTORY_KEY = "chat_agent_history"
        private const val DEFAULT_MODEL = "deepseek/deepseek-v3.2"
    }
}
