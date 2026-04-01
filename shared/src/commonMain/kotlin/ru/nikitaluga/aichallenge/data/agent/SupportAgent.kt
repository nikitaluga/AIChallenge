package ru.nikitaluga.aichallenge.data.agent

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

// День 33 — Support Agent.
// HTTP-клиент к /support/** эндпоинтам.
class SupportAgent(
    private val serverBaseUrl: String = "http://10.0.2.2:8080",
) {
    private val jsonConfig = Json { ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = false }
    private val client = HttpClient {
        install(ContentNegotiation) { json(jsonConfig) }
    }

    suspend fun getUsers(): List<UserItemDto> {
        val response = client.get("$serverBaseUrl/support/users")
        if (!response.status.isSuccess()) throw Exception("Ошибка ${response.status.value}")
        return response.body()
    }

    suspend fun getTickets(userId: String): List<TicketItemDto> {
        val response = client.get("$serverBaseUrl/support/tickets") {
            parameter("userId", userId)
        }
        if (!response.status.isSuccess()) throw Exception("Ошибка ${response.status.value}")
        return response.body()
    }

    suspend fun chat(userId: String, query: String, history: List<SupportHistoryMsg>): SupportResponseDto {
        val response = client.post("$serverBaseUrl/support/chat") {
            contentType(ContentType.Application.Json)
            setBody(SupportChatRequestDto(userId = userId, query = query, history = history))
        }
        if (!response.status.isSuccess()) throw Exception("Ошибка сервера ${response.status.value}")
        return response.body()
    }

    @Serializable
    data class UserItemDto(val id: String, val name: String, val plan: String)

    @Serializable
    data class TicketItemDto(
        val id: String,
        val subject: String,
        val status: String,
        val description: String = "",
        val createdAt: String = "",
    )

    @Serializable
    data class SupportHistoryMsg(val role: String, val content: String)

    @Serializable
    data class SupportResponseDto(
        val answer: String = "",
        val sources: List<SourceDto> = emptyList(),
        val toolsUsed: List<String> = emptyList(),
    )

    @Serializable
    data class SourceDto(
        val source: String,
        val section: String? = null,
        val preview: String,
    )

    @Serializable
    private data class SupportChatRequestDto(
        val userId: String,
        val query: String,
        val history: List<SupportHistoryMsg> = emptyList(),
    )
}
