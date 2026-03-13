package ru.nikitaluga.aichallenge.orchestrator

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import ru.nikitaluga.aichallenge.domain.model.McpServerInfo
import ru.nikitaluga.aichallenge.domain.model.OrchestratorMessage

object OrchestratorContract {

    data class State(
        val messages: ImmutableList<OrchestratorMessage> = persistentListOf(),
        val servers: ImmutableList<McpServerInfo> = persistentListOf(),
        val inputText: String = "",
        val isLoading: Boolean = false,
        val isDiscovering: Boolean = false,
        val errorMessage: String? = null,
    )

    sealed interface Event {
        data class InputChanged(val text: String) : Event
        data object SendMessage : Event
        data object ClearHistory : Event
        data object RefreshServers : Event
        data object DismissError : Event
    }

    sealed interface Effect {
        data object ScrollToBottom : Effect
    }
}
