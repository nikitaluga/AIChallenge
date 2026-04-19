package ru.nikitaluga.aichallenge.data.agent

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import ru.nikitaluga.aichallenge.util.CommonJson
import ru.nikitaluga.aichallenge.util.AgentConfig

// День 31 — Dev Assistant Agent.
// HTTP-клиент к /dev/* эндпоинтам: /dev/chat, /dev/docs/stats, /dev/docs/index
class DevAssistantAgent(
    private val serverBaseUrl: String = AgentConfig.DEFAULT_SERVER_URL,
) {
    private val json = CommonJson
    private val client = HttpClient {
        install(HttpTimeout) { requestTimeoutMillis = 30_000L }
        install(ContentNegotiation) { json(json) }
    }

    private val history = mutableListOf<DevHistoryMessageDto>()

    suspend fun sendMessage(text: String, useMcp: Boolean = true): DevChatResultDto {
        val response = client.post("$serverBaseUrl/dev/chat") {
            contentType(ContentType.Application.Json)
            setBody(
                DevChatRequestDto(
                    query = text,
                    history = history.toList(),
                    k = 5,
                    useMcp = useMcp,
                )
            )
        }
        if (!response.status.isSuccess()) {
            throw Exception("Ошибка сервера ${response.status.value}")
        }
        val result = response.body<DevChatResponseDto>()
        history.add(DevHistoryMessageDto(role = "user", content = text))
        history.add(DevHistoryMessageDto(role = "assistant", content = result.answer))
        return DevChatResultDto(
            answer = result.answer,
            sources = result.sources,
            toolsUsed = result.toolsUsed,
        )
    }

    suspend fun getStats(): DevDocsStatsDto {
        val response = client.get("$serverBaseUrl/dev/docs/stats")
        return response.body()
    }

    suspend fun buildIndex(): String {
        val response = client.post("$serverBaseUrl/dev/docs/index") {
            contentType(ContentType.Application.Json)
        }
        if (!response.status.isSuccess()) throw Exception("Ошибка индексации")
        return response.body<DevIndexResponseDto>().message
    }

    fun clearHistory() = history.clear()

    // ── DTOs ──────────────────────────────────────────────────────────────────

    @Serializable
    private data class DevHistoryMessageDto(val role: String, val content: String)

    @Serializable
    private data class DevChatRequestDto(
        val query: String,
        val history: List<DevHistoryMessageDto> = emptyList(),
        val k: Int = 5,
        val useMcp: Boolean = true,
    )

    @Serializable
    data class DevChatSourceDto(
        val source: String,
        val section: String? = null,
        val preview: String,
    )

    @Serializable
    private data class DevChatResponseDto(
        val answer: String,
        val sources: List<DevChatSourceDto> = emptyList(),
        val toolsUsed: List<String> = emptyList(),
    )

    @Serializable
    data class DevDocsStatsDto(
        val hasIndex: Boolean,
        val totalChunks: Int = 0,
        val docsIndexed: Int = 0,
        val model: String = "",
        val createdAt: String = "",
    )

    @Serializable
    private data class DevIndexResponseDto(
        val success: Boolean,
        val totalChunks: Int,
        val docsIndexed: Int,
        val message: String,
    )
}

// ── Domain result models (used by ViewModel) ──────────────────────────────────

data class DevChatResultDto(
    val answer: String,
    val sources: List<DevAssistantAgent.DevChatSourceDto>,
    val toolsUsed: List<String>,
)
