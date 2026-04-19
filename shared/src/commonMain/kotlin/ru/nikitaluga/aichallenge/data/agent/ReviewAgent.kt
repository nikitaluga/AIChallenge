package ru.nikitaluga.aichallenge.data.agent

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

// День 32 — Review Agent.
// HTTP-клиент к POST /review/pr.
class ReviewAgent(
    private val serverBaseUrl: String = "http://10.0.2.2:8080",
) {
    private val jsonConfig = Json { ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = false }
    private val client = HttpClient {
        install(HttpTimeout) { requestTimeoutMillis = 30_000L }
        install(ContentNegotiation) { json(jsonConfig) }
    }

    suspend fun reviewPr(diff: String, title: String = "", description: String = ""): ReviewResultDto {
        val response = client.post("$serverBaseUrl/review/pr") {
            contentType(ContentType.Application.Json)
            setBody(ReviewRequestDto(diff = diff, title = title, description = description))
        }
        if (!response.status.isSuccess()) {
            throw Exception("Ошибка сервера ${response.status.value}")
        }
        return response.body()
    }

    @Serializable
    private data class ReviewRequestDto(
        val diff: String,
        val title: String = "",
        val description: String = "",
        val maxDiffLength: Int = 8000,
    )

    @Serializable
    data class ReviewResultDto(
        val bugs: List<String> = emptyList(),
        val architecture: List<String> = emptyList(),
        val recommendations: List<String> = emptyList(),
        val summary: String = "",
        val model: String = "",
        val diffLength: Int = 0,
    )
}
