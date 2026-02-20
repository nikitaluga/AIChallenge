package ru.nikitaluga.aichallenge.chat

import ru.nikitaluga.aichallenge.domain.model.Message

object ChatContract {

    data class State(
        val messages: List<Message> = emptyList(),
        val inputText: String = "",
        val isLoading: Boolean = false,
    )

    sealed interface Event {
        data class InputChanged(val text: String) : Event
        data object SendMessage : Event
    }

    sealed interface Effect {
        data object ScrollToBottom : Effect
    }
}
