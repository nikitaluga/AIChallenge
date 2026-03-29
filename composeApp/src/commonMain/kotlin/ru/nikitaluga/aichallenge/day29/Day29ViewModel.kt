package ru.nikitaluga.aichallenge.day29

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.nikitaluga.aichallenge.data.agent.LocalLlmStreamAgent
import ru.nikitaluga.aichallenge.data.agent.LocalOptimizationAgent

class Day29ViewModel : ViewModel() {

    private val streamAgent = LocalLlmStreamAgent()
    private val optimizationAgent = LocalOptimizationAgent()

    private val _state = MutableStateFlow(Day29Contract.State())
    val state: StateFlow<Day29Contract.State> = _state.asStateFlow()

    init {
        loadModels()
    }

    fun onEvent(event: Day29Contract.Event) {
        when (event) {
            is Day29Contract.Event.QueryChanged ->
                _state.update { it.copy(query = event.text) }

            is Day29Contract.Event.SelectQuery ->
                _state.update { it.copy(query = event.query) }

            Day29Contract.Event.RunBenchmark -> runBenchmark()
            Day29Contract.Event.RunJudge -> runJudge()

            Day29Contract.Event.ClearResults ->
                _state.update { it.copy(result = null, judgeResult = null, error = null) }

            Day29Contract.Event.DismissError ->
                _state.update { it.copy(error = null) }

            // Before params
            is Day29Contract.Event.BeforeModelChanged ->
                _state.update { it.copy(beforeModel = event.model) }

            is Day29Contract.Event.BeforeOptionsChanged ->
                _state.update { it.copy(beforeOptions = event.options) }

            is Day29Contract.Event.BeforeSystemPromptChanged ->
                _state.update { it.copy(beforeSystemPrompt = event.text) }

            Day29Contract.Event.ToggleBeforeExpanded ->
                _state.update { it.copy(beforeExpanded = !it.beforeExpanded) }

            // After params
            is Day29Contract.Event.AfterModelChanged ->
                _state.update { it.copy(afterModel = event.model) }

            is Day29Contract.Event.AfterOptionsChanged ->
                _state.update { it.copy(afterOptions = event.options) }

            is Day29Contract.Event.AfterSystemPromptChanged ->
                _state.update { it.copy(afterSystemPrompt = event.text) }

            Day29Contract.Event.ToggleAfterExpanded ->
                _state.update { it.copy(afterExpanded = !it.afterExpanded) }
        }
    }

    private fun loadModels() {
        viewModelScope.launch {
            runCatching { streamAgent.getModels() }
                .onSuccess { models ->
                    _state.update { state ->
                        state.copy(
                            availableModels = models,
                            beforeModel = if (state.beforeModel in models) state.beforeModel
                                else models.firstOrNull() ?: state.beforeModel,
                            afterModel = if (state.afterModel in models) state.afterModel
                                else models.getOrNull(1) ?: models.firstOrNull() ?: state.afterModel,
                        )
                    }
                }
        }
    }

    private fun runBenchmark() {
        val s = _state.value
        val query = s.query.trim()
        if (query.isEmpty() || s.isRunning) return

        _state.update { it.copy(isRunning = true, result = null, judgeResult = null, error = null) }

        viewModelScope.launch {
            runCatching {
                optimizationAgent.benchmark(
                    query = query,
                    beforeModel = s.beforeModel,
                    afterModel = s.afterModel,
                    beforeOptions = s.beforeOptions,
                    afterOptions = s.afterOptions,
                    beforeSystemPrompt = s.beforeSystemPrompt,
                    afterSystemPrompt = s.afterSystemPrompt,
                )
            }.onSuccess { result ->
                _state.update { it.copy(isRunning = false, result = result) }
            }.onFailure { e ->
                _state.update { it.copy(isRunning = false, error = e.message) }
            }
        }
    }

    private fun runJudge() {
        val s = _state.value
        val result = s.result ?: return
        if (s.isJudging) return

        _state.update { it.copy(isJudging = true, judgeResult = null, error = null) }

        viewModelScope.launch {
            runCatching {
                optimizationAgent.judge(
                    query = s.query,
                    answerA = result.before.reply,
                    answerB = result.after.reply,
                )
            }.onSuccess { judge ->
                _state.update { it.copy(isJudging = false, judgeResult = judge) }
            }.onFailure { e ->
                _state.update { it.copy(isJudging = false, error = e.message) }
            }
        }
    }
}
