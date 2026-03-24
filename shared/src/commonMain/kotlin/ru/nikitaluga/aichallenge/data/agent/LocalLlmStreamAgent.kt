package ru.nikitaluga.aichallenge.data.agent

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.preparePost
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.readLine
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * День 27 — LocalLlmStreamAgent.
 *
 * Клиент к Ktor-серверу для стриминга ответов из Ollama:
 * - GET /local/models  — список установленных моделей
 * - POST /local/stream — стриминг через SSE (stream=true в Ollama)
 */
class LocalLlmStreamAgent(
    private val serverBaseUrl: String = "http://10.0.2.2:8080",
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val client = HttpClient {
        install(ContentNegotiation) { json(json) }
        install(HttpTimeout) {
            requestTimeoutMillis = 180_000
            connectTimeoutMillis = 10_000
        }
    }

    /** Возвращает список установленных Ollama-моделей. */
    suspend fun getModels(): List<String> {
        val response = client.get("$serverBaseUrl/local/models")
        if (!response.status.isSuccess()) {
            error("Ошибка получения моделей: ${response.status.value} — ${response.bodyAsText()}")
        }
        return response.body<List<String>>()
    }

    /**
     * Стримит ответ LLM из Ollama через SSE-прокси на сервере.
     * Каждый токен передаётся в [onChunk].
     */
    suspend fun streamChat(
        messages: List<StreamMessage>,
        model: String = "llama3.2:3b",
        onChunk: (String) -> Unit,
    ) {
        val dto = StreamChatRequest(
            messages = messages,
            model = model,
        )
        client.preparePost("$serverBaseUrl/local/stream") {
            contentType(ContentType.Application.Json)
            setBody(dto)
        }.execute { response ->
            if (!response.status.isSuccess()) {
                error("Ошибка стриминга: ${response.status.value} — ${response.bodyAsText()}")
            }
            val channel = response.bodyAsChannel()
            while (!channel.isClosedForRead) {
                val line = channel.readLine() ?: break
                if (!line.startsWith("data: ")) continue
                val data = line.removePrefix("data: ").trim()
                when {
                    data == "[DONE]" -> break
                    data.startsWith("[ERROR]") -> error(data.removePrefix("[ERROR] ").trim())
                    else -> {
                        // token is JSON-encoded string (e.g. "Hello" or " world")
                        runCatching {
                            val token = json.decodeFromString<String>(data)
                            if (token.isNotEmpty()) onChunk(token)
                        }
                    }
                }
            }
        }
    }
}

// ─── DTOs ────────────────────────────────────────────────────────────────────

@Serializable
data class StreamMessage(val role: String, val content: String)

@Serializable
private data class StreamChatRequest(
    val messages: List<StreamMessage>,
    val model: String,
)
