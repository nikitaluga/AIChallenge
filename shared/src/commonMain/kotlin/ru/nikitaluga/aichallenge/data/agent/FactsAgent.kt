package ru.nikitaluga.aichallenge.data.agent

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import ru.nikitaluga.aichallenge.api.ChatMessage
import ru.nikitaluga.aichallenge.api.RouterAiApiService
import ru.nikitaluga.aichallenge.api.Usage
import ru.nikitaluga.aichallenge.data.storage.PlatformStorage

data class FactsResult(
    val content: String,
    val usage: Usage?,
    val facts: Map<String, String>,
)

/**
 * Агент со стратегией «Sticky Facts / Key-Value Memory».
 *
 * Помимо скользящего окна последних [windowSize] сообщений хранит карту ключ→значение фактов.
 * После каждого сообщения пользователя LLM автоматически обновляет факты (отдельным запросом,
 * запускаемым параллельно с основным).
 *
 * В основном запросе факты передаются как синтетическая user/assistant пара перед историей.
 */
class FactsAgent(
    private val apiService: RouterAiApiService,
    private val model: String = DEFAULT_MODEL,
    private val systemPrompt: String? = null,
    val windowSize: Int = 8,
    private val storageKey: String = "facts_agent",
) {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
    private val _history = mutableListOf<ChatMessage>()
    private val _facts = mutableMapOf<String, String>()

    var lastUsage: Usage? = null
        private set

    val history: List<ChatMessage> get() = _history.toList()
    val facts: Map<String, String> get() = _facts.toMap()

    init {
        loadFromStorage()
    }

    suspend fun sendMessage(text: String): FactsResult {
        _history.add(ChatMessage(role = "user", content = text))
        saveToStorage()

        return try {
            coroutineScope {
                val chatDeferred = async { sendChatRequest() }
                val factsDeferred = async { extractFacts(text) }

                val chatResult = chatDeferred.await()
                val newFacts = factsDeferred.await()

                _history.add(ChatMessage(role = "assistant", content = chatResult.content))
                newFacts?.let { _facts.putAll(it) }
                lastUsage = chatResult.usage
                saveToStorage()

                FactsResult(
                    content = chatResult.content,
                    usage = chatResult.usage,
                    facts = _facts.toMap(),
                )
            }
        } catch (e: Exception) {
            _history.removeLast()
            saveToStorage()
            throw e
        }
    }

    fun clearHistory() {
        _history.clear()
        _facts.clear()
        lastUsage = null
        PlatformStorage.remove("${storageKey}_h")
        PlatformStorage.remove("${storageKey}_f")
    }

    private suspend fun sendChatRequest(): ru.nikitaluga.aichallenge.api.MessageWithMetrics {
        val messages = buildList {
            systemPrompt?.let { add(ChatMessage(role = "system", content = it)) }
            if (_facts.isNotEmpty()) {
                val factsText = _facts.entries.joinToString("\n") { "${it.key}: ${it.value}" }
                add(ChatMessage(role = "user", content = "Ключевые факты диалога:\n$factsText"))
                add(ChatMessage(role = "assistant", content = "Принял к сведению."))
            }
            addAll(_history.takeLast(windowSize))
        }
        return apiService.sendMessages(messages = messages, model = model)
    }

    /**
     * Вызов LLM для обновления фактов. Возвращает null при любой ошибке —
     * провал извлечения фактов не должен прерывать основной диалог.
     */
    private suspend fun extractFacts(userMessage: String): Map<String, String>? {
        val factsJson = if (_facts.isEmpty()) {
            "{}"
        } else {
            runCatching { json.encodeToString<Map<String, String>>(_facts) }.getOrDefault("{}")
        }
        val prompt = """Текущие факты диалога (JSON): $factsJson
Последнее сообщение пользователя: "$userMessage"
Обнови факты: добавь новые ключевые данные (цели, ограничения, решения, предпочтения, договорённости). Старые данные сохрани.
Верни ТОЛЬКО валидный JSON формата {"ключ": "значение"} без каких-либо пояснений."""

        return try {
            val result = apiService.sendMessages(
                messages = listOf(ChatMessage(role = "user", content = prompt)),
                model = model,
                temperature = 0.1,
            )
            val raw = result.content
            val start = raw.indexOf('{')
            val end = raw.lastIndexOf('}')
            if (start < 0 || end <= start) return null
            json.decodeFromString<Map<String, String>>(raw.substring(start, end + 1))
        } catch (e: Exception) {
            null
        }
    }

    private fun saveToStorage() {
        runCatching {
            val stored = _history.map { StoredMsg(it.role, it.content) }
            PlatformStorage.save("${storageKey}_h", json.encodeToString<List<StoredMsg>>(stored))
            PlatformStorage.save("${storageKey}_f", json.encodeToString<Map<String, String>>(_facts))
        }
    }

    private fun loadFromStorage() {
        runCatching {
            PlatformStorage.load("${storageKey}_h")?.let { encoded ->
                val stored = json.decodeFromString<List<StoredMsg>>(encoded)
                _history.addAll(stored.map { ChatMessage(role = it.role, content = it.content) })
            }
            PlatformStorage.load("${storageKey}_f")?.let { encoded ->
                _facts.putAll(json.decodeFromString<Map<String, String>>(encoded))
            }
        }
    }

    @Serializable
    private data class StoredMsg(val role: String, val content: String)

    companion object {
        private const val DEFAULT_MODEL = "deepseek/deepseek-v3.2"
    }
}
