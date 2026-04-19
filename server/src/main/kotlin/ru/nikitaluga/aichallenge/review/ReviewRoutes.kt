package ru.nikitaluga.aichallenge.review

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import kotlinx.serialization.Serializable
import ru.nikitaluga.aichallenge.api.RouterAiApiService
import ru.nikitaluga.aichallenge.dev.DevDocsIndexer
import ru.nikitaluga.aichallenge.dev.DevDocsRepository
import ru.nikitaluga.aichallenge.common.ErrorResponse

@Serializable
data class ReviewRequest(
    val diff: String,
    val title: String = "",
    val description: String = "",
    val maxDiffLength: Int = 8000,
)

@Serializable
data class ReviewResponse(
    val bugs: List<String>,
    val architecture: List<String>,
    val recommendations: List<String>,
    val summary: String,
    val model: String,
    val diffLength: Int,
)


fun Application.installReviewRoutes(
    repository: DevDocsRepository,
    indexer: DevDocsIndexer,
    apiService: RouterAiApiService,
) {
    routing {
        route("/review") {

            // POST /review/pr — анализ git diff через RAG + LLM
            post("/pr") {
                val request = runCatching { call.receive<ReviewRequest>() }.getOrElse {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("Неверный формат запроса"))
                    return@post
                }
                if (request.diff.isBlank()) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("Поле 'diff' обязательно"))
                    return@post
                }

                val result = runCatching {
                    ReviewService(repository, indexer, apiService).review(request)
                }.getOrElse { e ->
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        ErrorResponse("Ошибка анализа: ${e.message}")
                    )
                    return@post
                }

                call.respond(result)
            }
        }
    }
}
