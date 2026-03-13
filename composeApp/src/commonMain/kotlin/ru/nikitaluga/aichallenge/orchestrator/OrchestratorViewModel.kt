package ru.nikitaluga.aichallenge.orchestrator

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import ru.nikitaluga.aichallenge.api.RouterAiApiService
import ru.nikitaluga.aichallenge.data.agent.OrchestratorAgent
import ru.nikitaluga.aichallenge.domain.model.OrchestratorMessage

class OrchestratorViewModel : ViewModel() {

    private val apiService = RouterAiApiService()
    private val agent = OrchestratorAgent(apiService = apiService)

    private val _state = MutableStateFlow(OrchestratorContract.State())
    val state: StateFlow<OrchestratorContract.State> = _state.asStateFlow()

    private val _effects = Channel<OrchestratorContract.Effect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    init {
        discoverServers()
    }

    fun onEvent(event: OrchestratorContract.Event) {
        when (event) {
            is OrchestratorContract.Event.InputChanged -> _state.value = _state.value.copy(inputText = event.text)
            is OrchestratorContract.Event.SendMessage -> sendMessage()
            is OrchestratorContract.Event.ClearHistory -> {
                agent.clearHistory()
                _state.value = _state.value.copy(messages = kotlinx.collections.immutable.persistentListOf(), inputText = "")
            }
            is OrchestratorContract.Event.RefreshServers -> discoverServers()
            is OrchestratorContract.Event.DismissError -> _state.value = _state.value.copy(errorMessage = null)
        }
    }

    private fun discoverServers() {
        _state.value = _state.value.copy(isDiscovering = true)
        viewModelScope.launch {
            runCatching { agent.discoverTools() }
                .onSuccess { result ->
                    _state.value = _state.value.copy(
                        servers = result.toImmutableList(),
                        isDiscovering = false,
                    )
                }
                .onFailure {
                    _state.value = _state.value.copy(isDiscovering = false)
                }
        }
    }

    private fun sendMessage() {
        val text = _state.value.inputText.trim()
        if (text.isBlank() || _state.value.isLoading) return

        val userMsg = OrchestratorMessage(role = "user", content = text)
        _state.value = _state.value.copy(
            messages = (_state.value.messages + userMsg).toImmutableList(),
            inputText = "",
            isLoading = true,
        )
        _effects.trySend(OrchestratorContract.Effect.ScrollToBottom)

        viewModelScope.launch {
            runCatching { agent.sendMessage(text) }
                .onSuccess { result ->
                    val assistantMsg = OrchestratorMessage(
                        role = "assistant",
                        content = result.content,
                        toolSteps = result.toolSteps,
                    )
                    _state.value = _state.value.copy(
                        messages = (_state.value.messages + assistantMsg).toImmutableList(),
                        isLoading = false,
                    )
                    _effects.trySend(OrchestratorContract.Effect.ScrollToBottom)
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(
                        isLoading = false,
                        errorMessage = if (e is Exception) apiService.friendlyError(e) else e.message ?: "Ошибка",
                    )
                }
        }
    }
}
