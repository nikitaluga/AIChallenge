package ru.nikitaluga.aichallenge.files

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.serialization.Serializable
import ru.nikitaluga.aichallenge.api.RouterAiApiService
import ru.nikitaluga.aichallenge.common.ErrorResponse

// ── HTTP models ───────────────────────────────────────────────────────────────

@Serializable
data class FilesChatRequest(
    val query: String,
    val history: List<FilesChatHistoryMessage> = emptyList(),
    val useDryRun: Boolean = true,
)

@Serializable
data class FilesChatHistoryMessage(val role: String, val content: String)

@Serializable
data class FilesChatResponse(
    val answer: String,
    val toolsUsed: List<String> = emptyList(),
    val diffs: List<FileDiff> = emptyList(),
)

@Serializable
data class FileDiff(val path: String, val diff: String)


// ── Route installer ───────────────────────────────────────────────────────────

fun Application.installFilesAssistantRoutes(apiService: RouterAiApiService) {
    routing {
        post("/files/chat") {
            val request = runCatching { call.receive<FilesChatRequest>() }.getOrElse {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("Неверный формат запроса"))
                return@post
            }
            if (request.query.isBlank()) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("Параметр 'query' обязателен"))
                return@post
            }

            val result = runCatching {
                runFilesToolLoop(request, apiService)
            }.getOrElse { e ->
                call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Ошибка: ${e.message}"))
                return@post
            }

            call.respond(result)
        }
    }
}
