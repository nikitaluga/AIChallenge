package ru.nikitaluga.aichallenge.local

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.DefaultRequest
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondTextWriter
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.utils.io.readUTF8Line
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import ru.nikitaluga.aichallenge.api.RouterAiApiService
import ru.nikitaluga.aichallenge.common.ErrorResponse

// ─── Public request/response models ─────────────────────────────────────────

@Serializable
data class LocalChatMessageDto(val role: String, val content: String)

@Serializable
data class LocalChatRequest(
    val messages: List<LocalChatMessageDto>,
    val model: String = "llama3.2:3b",
    val options: LocalChatOptions? = null,
    @SerialName("system_prompt") val systemPrompt: String? = null,
)

@Serializable
data class LocalChatResponse(
    val reply: String,
    @SerialName("latency_ms") val latencyMs: Long,
    val backend: String,
)


// ─── Generation options (shared with LocalOptimizationRoutes) ────────────────

@Serializable
data class LocalChatOptions(
    val temperature: Float = 0.8f,
    @SerialName("top_p") val topP: Float = 0.9f,
    @SerialName("top_k") val topK: Int = 40,
    @SerialName("num_predict") val numPredict: Int = -1,
    @SerialName("num_ctx") val numCtx: Int = 2048,
)

// ─── Ollama API DTOs ─────────────────────────────────────────────────────────

@Serializable
private data class OllamaChatRequest(
    val model: String,
    val messages: List<LocalChatMessageDto>,
    val stream: Boolean = false,
    val options: LocalChatOptions? = null,
)

@Serializable
private data class OllamaChatResponse(
    val message: LocalChatMessageDto,
    val done: Boolean = true,
)

@Serializable
private data class OllamaStreamChunk(
    val message: LocalChatMessageDto,
    val done: Boolean = false,
)

@Serializable
private data class OllamaTagsResponse(
    val models: List<OllamaModelInfo> = emptyList(),
)

@Serializable
private data class OllamaModelInfo(
    val name: String,
)

@Serializable
private data class HealthResponse(
    val status: String,
    @SerialName("ollama_url") val ollamaUrl: String,
    val models: List<String>,
    @SerialName("active_requests") val activeRequests: Int,
    @SerialName("max_concurrent") val maxConcurrent: Int,
)

// ─── Route installer ─────────────────────────────────────────────────────────

fun Application.installLocalLlmRoutes(apiService: RouterAiApiService, config: OllamaConfig = OllamaConfig()) {

    val semaphore = Semaphore(config.maxConcurrent)
    val ollamaJson = Json { ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = false }
    val ollamaClient = HttpClient {
        install(ContentNegotiation) { json(ollamaJson) }
        install(HttpTimeout) {
            requestTimeoutMillis = 120_000
            connectTimeoutMillis = 10_000
        }
        install(DefaultRequest) {
            header("ngrok-skip-browser-warning", "true")
        }
    }

    routing {
        route("/local") {

            // ── GET /local/health — статус сервиса ────────────────────────────
            get("/health") {
                val (status, models) = runCatching {
                    val response = ollamaClient.get("${config.baseUrl}/api/tags")
                    if (!response.status.isSuccess()) error("Ollama error ${response.status.value}")
                    "ok" to response.body<OllamaTagsResponse>().models.map { it.name }
                }.getOrElse { "unavailable" to emptyList() }

                call.respond(
                    HealthResponse(
                        status = status,
                        ollamaUrl = config.baseUrl,
                        models = models,
                        activeRequests = config.maxConcurrent - semaphore.availablePermits,
                        maxConcurrent = config.maxConcurrent,
                    )
                )
            }

            // ── POST /local/chat — локальная LLM через Ollama ─────────────────
            post("/chat") {
                val req = call.receive<LocalChatRequest>()
                val cappedOptions = (req.options ?: LocalChatOptions()).let {
                    it.copy(numCtx = minOf(it.numCtx, config.maxCtx))
                }
                val cappedMessages = req.messages.takeLast(config.maxHistoryMessages)

                val startMs = System.currentTimeMillis()
                semaphore.withPermit {
                    runCatching {
                        val messages = buildList {
                            if (!req.systemPrompt.isNullOrBlank())
                                add(LocalChatMessageDto("system", req.systemPrompt))
                            addAll(cappedMessages)
                        }
                        val ollamaReq = OllamaChatRequest(
                            model = req.model,
                            messages = messages,
                            stream = false,
                            options = cappedOptions,
                        )
                        val response = ollamaClient.post("${config.baseUrl}/api/chat") {
                            contentType(ContentType.Application.Json)
                            setBody(ollamaReq)
                        }
                        if (!response.status.isSuccess()) {
                            val body = response.bodyAsText()
                            error("Ollama error ${response.status.value}: $body")
                        }
                        val ollamaResp = response.body<OllamaChatResponse>()
                        val latencyMs = System.currentTimeMillis() - startMs
                        LocalChatResponse(
                            reply = ollamaResp.message.content,
                            latencyMs = latencyMs,
                            backend = "local (${req.model})",
                        )
                    }.onSuccess { resp ->
                        call.respond(resp)
                    }.onFailure { e ->
                        call.respond(
                            HttpStatusCode.ServiceUnavailable,
                            ErrorResponse("Ollama недоступен: ${e.message}. Убедитесь, что `ollama serve` запущен."),
                        )
                    }
                }
            }

            // ── GET /local/models — список установленных Ollama-моделей ─────────
            get("/models") {
                runCatching {
                    val response = ollamaClient.get("${config.baseUrl}/api/tags")
                    if (!response.status.isSuccess()) {
                        error("Ollama error ${response.status.value}: ${response.bodyAsText()}")
                    }
                    response.body<OllamaTagsResponse>().models.map { it.name }
                }.onSuccess { models ->
                    call.respond(models)
                }.onFailure { e ->
                    call.respond(
                        HttpStatusCode.ServiceUnavailable,
                        ErrorResponse("Ollama недоступен: ${e.message}"),
                    )
                }
            }

            // ── POST /local/stream — стриминг через Ollama (SSE) ─────────────
            post("/stream") {
                val req = call.receive<LocalChatRequest>()
                val cappedOptions = (req.options ?: LocalChatOptions()).let {
                    it.copy(numCtx = minOf(it.numCtx, config.maxCtx))
                }
                val cappedMessages = req.messages.takeLast(config.maxHistoryMessages)

                call.respondTextWriter(contentType = ContentType("text", "event-stream")) {
                    semaphore.withPermit {
                        runCatching {
                            val messages = buildList {
                                if (!req.systemPrompt.isNullOrBlank())
                                    add(LocalChatMessageDto("system", req.systemPrompt))
                                addAll(cappedMessages)
                            }
                            val ollamaReq = OllamaChatRequest(
                                model = req.model,
                                messages = messages,
                                stream = true,
                                options = cappedOptions,
                            )
                            val response = ollamaClient.post("${config.baseUrl}/api/chat") {
                                contentType(ContentType.Application.Json)
                                setBody(ollamaReq)
                            }
                            if (!response.status.isSuccess()) {
                                val body = response.bodyAsText()
                                error("Ollama error ${response.status.value}: $body")
                            }
                            val channel = response.bodyAsChannel()
                            while (!channel.isClosedForRead) {
                                val line = channel.readUTF8Line() ?: break
                                if (line.isBlank()) continue
                                val chunk = ollamaJson.decodeFromString<OllamaStreamChunk>(line)
                                if (chunk.message.content.isNotEmpty()) {
                                    write("data: ${ollamaJson.encodeToString(chunk.message.content)}\n\n")
                                    flush()
                                }
                                if (chunk.done) break
                            }
                            write("data: [DONE]\n\n")
                            flush()
                        }.onFailure { e ->
                            write("data: [ERROR] ${e.message}\n\n")
                            flush()
                        }
                    }
                }
            }

            // ── POST /local/cloud — облачная LLM через routerai.ru ────────────
            post("/cloud") {
                val req = call.receive<LocalChatRequest>()

                val startMs = System.currentTimeMillis()
                runCatching {
                    val messages = req.messages.map {
                        ru.nikitaluga.aichallenge.api.ChatMessage(role = it.role, content = it.content)
                    }
                    val result = apiService.sendMessages(
                        messages = messages,
                        model = "openai/gpt-4o-mini",
                    )
                    val latencyMs = System.currentTimeMillis() - startMs
                    LocalChatResponse(
                        reply = result.content,
                        latencyMs = latencyMs,
                        backend = "cloud (gpt-4o-mini)",
                    )
                }.onSuccess { resp ->
                    call.respond(resp)
                }.onFailure { e ->
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        ErrorResponse("Cloud API error: ${e.message}"),
                    )
                }
            }
        }
    }
}
