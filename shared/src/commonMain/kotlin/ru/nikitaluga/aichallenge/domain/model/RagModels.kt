package ru.nikitaluga.aichallenge.domain.model

import androidx.compose.runtime.Immutable

enum class ChunkingStrategy(val key: String, val displayName: String) {
    FIXED("fixed", "Fixed"),
    STRUCTURAL("structural", "Structural"),
}

@Immutable
data class RagChunkResult(
    val chunkId: String,
    val source: String,
    val section: String?,
    val strategy: String,
    val text: String,
    val score: Float,
)

@Immutable
data class RagChatResult(
    val answer: String,
    val usedChunks: List<RagChunkResult>,
)

@Immutable
data class SampleChunkInfo(
    val source: String,
    val section: String?,
    val textPreview: String,
)

@Immutable
data class RagCompareResult(
    val ragAnswer: String,
    val noRagAnswer: String,
    val usedChunks: List<RagChunkResult>,
)

@Immutable
data class RagIndexStats(
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
    val sampleFixed: List<SampleChunkInfo> = emptyList(),
    val sampleStructural: List<SampleChunkInfo> = emptyList(),
)
