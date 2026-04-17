package ru.nikitaluga.aichallenge

import kotlinx.coroutines.test.runTest
import ru.nikitaluga.aichallenge.domain.model.ModelConfig
import ru.nikitaluga.aichallenge.domain.model.ModelResponse
import ru.nikitaluga.aichallenge.domain.model.ModelTier
import ru.nikitaluga.aichallenge.domain.repository.ModelQueryRepository
import ru.nikitaluga.aichallenge.domain.usecase.QueryModelUseCase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class QueryModelUseCaseTest {

    private val testConfig = ModelConfig(
        id = "gpt-4o-mini",
        displayName = "GPT-4o Mini",
        tier = ModelTier.MEDIUM,
        url = "https://api.openai.com/v1",
        inputPricePerMToken = 0.15,
        outputPricePerMToken = 0.60,
    )

    @Test
    fun `successful query returns ModelQueryResult with correct fields`() = runTest {
        val mockResponse = ModelResponse(text = "Hello, world!", inputTokens = 10, outputTokens = 5)
        val repo = object : ModelQueryRepository {
            override suspend fun query(modelId: String, prompt: String, maxTokens: Int) = mockResponse
        }
        val useCase = QueryModelUseCase(repo)

        val result = useCase(testConfig, "Hi")

        assertTrue(result.isSuccess)
        val value = result.getOrThrow()
        assertEquals("Hello, world!", value.responseText)
        assertEquals(10, value.inputTokens)
        assertEquals(5, value.outputTokens)
        assertEquals(testConfig, value.config)
        assertTrue(value.responseTimeMs >= 0)
    }

    @Test
    fun `estimatedCostUsd is computed correctly`() = runTest {
        val mockResponse = ModelResponse(text = "ok", inputTokens = 1_000_000, outputTokens = 1_000_000)
        val repo = object : ModelQueryRepository {
            override suspend fun query(modelId: String, prompt: String, maxTokens: Int) = mockResponse
        }
        val useCase = QueryModelUseCase(repo)

        val result = useCase(testConfig, "test")

        val value = result.getOrThrow()
        // 1M input * 0.15 + 1M output * 0.60 = 0.75
        assertEquals(0.75, value.estimatedCostUsd, 0.0001)
    }

    @Test
    fun `repository throws exception returns failure`() = runTest {
        val repo = object : ModelQueryRepository {
            override suspend fun query(modelId: String, prompt: String, maxTokens: Int): ModelResponse {
                throw RuntimeException("Network error")
            }
        }
        val useCase = QueryModelUseCase(repo)

        val result = useCase(testConfig, "test")

        assertTrue(result.isFailure)
        assertEquals("Network error", result.exceptionOrNull()?.message)
    }

    @Test
    fun `modelId from config is passed to repository`() = runTest {
        var capturedModelId: String? = null
        val repo = object : ModelQueryRepository {
            override suspend fun query(modelId: String, prompt: String, maxTokens: Int): ModelResponse {
                capturedModelId = modelId
                return ModelResponse(text = "", inputTokens = 0, outputTokens = 0)
            }
        }
        val useCase = QueryModelUseCase(repo)

        useCase(testConfig, "test")

        assertEquals("gpt-4o-mini", capturedModelId)
    }
}
