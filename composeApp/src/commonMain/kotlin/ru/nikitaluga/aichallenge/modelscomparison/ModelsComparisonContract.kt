package ru.nikitaluga.aichallenge.modelscomparison

import ru.nikitaluga.aichallenge.domain.model.ModelConfig
import ru.nikitaluga.aichallenge.domain.model.ModelQueryResult

const val COMPARISON_PROMPT =
    "Объясни, что такое квантовая запутанность, простыми словами. " +
        "Ответ должен быть не длиннее 5 предложений."

object ModelsComparisonContract {

    data class ModelSlotState(
        val config: ModelConfig,
        val isLoading: Boolean = false,
        val result: ModelQueryResult? = null,
        val error: String? = null,
    )

    data class State(
        val slots: List<ModelSlotState>,
        val isRunning: Boolean = false,
        val allDone: Boolean = false,
    )

    sealed interface Event {
        data object RunComparison : Event
    }
}
