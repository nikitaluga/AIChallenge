package ru.nikitaluga.aichallenge.rag

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
fun Application.installLocalRagRoutes(repository: LocalRagRepository, indexer: LocalRagIndexer) {
    routing {
        route("/rag/local") {

            // ── GET /rag/local/index/stats ────────────────────────────────────
            get("/index/stats") {
                val index = repository.load()
                call.respond(
                    LocalRagStatsResponse(
                        hasIndex = index != null,
                        chunkCount = index?.chunkCount ?: 0,
                        model = index?.model ?: "",
                        createdAt = index?.createdAt ?: "",
                    )
                )
            }

            // ── POST /rag/local/index ─────────────────────────────────────────
            post("/index") {
                val request = runCatching { call.receive<LocalRagIndexRequest>() }
                    .getOrDefault(LocalRagIndexRequest())

                val result = runCatching {
                    indexer.buildLocalIndex(
                        chunkSize = request.chunkSize.coerceIn(50, 1000),
                        overlap = request.overlap.coerceIn(0, 200),
                    )
                }.getOrElse { e ->
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        LocalRagIndexResponse(
                            success = false,
                            chunkCount = 0,
                            model = "",
                            message = "Ошибка локальной индексации: ${e.message}",
                        )
                    )
                    return@post
                }
                call.respond(result)
            }

            // ── POST /rag/local/chat ──────────────────────────────────────────
            post("/chat") {
                val request = runCatching { call.receive<LocalRagChatRequest>() }.getOrElse {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Неверный формат запроса"))
                    return@post
                }
                if (request.query.isBlank()) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Параметр 'query' обязателен"))
                    return@post
                }
                val result = runCatching {
                    indexer.chatLocal(
                        query = request.query,
                        k = request.k.coerceIn(1, 20),
                        model = request.model,
                    )
                }.getOrElse { e ->
                    call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Ошибка локального RAG: ${e.message}"))
                    return@post
                }
                call.respond(result)
            }

            // ── POST /rag/local/compare ───────────────────────────────────────
            post("/compare") {
                val request = runCatching { call.receive<LocalRagCompareRequest>() }.getOrElse {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Неверный формат запроса"))
                    return@post
                }
                if (request.query.isBlank()) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Параметр 'query' обязателен"))
                    return@post
                }
                val result = runCatching {
                    indexer.compareLocalVsCloud(
                        query = request.query,
                        k = request.k.coerceIn(1, 20),
                        localModel = request.localModel,
                    )
                }.getOrElse { e ->
                    call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Ошибка сравнения: ${e.message}"))
                    return@post
                }
                call.respond(result)
            }
        }
    }
}
