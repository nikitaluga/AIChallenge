package ru.nikitaluga.aichallenge.pipeline

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.collections.immutable.toImmutableList
import ru.nikitaluga.aichallenge.api.RouterAiApiService

import ru.nikitaluga.aichallenge.data.agent.PipelineAgent
import ru.nikitaluga.aichallenge.domain.model.PipelineChatMessage

class PipelineViewModel : ViewModel() {

    private val apiService = RouterAiApiService()
    private val agent = PipelineAgent(apiService = apiService)

    private val _state = MutableStateFlow(PipelineContract.State())
    val state: StateFlow<PipelineContract.State> = _state.asStateFlow()

    private val _effects = Channel<PipelineContract.Effect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    init {
        loadFiles()
    }

    fun onEvent(event: PipelineContract.Event) {
        when (event) {
            is PipelineContract.Event.InputChanged -> _state.value = _state.value.copy(inputText = event.text)
            is PipelineContract.Event.SendMessage -> sendMessage()
            is PipelineContract.Event.ClearHistory -> {
                agent.clearHistory()
                _state.value = _state.value.copy(messages = emptyList(), inputText = "")
            }
            is PipelineContract.Event.RefreshFiles -> loadFiles()
            is PipelineContract.Event.DismissError -> _state.value = _state.value.copy(errorMessage = null)
            is PipelineContract.Event.OpenFile -> openFile(event.filename)
            is PipelineContract.Event.CloseFile -> _state.value = _state.value.copy(openedFilename = null, openedFileContent = null)
        }
    }

    private fun sendMessage() {
        val text = _state.value.inputText.trim()
        if (text.isBlank() || _state.value.isLoading) return

        val userMsg = PipelineChatMessage(role = "user", content = text)
        _state.value = _state.value.copy(
            messages = _state.value.messages + userMsg,
            inputText = "",
            isLoading = true,
        )
        _effects.trySend(PipelineContract.Effect.ScrollToBottom)

        viewModelScope.launch {
            runCatching { agent.sendMessage(text) }
                .onSuccess { result ->
                    val assistantMsg = PipelineChatMessage(
                        role = "assistant",
                        content = result.content ?: "",
                        toolSteps = result.toolSteps,
                    )
                    _state.value = _state.value.copy(
                        messages = _state.value.messages + assistantMsg,
                        isLoading = false,
                    )
                    _effects.trySend(PipelineContract.Effect.ScrollToBottom)
                    if (result.toolSteps.any { it.toolName == "save_to_file" }) {
                        loadFiles()
                    }
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(
                        isLoading = false,
                        errorMessage = if (e is Exception) apiService.friendlyError(e) else e.message ?: "Ошибка",
                    )
                }
        }
    }

    private fun openFile(filename: String) {
        _state.value = _state.value.copy(openedFilename = filename, openedFileContent = null)
        viewModelScope.launch {
            runCatching { agent.loadFileContent(filename) }
                .onSuccess { content -> _state.value = _state.value.copy(openedFileContent = content) }
                .onFailure { e -> _state.value = _state.value.copy(openedFilename = null, errorMessage = e.message ?: "Ошибка загрузки файла") }
        }
    }

    private fun loadFiles() {
        _state.value = _state.value.copy(isFilesLoading = true, filesError = null)
        viewModelScope.launch {
            runCatching { agent.loadSavedFiles() }
                .onSuccess { files -> _state.value = _state.value.copy(savedFiles = files.toImmutableList(), isFilesLoading = false) }
                .onFailure { e -> _state.value = _state.value.copy(isFilesLoading = false, filesError = "Сервер недоступен: ${e.message}") }
        }
    }
}
