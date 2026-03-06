package ru.nikitaluga.aichallenge.invariants

import ru.nikitaluga.aichallenge.domain.model.Invariant
import ru.nikitaluga.aichallenge.domain.model.InvariantChatMessage

object InvariantsContract {

    data class State(
        val invariants: List<Invariant> = emptyList(),
        val messages: List<InvariantChatMessage> = emptyList(),
        val inputText: String = "",
        val isLoading: Boolean = false,
        val showDialog: Boolean = false,
        /** null = создание нового, non-null = редактирование существующего */
        val editingInvariant: Invariant? = null,
        val errorMessage: String? = null,
    )

    sealed interface Event {
        data object AddInvariant : Event
        data class EditInvariant(val id: String) : Event
        data class DeleteInvariant(val id: String) : Event
        data class ToggleInvariant(val id: String) : Event
        data class SaveInvariant(val invariant: Invariant) : Event
        data object DismissDialog : Event
        data class InputChanged(val text: String) : Event
        data object SendMessage : Event
        data object ClearHistory : Event
        data object DismissError : Event
    }

    sealed interface Effect {
        data object ScrollToBottom : Effect
    }
}
