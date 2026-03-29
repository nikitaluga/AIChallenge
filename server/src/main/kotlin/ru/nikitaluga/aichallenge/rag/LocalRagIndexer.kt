package ru.nikitaluga.aichallenge.rag

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import ru.nikitaluga.aichallenge.api.RouterAiApiService
import java.time.Instant
import kotlin.math.sqrt

private const val LOCAL_EMBEDDING_MODEL = "nomic-embed-text"
private const val OLLAMA_BASE = "http://localhost:11434"
private const val EMBED_BATCH_SIZE = 50

class LocalRagIndexer(
    private val repository: LocalRagRepository,
    private val ragIndexer: RagIndexer,
    @Suppress("UnusedPrivateProperty")
    private val apiService: RouterAiApiService,
) {

    private val ollamaJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val ollamaClient = HttpClient {
        install(ContentNegotiation) { json(ollamaJson) }
        install(HttpTimeout) {
            requestTimeoutMillis = 300_000
            connectTimeoutMillis = 10_000
        }
    }

    // ── Public API ───────────────────────────────────────────────────────────

    suspend fun buildLocalIndex(chunkSize: Int = 300, overlap: Int = 50): LocalRagIndexResponse =
        withContext(Dispatchers.IO) {
            val rawChunks = ragIndexer.buildRawChunks(chunkSize, overlap)
            val texts = rawChunks.map { it.text }
            val embeddings = embedBatch(texts)

            val embeddedChunks = rawChunks.mapIndexed { i, chunk ->
                chunk.copy(embedding = embeddings.getOrElse(i) { emptyList() })
            }

            repository.save(
                LocalRagIndexFile(
                    model = LOCAL_EMBEDDING_MODEL,
                    chunkSize = chunkSize,
                    overlap = overlap,
                    createdAt = Instant.now().toString(),
                    chunkCount = embeddedChunks.size,
                    chunks = embeddedChunks,
                )
            )

            LocalRagIndexResponse(
                success = true,
                chunkCount = embeddedChunks.size,
                model = LOCAL_EMBEDDING_MODEL,
                message = "Локальный индекс построен: ${embeddedChunks.size} чанков ($LOCAL_EMBEDDING_MODEL)",
            )
        }

    suspend fun searchLocal(query: String, k: Int): List<RagSearchResult> {
        val index = repository.load() ?: return emptyList()
        val queryEmbedding = embedBatch(listOf(query)).firstOrNull() ?: return emptyList()
        return index.chunks
            .map { chunk -> chunk to cosineSimilarity(queryEmbedding, chunk.embedding) }
            .sortedByDescending { it.second }
            .take(k)
            .map { (chunk, score) ->
                RagSearchResult(
                    chunkId = chunk.chunkId,
                    source = chunk.source,
                    section = chunk.section,
                    strategy = chunk.strategy,
                    text = chunk.text,
                    score = score,
                )
            }
    }

    suspend fun chatLocal(
        query: String,
        k: Int = 5,
        model: String = "llama3.2:3b",
    ): LocalRagChatResponseDto {
        val start = System.currentTimeMillis()
        val chunks = searchLocal(query, k)

        val systemPrompt = if (chunks.isEmpty()) {
            "Ты полезный ассистент. Отвечай на русском языке."
        } else {
            val context = chunks.joinToString("\n\n---\n\n") { chunk ->
                buildString {
                    append("Источник: ${chunk.source}")
                    if (chunk.section != null) append(" / ${chunk.section}")
                    append("\n${chunk.text}")
                }
            }
            "Ты помощник по документации проекта.\nИспользуй только предоставленный контекст для ответа.\nЕсли ответа нет в контексте — скажи об этом честно.\n\nКОНТЕКСТ:\n$context"
        }

        val answer = ollamaChat(
            messages = listOf(
                OllamaMsgDto(role = "system", content = systemPrompt),
                OllamaMsgDto(role = "user", content = query),
            ),
            model = model,
        )

        return LocalRagChatResponseDto(
            answer = answer,
            sources = chunks.map { it.source }.distinct(),
            latencyMs = System.currentTimeMillis() - start,
        )
    }

    suspend fun compareLocalVsCloud(
        query: String,
        k: Int = 5,
        localModel: String = "llama3.2:3b",
    ): LocalRagCompareResponse = coroutineScope {
        val localDeferred = async {
            val start = System.currentTimeMillis()
            runCatching { chatLocal(query, k, localModel) }.getOrElse { e ->
                LocalRagChatResponseDto(
                    answer = "Ошибка локальной LLM: ${e.message}",
                    latencyMs = System.currentTimeMillis() - start,
                )
            }
        }
        val cloudDeferred = async {
            val start = System.currentTimeMillis()
            runCatching {
                val result = ragIndexer.buildContextAndAnswer(query, k, "structural")
                LocalRagChatResponseDto(
                    answer = result.answer,
                    sources = result.usedChunks.map { it.source }.distinct(),
                    latencyMs = System.currentTimeMillis() - start,
                )
            }.getOrElse { e ->
                LocalRagChatResponseDto(
                    answer = "Ошибка облачной LLM: ${e.message}",
                    latencyMs = System.currentTimeMillis() - start,
                )
            }
        }
        LocalRagCompareResponse(local = localDeferred.await(), cloud = cloudDeferred.await())
    }

    // ── Ollama helpers ───────────────────────────────────────────────────────

    private suspend fun embedBatch(texts: List<String>): List<List<Float>> {
        if (texts.isEmpty()) return emptyList()
        val result = mutableListOf<List<Float>>()
        texts.chunked(EMBED_BATCH_SIZE).forEach { batch ->
            runCatching {
                val response = ollamaClient.post("$OLLAMA_BASE/api/embed") {
                    contentType(ContentType.Application.Json)
                    setBody(OllamaEmbedRequest(model = LOCAL_EMBEDDING_MODEL, input = batch))
                }
                if (response.status.isSuccess()) {
                    result.addAll(response.body<OllamaEmbedResponse>().embeddings)
                } else {
                    repeat(batch.size) { result.add(emptyList()) }
                }
            }.onFailure {
                repeat(batch.size) { result.add(emptyList()) }
            }
        }
        return result
    }

    private suspend fun ollamaChat(messages: List<OllamaMsgDto>, model: String): String {
        val response = ollamaClient.post("$OLLAMA_BASE/api/chat") {
            contentType(ContentType.Application.Json)
            setBody(OllamaChatReqDto(model = model, messages = messages, stream = false))
        }
        if (!response.status.isSuccess()) {
            error("Ollama chat error ${response.status.value}")
        }
        return response.body<OllamaChatRespDto>().message.content
    }

    // ── Math ─────────────────────────────────────────────────────────────────

    private fun cosineSimilarity(a: List<Float>, b: List<Float>): Float {
        if (a.isEmpty() || b.isEmpty() || a.size != b.size) return 0f
        var dot = 0.0
        var normA = 0.0
        var normB = 0.0
        for (i in a.indices) {
            dot += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }
        val denom = sqrt(normA) * sqrt(normB)
        return if (denom == 0.0) 0f else (dot / denom).toFloat()
    }
}

// ── Ollama DTOs ──────────────────────────────────────────────────────────────

@Serializable
private data class OllamaMsgDto(val role: String, val content: String)

@Serializable
private data class OllamaChatReqDto(
    val model: String,
    val messages: List<OllamaMsgDto>,
    val stream: Boolean = false,
)

@Serializable
private data class OllamaChatRespDto(val message: OllamaMsgDto, val done: Boolean = true)
