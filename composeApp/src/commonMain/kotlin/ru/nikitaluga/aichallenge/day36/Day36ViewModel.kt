package ru.nikitaluga.aichallenge.day36

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import ru.nikitaluga.aichallenge.data.agent.ReflectionAgent

class Day36ViewModel : ViewModel() {

    private val agent = ReflectionAgent()

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private val _effects = Channel<Effect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    fun onEvent(event: Event) {
        when (event) {
            is Event.QueryChanged -> _state.value = _state.value.copy(query = event.text, error = null)
            is Event.RubricChanged -> _state.value = _state.value.copy(rubric = event.text)
            Event.Generate -> generate()
            Event.Clear -> _state.value = State()
            Event.DismissError -> _state.value = _state.value.copy(error = null)
            is Event.ToggleIteration -> toggleIteration(event.attempt)
        }
    }

    private fun generate() {
        val query = _state.value.query.trim()
        if (query.isBlank() || _state.value.isLoading) return

        _state.value = _state.value.copy(
            isLoading = true,
            iterations = emptyList(),
            finalAnswer = null,
            error = null,
        )

        viewModelScope.launch {
            runCatching {
                agent.reflect(
                    query = query,
                    rubric = _state.value.rubric.trim().takeIf { it.isNotBlank() },
                )
            }
                .onSuccess { result ->
                    _state.value = _state.value.copy(
                        isLoading = false,
                        iterations = result.iterations.map { it.toMsg() },
                        finalAnswer = result.answer,
                    )
                    _effects.trySend(Effect.ScrollToBottom)
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(isLoading = false, error = e.message ?: "Ошибка")
                }
        }
    }

    private fun toggleIteration(attempt: Int) {
        _state.value = _state.value.copy(
            iterations = _state.value.iterations.map {
                if (it.attempt == attempt) it.copy(isExpanded = !it.isExpanded) else it
            },
        )
    }
}

private fun ReflectionAgent.IterationDto.toMsg() = IterationMsg(
    attempt = attempt,
    draft = draft,
    critique = critique,
    score = score,
)