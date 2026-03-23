package ru.nikitaluga.aichallenge.local

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.HttpTimeout
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
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import ru.nikitaluga.aichallenge.api.RouterAiApiService

// ─── Public request/response models ─────────────────────────────────────────

@Serializable
data class LocalChatMessageDto(val role: String, val content: String)

@Serializable
data class LocalChatRequest(
    val messages: List<LocalChatMessageDto>,
    val model: String = "llama3.2:3b",
)

@Serializable
data class LocalChatResponse(
    val reply: String,
    @SerialName("latency_ms") val latencyMs: Long,
    val backend: String,
)

@Serializable
private data class ErrorResponse(val error: String)

// ─── Ollama API DTOs ─────────────────────────────────────────────────────────

@Serializable
private data class OllamaChatRequest(
    val model: String,
    val messages: List<LocalChatMessageDto>,
    val stream: Boolean = false,
)

@Serializable
private data class OllamaChatResponse(
    val message: LocalChatMessageDto,
    val done: Boolean = true,
)

// ─── Route installer ─────────────────────────────────────────────────────────

fun Application.installLocalLlmRoutes(apiService: RouterAiApiService) {

    val ollamaJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    val ollamaClient = HttpClient {
        install(ContentNegotiation) { json(ollamaJson) }
        install(HttpTimeout) {
            requestTimeoutMillis = 120_000
            connectTimeoutMillis = 10_000
        }
    }

    routing {
        route("/local") {

            // ── POST /local/chat — локальная LLM через Ollama ─────────────────
            post("/chat") {
                val req = call.receive<LocalChatRequest>()

                val startMs = System.currentTimeMillis()
                runCatching {
                    val ollamaReq = OllamaChatRequest(
                        model = req.model,
                        messages = req.messages,
                        stream = false,
                    )
                    val response = ollamaClient.post("http://localhost:11434/api/chat") {
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
