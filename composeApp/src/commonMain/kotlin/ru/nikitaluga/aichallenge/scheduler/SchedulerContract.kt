package ru.nikitaluga.aichallenge.scheduler

import ru.nikitaluga.aichallenge.domain.model.ScheduleInfo
import ru.nikitaluga.aichallenge.domain.model.SchedulerChatMessage

object SchedulerContract {

    data class State(
        val messages: List<SchedulerChatMessage> = emptyList(),
        val schedules: List<ScheduleInfo> = emptyList(),
        val inputText: String = "",
        val isLoading: Boolean = false,
        val errorMessage: String? = null,
    )

    sealed interface Event {
        data class InputChanged(val text: String) : Event
        data object SendMessage : Event
        data class DeleteSchedule(val id: String) : Event
        data object RefreshSchedules : Event
        data object ClearHistory : Event
        data object DismissError : Event
    }

    sealed interface Effect {
        data object ScrollToBottom : Effect
    }
}
