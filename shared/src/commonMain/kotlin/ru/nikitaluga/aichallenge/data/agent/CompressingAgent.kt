package ru.nikitaluga.aichallenge.data.agent

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import ru.nikitaluga.aichallenge.api.ChatMessage
import ru.nikitaluga.aichallenge.api.RouterAiApiService
import ru.nikitaluga.aichallenge.api.Usage
import ru.nikitaluga.aichallenge.data.storage.PlatformStorage

/**
 * Одна запись в сжатой истории диалога.
 *
 * @param role    "user" или "assistant"
 * @param content текст сообщения
 * @param type    "raw" — оригинальное, "summary" — результат суммаризации
 */
@Serializable
data class HistoryEntry(
    val role: String,
    val content: String,
    val type: String, // "raw" | "summary"
)

/**
 * Ответ агента на одно сообщение пользователя.
 *
 * Если [isSingleMode] = true — summary ещё не появились, отправлялся один запрос.
 * В этом случае [fullContent] и [fullUsage] равны null — сравнивать нечего.
 *
 * Если [isSingleMode] = false — есть хотя бы одно summary, оба запроса выполнены
 * параллельно.
 */
data class DualResponse(
    val compressedContent: String,
    val fullContent: String?,
    val compressedUsage: Usage?,
    val fullUsage: Usage?,
    val compressionTriggered: Boolean = false,
    val isSingleMode: Boolean = false,
)

/**
 * Агент с управлением контекстом через компрессию истории диалога.
 *
 * Хранит две параллельные истории:
 * - [_compressedHistory] — summaries + последние [rawWindowSize] raw-сообщений
 * - [_rawHistory] — полная история без сжатия
 *
 * Режим отправки зависит от наличия summary:
 * - Нет summary (история короткая) → один запрос с полной историей, сравнения нет
 * - Есть хотя бы одно summary → два запроса параллельно: сжатый и полный
 *
 * Summary создаётся автоматически когда история набирает
 * [compressionBatchSize] + [rawWindowSize] сообщений (16/26/36/…).
 */
class CompressingAgent(
    private val apiService: RouterAiApiService,
    private val model: String = DEFAULT_MODEL,
    private val systemPrompt: String? = null,
    /** Количество последних raw-сообщений, которые не трогает сжатие. */
    val rawWindowSize: Int = 6,
    /** Сколько старейших raw-сообщений сжимать за один раз. */
    val compressionBatchSize: Int = 10,
    private val storageKey: String = DEFAULT_STORAGE_KEY,
) {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    /** Сжатая история: summaries + последние [rawWindowSize] raw-сообщений. */
    private val _compressedHistory = mutableListOf<HistoryEntry>()

    /** Полная история без какого-либо сжатия. */
    private val _rawHistory = mutableListOf<ChatMessage>()

    private var _compressionCount = 0

    /** Количество выполненных суммаризаций в этой сессии. */
    val compressionCount: Int get() = _compressionCount

    /** Полная история сообщений (user + assistant), загруженная из хранилища. */
    val rawHistory: List<ChatMessage> get() = _rawHistory.toList()

    init {
        loadFromStorage()
    }

    // ── Публичный API ─────────────────────────────────────────────────────────

    /**
     * Отправляет [userMessage] и возвращает [DualResponse].
     *
     * Если в истории ещё нет summary ([DualResponse.isSingleMode] = true):
     * - отправляется один запрос с полной историей
     * - сравнение недоступно
     *
     * Если summary уже есть ([DualResponse.isSingleMode] = false):
     * - два запроса запускаются **параллельно** (compressed + full)
     * - в ответе доступны оба результата и экономия токенов
     *
     * Суммаризация запускается **после** получения ответа, поэтому она активируется
     * начиная со следующего обмена после достижения порога
     * [compressionBatchSize] + [rawWindowSize] сообщений.
     */
    suspend fun sendMessage(userMessage: String): DualResponse {
        _compressedHistory.add(HistoryEntry(role = "user", content = userMessage, type = "raw"))
        _rawHistory.add(ChatMessage(role = "user", content = userMessage))

        val hasSummaries = _compressedHistory.any { it.type == "summary" }

        return try {
            if (hasSummaries) {
                // Параллельная отправка: summary + last N raw vs полная история
                val (compressedResult, fullResult) = coroutineScope {
                    val compressedDeferred = async {
                        apiService.sendMessages(messages = buildCompressedMessages(), model = model)
                    }
                    val fullDeferred = async {
                        apiService.sendMessages(messages = buildFullMessages(), model = model)
                    }
                    Pair(compressedDeferred.await(), fullDeferred.await())
                }

                _compressedHistory.add(HistoryEntry(role = "assistant", content = compressedResult.content, type = "raw"))
                _rawHistory.add(ChatMessage(role = "assistant", content = compressedResult.content))

                val triggered = maybeCompress()
                saveToStorage()

                DualResponse(
                    compressedContent = compressedResult.content,
                    fullContent = fullResult.content,
                    compressedUsage = compressedResult.usage,
                    fullUsage = fullResult.usage,
                    compressionTriggered = triggered,
                    isSingleMode = false,
                )
            } else {
                // Один запрос — полная история (summary ещё нет)
                val result = apiService.sendMessages(messages = buildFullMessages(), model = model)

                _compressedHistory.add(HistoryEntry(role = "assistant", content = result.content, type = "raw"))
                _rawHistory.add(ChatMessage(role = "assistant", content = result.content))

                val triggered = maybeCompress()
                saveToStorage()

                DualResponse(
                    compressedContent = result.content,
                    fullContent = null,
                    compressedUsage = result.usage,
                    fullUsage = null,
                    compressionTriggered = triggered,
                    isSingleMode = true,
                )
            }
        } catch (e: Exception) {
            _compressedHistory.removeLast()
            _rawHistory.removeLast()
            throw e
        }
    }

    /** Сбрасывает обе истории и счётчик сжатий. */
    fun clearHistory() {
        _compressedHistory.clear()
        _rawHistory.clear()
        _compressionCount = 0
        PlatformStorage.remove("${storageKey}_compressed")
        PlatformStorage.remove("${storageKey}_raw")
        PlatformStorage.remove("${storageKey}_count")
    }

    // ── Построение контекстов ─────────────────────────────────────────────────

    /**
     * Контекст со сжатием:
     * [system] → [суммари как контекстный блок, если есть] → [последние rawWindowSize raw]
     */
    private fun buildCompressedMessages(): List<ChatMessage> {
        val messages = mutableListOf<ChatMessage>()
        systemPrompt?.let { messages.add(ChatMessage(role = "system", content = it)) }

        val summaries = _compressedHistory.filter { it.type == "summary" }
        if (summaries.isNotEmpty()) {
            val combined = summaries.joinToString("\n\n") { it.content }
            messages.add(
                ChatMessage(
                    role = "user",
                    content = "Краткое содержание предыдущих сообщений диалога:\n$combined",
                ),
            )
            messages.add(ChatMessage(role = "assistant", content = "Понял, продолжу с учётом этого контекста."))
        }

        val recentRaw = _compressedHistory.filter { it.type == "raw" }.takeLast(rawWindowSize)
        recentRaw.forEach { messages.add(ChatMessage(role = it.role, content = it.content)) }

        return messages
    }

    /** Контекст без сжатия: [system] → вся [_rawHistory]. */
    private fun buildFullMessages(): List<ChatMessage> {
        val messages = mutableListOf<ChatMessage>()
        systemPrompt?.let { messages.add(ChatMessage(role = "system", content = it)) }
        messages.addAll(_rawHistory)
        return messages
    }

    // ── Логика сжатия ─────────────────────────────────────────────────────────

    /**
     * Если количество сжимаемых raw-сообщений (rawCount − [rawWindowSize]) ≥ [compressionBatchSize],
     * берёт старейшие [compressionBatchSize] raw, суммаризирует их через API
     * и заменяет одной summary-записью.
     *
     * @return true если суммаризация была выполнена.
     */
    private suspend fun maybeCompress(): Boolean {
        val rawEntries = _compressedHistory.filter { it.type == "raw" }
        val eligibleCount = rawEntries.size - rawWindowSize
        if (eligibleCount < compressionBatchSize) return false

        // Самые старые [compressionBatchSize] raw-сообщений
        val toCompress = rawEntries.take(compressionBatchSize)

        val dialogText = toCompress.joinToString("\n") { entry ->
            val label = if (entry.role == "user") "Пользователь" else "Ассистент"
            "$label: ${entry.content}"
        }

        val summaryResult = runCatching {
            apiService.sendMessages(
                messages = listOf(
                    ChatMessage(
                        role = "user",
                        content = "Суммаризируй следующие сообщения диалога, сохранив ключевые " +
                            "договорённости, вопросы и ответы. Формат: краткое связное резюме\n\n$dialogText",
                    ),
                ),
                model = model,
            )
        }.getOrNull() ?: return false

        // Удаляем [compressionBatchSize] старейших raw-сообщений через итератор
        var removed = 0
        val iter = _compressedHistory.iterator()
        while (iter.hasNext() && removed < compressionBatchSize) {
            if (iter.next().type == "raw") {
                iter.remove()
                removed++
            }
        }

        // Вставляем summary перед первым raw-сообщением
        val summaryEntry = HistoryEntry(
            role = "assistant",
            content = summaryResult.content,
            type = "summary",
        )
        val firstRawIdx = _compressedHistory.indexOfFirst { it.type == "raw" }
        if (firstRawIdx >= 0) {
            _compressedHistory.add(firstRawIdx, summaryEntry)
        } else {
            _compressedHistory.add(summaryEntry)
        }

        _compressionCount++
        return true
    }

    // ── Персистентность ───────────────────────────────────────────────────────

    private fun saveToStorage() {
        runCatching {
            PlatformStorage.save(
                "${storageKey}_compressed",
                json.encodeToString<List<HistoryEntry>>(_compressedHistory),
            )
            val rawStored = _rawHistory.map { StoredRawMessage(role = it.role, content = it.content) }
            PlatformStorage.save(
                "${storageKey}_raw",
                json.encodeToString<List<StoredRawMessage>>(rawStored),
            )
            PlatformStorage.save("${storageKey}_count", _compressionCount.toString())
        }
    }

    private fun loadFromStorage() {
        runCatching {
            PlatformStorage.load("${storageKey}_compressed")?.let { encoded ->
                _compressedHistory.addAll(json.decodeFromString<List<HistoryEntry>>(encoded))
            }
            PlatformStorage.load("${storageKey}_raw")?.let { encoded ->
                val stored = json.decodeFromString<List<StoredRawMessage>>(encoded)
                _rawHistory.addAll(stored.map { ChatMessage(role = it.role, content = it.content) })
            }
            _compressionCount = PlatformStorage.load("${storageKey}_count")?.toIntOrNull() ?: 0
        }
    }

    @Serializable
    private data class StoredRawMessage(val role: String, val content: String)

    companion object {
        private const val DEFAULT_MODEL = "deepseek/deepseek-v3.2"
        private const val DEFAULT_STORAGE_KEY = "compressing_agent_history"
    }
}
