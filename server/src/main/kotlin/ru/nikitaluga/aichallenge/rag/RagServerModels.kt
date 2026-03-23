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

@Serializable
data class RagCompareRequest(
    val query: String,
    val k: Int = 5,
    val strategy: String = "structural",
)

@Serializable
data class RagCompareResponse(
    val ragAnswer: String,
    val noRagAnswer: String,
    val usedChunks: List<RagSearchResult>,
)

@Serializable
data class FilterStats(
    val candidatesBefore: Int,
    val candidatesAfter: Int,
    val threshold: Float,
    val rewrittenQuery: String?,
)

@Serializable
data class RagEnhancedCompareRequest(
    val query: String,
    val k: Int = 5,
    val strategy: String = "structural",
    val threshold: Float = 0.35f,
    val topKBefore: Int = 20,
    val rewriteQuery: Boolean = true,
)

@Serializable
data class RagEnhancedCompareResponse(
    val noRagAnswer: String,
    val ragBaselineAnswer: String,
    val ragEnhancedAnswer: String,
    val baselineChunks: List<RagSearchResult>,
    val enhancedChunks: List<RagSearchResult>,
    val filterStats: FilterStats,
)

// ── День 25: V3 — чат с историей диалога и памятью задачи ────────────────────

@Serializable
data class RagHistoryMessageV3(
    val role: String,
    val content: String,
)

@Serializable
data class TaskMemoryV3(
    val goal: String = "",
    val terms: List<String> = emptyList(),
    val constraints: List<String> = emptyList(),
)

@Serializable
data class RagChatV3Request(
    val query: String,
    val history: List<RagHistoryMessageV3> = emptyList(),
    val taskMemory: TaskMemoryV3 = TaskMemoryV3(),
    val k: Int = 5,
    val strategy: String = "structural",
    val threshold: Float = 0.35f,
)

@Serializable
data class RagChatV3Response(
    val answer: String,
    val usedChunks: List<RagSearchResult>,
    val sources: List<RagSourceV2>,
    val citations: List<RagCitationV2>,
    val belowThreshold: Boolean = false,
    val taskMemory: TaskMemoryV3,
)

// ── День 24: V2 с цитатами, источниками и анти-галлюцинациями ────────────────

@Serializable
data class RagSourceV2(
    val chunkId: String,
    val source: String,
    val section: String? = null,
)

@Serializable
data class RagCitationV2(
    val text: String,
    val chunkId: String,
)

@Serializable
data class RagChatV2Request(
    val query: String,
    val k: Int = 5,
    val strategy: String = "structural",
    val threshold: Float = 0.35f,
)

@Serializable
data class RagChatV2Response(
    val answer: String,
    val usedChunks: List<RagSearchResult>,
    val sources: List<RagSourceV2>,
    val citations: List<RagCitationV2>,
    val belowThreshold: Boolean = false,
)
