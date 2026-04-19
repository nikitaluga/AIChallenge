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
import ru.nikitaluga.aichallenge.domain.model.BenchmarkResult
import ru.nikitaluga.aichallenge.domain.model.JudgeResult
import ru.nikitaluga.aichallenge.domain.model.LocalChatResult
import ru.nikitaluga.aichallenge.domain.model.OllamaOptions
import ru.nikitaluga.aichallenge.util.CommonJson

/**
 * День 29 — LocalOptimizationAgent.
 *
 * Клиент для benchmark и LLM-judge эндпоинтов:
 * - POST /local/benchmark — запускает два запроса (до/после) и возвращает результаты
 * - POST /local/judge    — оценивает два ответа через GPT-4o-mini (1-5)
 */
class LocalOptimizationAgent(
    private val serverBaseUrl: String = "http://10.0.2.2:8080",
) {
    private val json = CommonJson
    private val client = HttpClient {
        install(ContentNegotiation) { json(json) }
        install(HttpTimeout) {
            requestTimeoutMillis = 300_000
            connectTimeoutMillis = 10_000
        }
    }

    suspend fun benchmark(
        query: String,
        beforeModel: String,
        afterModel: String,
        beforeOptions: OllamaOptions,
        afterOptions: OllamaOptions,
        beforeSystemPrompt: String,
        afterSystemPrompt: String,
    ): BenchmarkResult {
        val dto = BenchmarkRequestDto(
            query = query,
            beforeModel = beforeModel,
            afterModel = afterModel,
            beforeOptions = beforeOptions.toDto(),
            afterOptions = afterOptions.toDto(),
            beforeSystemPrompt = beforeSystemPrompt,
            afterSystemPrompt = afterSystemPrompt,
        )
        val response = client.post("$serverBaseUrl/local/benchmark") {
            contentType(ContentType.Application.Json)
            setBody(dto)
        }
        if (!response.status.isSuccess()) {
            error("Benchmark error: ${response.status.value} — ${response.bodyAsText()}")
        }
        return response.body<BenchmarkResponseDto>().toDomain()
    }

    suspend fun judge(query: String, answerA: String, answerB: String): JudgeResult {
        val dto = JudgeRequestDto(query = query, answerA = answerA, answerB = answerB)
        val response = client.post("$serverBaseUrl/local/judge") {
            contentType(ContentType.Application.Json)
            setBody(dto)
        }
        if (!response.status.isSuccess()) {
            error("Judge error: ${response.status.value} — ${response.bodyAsText()}")
        }
        return response.body<JudgeResponseDto>().toDomain()
    }

    private fun OllamaOptions.toDto() = OllamaOptionsDto(
        temperature = temperature,
        topP = topP,
        topK = topK,
        numPredict = numPredict,
        numCtx = numCtx,
    )
}

// ─── DTOs ─────────────────────────────────────────────────────────────────────

@Serializable
private data class OllamaOptionsDto(
    val temperature: Float,
    @SerialName("top_p") val topP: Float,
    @SerialName("top_k") val topK: Int,
    @SerialName("num_predict") val numPredict: Int,
    @SerialName("num_ctx") val numCtx: Int,
)

@Serializable
private data class BenchmarkRequestDto(
    val query: String,
    @SerialName("before_model") val beforeModel: String,
    @SerialName("after_model") val afterModel: String,
    @SerialName("before_options") val beforeOptions: OllamaOptionsDto,
    @SerialName("after_options") val afterOptions: OllamaOptionsDto,
    @SerialName("before_system_prompt") val beforeSystemPrompt: String,
    @SerialName("after_system_prompt") val afterSystemPrompt: String,
)

@Serializable
private data class BenchmarkResponseDto(
    val before: BenchChatResultDto,
    val after: BenchChatResultDto,
) {
    fun toDomain() = BenchmarkResult(
        before = before.toDomain(),
        after = after.toDomain(),
    )
}

@Serializable
private data class BenchChatResultDto(
    val reply: String,
    @SerialName("latency_ms") val latencyMs: Long,
    val backend: String,
) {
    fun toDomain() = LocalChatResult(reply = reply, latencyMs = latencyMs, backend = backend)
}

@Serializable
private data class JudgeRequestDto(
    val query: String,
    @SerialName("answer_a") val answerA: String,
    @SerialName("answer_b") val answerB: String,
)

@Serializable
private data class JudgeResponseDto(
    @SerialName("score_a") val scoreA: Int,
    @SerialName("score_b") val scoreB: Int,
    val reasoning: String,
) {
    fun toDomain() = JudgeResult(scoreA = scoreA, scoreB = scoreB, reasoning = reasoning)
}
