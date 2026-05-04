package ru.nikitaluga.aichallenge.day12indirectinjection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import ru.nikitaluga.aichallenge.data.agent.IndirectInjectionAgent

class Day12IndirectInjectionViewModel : ViewModel() {

    private val agent = IndirectInjectionAgent()

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private val _effects = Channel<Effect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    fun onEvent(event: Event) {
        when (event) {
            is Event.SelectVector -> _state.value = _state.value.copy(vectorType = event.type, result = null)
            is Event.SelectDefense -> {
                val current = _state.value
                _state.value = current.copy(
                    defenseModePerVector = current.defenseModePerVector + (current.vectorType to event.mode),
                    result = null,
                )
            }
            Event.RunAttack -> runAttack()
            Event.Clear -> _state.value = _state.value.copy(result = null, error = null)
            Event.DismissError -> _state.value = _state.value.copy(error = null)
        }
    }

    private fun runAttack() {
        if (_state.value.isLoading) return
        val vectorType = _state.value.vectorType
        val defenseMode = _state.value.defenseMode

        _state.value = _state.value.copy(isLoading = true, error = null)

        viewModelScope.launch {
            runCatching {
                agent.attack(
                    vectorType = vectorType.apiKey,
                    defenseMode = defenseMode.apiKey,
                )
            }
                .onSuccess { dto ->
                    _state.value = _state.value.copy(
                        isLoading = false,
                        result = AttackResult(
                            vectorType = vectorType,
                            defenseMode = defenseMode,
                            hiddenPayload = dto.hiddenPayload,
                            visibleContent = dto.visibleContent,
                            sanitizedContent = dto.sanitizedContent,
                            agentOutput = dto.agentOutput,
                            verdict = dto.verdict,
                            judgeReasoning = dto.judgeReasoning,
                        ),
                    )
                    _effects.trySend(Effect.ScrollToResult)
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(isLoading = false, error = e.message ?: "Ошибка")
                }
        }
    }
}
