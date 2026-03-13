package ru.nikitaluga.aichallenge.pipeline

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import ru.nikitaluga.aichallenge.domain.model.PipelineChatMessage
import ru.nikitaluga.aichallenge.domain.model.SavedFileInfo

object PipelineContract {

    data class State(
        val messages: List<PipelineChatMessage> = emptyList(),
        val savedFiles: ImmutableList<SavedFileInfo> = persistentListOf(),
        val inputText: String = "",
        val isLoading: Boolean = false,
        val errorMessage: String? = null,
        val openedFilename: String? = null,
        val openedFileContent: String? = null,
        val isFilesLoading: Boolean = false,
        val filesError: String? = null,
    )

    sealed interface Event {
        data class InputChanged(val text: String) : Event
        data object SendMessage : Event
        data object ClearHistory : Event
        data object RefreshFiles : Event
        data object DismissError : Event
        data class OpenFile(val filename: String) : Event
        data object CloseFile : Event
    }

    sealed interface Effect {
        data object ScrollToBottom : Effect
    }
}
