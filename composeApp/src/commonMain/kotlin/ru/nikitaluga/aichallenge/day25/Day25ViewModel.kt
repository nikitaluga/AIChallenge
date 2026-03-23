package ru.nikitaluga.aichallenge.day25

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import ru.nikitaluga.aichallenge.data.agent.RagAgent
import ru.nikitaluga.aichallenge.domain.model.RagHistoryMessage

class Day25ViewModel : ViewModel() {

    private val agent = RagAgent()

    private val _state = MutableStateFlow(Day25Contract.State())
    val state: StateFlow<Day25Contract.State> = _state.asStateFlow()

    private val _effects = Channel<Day25Contract.Effect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    fun onEvent(event: Day25Contract.Event) {
        when (event) {
            is Day25Contract.Event.InputChanged ->
                _state.value = _state.value.copy(inputText = event.text)

            is Day25Contract.Event.SendMessage -> sendMessage()

            is Day25Contract.Event.ClearHistory ->
                _state.value = _state.value.copy(
                    messages = kotlinx.collections.immutable.persistentListOf(),
                    inputText = "",
                    taskMemory = ru.nikitaluga.aichallenge.domain.model.TaskMemory(),
                )

            is Day25Contract.Event.ThresholdChanged ->
                _state.value = _state.value.copy(threshold = event.value)

            is Day25Contract.Event.TopKChanged ->
                _state.value = _state.value.copy(topK = event.k)

            is Day25Contract.Event.BuildIndex -> buildIndex()

            is Day25Contract.Event.SelectQuestion -> {
                _state.value = _state.value.copy(inputText = event.question)
                sendMessage()
            }

            is Day25Contract.Event.DismissError ->
                _state.value = _state.value.copy(errorMessage = null)
        }
    }

    private fun buildIndex() {
        if (_state.value.isIndexing) return
        _state.value = _state.value.copy(isIndexing = true)
        viewModelScope.launch {
            runCatching { agent.buildIndex(chunkSize = 300, overlap = 50) }
                .onSuccess { _state.value = _state.value.copy(isIndexing = false) }
                .onFailure { e ->
                    _state.value = _state.value.copy(
                        isIndexing = false,
                        errorMessage = "Ошибка индексации: ${e.message}",
                    )
                }
        }
    }

    private fun sendMessage() {
        val text = _state.value.inputText.trim()
        if (text.isBlank() || _state.value.isLoading) return

        val userMsg = Day25Contract.Message(role = "user", content = text)
        _state.value = _state.value.copy(
            messages = (_state.value.messages + userMsg).toImmutableList(),
            inputText = "",
            isLoading = true,
        )
        _effects.trySend(Day25Contract.Effect.ScrollToBottom)

        val topK = _state.value.topK
        val strategy = _state.value.strategy
        val threshold = _state.value.threshold
        val taskMemory = _state.value.taskMemory
        // Build history from all messages so far (excluding the just-added user message)
        val history = _state.value.messages.dropLast(1).map {
            RagHistoryMessage(role = it.role, content = it.content)
        }

        viewModelScope.launch {
            runCatching {
                agent.chatV3(
                    query = text,
                    history = history,
                    taskMemory = taskMemory,
                    k = topK,
                    strategy = strategy,
                    threshold = threshold,
                )
            }
                .onSuccess { result ->
                    val assistantMsg = Day25Contract.Message(
                        role = "assistant",
                        content = result.answer,
                        sources = result.sources.toImmutableList(),
                        citations = result.citations.toImmutableList(),
                        belowThreshold = result.belowThreshold,
                    )
                    _state.value = _state.value.copy(
                        messages = (_state.value.messages + assistantMsg).toImmutableList(),
                        isLoading = false,
                        taskMemory = result.taskMemory,
                    )
                    _effects.trySend(Day25Contract.Effect.ScrollToBottom)
                }
                .onFailure { e ->
                    val errMsg = Day25Contract.Message(
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
