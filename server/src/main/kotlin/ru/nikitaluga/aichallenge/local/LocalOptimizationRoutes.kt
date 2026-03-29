package ru.nikitaluga.aichallenge.local

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.DefaultRequest
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import ru.nikitaluga.aichallenge.api.ChatMessage
import ru.nikitaluga.aichallenge.api.RouterAiApiService

// ─── Request/Response models ──────────────────────────────────────────────────

@Serializable
data class BenchmarkRequest(
    val query: String,
    @SerialName("before_model") val beforeModel: String = "llama3.2:3b",
    @SerialName("after_model") val afterModel: String = "phi3:mini",
    @SerialName("before_options") val beforeOptions: LocalChatOptions = LocalChatOptions(),
    @SerialName("after_options") val afterOptions: LocalChatOptions = LocalChatOptions(
        temperature = 0.3f,
        numPredict = 512,
        numCtx = 4096,
    ),
    @SerialName("before_system_prompt") val beforeSystemPrompt: String = "",
    @SerialName("after_system_prompt") val afterSystemPrompt: String = "",
)

@Serializable
data class BenchmarkResponse(
    val before: LocalChatResponse,
    val after: LocalChatResponse,
)

@Serializable
data class JudgeRequest(
    val query: String,
    @SerialName("answer_a") val answerA: String,
    @SerialName("answer_b") val answerB: String,
)

@Serializable
data class JudgeResponse(
    @SerialName("score_a") val scoreA: Int,
    @SerialName("score_b") val scoreB: Int,
    val reasoning: String,
)

// ─── Route installer ──────────────────────────────────────────────────────────

fun Application.installLocalOptimizationRoutes(apiService: RouterAiApiService, config: OllamaConfig = OllamaConfig()) {

    val semaphore = Semaphore(config.maxConcurrent)
    val ollamaJson = Json { ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = false }
    val ollamaClient = HttpClient {
        install(ContentNegotiation) { json(ollamaJson) }
        install(HttpTimeout) {
            requestTimeoutMillis = 180_000
            connectTimeoutMillis = 10_000
        }
        install(DefaultRequest) {
            header("ngrok-skip-browser-warning", "true")
        }
    }

    routing {
        route("/local") {

            // ── POST /local/benchmark — сравнение до/после оптимизации ────────
            post("/benchmark") {
                val req = call.receive<BenchmarkRequest>()

                runCatching {
                    coroutineScope {
                        val beforeDeferred = async {
                            runChat(ollamaClient, ollamaJson, config, semaphore, req.beforeModel, req.query, req.beforeOptions, req.beforeSystemPrompt)
                        }
                        val afterDeferred = async {
                            runChat(ollamaClient, ollamaJson, config, semaphore, req.afterModel, req.query, req.afterOptions, req.afterSystemPrompt)
                        }
                        BenchmarkResponse(
                            before = beforeDeferred.await(),
                            after = afterDeferred.await(),
                        )
                    }
                }.onSuccess { resp ->
                    call.respond(resp)
                }.onFailure { e ->
                    call.respond(HttpStatusCode.ServiceUnavailable, mapOf("error" to (e.message ?: "unknown")))
                }
            }

            // ── POST /local/judge — LLM-as-judge оценка ответов ──────────────
            post("/judge") {
                val req = call.receive<JudgeRequest>()

                runCatching {
                    val judgePrompt = """
                        You are an impartial judge evaluating two AI assistant responses.

                        QUESTION: ${req.query}

                        RESPONSE A:
                        ${req.answerA}

                        RESPONSE B:
                        ${req.answerB}

                        Rate each response from 1 to 5 based on:
                        - Accuracy and correctness
                        - Conciseness (no unnecessary filler)
                        - Helpfulness for a developer

                        Return ONLY valid JSON, no markdown, no explanation outside JSON:
                        {"score_a": <1-5>, "score_b": <1-5>, "reasoning": "<one sentence>"}
                    """.trimIndent()

                    val result = apiService.sendMessages(
                        messages = listOf(ChatMessage(role = "user", content = judgePrompt)),
                        model = "openai/gpt-4o-mini",
                    )

                    val json = Json { ignoreUnknownKeys = true }
                    val clean = result.content.trim()
                        .removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
                    json.decodeFromString<JudgeResponse>(clean)
                }.onSuccess { resp ->
                    call.respond(resp)
                }.onFailure { e ->
                    call.respond(HttpStatusCode.InternalServerError, mapOf("error" to (e.message ?: "unknown")))
                }
            }
        }
    }
}

// ─── Helper ───────────────────────────────────────────────────────────────────

@Serializable
private data class OllamaRequest(
    val model: String,
    val messages: List<LocalChatMessageDto>,
    val stream: Boolean = false,
    val options: LocalChatOptions? = null,
)

@Serializable
private data class OllamaResponse(
    val message: LocalChatMessageDto,
    val done: Boolean = true,
)

private suspend fun runChat(
    client: HttpClient,
    json: Json,
    config: OllamaConfig,
    semaphore: Semaphore,
    model: String,
    query: String,
    options: LocalChatOptions,
    systemPrompt: String,
): LocalChatResponse {
    val cappedOptions = options.copy(numCtx = minOf(options.numCtx, config.maxCtx))
    val messages = buildList {
        if (systemPrompt.isNotBlank()) add(LocalChatMessageDto("system", systemPrompt))
        add(LocalChatMessageDto("user", query))
    }
    val startMs = System.currentTimeMillis()
    return semaphore.withPermit {
        val response = client.post("${config.baseUrl}/api/chat") {
            contentType(ContentType.Application.Json)
            setBody(OllamaRequest(model = model, messages = messages, options = cappedOptions))
        }
        if (!response.status.isSuccess()) {
            error("Ollama error ${response.status.value}: ${response.bodyAsText()}")
        }
        val ollamaResp = response.body<OllamaResponse>()
        LocalChatResponse(
            reply = ollamaResp.message.content,
            latencyMs = System.currentTimeMillis() - startMs,
            backend = "local ($model)",
        )
    }
}
