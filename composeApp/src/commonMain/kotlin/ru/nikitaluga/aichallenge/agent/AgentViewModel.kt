package ru.nikitaluga.aichallenge.agent

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.nikitaluga.aichallenge.api.RouterAiApiService
import ru.nikitaluga.aichallenge.data.agent.ChatAgent

class AgentViewModel : ViewModel() {

    private val apiService = RouterAiApiService()
    private val agent = ChatAgent(
        apiService = apiService,
        systemPrompt = "Ты полезный ассистент. Отвечай на том языке, на котором пишет пользователь.",
    )

    private val _state = MutableStateFlow(
        AgentContract.State(
            messages = agent.history.map {
                AgentContract.DisplayMessage(it.role, it.effectiveContent)
            },
        ),
    )
    val state: StateFlow<AgentContract.State> = _state.asStateFlow()

    private val _effects = Channel<AgentContract.Effect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    fun onEvent(event: AgentContract.Event) {
        when (event) {
            is AgentContract.Event.InputChanged ->
                _state.update { it.copy(inputText = event.text) }
            AgentContract.Event.SendMessage -> sendMessage()
            AgentContract.Event.ClearHistory -> clearHistory()
        }
    }

    private fun sendMessage() {
        val text = _state.value.inputText.trim()
        if (text.isEmpty() || _state.value.isStreaming) return

        // Show user message immediately and reset input
        _state.update { state ->
            state.copy(
                inputText = "",
                isStreaming = true,
                streamingText = "",
                messages = state.messages + AgentContract.DisplayMessage("user", text),
            )
        }

        viewModelScope.launch {
            try {
                agent.streamMessage(
                    userMessage = text,
                    onChunk = { chunk ->
                        _state.update { it.copy(streamingText = it.streamingText + chunk) }
                    },
                )
                // Streaming finished — commit the full text to the message list
                val finalText = _state.value.streamingText
                _state.update { state ->
                    state.copy(
                        isStreaming = false,
                        streamingText = "",
                        messages = state.messages + AgentContract.DisplayMessage("assistant", finalText),
                    )
                }
            } catch (e: Exception) {
                // Show whatever arrived so far (may be empty on connection error)
                val partial = _state.value.streamingText
                val errorText = partial.ifEmpty { "Ошибка: ${e.message}" }
                _state.update { state ->
                    state.copy(
                        isStreaming = false,
                        streamingText = "",
                        messages = state.messages + AgentContract.DisplayMessage("assistant", errorText),
                    )
                }
            }
            _effects.send(AgentContract.Effect.ScrollToBottom)
        }
    }

    private fun clearHistory() {
        agent.clearHistory()
        _state.update { AgentContract.State() }
    }
}
