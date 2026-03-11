package ru.nikitaluga.aichallenge.mcp

import ru.nikitaluga.aichallenge.domain.model.McpChatMessage

object McpContract {

    data class State(
        val isMcpConnected: Boolean = false,
        val messages: List<McpChatMessage> = emptyList(),
        val inputText: String = "",
        val isLoading: Boolean = false,
        val errorMessage: String? = null,
    )

    sealed interface Event {
        data object Connect : Event
        data object Disconnect : Event
        data class InputChanged(val text: String) : Event
        data object SendMessage : Event
        data object ClearHistory : Event
        data object DismissError : Event
    }

    sealed interface Effect {
        data object ScrollToBottom : Effect
    }
}
