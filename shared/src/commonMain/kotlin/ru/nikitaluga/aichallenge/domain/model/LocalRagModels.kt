package ru.nikitaluga.aichallenge.domain.model

import androidx.compose.runtime.Immutable

@Immutable
data class LocalRagChatResult(
    val answer: String,
    val sources: List<String> = emptyList(),
    val latencyMs: Long = 0,
)

@Immutable
data class LocalRagCompareResult(
    val local: LocalRagChatResult,
    val cloud: LocalRagChatResult,
)

@Immutable
data class LocalRagStats(
    val hasIndex: Boolean,
    val chunkCount: Int = 0,
    val model: String = "",
    val createdAt: String = "",
)
