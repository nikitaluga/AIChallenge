package ru.nikitaluga.aichallenge.rag

import kotlinx.serialization.Serializable

// ── Stored in local_rag_index.json ─────────────────────────────────────────────

@Serializable
data class LocalRagIndexFile(
    val version: Int = 1,
    val model: String,
    val chunkSize: Int,
    val overlap: Int,
    val createdAt: String,
    val chunkCount: Int,
    val chunks: List<RagChunk> = emptyList(),
)

// ── HTTP request / response models ─────────────────────────────────────────────

@Serializable
data class LocalRagIndexRequest(
    val chunkSize: Int = 300,
    val overlap: Int = 50,
)

@Serializable
data class LocalRagIndexResponse(
    val success: Boolean,
    val chunkCount: Int,
    val model: String,
    val message: String,
)

@Serializable
data class LocalRagStatsResponse(
    val hasIndex: Boolean,
    val chunkCount: Int = 0,
    val model: String = "",
    val createdAt: String = "",
)

@Serializable
data class LocalRagChatRequest(
    val query: String,
    val k: Int = 5,
    val model: String = "llama3.2:3b",
)

@Serializable
data class LocalRagChatResponseDto(
    val answer: String,
    val sources: List<String> = emptyList(),
    val latencyMs: Long = 0,
)

@Serializable
data class LocalRagCompareRequest(
    val query: String,
    val k: Int = 5,
    val localModel: String = "llama3.2:3b",
)

@Serializable
data class LocalRagCompareResponse(
    val local: LocalRagChatResponseDto,
    val cloud: LocalRagChatResponseDto,
)

// ── Ollama Embed DTOs ───────────────────────────────────────────────────────────

@Serializable
internal data class OllamaEmbedRequest(
    val model: String,
    val input: List<String>,
)

@Serializable
internal data class OllamaEmbedResponse(
    val embeddings: List<List<Float>> = emptyList(),
)
