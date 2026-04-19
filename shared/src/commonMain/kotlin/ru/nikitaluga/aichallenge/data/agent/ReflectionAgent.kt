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
import ru.nikitaluga.aichallenge.util.CommonJson

// День 36 — Reflection Agent. HTTP-клиент к /reflection/chat эндпоинту.
class ReflectionAgent(private val serverBaseUrl: String = "http://10.0.2.2:8080") {

    private val jsonConfig = CommonJson
    private val client = HttpClient {
        install(HttpTimeout) { requestTimeoutMillis = 30_000L }
        install(ContentNegotiation) { json(jsonConfig) }
    }

    suspend fun reflect(
        query: String,
        rubric: String? = null,
        maxIterations: Int = 3,
    ): ReflectionResponseDto {
        val response = client.post("$serverBaseUrl/reflection/chat") {
            contentType(ContentType.Application.Json)
            setBody(ReflectionRequestDto(query = query, rubric = rubric, maxIterations = maxIterations))
        }
        if (!response.status.isSuccess()) throw Exception("Ошибка сервера ${response.status.value}")
        return response.body()
    }

    @Serializable
    data class IterationDto(
        val attempt: Int = 0,
        val draft: String = "",
        val critique: String = "",
        val score: Int = 0,
    )

    @Serializable
    data class ReflectionResponseDto(
        val answer: String = "",
        val totalIterations: Int = 0,
        val iterations: List<IterationDto> = emptyList(),
    )

    @Serializable
    private data class ReflectionRequestDto(
        val query: String,
        val rubric: String? = null,
        val maxIterations: Int = 3,
    )
}
