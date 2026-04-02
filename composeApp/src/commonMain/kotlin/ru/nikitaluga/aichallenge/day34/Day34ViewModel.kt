package ru.nikitaluga.aichallenge.day34

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import ru.nikitaluga.aichallenge.data.agent.FilesAgent

class Day34ViewModel : ViewModel() {

    private val agent = FilesAgent()

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private val _effects = Channel<Effect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    fun onEvent(event: Event) {
        when (event) {
            is Event.InputChanged -> _state.value = _state.value.copy(inputText = event.text, error = null)
            Event.SendMessage -> sendMessage()
            is Event.QuickPromptSelected -> {
                _state.value = _state.value.copy(inputText = event.text)
                sendMessage()
            }
            Event.ClearChat -> _state.value = _state.value.copy(messages = emptyList(), inputText = "")
            Event.DismissError -> _state.value = _state.value.copy(error = null)
        }
    }

    private fun sendMessage() {
        val text = _state.value.inputText.trim()
        if (text.isBlank() || _state.value.isLoading) return

        val userMsg = ChatMsg(role = "user", content = text)
        val history = _state.value.messages.takeLast(10).map {
            FilesAgent.FilesHistoryMsg(it.role, it.content)
        }
        _state.value = _state.value.copy(
            messages = _state.value.messages + userMsg,
            inputText = "",
            isLoading = true,
            error = null,
        )
        _effects.trySend(Effect.ScrollToBottom)

        viewModelScope.launch {
            runCatching { agent.chat(query = text, history = history) }
                .onSuccess { response ->
                    val diffSummary = if (response.diffs.isNotEmpty()) {
                        "\n\n**Diff-ы:**\n" + response.diffs.joinToString("\n\n") { diff ->
                            "```diff\n// ${diff.path}\n${diff.diff}\n```"
                        }
                    } else ""
                    val assistantMsg = ChatMsg(
                        role = "assistant",
                        content = response.answer + diffSummary,
                        toolsUsed = response.toolsUsed.distinct(),
                    )
                    _state.value = _state.value.copy(
                        messages = _state.value.messages + assistantMsg,
                        isLoading = false,
                    )
                    _effects.trySend(Effect.ScrollToBottom)
                }
                .onFailure { e ->
                    val errorText = e.message ?: "Нет соединения с сервером"
                    val errMsg = ChatMsg(role = "assistant", content = "Не удалось получить ответ: $errorText")
                    _state.value = _state.value.copy(
                        messages = _state.value.messages + errMsg,
                        isLoading = false,
                    )
                    _effects.trySend(Effect.ScrollToBottom)
                }
        }
    }
}
