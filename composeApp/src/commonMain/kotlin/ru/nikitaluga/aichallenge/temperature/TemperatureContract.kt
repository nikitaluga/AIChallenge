package ru.nikitaluga.aichallenge.temperature

import androidx.compose.ui.graphics.Color

object TemperatureContract {

    data class TemperatureGroup(
        val temperature: Double,
        val label: String,
        val description: String,
        val color: Color,
    )

    data class SlotState(
        val isLoading: Boolean = false,
        val result: String? = null,
    )

    data class State(
        val groups: List<TemperatureGroup> = emptyList(),
        val slots: List<SlotState> = List(9) { SlotState() },
        val isRunningAll: Boolean = false,
        val allDone: Boolean = false,
    )

    sealed interface Event {
        data object RunAll : Event
    }
}
