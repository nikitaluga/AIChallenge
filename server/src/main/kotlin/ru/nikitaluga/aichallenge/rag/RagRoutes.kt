package ru.nikitaluga.aichallenge.rag

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import kotlinx.serialization.Serializable

@Serializable
private data class ErrorResponse(val error: String)

fun Application.installRagRoutes(repository: RagRepository, indexer: RagIndexer) {
    routing {
        route("/rag") {

            // ── GET /rag/index/stats ─────────────────────────────────────────
            get("/index/stats") {
                val index = repository.load()
                if (index == null) {
                    call.respond(RagStatsResponse(hasIndex = false))
                    return@get
                }
                val fixed = index.chunks.filter { it.strategy == "fixed" }
                val structural = index.chunks.filter { it.strategy == "structural" }

                call.respond(
                    RagStatsResponse(
                        hasIndex = true,
                        totalChunks = index.chunks.size,
                        fixedChunks = fixed.size,
                        structuralChunks = structural.size,
                        avgFixedSize = index.strategies["fixed"]?.avgChunkSize ?: 0,
                        avgStructuralSize = index.strategies["structural"]?.avgChunkSize ?: 0,
                        model = index.model,
                        createdAt = index.createdAt,
                        chunkSize = index.chunkSize,
                        overlap = index.overlap,
                        sampleFixed = fixed.take(5).map { chunk ->
                            SampleChunk(
                                source = chunk.source,
                                section = chunk.section,
                                textPreview = chunk.text.take(200),
                            )
                        },
                        sampleStructural = structural.take(5).map { chunk ->
                            SampleChunk(
                                source = chunk.source,
                                section = chunk.section,
                                textPreview = chunk.text.take(200),
                            )
                        },
                    )
                )
            }

            // ── POST /rag/index ──────────────────────────────────────────────
            post("/index") {
                val request = runCatching { call.receive<RagIndexRequest>() }
                    .getOrDefault(RagIndexRequest())

                val result = runCatching {
                    indexer.buildIndex(
                        chunkSize = request.chunkSize.coerceIn(50, 1000),
                        overlap = request.overlap.coerceIn(0, 200),
                    )
                }.getOrElse { e ->
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        RagIndexResponse(
                            success = false,
                            totalChunks = 0,
                            fixedChunks = 0,
                            structuralChunks = 0,
                            message = "Ошибка индексации: ${e.message}",
                        )
                    )
                    return@post
                }
                call.respond(result)
            }

            // ── POST /rag/search ─────────────────────────────────────────────
            post("/search") {
                val request = runCatching { call.receive<RagSearchRequest>() }.getOrElse {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("Неверный формат запроса"))
                    return@post
                }
                if (request.query.isBlank()) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("Параметр 'query' обязателен"))
                    return@post
                }
                val results = runCatching {
                    indexer.search(
                        query = request.query,
                        k = request.k.coerceIn(1, 20),
                        strategy = request.strategy,
                    )
                }.getOrElse { e ->
                    call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Ошибка поиска: ${e.message}"))
                    return@post
                }
                call.respond(RagSearchResponse(results = results))
            }

            // ── POST /rag/chat ───────────────────────────────────────────────
            post("/chat") {
                val request = runCatching { call.receive<RagChatRequest>() }.getOrElse {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("Неверный формат запроса"))
                    return@post
                }
                if (request.query.isBlank()) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("Параметр 'query' обязателен"))
                    return@post
                }
                val result = runCatching {
                    indexer.buildContextAndAnswer(
                        query = request.query,
                        k = request.k.coerceIn(1, 20),
                        strategy = request.strategy,
                    )
                }.getOrElse { e ->
                    call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Ошибка RAG: ${e.message}"))
                    return@post
                }
                call.respond(result)
            }

            // ── POST /rag/compare ────────────────────────────────────────────
            // День 22: сравнение ответа с RAG и без RAG
            post("/compare") {
                val request = runCatching { call.receive<RagCompareRequest>() }.getOrElse {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("Неверный формат запроса"))
                    return@post
                }
                if (request.query.isBlank()) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("Параметр 'query' обязателен"))
                    return@post
                }
                val result = runCatching {
                    indexer.compare(
                        query = request.query,
                        k = request.k.coerceIn(1, 20),
                        strategy = request.strategy,
                    )
                }.getOrElse { e ->
                    call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Ошибка сравнения: ${e.message}"))
                    return@post
                }
                call.respond(result)
            }
        }
    }
}
