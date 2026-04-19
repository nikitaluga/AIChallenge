package ru.nikitaluga.aichallenge.data.agent

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import ru.nikitaluga.aichallenge.domain.model.ChunkingStrategy
import ru.nikitaluga.aichallenge.domain.model.FilterStats
import ru.nikitaluga.aichallenge.domain.model.RagChatResult
import ru.nikitaluga.aichallenge.domain.model.RagChatV2Result
import ru.nikitaluga.aichallenge.domain.model.RagChatV3Result
import ru.nikitaluga.aichallenge.domain.model.RagChunkResult
import ru.nikitaluga.aichallenge.domain.model.RagCitation
import ru.nikitaluga.aichallenge.domain.model.RagCompareResult
import ru.nikitaluga.aichallenge.domain.model.RagHistoryMessage
import ru.nikitaluga.aichallenge.domain.model.RagIndexStats
import ru.nikitaluga.aichallenge.domain.model.RagSource
import ru.nikitaluga.aichallenge.domain.model.RagTripleCompareResult
import ru.nikitaluga.aichallenge.domain.model.SampleChunkInfo
import ru.nikitaluga.aichallenge.domain.model.TaskMemory
import ru.nikitaluga.aichallenge.util.CommonJson
import ru.nikitaluga.aichallenge.util.AgentConfig

/**
 * День 21 — RAG Agent.
 *
 * Клиент к серверному RAG-пайплайну:
 * - POST /rag/index        — запустить индексацию
 * - GET  /rag/index/stats  — статистика текущего индекса
 * - POST /rag/search       — семантический поиск top-K чанков
 * - POST /rag/chat         — RAG-чат: поиск + LLM-ответ
 */
class RagAgent(
    private val serverBaseUrl: String = AgentConfig.DEFAULT_SERVER_URL,
) {
    private val json = CommonJson
    private val client = HttpClient {
        install(HttpTimeout) { requestTimeoutMillis = 30_000L }
        install(ContentNegotiation) { json(json) }
    }

    suspend fun getStats(): RagIndexStats {
        val response = client.get("$serverBaseUrl/rag/index/stats")
        response.requireSuccess()
        return response.body<RagStatsDto>().toDomain()
    }

    suspend fun buildIndex(chunkSize: Int, overlap: Int): String {
        val response = client.post("$serverBaseUrl/rag/index") {
            contentType(ContentType.Application.Json)
            setBody(RagIndexRequestDto(chunkSize = chunkSize, overlap = overlap))
        }
        response.requireSuccess()
        return response.body<RagIndexResponseDto>().message
    }

    suspend fun search(query: String, k: Int, strategy: ChunkingStrategy): List<RagChunkResult> {
        val response = client.post("$serverBaseUrl/rag/search") {
            contentType(ContentType.Application.Json)
            setBody(RagSearchRequestDto(query = query, k = k, strategy = strategy.key))
        }
        response.requireSuccess()
        return response.body<RagSearchResponseDto>().results.map { it.toDomain() }
    }

    suspend fun chat(query: String, k: Int, strategy: ChunkingStrategy): RagChatResult {
        val response = client.post("$serverBaseUrl/rag/chat") {
            contentType(ContentType.Application.Json)
            setBody(RagChatRequestDto(query = query, k = k, strategy = strategy.key))
        }
        response.requireSuccess()
        val dto = response.body<RagChatResponseDto>()
        return RagChatResult(
            answer = dto.answer,
            usedChunks = dto.usedChunks.map { it.toDomain() },
        )
    }

    suspend fun chatV2(
        query: String,
        k: Int = 5,
        strategy: ChunkingStrategy = ChunkingStrategy.STRUCTURAL,
        threshold: Float = 0.35f,
    ): RagChatV2Result {
        val response = client.post("$serverBaseUrl/rag/chat/v2") {
            contentType(ContentType.Application.Json)
            setBody(RagChatV2RequestDto(query = query, k = k, strategy = strategy.key, threshold = threshold))
        }
        response.requireSuccess()
        val dto = response.body<RagChatV2ResponseDto>()
        return RagChatV2Result(
            answer = dto.answer,
            usedChunks = dto.usedChunks.map { it.toDomain() },
            sources = dto.sources.map { RagSource(it.chunkId, it.source, it.section) },
            citations = dto.citations.map { RagCitation(it.text, it.chunkId) },
            belowThreshold = dto.belowThreshold,
        )
    }

    suspend fun chatV3(
        query: String,
        history: List<RagHistoryMessage>,
        taskMemory: TaskMemory,
        k: Int = 5,
        strategy: ChunkingStrategy = ChunkingStrategy.STRUCTURAL,
        threshold: Float = 0.35f,
    ): RagChatV3Result {
        val response = client.post("$serverBaseUrl/rag/chat/v3") {
            contentType(ContentType.Application.Json)
            setBody(
                RagChatV3RequestDto(
                    query = query,
                    history = history.map { RagHistoryMessageDto(it.role, it.content) },
                    taskMemory = TaskMemoryDto(taskMemory.goal, taskMemory.terms, taskMemory.constraints),
                    k = k,
                    strategy = strategy.key,
                    threshold = threshold,
                )
            )
        }
        response.requireSuccess()
        val dto = response.body<RagChatV3ResponseDto>()
        return RagChatV3Result(
            answer = dto.answer,
            usedChunks = dto.usedChunks.map { it.toDomain() },
            sources = dto.sources.map { RagSource(it.chunkId, it.source, it.section) },
            citations = dto.citations.map { RagCitation(it.text, it.chunkId) },
            belowThreshold = dto.belowThreshold,
            taskMemory = TaskMemory(dto.taskMemory.goal, dto.taskMemory.terms, dto.taskMemory.constraints),
        )
    }

    suspend fun compare(query: String, k: Int, strategy: ChunkingStrategy): RagCompareResult {
        val response = client.post("$serverBaseUrl/rag/compare") {
            contentType(ContentType.Application.Json)
            setBody(RagCompareRequestDto(query = query, k = k, strategy = strategy.key))
        }
        response.requireSuccess()
        val dto = response.body<RagCompareResponseDto>()
        return RagCompareResult(
            ragAnswer = dto.ragAnswer,
            noRagAnswer = dto.noRagAnswer,
            usedChunks = dto.usedChunks.map { it.toDomain() },
        )
    }

    suspend fun compareEnhanced(
        query: String,
        k: Int = 5,
        strategy: ChunkingStrategy = ChunkingStrategy.STRUCTURAL,
        threshold: Float = 0.35f,
        topKBefore: Int = 20,
        rewriteQuery: Boolean = true,
    ): RagTripleCompareResult {
        val response = client.post("$serverBaseUrl/rag/compare/enhanced") {
            contentType(ContentType.Application.Json)
            setBody(
                RagEnhancedCompareRequestDto(
                    query = query,
                    k = k,
                    strategy = strategy.key,
                    threshold = threshold,
                    topKBefore = topKBefore,
                    rewriteQuery = rewriteQuery,
                )
            )
        }
        response.requireSuccess()
        val dto = response.body<RagEnhancedCompareResponseDto>()
        return RagTripleCompareResult(
            noRagAnswer = dto.noRagAnswer,
            ragBaselineAnswer = dto.ragBaselineAnswer,
            ragEnhancedAnswer = dto.ragEnhancedAnswer,
            baselineChunks = dto.baselineChunks.map { it.toDomain() },
            enhancedChunks = dto.enhancedChunks.map { it.toDomain() },
            filterStats = FilterStats(
                candidatesBefore = dto.filterStats.candidatesBefore,
                candidatesAfter = dto.filterStats.candidatesAfter,
                threshold = dto.filterStats.threshold,
                rewrittenQuery = dto.filterStats.rewrittenQuery,
            ),
        )
    }

    private suspend fun HttpResponse.requireSuccess() {
        if (!status.isSuccess()) {
            val body = runCatching { bodyAsText() }.getOrDefault("")
            // Try to extract error message from {"error": "..."}
            val msg = Regex(""""error"\s*:\s*"([^"]+)"""").find(body)?.groupValues?.getOrNull(1) ?: body
            throw Exception("Сервер вернул ${status.value}: $msg")
        }
    }

    // ── DTOs ──────────────────────────────────────────────────────────────────

    @Serializable
    private data class RagIndexRequestDto(val chunkSize: Int = 300, val overlap: Int = 50)

    @Serializable
    private data class RagIndexResponseDto(
        val success: Boolean,
        val totalChunks: Int,
        val fixedChunks: Int,
        val structuralChunks: Int,
        val message: String,
    )

    @Serializable
    private data class SampleChunkDto(
        val source: String,
        val section: String? = null,
        val textPreview: String,
    )

    @Serializable
    private data class RagStatsDto(
        val hasIndex: Boolean,
        val totalChunks: Int = 0,
        val fixedChunks: Int = 0,
        val structuralChunks: Int = 0,
        val avgFixedSize: Int = 0,
        val avgStructuralSize: Int = 0,
        val model: String = "",
        val createdAt: String = "",
        val chunkSize: Int = 0,
        val overlap: Int = 0,
        val sampleFixed: List<SampleChunkDto> = emptyList(),
        val sampleStructural: List<SampleChunkDto> = emptyList(),
    ) {
        fun toDomain() = RagIndexStats(
            hasIndex = hasIndex,
            totalChunks = totalChunks,
            fixedChunks = fixedChunks,
            structuralChunks = structuralChunks,
            avgFixedSize = avgFixedSize,
            avgStructuralSize = avgStructuralSize,
            model = model,
            createdAt = createdAt,
            chunkSize = chunkSize,
            overlap = overlap,
            sampleFixed = sampleFixed.map { SampleChunkInfo(it.source, it.section, it.textPreview) },
            sampleStructural = sampleStructural.map { SampleChunkInfo(it.source, it.section, it.textPreview) },
        )
    }

    @Serializable
    private data class RagSearchRequestDto(
        val query: String,
        val k: Int = 5,
        val strategy: String = "structural",
    )

    @Serializable
    private data class RagSearchResultDto(
        val chunkId: String,
        val source: String,
        val section: String? = null,
        val strategy: String,
        val text: String,
        val score: Float,
    ) {
        fun toDomain() = RagChunkResult(
            chunkId = chunkId,
            source = source,
            section = section,
            strategy = strategy,
            text = text,
            score = score,
        )
    }

    @Serializable
    private data class RagSearchResponseDto(val results: List<RagSearchResultDto>)

    @Serializable
    private data class RagChatRequestDto(
        val query: String,
        val k: Int = 5,
        val strategy: String = "structural",
    )

    @Serializable
    private data class RagChatResponseDto(
        val answer: String,
        @SerialName("usedChunks") val usedChunks: List<RagSearchResultDto> = emptyList(),
    )

    @Serializable
    private data class RagChatV2RequestDto(
        val query: String,
        val k: Int = 5,
        val strategy: String = "structural",
        val threshold: Float = 0.35f,
    )

    @Serializable
    private data class RagSourceV2Dto(
        val chunkId: String,
        val source: String,
        val section: String? = null,
    )

    @Serializable
    private data class RagCitationV2Dto(
        val text: String,
        val chunkId: String,
    )

    @Serializable
    private data class RagChatV2ResponseDto(
        val answer: String,
        val usedChunks: List<RagSearchResultDto> = emptyList(),
        val sources: List<RagSourceV2Dto> = emptyList(),
        val citations: List<RagCitationV2Dto> = emptyList(),
        val belowThreshold: Boolean = false,
    )

    @Serializable
    private data class RagCompareRequestDto(
        val query: String,
        val k: Int = 5,
        val strategy: String = "structural",
    )

    @Serializable
    private data class RagCompareResponseDto(
        val ragAnswer: String,
        val noRagAnswer: String,
        val usedChunks: List<RagSearchResultDto> = emptyList(),
    )

    @Serializable
    private data class RagHistoryMessageDto(val role: String, val content: String)

    @Serializable
    private data class TaskMemoryDto(
        val goal: String = "",
        val terms: List<String> = emptyList(),
        val constraints: List<String> = emptyList(),
    )

    @Serializable
    private data class RagChatV3RequestDto(
        val query: String,
        val history: List<RagHistoryMessageDto> = emptyList(),
        val taskMemory: TaskMemoryDto = TaskMemoryDto(),
        val k: Int = 5,
        val strategy: String = "structural",
        val threshold: Float = 0.35f,
    )

    @Serializable
    private data class RagChatV3ResponseDto(
        val answer: String,
        val usedChunks: List<RagSearchResultDto> = emptyList(),
        val sources: List<RagSourceV2Dto> = emptyList(),
        val citations: List<RagCitationV2Dto> = emptyList(),
        val belowThreshold: Boolean = false,
        val taskMemory: TaskMemoryDto = TaskMemoryDto(),
    )

    @Serializable
    private data class RagEnhancedCompareRequestDto(
        val query: String,
        val k: Int = 5,
        val strategy: String = "structural",
        val threshold: Float = 0.35f,
        val topKBefore: Int = 20,
        val rewriteQuery: Boolean = true,
    )

    @Serializable
    private data class FilterStatsDto(
        val candidatesBefore: Int,
        val candidatesAfter: Int,
        val threshold: Float,
        val rewrittenQuery: String? = null,
    )

    @Serializable
    private data class RagEnhancedCompareResponseDto(
        val noRagAnswer: String,
        val ragBaselineAnswer: String,
        val ragEnhancedAnswer: String,
        val baselineChunks: List<RagSearchResultDto> = emptyList(),
        val enhancedChunks: List<RagSearchResultDto> = emptyList(),
        val filterStats: FilterStatsDto,
    )
}
