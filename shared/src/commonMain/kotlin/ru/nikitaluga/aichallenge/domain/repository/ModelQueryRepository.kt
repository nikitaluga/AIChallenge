package ru.nikitaluga.aichallenge.domain.repository

import ru.nikitaluga.aichallenge.domain.model.ModelResponse

interface ModelQueryRepository {
    suspend fun query(
        modelId: String,
        prompt: String,
        maxTokens: Int = 300,
    ): ModelResponse
}
