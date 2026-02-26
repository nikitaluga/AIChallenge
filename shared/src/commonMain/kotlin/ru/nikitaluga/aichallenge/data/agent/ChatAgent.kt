package ru.nikitaluga.aichallenge.data.agent

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import ru.nikitaluga.aichallenge.api.ChatMessage
import ru.nikitaluga.aichallenge.api.RouterAiApiService
import ru.nikitaluga.aichallenge.data.storage.PlatformStorage
import ru.nikitaluga.aichallenge.domain.model.TokenStats

/** API-reported token usage for a single request/response cycle. */
data class TokenUsage(
    val promptTokens: Int,
    val completionTokens: Int,
    val totalTokens: Int,
    val model: String,
)

/**
 * Persistent storage model for a single message, including optional API token metadata
 * for assistant messages.
 */
@Serializable
private data class StoredMessage(
    val role: String,
    val content: String,
    val reasoning: String? = null,
    val promptTokens: Int? = null,
    val completionTokens: Int? = null,
    val totalTokens: Int? = null,
    val model: String? = null,
) {
    val effectiveContent: String get() = content.ifBlank { reasoning.orEmpty() }
}

/**
 * Stateful chat agent that manages conversation history, persists it across
 * sessions via [PlatformStorage], and delegates HTTP calls to [RouterAiApiService].
 *
 * Token tracking relies exclusively on API-reported usage data ([TokenUsage]).
 * No heuristic estimation is performed.
 *
 * Usage:
 * ```
 * val agent = ChatAgent(apiService)
 * val reply1 = agent.sendMessage("Привет, меня зовут Александр")
 * val reply2 = agent.sendMessage("Какое у меня имя?")
 * agent.clearHistory()
 * ```
 */
class ChatAgent(
    private val apiService: RouterAiApiService,
    private val model: String = DEFAULT_MODEL,
    private val systemPrompt: String? = null,
    /** Maximum number of messages kept in history before oldest are trimmed. */
    private val maxHistoryMessages: Int = 50,
    /** Model's actual context window in tokens — used for the progress bar. */
    val contextWindowLimit: Int = 4096,
    /** Storage key for history persistence — use distinct keys for separate agent instances. */
    private val storageKey: String = DEFAULT_HISTORY_KEY,
) {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
    private val _history = mutableListOf<ChatMessage>()

    /**
     * Parallel to [_history]: API-reported token usage for each message.
     * Non-null only for assistant messages where the API returned usage data.
     */
    private val _messageTokens = mutableListOf<TokenUsage?>()

    /** Read-only snapshot of the current conversation history. */
    val history: List<ChatMessage> get() = _history.toList()

    /** History paired with optional per-message API token usage. */
    val historyWithTokens: List<Pair<ChatMessage, TokenUsage?>>
        get() = _history.zip(_messageTokens)

    // ── Token tracking (API-based only) ───────────────────────────────────────
    private var _lastTokenUsage: TokenUsage? = null
    private var sessionPromptTokens: Int = 0
    private var sessionCompletionTokens: Int = 0

    /** API-reported token usage from the most recent [sendMessage] call, or null if unavailable. */
    val lastTokenUsage: TokenUsage? get() = _lastTokenUsage

    init {
        loadFromStorage()
    }

    // ── Public token API ──────────────────────────────────────────────────────

    /** Return a snapshot of token statistics for the current session. */
    fun getTokenStats(): TokenStats = TokenStats(
        lastPromptTokens = _lastTokenUsage?.promptTokens ?: 0,
        lastCompletionTokens = _lastTokenUsage?.completionTokens ?: 0,
        lastTotalTokens = _lastTokenUsage?.totalTokens ?: 0,
        sessionPromptTokens = sessionPromptTokens,
        sessionCompletionTokens = sessionCompletionTokens,
        messageCount = _history.size,
        contextWindowLimit = contextWindowLimit,
        lastModel = _lastTokenUsage?.model ?: "",
    )

    // ── Messaging ─────────────────────────────────────────────────────────────

    /**
     * Add [userMessage] to history, call the API with the full history,
     * add the assistant reply to history, persist everything, and return
     * the reply text.
     *
     * On success, [lastTokenUsage] is updated with the API-reported usage.
     * On error, [lastTokenUsage] retains its previous value so the UI keeps showing
     * the last known context state.
     */
    suspend fun sendMessage(userMessage: String): String {
        _history.add(ChatMessage(role = "user", content = userMessage))
        _messageTokens.add(null)
        saveToStorage()

        return try {
            val result = apiService.sendMessages(
                messages = buildMessagesList(),
                model = model,
            )
            val responseText = result.content
            val apiUsage = result.usage

            if (apiUsage != null) {
                _lastTokenUsage = TokenUsage(
                    promptTokens = apiUsage.promptTokens,
                    completionTokens = apiUsage.completionTokens,
                    totalTokens = apiUsage.totalTokens,
                    model = model,
                )
                sessionPromptTokens += apiUsage.promptTokens
                sessionCompletionTokens += apiUsage.completionTokens
            }

            _history.add(ChatMessage(role = "assistant", content = responseText))
            _messageTokens.add(_lastTokenUsage)
            trimIfNeeded()
            saveToStorage()
            responseText
        } catch (e: Exception) {
            _history.removeLast()
            _messageTokens.removeLast()
            throw e
        }
    }

    /**
     * Streaming variant: calls [onChunk] for every text fragment received,
     * then saves the complete assembled reply to history.
     *
     * Token usage is not available for streaming responses.
     *
     * @return The full assembled response text.
     */
    suspend fun streamMessage(
        userMessage: String,
        onChunk: (String) -> Unit,
    ): String {
        _history.add(ChatMessage(role = "user", content = userMessage))
        _messageTokens.add(null)
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
            _messageTokens.add(null)
            trimIfNeeded()
            saveToStorage()
            responseText
        } catch (e: Exception) {
            _history.removeLast()
            _messageTokens.removeLast()
            throw e
        }
    }

    /** Clear conversation history in memory and storage, and reset token counters. */
    fun clearHistory() {
        _history.clear()
        _messageTokens.clear()
        _lastTokenUsage = null
        sessionPromptTokens = 0
        sessionCompletionTokens = 0
        PlatformStorage.remove(storageKey)
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private fun buildMessagesList(): List<ChatMessage> {
        val system = systemPrompt
            ?.let { listOf(ChatMessage(role = "system", content = it)) }
            ?: emptyList()
        return system + _history
    }

    /** Trim history when it exceeds [maxHistoryMessages]. */
    private fun trimIfNeeded() {
        while (_history.size > maxHistoryMessages) {
            _history.removeAt(0)
            _messageTokens.removeAt(0)
        }
    }

    private fun saveToStorage() {
        runCatching {
            val stored = _history.zip(_messageTokens).map { (msg, usage) ->
                StoredMessage(
                    role = msg.role,
                    content = msg.content,
                    reasoning = msg.reasoning,
                    promptTokens = usage?.promptTokens,
                    completionTokens = usage?.completionTokens,
                    totalTokens = usage?.totalTokens,
                    model = usage?.model,
                )
            }
            PlatformStorage.save(storageKey, json.encodeToString<List<StoredMessage>>(stored))
        }
    }

    private fun loadFromStorage() {
        runCatching {
            val encoded = PlatformStorage.load(storageKey) ?: return
            val stored = json.decodeFromString<List<StoredMessage>>(encoded)
            for (sm in stored) {
                _history.add(ChatMessage(role = sm.role, content = sm.content, reasoning = sm.reasoning))
                val usage = if (sm.totalTokens != null && sm.model != null) {
                    TokenUsage(
                        promptTokens = sm.promptTokens ?: 0,
                        completionTokens = sm.completionTokens ?: 0,
                        totalTokens = sm.totalTokens,
                        model = sm.model,
                    )
                } else null
                _messageTokens.add(usage)
                if (sm.role == "assistant" && usage != null) {
                    sessionPromptTokens += usage.promptTokens
                    sessionCompletionTokens += usage.completionTokens
                    _lastTokenUsage = usage
                }
            }
        }
    }

    companion object {
        private const val DEFAULT_HISTORY_KEY = "chat_agent_history"
        private const val DEFAULT_MODEL = "deepseek/deepseek-v3.2"
    }
}
