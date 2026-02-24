package ru.nikitaluga.aichallenge.agent

object AgentContract {

    data class State(
        val messages: List<DisplayMessage> = emptyList(),
        /** Accumulated text of the currently-streaming assistant reply. */
        val streamingText: String = "",
        val isStreaming: Boolean = false,
        val inputText: String = "",
    )

    data class DisplayMessage(
        val role: String,   // "user" | "assistant"
        val content: String,
    )

    sealed interface Event {
        data class InputChanged(val text: String) : Event
        data object SendMessage : Event
        data object ClearHistory : Event
    }

    sealed interface Effect {
        data object ScrollToBottom : Effect
    }
}
