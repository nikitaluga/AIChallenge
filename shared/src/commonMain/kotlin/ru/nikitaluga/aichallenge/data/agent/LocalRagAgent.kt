package ru.nikitaluga.aichallenge.data.agent

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import ru.nikitaluga.aichallenge.domain.model.LocalRagChatResult
import ru.nikitaluga.aichallenge.domain.model.LocalRagCompareResult
import ru.nikitaluga.aichallenge.domain.model.LocalRagStats
import ru.nikitaluga.aichallenge.util.CommonJson

// День 28 — LocalRagAgent.
// Клиент к эндпоинтам /rag/local/ для полностью локального RAG-пайплайна.
// GET  /rag/local/index/stats  — статистика локального индекса
// POST /rag/local/index        — построить локальный индекс (nomic-embed-text)
// POST /rag/local/chat         — RAG-чат через локальную LLM
// POST /rag/local/compare      — сравнение: локальный vs облачный RAG
class LocalRagAgent(
    private val serverBaseUrl: String = "http://10.0.2.2:8080",
) {

    private val json = CommonJson
    private val client = HttpClient {
        install(ContentNegotiation) { json(json) }
        install(HttpTimeout) {
            requestTimeoutMillis = 300_000
            connectTimeoutMillis = 10_000
        }
    }

    suspend fun getStats(): LocalRagStats {
        val response = client.get("$serverBaseUrl/rag/local/index/stats")
        if (!response.status.isSuccess()) error("Ошибка: ${response.status.value}")
        return response.body<LocalRagStatsDto>().toDomain()
    }

    suspend fun buildLocalIndex(chunkSize: Int = 300, overlap: Int = 50): String {
        val response = client.post("$serverBaseUrl/rag/local/index") {
            contentType(ContentType.Application.Json)
            setBody(LocalRagIndexRequestDto(chunkSize = chunkSize, overlap = overlap))
        }
        if (!response.status.isSuccess()) error("Ошибка индексации: ${response.bodyAsText()}")
        return response.body<LocalRagIndexResponseDto>().message
    }

    suspend fun chatLocal(query: String, k: Int = 5, model: String = "llama3.2:3b"): LocalRagChatResult {
        val response = client.post("$serverBaseUrl/rag/local/chat") {
            contentType(ContentType.Application.Json)
            setBody(LocalRagChatRequestDto(query = query, k = k, model = model))
        }
        if (!response.status.isSuccess()) error("Ошибка: ${response.bodyAsText()}")
        return response.body<LocalRagChatResponseDto>().toDomain()
    }

    suspend fun compareLocalVsCloud(
        query: String,
        k: Int = 5,
        localModel: String = "llama3.2:3b",
    ): LocalRagCompareResult {
        val response = client.post("$serverBaseUrl/rag/local/compare") {
            contentType(ContentType.Application.Json)
            setBody(LocalRagCompareRequestDto(query = query, k = k, localModel = localModel))
        }
        if (!response.status.isSuccess()) error("Ошибка сравнения: ${response.bodyAsText()}")
        return response.body<LocalRagCompareResponseDto>().toDomain()
    }
}

// ─── DTOs ────────────────────────────────────────────────────────────────────

@Serializable
private data class LocalRagStatsDto(
    val hasIndex: Boolean,
    val chunkCount: Int = 0,
    val model: String = "",
    val createdAt: String = "",
) {
    fun toDomain() = LocalRagStats(hasIndex = hasIndex, chunkCount = chunkCount, model = model, createdAt = createdAt)
}

@Serializable
private data class LocalRagIndexRequestDto(val chunkSize: Int = 300, val overlap: Int = 50)

@Serializable
private data class LocalRagIndexResponseDto(val success: Boolean, val chunkCount: Int, val model: String, val message: String)

@Serializable
private data class LocalRagChatRequestDto(val query: String, val k: Int = 5, val model: String = "llama3.2:3b")

@Serializable
private data class LocalRagChatResponseDto(
    val answer: String,
    val sources: List<String> = emptyList(),
    val latencyMs: Long = 0,
) {
    fun toDomain() = LocalRagChatResult(answer = answer, sources = sources, latencyMs = latencyMs)
}

@Serializable
private data class LocalRagCompareRequestDto(val query: String, val k: Int = 5, val localModel: String = "llama3.2:3b")

@Serializable
private data class LocalRagCompareResponseDto(
    val local: LocalRagChatResponseDto,
    val cloud: LocalRagChatResponseDto,
) {
    fun toDomain() = LocalRagCompareResult(local = local.toDomain(), cloud = cloud.toDomain())
}
