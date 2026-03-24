package ru.nikitaluga.aichallenge.day27

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.nikitaluga.aichallenge.data.agent.LocalLlmStreamAgent
import ru.nikitaluga.aichallenge.data.agent.StreamMessage

class Day27ViewModel : ViewModel() {

    private val agent = LocalLlmStreamAgent()

    private val _state = MutableStateFlow(Day27Contract.State())
    val state: StateFlow<Day27Contract.State> = _state.asStateFlow()

    private val _effects = Channel<Day27Contract.Effect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    init {
        loadModels()
    }

    fun onEvent(event: Day27Contract.Event) {
        when (event) {
            is Day27Contract.Event.InputChanged ->
                _state.update { it.copy(inputText = event.text) }
            Day27Contract.Event.SendMessage -> sendMessage()
            Day27Contract.Event.ClearHistory ->
                _state.update { it.copy(messages = emptyList(), streamingText = "", error = null) }
            is Day27Contract.Event.SelectModel ->
                _state.update { it.copy(selectedModel = event.model) }
            Day27Contract.Event.LoadModels -> loadModels()
            Day27Contract.Event.DismissError ->
                _state.update { it.copy(error = null) }
        }
    }

    private fun loadModels() {
        viewModelScope.launch {
            _state.update { it.copy(isLoadingModels = true) }
            runCatching { agent.getModels() }
                .onSuccess { models ->
                    _state.update { state ->
                        state.copy(
                            isLoadingModels = false,
                            models = models,
                            selectedModel = if (models.isNotEmpty() && state.selectedModel !in models)
                                models.first()
                            else
                                state.selectedModel,
                        )
                    }
                }
                .onFailure { e ->
                    _state.update { it.copy(isLoadingModels = false, error = e.message) }
                }
        }
    }

    private fun sendMessage() {
        val text = _state.value.inputText.trim()
        if (text.isEmpty() || _state.value.isStreaming) return

        val history = _state.value.messages
        val model = _state.value.selectedModel

        _state.update { state ->
            state.copy(
                inputText = "",
                isStreaming = true,
                streamingText = "",
                error = null,
                messages = state.messages + Day27Contract.Message("user", text),
            )
        }

        viewModelScope.launch {
            runCatching {
                val apiMessages = (history + Day27Contract.Message("user", text)).map {
                    StreamMessage(role = it.role, content = it.content)
                }
                agent.streamChat(
                    messages = apiMessages,
                    model = model,
                    onChunk = { chunk ->
                        _state.update { it.copy(streamingText = it.streamingText + chunk) }
                    },
                )
                val finalText = _state.value.streamingText
                _state.update { state ->
                    state.copy(
                        isStreaming = false,
                        streamingText = "",
                        messages = state.messages + Day27Contract.Message("assistant", finalText),
                    )
                }
            }.onFailure { e ->
                val partial = _state.value.streamingText
                val errorText = partial.ifEmpty { "Ошибка: ${e.message}" }
                _state.update { state ->
                    state.copy(
                        isStreaming = false,
                        streamingText = "",
                        messages = state.messages + Day27Contract.Message("assistant", errorText),
                    )
                }
            }
            _effects.send(Day27Contract.Effect.ScrollToBottom)
        }
    }
}
