package ru.nikitaluga.aichallenge.domain.usecase

import ru.nikitaluga.aichallenge.domain.model.ModelConfig
import ru.nikitaluga.aichallenge.domain.model.ModelQueryResult
import ru.nikitaluga.aichallenge.domain.repository.ModelQueryRepository
import kotlin.time.measureTimedValue

class QueryModelUseCase(private val repository: ModelQueryRepository) {
    suspend operator fun invoke(
        config: ModelConfig,
        prompt: String,
        maxTokens: Int = 300,
    ): Result<ModelQueryResult> = runCatching {
        if (prompt.isBlank()) throw IllegalArgumentException("Промпт не может быть пустым")
        val clampedTokens = maxTokens.coerceAtLeast(1)
        val (response, duration) = measureTimedValue {
            repository.query(config.id, prompt, clampedTokens)
        }
        ModelQueryResult(
            config = config,
            responseText = response.text,
            responseTimeMs = duration.inWholeMilliseconds,
            inputTokens = response.inputTokens,
            outputTokens = response.outputTokens,
        )
    }
}
