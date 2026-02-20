package ru.nikitaluga.aichallenge.temperature

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.nikitaluga.aichallenge.data.repository.ChatRepositoryImpl
import ru.nikitaluga.aichallenge.domain.usecase.SendMessageUseCase

private const val TEMP_PROMPT =
    "Напиши краткий рассказ о путешествии во времени (3-4 предложения)."

private const val RUNS_PER_TEMP = 3

private val TEMPERATURE_GROUPS = listOf(
    TemperatureContract.TemperatureGroup(
        temperature = 0.0,
        label = "temperature = 0.0",
        description = "Минимальная креативность, максимальная детерминированность",
        color = Color(0xFF1565C0),
    ),
    TemperatureContract.TemperatureGroup(
        temperature = 0.7,
        label = "temperature = 0.7",
        description = "Стандартное значение, баланс точности и креативности",
        color = Color(0xFF2E7D32),
    ),
    TemperatureContract.TemperatureGroup(
        temperature = 1.2,
        label = "temperature = 1.2",
        description = "Высокая креативность и случайность",
        color = Color(0xFF6A1B9A),
    ),
)

class TemperatureViewModel : ViewModel() {

    private val _state = MutableStateFlow(
        TemperatureContract.State(groups = TEMPERATURE_GROUPS),
    )
    val state: StateFlow<TemperatureContract.State> = _state.asStateFlow()

    fun onEvent(event: TemperatureContract.Event) {
        when (event) {
            TemperatureContract.Event.RunAll -> runAll()
        }
    }

    private fun runAll() {
        _state.update { state ->
            state.copy(
                isRunningAll = true,
                allDone = false,
                slots = List(9) { TemperatureContract.SlotState() },
            )
        }

        viewModelScope.launch {
            TEMPERATURE_GROUPS.forEachIndexed { groupIdx, group ->
                repeat(RUNS_PER_TEMP) { runIdx ->
                    val slot = groupIdx * RUNS_PER_TEMP + runIdx
                    _state.update { state ->
                        state.copy(
                            slots = state.slots.toMutableList().also {
                                it[slot] = TemperatureContract.SlotState(isLoading = true)
                            },
                        )
                    }
                    // Each slot uses a fresh service instance — no shared history
                    val useCase = SendMessageUseCase(ChatRepositoryImpl())
                    val result = useCase(
                        prompt = TEMP_PROMPT,
                        temperature = group.temperature,
                        maxTokens = 300,
                    )
                    _state.update { state ->
                        state.copy(
                            slots = state.slots.toMutableList().also {
                                it[slot] = TemperatureContract.SlotState(
                                    isLoading = false,
                                    result = result.getOrElse { e -> "Ошибка: ${e.message}" },
                                )
                            },
                        )
                    }
                }
            }
            _state.update { it.copy(isRunningAll = false, allDone = true) }
        }
    }
}
