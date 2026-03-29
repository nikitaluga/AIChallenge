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

@Immutable
data class OllamaOptions(
    val temperature: Float = 0.8f,
    val topP: Float = 0.9f,
    val topK: Int = 40,
    val numPredict: Int = -1,
    val numCtx: Int = 2048,
)

@Immutable
data class JudgeResult(
    val scoreA: Int,
    val scoreB: Int,
    val reasoning: String,
)

@Immutable
data class BenchmarkResult(
    val before: LocalChatResult,
    val after: LocalChatResult,
    val judgeResult: JudgeResult? = null,
)

@Immutable
data class ServiceHealth(
    val status: String,
    val ollamaUrl: String,
    val models: List<String>,
    val activeRequests: Int,
    val maxConcurrent: Int,
) {
    val isOk: Boolean get() = status == "ok"
}
