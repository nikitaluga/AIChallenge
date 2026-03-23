package ru.nikitaluga.aichallenge.domain.model

import androidx.compose.runtime.Immutable

@Immutable
data class LocalChatMessage(val role: String, val content: String)

@Immutable
data class LocalChatResult(
    val reply: String,
    val latencyMs: Long,
    val backend: String,
)
