package ru.nikitaluga.aichallenge.reflection

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import ru.nikitaluga.aichallenge.api.ChatMessage
import ru.nikitaluga.aichallenge.api.RouterAiApiService
import ru.nikitaluga.aichallenge.common.ErrorResponse

// ── HTTP models ───────────────────────────────────────────────────────────────

@Serializable
data class ReflectionRequest(
    val query: String,
    val rubric: String? = null,
    val maxIterations: Int = 3,
)

@Serializable
data class IterationResult(
    val attempt: Int,
    val draft: String,
    val critique: String,
    val score: Int,
)

@Serializable
data class ReflectionResponse(
    val answer: String,
    val totalIterations: Int,
    val iterations: List<IterationResult>,
)


// ── Route installer ───────────────────────────────────────────────────────────

fun Application.installReflectionRoutes(apiService: RouterAiApiService) {
    routing {
        route("/reflection") {
            post("/chat") {
                val request = runCatching { call.receive<ReflectionRequest>() }.getOrElse {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("Неверный формат запроса"))
                    return@post
                }
                if (request.query.isBlank()) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("Параметр 'query' обязателен"))
                    return@post
                }
                val sanitized = request.copy(maxIterations = request.maxIterations.coerceIn(1, 5))
                val result = runCatching { runReflectionLoop(sanitized, apiService) }.getOrElse { e ->
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

private suspend fun runReflectionLoop(
    request: ReflectionRequest,
    apiService: RouterAiApiService,
): ReflectionResponse {
    val rubricDesc = request.rubric?.takeIf { it.isNotBlank() }
        ?: "полнота, точность и ясность ответа"

    var currentDraft = generateDraft(request.query, rubricDesc, apiService)
    val iterations = mutableListOf<IterationResult>()

    for (i in 0 until request.maxIterations) {
        val critique = critiqueAndImprove(
            query = request.query,
            draft = currentDraft,
            rubric = rubricDesc,
            apiService = apiService,
        )
        iterations += IterationResult(
            attempt = i + 1,
            draft = currentDraft,
            critique = critique.critique,
            score = critique.score,
        )
        if (critique.score >= 4) break
        currentDraft = critique.improved
    }

    return ReflectionResponse(
        answer = currentDraft,
        totalIterations = iterations.size,
        iterations = iterations,
    )
}

private suspend fun generateDraft(query: String, rubric: String, apiService: RouterAiApiService): String {
    val result = apiService.sendMessages(
        messages = listOf(
            ChatMessage(role = "system", content = "Ты — полезный ассистент. Отвечай развёрнуто и точно. Критерий хорошего ответа: $rubric"),
            ChatMessage(role = "user", content = query.take(2000)),
        ),
        model = CHAT_MODEL,
    )
    return result.content ?: ""
}

private data class CritiqueResult(val critique: String, val score: Int, val improved: String)

private suspend fun critiqueAndImprove(
    query: String,
    draft: String,
    rubric: String,
    apiService: RouterAiApiService,
): CritiqueResult {
    val systemPrompt = """
        Ты — строгий рецензент. Оцени черновик ответа по критерию: $rubric.

        Верни JSON строго в формате (без markdown, только JSON):
        {"score": <число от 1 до 5>, "critique": "<краткая критика, 1-2 предложения>", "improved": "<улучшенная версия ответа>"}

        Правила:
        - score 5 = идеально по критерию, score 1 = плохо
        - critique — конкретные замечания что именно не так
        - improved — полный улучшенный ответ, устраняющий замечания
    """.trimIndent()

    val userPrompt = "Вопрос: ${query.take(2000)}\n\nЧерновик:\n${draft.take(3000)}"

    val result = apiService.sendMessages(
        messages = listOf(
            ChatMessage(role = "system", content = systemPrompt),
            ChatMessage(role = "user", content = userPrompt),
        ),
        model = CHAT_MODEL,
    )

    return parseCritique(result.content ?: "")
}

// ── Parsing ───────────────────────────────────────────────────────────────────

private fun parseCritique(raw: String): CritiqueResult {
    val cleaned = raw.trim()
        .removePrefix("```json").removePrefix("```")
        .removeSuffix("```").trim()
    return runCatching {
        val obj = json.parseToJsonElement(cleaned).jsonObject
        CritiqueResult(
            score = obj["score"]?.jsonPrimitive?.int ?: 3,
            critique = obj["critique"]?.jsonPrimitive?.content ?: "",
            improved = obj["improved"]?.jsonPrimitive?.content ?: cleaned,
        )
    }.getOrElse {
        CritiqueResult(score = 3, critique = "Не удалось разобрать оценку", improved = cleaned)
    }
}
