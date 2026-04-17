package ru.nikitaluga.aichallenge

import kotlinx.coroutines.test.runTest
import ru.nikitaluga.aichallenge.domain.model.ModelConfig
import ru.nikitaluga.aichallenge.domain.model.ModelResponse
import ru.nikitaluga.aichallenge.domain.model.ModelTier
import ru.nikitaluga.aichallenge.domain.repository.ModelQueryRepository
import ru.nikitaluga.aichallenge.domain.usecase.QueryModelUseCase
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Контракт: QueryModelUseCase должен валидировать входные параметры.
 * ОЖИДАЕМЫЙ РЕЗУЛЬТАТ: все тесты ПАДАЮТ — валидации нет.
 */
class QueryModelUseCaseValidationTest {

    private val config = ModelConfig(
        id = "gpt-4o-mini",
        displayName = "GPT-4o Mini",
        tier = ModelTier.MEDIUM,
        url = "https://api.openai.com/v1",
        inputPricePerMToken = 0.15,
        outputPricePerMToken = 0.60,
    )

    private val passthroughRepo = object : ModelQueryRepository {
        override suspend fun query(modelId: String, prompt: String, maxTokens: Int) =
            ModelResponse(text = "ok", inputTokens = 0, outputTokens = 0)
    }

    @Test
    fun `invoke with blank prompt returns failure`() = runTest {
        // FAIL: пустой промпт проходит насквозь в репозиторий без ошибки
        val result = QueryModelUseCase(passthroughRepo).invoke(config, "   ")
        assertTrue(result.isFailure, "Пустой промпт должен возвращать Result.failure, но вернул success")
    }

    @Test
    fun `invoke with zero maxTokens clamps to minimum 1`() = runTest {
        var capturedMaxTokens = -1
        val capturingRepo = object : ModelQueryRepository {
            override suspend fun query(modelId: String, prompt: String, maxTokens: Int): ModelResponse {
                capturedMaxTokens = maxTokens
                return ModelResponse("ok", 0, 0)
            }
        }
        // FAIL: maxTokens=0 передаётся в репозиторий без корректировки
        QueryModelUseCase(capturingRepo).invoke(config, "hello", maxTokens = 0)
        assertTrue(capturedMaxTokens >= 1, "maxTokens=$capturedMaxTokens должен быть >= 1 после клампинга")
    }

    @Test
    fun `invoke with negative maxTokens clamps to minimum 1`() = runTest {
        var capturedMaxTokens = -1
        val capturingRepo = object : ModelQueryRepository {
            override suspend fun query(modelId: String, prompt: String, maxTokens: Int): ModelResponse {
                capturedMaxTokens = maxTokens
                return ModelResponse("ok", 0, 0)
            }
        }
        // FAIL: maxTokens=-100 передаётся в репозиторий как есть
        QueryModelUseCase(capturingRepo).invoke(config, "hello", maxTokens = -100)
        assertTrue(capturedMaxTokens >= 1, "maxTokens=$capturedMaxTokens должен быть >= 1 после клампинга")
    }
}
