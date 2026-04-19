package ru.nikitaluga.aichallenge.data.agent

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import ru.nikitaluga.aichallenge.domain.model.ServiceHealth
import ru.nikitaluga.aichallenge.util.AgentConfig

/**
 * День 30 — LocalServiceAgent.
 *
 * Клиент к /local/health — статус Ollama-сервиса.
 */
class LocalServiceAgent(
    private val serverBaseUrl: String = AgentConfig.DEFAULT_SERVER_URL,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val client = HttpClient {
        install(ContentNegotiation) { json(json) }
        install(HttpTimeout) {
            requestTimeoutMillis = 10_000
            connectTimeoutMillis = 5_000
        }
    }

    suspend fun health(): ServiceHealth {
        val response = client.get("$serverBaseUrl/local/health")
        if (!response.status.isSuccess()) {
            error("Health check failed: ${response.status.value} — ${response.bodyAsText()}")
        }
        return response.body<HealthDto>().toDomain()
    }
}

// ─── DTO ─────────────────────────────────────────────────────────────────────

@Serializable
private data class HealthDto(
    val status: String,
    @SerialName("ollama_url") val ollamaUrl: String,
    val models: List<String> = emptyList(),
    @SerialName("active_requests") val activeRequests: Int = 0,
    @SerialName("max_concurrent") val maxConcurrent: Int = 2,
) {
    fun toDomain() = ServiceHealth(
        status = status,
        ollamaUrl = ollamaUrl,
        models = models,
        activeRequests = activeRequests,
        maxConcurrent = maxConcurrent,
    )
}
