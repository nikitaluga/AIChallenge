package ru.nikitaluga.aichallenge.data.agent

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import ru.nikitaluga.aichallenge.domain.model.LocalChatMessage
import ru.nikitaluga.aichallenge.domain.model.LocalChatResult

/**
 * День 26 — LocalLlmAgent.
 *
 * Клиент к Ktor-серверу, который проксирует запросы к Ollama (local) или OpenAI (cloud).
 * - POST /local/chat  — локальная LLM через Ollama
 * - POST /local/cloud — облачная LLM через OpenAI gpt-4o-mini
 */
class LocalLlmAgent(
    private val serverBaseUrl: String = "http://10.0.2.2:8080",
) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = false }
    private val client = HttpClient {
        install(HttpTimeout) { requestTimeoutMillis = 30_000L }
        install(ContentNegotiation) { json(json) }
    }

    suspend fun chat(
        messages: List<LocalChatMessage>,
        model: String = "llama3.2:3b",
    ): LocalChatResult {
        val dto = LocalChatRequestDto(
            messages = messages.map { LocalChatMessageDto(role = it.role, content = it.content) },
            model = model,
        )
        val response = client.post("$serverBaseUrl/local/chat") {
            contentType(ContentType.Application.Json)
            setBody(dto)
        }
        if (!response.status.isSuccess()) {
            error("Ошибка сервера: ${response.status.value} — ${response.bodyAsText()}")
        }
        return response.body<LocalChatResponseDto>().toDomain()
    }

    suspend fun chatCloud(messages: List<LocalChatMessage>): LocalChatResult {
        val dto = LocalChatRequestDto(
            messages = messages.map { LocalChatMessageDto(role = it.role, content = it.content) },
            model = "gpt-4o-mini",
        )
        val response = client.post("$serverBaseUrl/local/cloud") {
            contentType(ContentType.Application.Json)
            setBody(dto)
        }
        if (!response.status.isSuccess()) {
            error("Ошибка сервера: ${response.status.value} — ${response.bodyAsText()}")
        }
        return response.body<LocalChatResponseDto>().toDomain()
    }
}

// ─── DTOs ────────────────────────────────────────────────────────────────────

@Serializable
private data class LocalChatMessageDto(
    val role: String,
    val content: String,
)

@Serializable
private data class LocalChatRequestDto(
    val messages: List<LocalChatMessageDto>,
    val model: String,
)

@Serializable
private data class LocalChatResponseDto(
    val reply: String,
    @SerialName("latency_ms") val latencyMs: Long,
    val backend: String,
) {
    fun toDomain() = LocalChatResult(reply = reply, latencyMs = latencyMs, backend = backend)
}
