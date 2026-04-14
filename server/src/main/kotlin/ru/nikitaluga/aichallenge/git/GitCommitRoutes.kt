package ru.nikitaluga.aichallenge.git

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import ru.nikitaluga.aichallenge.api.ChatMessage
import ru.nikitaluga.aichallenge.api.RouterAiApiService

// ── HTTP models ───────────────────────────────────────────────────────────────

@Serializable
data class GitCommitRequest(
    val diff: String,
    val context: String? = null,
)

@Serializable
data class GitCommitResponse(
    val message: String,
    val alternatives: List<String> = emptyList(),
)

@Serializable
private data class ErrorResponse(val error: String)

// ── Route installer ───────────────────────────────────────────────────────────

fun Application.installGitCommitRoutes(apiService: RouterAiApiService) {
    routing {
        route("/git") {
            post("/commit") {
                val request = runCatching { call.receive<GitCommitRequest>() }.getOrElse {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("Неверный формат запроса"))
                    return@post
                }
                if (request.diff.isBlank()) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("Параметр 'diff' обязателен"))
                    return@post
                }
                val result = runCatching { buildCommitMessage(request, apiService) }.getOrElse { e ->
                    call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Ошибка: ${e.message}"))
                    return@post
                }
                call.respond(result)
            }
        }
    }
}

// ── Business logic ────────────────────────────────────────────────────────────

private const val CHAT_MODEL = "openai/gpt-4o-mini"

private val json = Json { ignoreUnknownKeys = true }

private suspend fun buildCommitMessage(
    request: GitCommitRequest,
    apiService: RouterAiApiService,
): GitCommitResponse {
    val contextHint = if (!request.context.isNullOrBlank()) "\nДополнительный контекст: ${request.context}" else ""

    val systemPrompt = """
        Ты — эксперт по git commit messages в стиле Conventional Commits.

        Формат: <type>(<scope>): <description>
        Типы: feat, fix, refactor, docs, test, chore, style, perf

        Правила:
        - Описание на английском языке, нижний регистр
        - Без точки в конце
        - Максимум 72 символа
        - Описание должно отражать ЧТО изменилось и ЗАЧЕМ

        Верни ответ строго в JSON:
        {"message": "основной вариант", "alternatives": ["вариант 2", "вариант 3"]}

        Без markdown, только JSON.
    """.trimIndent()

    val userPrompt = "Diff:\n```\n${request.diff.take(3000)}\n```$contextHint"

    val result = apiService.sendMessages(
        messages = listOf(
            ChatMessage(role = "system", content = systemPrompt),
            ChatMessage(role = "user", content = userPrompt),
        ),
        model = CHAT_MODEL,
    )

    return parseCommitResponse(result.content ?: "")
}

private fun parseCommitResponse(raw: String): GitCommitResponse {
    val cleaned = raw.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
    return runCatching {
        val obj = json.parseToJsonElement(cleaned).jsonObject
        val message = obj["message"]?.jsonPrimitive?.content ?: cleaned
        val alternatives = obj["alternatives"]?.jsonArray
            ?.mapNotNull { it.jsonPrimitive.content }
            ?: emptyList()
        GitCommitResponse(message = message, alternatives = alternatives)
    }.getOrElse {
        GitCommitResponse(message = cleaned.lines().firstOrNull()?.trim() ?: cleaned)
    }
}
