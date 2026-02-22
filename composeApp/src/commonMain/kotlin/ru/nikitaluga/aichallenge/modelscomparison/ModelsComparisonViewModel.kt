package ru.nikitaluga.aichallenge.modelscomparison

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.nikitaluga.aichallenge.data.repository.ModelQueryRepositoryImpl
import ru.nikitaluga.aichallenge.domain.model.ModelConfig
import ru.nikitaluga.aichallenge.domain.model.ModelTier
import ru.nikitaluga.aichallenge.domain.usecase.QueryModelUseCase

private val MODEL_CONFIGS = listOf(
    ModelConfig(
        id = "google/gemma-3n-e4b-it",
        displayName = "Gemma 3n E4B Instruct",
        tier = ModelTier.WEAK,
        url = "https://huggingface.co/google/gemma-3n-E4B-it",
        inputPricePerMToken = 0.002,
        outputPricePerMToken = 0.004,
    ),
    ModelConfig(
        id = "nvidia/nemotron-nano-9b-v2",
        displayName = "Nemotron Nano 9B v2",
        tier = ModelTier.MEDIUM,
        url = "https://huggingface.co/nvidia/Nemotron-Nano-9B-v2",
        inputPricePerMToken = 0.04,
        outputPricePerMToken = 0.20,
    ),
    ModelConfig(
        id = "deepseek/deepseek-v3.2",
        displayName = "DeepSeek V3.2",
        tier = ModelTier.STRONG,
        url = "https://huggingface.co/deepseek-ai/DeepSeek-V3",
        inputPricePerMToken = 0.27,
        outputPricePerMToken = 1.10,
    ),
)

class ModelsComparisonViewModel : ViewModel() {

    private val _state = MutableStateFlow(
        ModelsComparisonContract.State(
            slots = MODEL_CONFIGS.map { ModelsComparisonContract.ModelSlotState(config = it) },
        ),
    )
    val state: StateFlow<ModelsComparisonContract.State> = _state.asStateFlow()

    fun onEvent(event: ModelsComparisonContract.Event) {
        when (event) {
            ModelsComparisonContract.Event.RunComparison -> runComparison()
        }
    }

    private fun runComparison() {
        _state.update {
            it.copy(
                isRunning = true,
                allDone = false,
                slots = MODEL_CONFIGS.map { config ->
                    ModelsComparisonContract.ModelSlotState(config = config)
                },
            )
        }

        viewModelScope.launch {
            MODEL_CONFIGS.forEachIndexed { idx, config ->
                _state.update { state ->
                    state.copy(
                        slots = state.slots.toMutableList().also {
                            it[idx] = it[idx].copy(isLoading = true)
                        },
                    )
                }

                // Each slot gets a fresh repository instance — no shared state
                val useCase = QueryModelUseCase(ModelQueryRepositoryImpl())
                val result = useCase(config = config, prompt = COMPARISON_PROMPT)

                _state.update { state ->
                    state.copy(
                        slots = state.slots.toMutableList().also {
                            it[idx] = ModelsComparisonContract.ModelSlotState(
                                config = config,
                                isLoading = false,
                                result = result.getOrNull(),
                                error = result.exceptionOrNull()?.message,
                            )
                        },
                    )
                }
            }
            _state.update { it.copy(isRunning = false, allDone = true) }
        }
    }
}
