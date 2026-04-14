package ru.nikitaluga.aichallenge.day35

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import ru.nikitaluga.aichallenge.data.agent.GitCommitAgent

class Day35ViewModel : ViewModel() {

    private val agent = GitCommitAgent()

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private val _effects = Channel<Effect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    fun onEvent(event: Event) {
        when (event) {
            is Event.DiffChanged -> _state.value = _state.value.copy(diff = event.text, error = null)
            is Event.ContextChanged -> _state.value = _state.value.copy(context = event.text, error = null)
            Event.Generate -> generate()
            is Event.QuickPromptSelected -> {
                _state.value = _state.value.copy(diff = event.diff, error = null)
                generate()
            }
            is Event.CopyMessage -> {
                _state.value = _state.value.copy(copiedMessage = event.text)
                _effects.trySend(Effect.CopyToClipboard(event.text))
            }
            Event.DismissError -> _state.value = _state.value.copy(error = null)
            Event.DismissCopied -> _state.value = _state.value.copy(copiedMessage = null)
        }
    }

    private fun generate() {
        val diff = _state.value.diff.trim()
        if (diff.isBlank() || _state.value.isLoading) return

        _state.value = _state.value.copy(isLoading = true, results = emptyList(), error = null)

        viewModelScope.launch {
            runCatching { agent.generate(diff = diff, context = _state.value.context.trim().ifBlank { null }) }
                .onSuccess { response ->
                    val all = listOf(response.message) + response.alternatives
                    _state.value = _state.value.copy(results = all.distinct(), isLoading = false)
                    _effects.trySend(Effect.ScrollToResults)
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(isLoading = false, error = e.message ?: "Ошибка генерации")
                }
        }
    }
}
