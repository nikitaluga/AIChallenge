package ru.nikitaluga.aichallenge.day31

import androidx.compose.runtime.Immutable

object Day31Contract {

    @Immutable
    data class ChatSource(val source: String, val section: String?, val preview: String)

    @Immutable
    data class ChatEntry(
        val role: String,
        val content: String,
        val sources: List<ChatSource> = emptyList(),
        val toolsUsed: List<String> = emptyList(),
    )

    @Immutable
    data class IndexStats(
        val hasIndex: Boolean,
        val totalChunks: Int,
        val docsIndexed: Int,
        val createdAt: String,
    )

    @Immutable
    data class SlashCommand(
        val name: String,
        val description: String,
        val prompt: String,
    )

    @Immutable
    data class State(
        val messages: List<ChatEntry> = emptyList(),
        val inputText: String = "",
        val isLoading: Boolean = false,
        val isIndexing: Boolean = false,
        val indexStats: IndexStats? = null,
        val useMcp: Boolean = true,
        val error: String? = null,
        val commandSuggestions: List<SlashCommand> = emptyList(),
    )

    sealed interface Event {
        data class InputChanged(val text: String) : Event
        data object SendMessage : Event
        data object ClearHistory : Event
        data object BuildIndex : Event
        data class SelectSlashCommand(val command: SlashCommand) : Event
        data class ToggleMcp(val enabled: Boolean) : Event
        data object DismissError : Event
    }

    sealed interface Effect {
        data object ScrollToBottom : Effect
    }
}
