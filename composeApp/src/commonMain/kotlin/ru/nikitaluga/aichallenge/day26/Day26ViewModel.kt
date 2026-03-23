package ru.nikitaluga.aichallenge.day26

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import ru.nikitaluga.aichallenge.data.agent.LocalLlmAgent
import ru.nikitaluga.aichallenge.domain.model.LocalChatMessage

class Day26ViewModel : ViewModel() {

    private val agent = LocalLlmAgent()

    private val _state = MutableStateFlow(Day26Contract.State())
    val state: StateFlow<Day26Contract.State> = _state.asStateFlow()

    private val _effects = Channel<Day26Contract.Effect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    fun onEvent(event: Day26Contract.Event) {
        when (event) {
            is Day26Contract.Event.InputChanged ->
                _state.value = _state.value.copy(inputText = event.text)

            is Day26Contract.Event.SendMessage -> sendMessage()

            is Day26Contract.Event.ClearHistory ->
                _state.value = _state.value.copy(
                    messages = kotlinx.collections.immutable.persistentListOf(),
                    inputText = "",
                )

            is Day26Contract.Event.ToggleBackend ->
                _state.value = _state.value.copy(useLocal = event.useLocal)

            is Day26Contract.Event.SelectQuestion -> {
                _state.value = _state.value.copy(inputText = event.question)
                sendMessage()
            }

            is Day26Contract.Event.DismissError ->
                _state.value = _state.value.copy(errorMessage = null)
        }
    }

    private fun sendMessage() {
        val text = _state.value.inputText.trim()
        if (text.isBlank() || _state.value.isLoading) return

        val userMsg = Day26Contract.Message(role = "user", content = text)
        _state.value = _state.value.copy(
            messages = (_state.value.messages + userMsg).toImmutableList(),
            inputText = "",
            isLoading = true,
        )
        _effects.trySend(Day26Contract.Effect.ScrollToBottom)

        val useLocal = _state.value.useLocal
        val history = _state.value.messages.map {
            LocalChatMessage(role = it.role, content = it.content)
        }

        viewModelScope.launch {
            runCatching {
                if (useLocal) {
                    agent.chat(messages = history)
                } else {
                    agent.chatCloud(messages = history)
                }
            }.onSuccess { result ->
                val assistantMsg = Day26Contract.Message(
                    role = "assistant",
                    content = result.reply,
                    latencyMs = result.latencyMs,
                    backend = result.backend,
                )
                _state.value = _state.value.copy(
                    messages = (_state.value.messages + assistantMsg).toImmutableList(),
                    isLoading = false,
                )
                _effects.trySend(Day26Contract.Effect.ScrollToBottom)
            }.onFailure { e ->
                val errMsg = Day26Contract.Message(
                    role = "assistant",
                    content = "Ошибка: ${e.message}",
                )
                _state.value = _state.value.copy(
                    messages = (_state.value.messages + errMsg).toImmutableList(),
                    isLoading = false,
                    errorMessage = e.message ?: "Ошибка запроса",
                )
            }
        }
    }
}
