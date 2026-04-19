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

// День 35 — Git Commit Agent. HTTP-клиент к /git/commit эндпоинту.
class GitCommitAgent(private val serverBaseUrl: String = "http://10.0.2.2:8080") {

    private val jsonConfig = Json { ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = false }
    private val client = HttpClient {
        install(HttpTimeout) { requestTimeoutMillis = 30_000L }
        install(ContentNegotiation) { json(jsonConfig) }
    }

    suspend fun generate(diff: String, context: String? = null): GitCommitResponseDto {
        val response = client.post("$serverBaseUrl/git/commit") {
            contentType(ContentType.Application.Json)
            setBody(GitCommitRequestDto(diff = diff, context = context))
        }
        if (!response.status.isSuccess()) throw Exception("Ошибка сервера ${response.status.value}")
        return response.body()
    }

    @Serializable
    data class GitCommitResponseDto(
        val message: String = "",
        val alternatives: List<String> = emptyList(),
    )

    @Serializable
    private data class GitCommitRequestDto(
        val diff: String,
        val context: String? = null,
    )
}
