package ru.nikitaluga.aichallenge.rag

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import ru.nikitaluga.aichallenge.data.agent.RagAgent

class RagViewModel : ViewModel() {

    private val agent = RagAgent()

    private val _state = MutableStateFlow(RagContract.State())
    val state: StateFlow<RagContract.State> = _state.asStateFlow()

    private val _effects = Channel<RagContract.Effect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    init {
        loadStats()
    }

    fun onEvent(event: RagContract.Event) {
        when (event) {
            is RagContract.Event.InputChanged ->
                _state.value = _state.value.copy(inputText = event.text)

            is RagContract.Event.SendMessage -> sendMessage()

            is RagContract.Event.ClearHistory ->
                _state.value = _state.value.copy(
                    messages = persistentListOf(),
                    inputText = "",
                )

            is RagContract.Event.TabSelected ->
                _state.value = _state.value.copy(tab = event.tab)

            is RagContract.Event.StrategyChanged ->
                _state.value = _state.value.copy(activeStrategy = event.strategy)

            is RagContract.Event.ChunkSizeChanged ->
                _state.value = _state.value.copy(chunkSize = event.size)

            is RagContract.Event.OverlapChanged ->
                _state.value = _state.value.copy(overlap = event.overlap)

            is RagContract.Event.TopKChanged ->
                _state.value = _state.value.copy(topK = event.k)

            is RagContract.Event.CompareInputChanged ->
                _state.value = _state.value.copy(compareInput = event.text)

            is RagContract.Event.RunCompare -> runCompare()

            is RagContract.Event.SelectControlQuestion -> {
                _state.value = _state.value.copy(compareInput = event.question)
                runCompare()
            }

            is RagContract.Event.BuildIndex -> buildIndex()

            is RagContract.Event.LoadStats -> loadStats()

            is RagContract.Event.DismissError ->
                _state.value = _state.value.copy(errorMessage = null)

            is RagContract.Event.ThresholdChanged ->
                _state.value = _state.value.copy(threshold = event.value)

            is RagContract.Event.TopKBeforeChanged ->
                _state.value = _state.value.copy(topKBefore = event.value)

            is RagContract.Event.RewriteToggled ->
                _state.value = _state.value.copy(rewriteEnabled = event.enabled)

            is RagContract.Event.RunEnhancedCompare -> runEnhancedCompare()

            is RagContract.Event.SelectEnhancedQuestion -> {
                _state.value = _state.value.copy(compareInput = event.question)
                runEnhancedCompare()
            }

            is RagContract.Event.RunDay24Test -> runDay24Test()
        }
    }

    private fun sendMessage() {
        val text = _state.value.inputText.trim()
        if (text.isBlank() || _state.value.isLoading) return

        val userMsg = RagContract.RagMessage(role = "user", content = text)
        _state.value = _state.value.copy(
            messages = (_state.value.messages + userMsg).toImmutableList(),
            inputText = "",
            isLoading = true,
        )
        _effects.trySend(RagContract.Effect.ScrollToBottom)

        val topK = _state.value.topK
        val strategy = _state.value.activeStrategy

        viewModelScope.launch {
            runCatching { agent.chat(query = text, k = topK, strategy = strategy) }
                .onSuccess { result ->
                    val assistantMsg = RagContract.RagMessage(
                        role = "assistant",
                        content = result.answer,
                        usedChunks = result.usedChunks.toImmutableList(),
                    )
                    _state.value = _state.value.copy(
                        messages = (_state.value.messages + assistantMsg).toImmutableList(),
                        isLoading = false,
                    )
                    _effects.trySend(RagContract.Effect.ScrollToBottom)
                }
                .onFailure { e ->
                    val errMsg = RagContract.RagMessage(
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

    private fun runCompare() {
        val query = _state.value.compareInput.trim()
        if (query.isBlank() || _state.value.isComparing) return
        val topK = _state.value.topK
        val strategy = _state.value.activeStrategy
        _state.value = _state.value.copy(isComparing = true, compareResult = null)
        viewModelScope.launch {
            runCatching { agent.compare(query = query, k = topK, strategy = strategy) }
                .onSuccess { result ->
                    _state.value = _state.value.copy(isComparing = false, compareResult = result)
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(
                        isComparing = false,
                        errorMessage = "Ошибка сравнения: ${e.message}",
                    )
                }
        }
    }

    private fun runEnhancedCompare() {
        val query = _state.value.compareInput.trim()
        if (query.isBlank() || _state.value.isEnhancedComparing) return
        _state.value = _state.value.copy(isEnhancedComparing = true, tripleCompareResult = null)
        val topK = _state.value.topK
        val strategy = _state.value.activeStrategy
        val threshold = _state.value.threshold
        val topKBefore = _state.value.topKBefore
        val rewrite = _state.value.rewriteEnabled
        viewModelScope.launch {
            runCatching {
                agent.compareEnhanced(
                    query = query,
                    k = topK,
                    strategy = strategy,
                    threshold = threshold,
                    topKBefore = topKBefore,
                    rewriteQuery = rewrite,
                )
            }
                .onSuccess { result ->
                    _state.value = _state.value.copy(isEnhancedComparing = false, tripleCompareResult = result)
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(
                        isEnhancedComparing = false,
                        errorMessage = "Ошибка расширенного сравнения: ${e.message}",
                    )
                }
        }
    }

    private fun runDay24Test() {
        if (_state.value.isDay24Running) return
        _state.value = _state.value.copy(
            isDay24Running = true,
            day24Results = kotlinx.collections.immutable.persistentListOf(),
            day24CurrentIndex = 0,
        )
        val strategy = _state.value.activeStrategy
        val topK = _state.value.topK
        val threshold = _state.value.threshold

        viewModelScope.launch {
            val results = mutableListOf<RagContract.Day24QuestionResult>()
            RagContract.CONTROL_QUESTIONS.forEachIndexed { idx, question ->
                _state.value = _state.value.copy(day24CurrentIndex = idx + 1)
                val result = runCatching {
                    agent.chatV2(query = question, k = topK, strategy = strategy, threshold = threshold)
                }.getOrNull()
                results.add(
                    RagContract.Day24QuestionResult(
                        question = question,
                        answer = result?.answer ?: "Ошибка запроса",
                        sources = result?.sources ?: emptyList(),
                        citations = result?.citations ?: emptyList(),
                        belowThreshold = result?.belowThreshold ?: false,
                    )
                )
                _state.value = _state.value.copy(day24Results = results.toImmutableList())
            }
            _state.value = _state.value.copy(isDay24Running = false)
        }
    }

    private fun buildIndex() {
        if (_state.value.isIndexing) return
        val chunkSize = _state.value.chunkSize
        val overlap = _state.value.overlap
        _state.value = _state.value.copy(isIndexing = true, indexMessage = null)

        viewModelScope.launch {
            runCatching { agent.buildIndex(chunkSize = chunkSize, overlap = overlap) }
                .onSuccess { msg ->
                    _state.value = _state.value.copy(isIndexing = false, indexMessage = msg)
                    loadStats()
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(
                        isIndexing = false,
                        errorMessage = "Ошибка индексации: ${e.message}",
                    )
                }
        }
    }

    private fun loadStats() {
        viewModelScope.launch {
            runCatching { agent.getStats() }
                .onSuccess { stats -> _state.value = _state.value.copy(stats = stats) }
                .onFailure { /* silent — server may not be running */ }
        }
    }
}
