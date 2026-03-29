package ru.nikitaluga.aichallenge.day28

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
import ru.nikitaluga.aichallenge.data.agent.LocalRagAgent

class Day28ViewModel : ViewModel() {

    private val ragAgent = LocalRagAgent()
    private val modelsAgent = LocalLlmStreamAgent()

    private val _state = MutableStateFlow(Day28Contract.State())
    val state: StateFlow<Day28Contract.State> = _state.asStateFlow()

    private val _effects = Channel<Day28Contract.Effect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    init {
        loadModels()
        loadStats()
    }

    fun onEvent(event: Day28Contract.Event) {
        when (event) {
            Day28Contract.Event.BuildIndex -> buildIndex()
            is Day28Contract.Event.QueryChanged -> _state.update { it.copy(query = event.query) }
            Day28Contract.Event.Compare -> compare()
            is Day28Contract.Event.ModelSelected -> _state.update { it.copy(selectedModel = event.model) }
            Day28Contract.Event.DismissError -> _state.update { it.copy(error = null) }
        }
    }

    private fun loadModels() {
        viewModelScope.launch {
            runCatching { modelsAgent.getModels() }.onSuccess { models ->
                _state.update { state ->
                    state.copy(
                        availableModels = models,
                        selectedModel = if (models.isNotEmpty() && state.selectedModel !in models)
                            models.first()
                        else
                            state.selectedModel,
                    )
                }
            }
        }
    }

    private fun loadStats() {
        viewModelScope.launch {
            runCatching { ragAgent.getStats() }.onSuccess { stats ->
                _state.update { it.copy(localStats = stats) }
            }
        }
    }

    private fun buildIndex() {
        viewModelScope.launch {
            _state.update { it.copy(isIndexing = true, error = null) }
            runCatching { ragAgent.buildLocalIndex() }
                .onSuccess { message ->
                    _state.update { it.copy(isIndexing = false) }
                    loadStats()
                }
                .onFailure { e ->
                    _state.update { it.copy(isIndexing = false, error = e.message) }
                }
        }
    }

    private fun compare() {
        val query = _state.value.query.trim()
        if (query.isEmpty() || _state.value.isLoading) return
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, localResult = null, cloudResult = null, error = null) }
            runCatching {
                ragAgent.compareLocalVsCloud(
                    query = query,
                    localModel = _state.value.selectedModel,
                )
            }.onSuccess { result ->
                _state.update { it.copy(isLoading = false, localResult = result.local, cloudResult = result.cloud) }
                _effects.send(Day28Contract.Effect.ScrollToResults)
            }.onFailure { e ->
                _state.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }
}
