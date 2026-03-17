package ru.nikitaluga.aichallenge.rag

import kotlinx.serialization.Serializable

// ── Stored in rag_index.json ───────────────────────────────────────────────────

@Serializable
data class RagChunk(
    val chunkId: String,
    val source: String,
    val section: String? = null,
    val strategy: String, // "fixed" or "structural"
    val text: String,
    val embedding: List<Float> = emptyList(),
)

@Serializable
data class StrategyStats(
    val chunkCount: Int,
    val avgChunkSize: Int,
)

@Serializable
data class RagIndexFile(
    val version: Int = 1,
    val model: String,
    val chunkSize: Int,
    val overlap: Int,
    val createdAt: String,
    val strategies: Map<String, StrategyStats> = emptyMap(),
    val chunks: List<RagChunk> = emptyList(),
)

// ── HTTP request / response models ────────────────────────────────────────────

@Serializable
data class RagIndexRequest(
    val chunkSize: Int = 300,
    val overlap: Int = 50,
)

@Serializable
data class SampleChunk(
    val source: String,
    val section: String? = null,
    val textPreview: String,
)

@Serializable
data class RagStatsResponse(
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
    val sampleFixed: List<SampleChunk> = emptyList(),
    val sampleStructural: List<SampleChunk> = emptyList(),
)

@Serializable
data class RagIndexResponse(
    val success: Boolean,
    val totalChunks: Int,
    val fixedChunks: Int,
    val structuralChunks: Int,
    val message: String,
)

@Serializable
data class RagSearchRequest(
    val query: String,
    val k: Int = 5,
    val strategy: String = "structural", // "fixed", "structural", or "all"
)

@Serializable
data class RagSearchResult(
    val chunkId: String,
    val source: String,
    val section: String? = null,
    val strategy: String,
    val text: String,
    val score: Float,
)

@Serializable
data class RagSearchResponse(
    val results: List<RagSearchResult>,
)

@Serializable
data class RagChatRequest(
    val query: String,
    val k: Int = 5,
    val strategy: String = "structural",
)

@Serializable
data class RagChatResponse(
    val answer: String,
    val usedChunks: List<RagSearchResult>,
)
