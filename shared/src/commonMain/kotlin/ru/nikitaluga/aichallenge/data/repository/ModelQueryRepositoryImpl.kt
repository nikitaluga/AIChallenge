package ru.nikitaluga.aichallenge.data.repository

import ru.nikitaluga.aichallenge.api.RouterAiApiService
import ru.nikitaluga.aichallenge.domain.model.ModelResponse
import ru.nikitaluga.aichallenge.domain.repository.ModelQueryRepository

class ModelQueryRepositoryImpl(
    private val apiService: RouterAiApiService = RouterAiApiService(),
) : ModelQueryRepository {

    override suspend fun query(
        modelId: String,
        prompt: String,
        maxTokens: Int,
    ): ModelResponse {
        val response = apiService.sendSingleMessage(
            model = modelId,
            prompt = prompt,
            maxTokens = maxTokens,
        )
        return ModelResponse(
            text = response.content,
            inputTokens = response.usage?.promptTokens ?: 0,
            outputTokens = response.usage?.completionTokens ?: 0,
        )
    }
}
