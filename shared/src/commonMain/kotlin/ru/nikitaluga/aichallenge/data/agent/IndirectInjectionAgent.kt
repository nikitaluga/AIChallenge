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
import ru.nikitaluga.aichallenge.util.AgentConfig
import ru.nikitaluga.aichallenge.util.CommonJson

// День 12 (adv) — Indirect Injection Agent. HTTP-клиент к /indirect-injection/** эндпоинтам.
class IndirectInjectionAgent(private val serverBaseUrl: String = AgentConfig.DEFAULT_SERVER_URL) {

    private val client = HttpClient {
        install(HttpTimeout) { requestTimeoutMillis = 45_000L }
        install(ContentNegotiation) { json(CommonJson) }
    }

    suspend fun attack(vectorType: String, defenseMode: String): AttackResponseDto {
        val response = client.post("$serverBaseUrl/indirect-injection/attack") {
            contentType(ContentType.Application.Json)
            setBody(AttackRequestDto(vectorType = vectorType, defenseMode = defenseMode))
        }
        if (!response.status.isSuccess()) throw Exception("Ошибка сервера ${response.status.value}")
        return response.body()
    }

    @Serializable
    data class AttackResponseDto(
        val vectorType: String = "",
        val defenseMode: String = "",
        val hiddenPayload: String = "",
        val visibleContent: String = "",
        val sanitizedContent: String = "",
        val agentOutput: String = "",
        val verdict: String = "",
        val judgeReasoning: String = "",
    )

    @Serializable
    private data class AttackRequestDto(
        val vectorType: String,
        val defenseMode: String,
    )
}
