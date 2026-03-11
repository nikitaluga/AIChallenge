package ru.nikitaluga.aichallenge.mcp

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
import ru.nikitaluga.aichallenge.data.agent.McpWeatherAgent
import ru.nikitaluga.aichallenge.domain.model.McpChatMessage

class McpViewModel : ViewModel() {

    private val apiService = RouterAiApiService()
    private val agent = McpWeatherAgent(apiService = apiService)

    private val _state = MutableStateFlow(McpContract.State())
    val state: StateFlow<McpContract.State> = _state.asStateFlow()

    private val _effects = Channel<McpContract.Effect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    fun onEvent(event: McpContract.Event) {
        when (event) {
            McpContract.Event.Connect -> _state.update { it.copy(isMcpConnected = true) }

            McpContract.Event.Disconnect -> _state.update { it.copy(isMcpConnected = false) }

            is McpContract.Event.InputChanged -> _state.update { it.copy(inputText = event.text) }

            McpContract.Event.SendMessage -> sendMessage()

            McpContract.Event.ClearHistory -> {
                agent.clearHistory()
                _state.update { it.copy(messages = emptyList()) }
            }

            McpContract.Event.DismissError -> _state.update { it.copy(errorMessage = null) }
        }
    }

    private fun sendMessage() {
        val text = _state.value.inputText.trim()
        if (text.isEmpty() || _state.value.isLoading) return
        val connected = _state.value.isMcpConnected

        _state.update { current ->
            current.copy(
                inputText = "",
                isLoading = true,
                messages = current.messages + McpChatMessage(role = "user", content = text),
            )
        }

        viewModelScope.launch {
            try {
                val result = agent.sendMessage(text, connected)
                val assistantMsg = McpChatMessage(
                    role = "assistant",
                    content = result.content,
                    toolCallMade = result.toolCallMade,
                    toolName = result.toolName,
                    toolResult = result.toolResult,
                )
                _state.update { it.copy(isLoading = false, messages = it.messages + assistantMsg) }
                _effects.send(McpContract.Effect.ScrollToBottom)
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        isLoading = false,
                        messages = it.messages.dropLast(1),
                        errorMessage = apiService.friendlyError(e),
                    )
                }
            }
        }
    }
}
