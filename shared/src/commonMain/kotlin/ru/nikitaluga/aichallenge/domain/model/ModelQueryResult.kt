package ru.nikitaluga.aichallenge.domain.model

enum class ModelTier(val label: String) {
    WEAK("Слабая"),
    MEDIUM("Средняя"),
    STRONG("Сильная"),
}

data class ModelConfig(
    val id: String,
    val displayName: String,
    val tier: ModelTier,
    val url: String,
    /** Price per one million input tokens, USD. */
    val inputPricePerMToken: Double,
    /** Price per one million output tokens, USD. */
    val outputPricePerMToken: Double,
)

/** Raw API response returned by the data layer. */
data class ModelResponse(
    val text: String,
    val inputTokens: Int,
    val outputTokens: Int,
)

data class ModelQueryResult(
    val config: ModelConfig,
    val responseText: String,
    val responseTimeMs: Long,
    val inputTokens: Int,
    val outputTokens: Int,
) {
    val estimatedCostUsd: Double
        get() = inputTokens / 1_000_000.0 * config.inputPricePerMToken +
            outputTokens / 1_000_000.0 * config.outputPricePerMToken
}
