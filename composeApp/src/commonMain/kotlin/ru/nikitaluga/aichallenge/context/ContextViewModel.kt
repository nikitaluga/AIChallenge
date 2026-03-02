package ru.nikitaluga.aichallenge.context

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.nikitaluga.aichallenge.api.ChatMessage
import ru.nikitaluga.aichallenge.api.RouterAiApiService
import ru.nikitaluga.aichallenge.api.Usage
import ru.nikitaluga.aichallenge.data.agent.BranchInfo
import ru.nikitaluga.aichallenge.data.agent.BranchingAgent
import ru.nikitaluga.aichallenge.data.agent.FactsAgent
import ru.nikitaluga.aichallenge.data.agent.SlidingWindowAgent

private const val SYSTEM_PROMPT =
    "Ты полезный ассистент. Отвечаешь на любые вопросы."

class ContextViewModel : ViewModel() {

    private val apiService = RouterAiApiService()

    private val slidingAgent = SlidingWindowAgent(
        apiService = apiService,
        windowSize = 10,
        systemPrompt = SYSTEM_PROMPT,
        storageKey = "ctx_day10_sliding",
    )
    private val factsAgent = FactsAgent(
        apiService = apiService,
        windowSize = 8,
        systemPrompt = SYSTEM_PROMPT,
        storageKey = "ctx_day10_facts",
    )
    private val branchingAgent = BranchingAgent(
        apiService = apiService,
        systemPrompt = SYSTEM_PROMPT,
        storageKey = "ctx_day10_branching",
    )

    private val _state = MutableStateFlow(
        ContextContract.State(
            messages = slidingAgent.history.toDisplay(),
            lastUsage = slidingAgent.lastUsage?.toInfo(),
            windowSize = slidingAgent.windowSize,
        ),
    )
    val state: StateFlow<ContextContract.State> = _state.asStateFlow()

    private val _effects = Channel<ContextContract.Effect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    fun onEvent(event: ContextContract.Event) {
        when (event) {
            is ContextContract.Event.InputChanged ->
                _state.update { it.copy(inputText = event.text) }

            ContextContract.Event.SendMessage -> sendMessage()

            is ContextContract.Event.SwitchStrategy -> switchStrategy(event.strategy)

            ContextContract.Event.ClearHistory -> clearHistory()

            ContextContract.Event.ToggleFacts ->
                _state.update { it.copy(showFacts = !it.showFacts) }

            ContextContract.Event.ConfirmCreateBranch -> createBranch()

            ContextContract.Event.DismissTopicShift ->
                _state.update { it.copy(topicShiftSuggested = false, suggestedBranchName = null, suggestedCurrentBranchName = null) }

            is ContextContract.Event.SwitchBranch -> switchBranch(event.id)
        }
    }

    private fun sendMessage() {
        val text = _state.value.inputText.trim()
        if (text.isEmpty() || _state.value.isLoading) return
        _state.update { it.copy(inputText = "", isLoading = true) }

        viewModelScope.launch {
            try {
                when (_state.value.strategy) {
                    ContextContract.Strategy.SlidingWindow -> {
                        val result = slidingAgent.sendMessage(text)
                        _state.update {
                            it.copy(
                                isLoading = false,
                                messages = slidingAgent.history.toDisplay(),
                                lastUsage = result.usage?.toInfo(),
                            )
                        }
                    }

                    ContextContract.Strategy.Facts -> {
                        val result = factsAgent.sendMessage(text)
                        _state.update {
                            it.copy(
                                isLoading = false,
                                messages = factsAgent.history.toDisplay(),
                                facts = result.facts,
                                lastUsage = result.usage?.toInfo(),
                            )
                        }
                    }

                    ContextContract.Strategy.Branching -> {
                        val result = branchingAgent.sendMessage(text)
                        _state.update {
                            it.copy(
                                isLoading = false,
                                messages = branchingAgent.branchHistory.toDisplay(),
                                branches = branchingAgent.branchList.toContract(),
                                currentBranchId = branchingAgent.currentBranchId,
                                topicShiftSuggested = result.topicShiftDetected,
                                suggestedBranchName = if (result.topicShiftDetected) result.suggestedBranchName else null,
                                suggestedCurrentBranchName = if (result.topicShiftDetected) result.suggestedCurrentBranchName else null,
                                lastUsage = result.usage?.toInfo(),
                            )
                        }
                    }
                }
                _effects.send(ContextContract.Effect.ScrollToBottom)
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false) }
            }
        }
    }

    private fun switchStrategy(strategy: ContextContract.Strategy) {
        val messages = when (strategy) {
            ContextContract.Strategy.SlidingWindow -> slidingAgent.history.toDisplay()
            ContextContract.Strategy.Facts -> factsAgent.history.toDisplay()
            ContextContract.Strategy.Branching -> branchingAgent.branchHistory.toDisplay()
        }
        _state.update {
            it.copy(
                strategy = strategy,
                messages = messages,
                topicShiftSuggested = false,
                facts = if (strategy == ContextContract.Strategy.Facts) factsAgent.facts else emptyMap(),
                branches = if (strategy == ContextContract.Strategy.Branching) {
                    branchingAgent.branchList.toContract()
                } else {
                    emptyList()
                },
                currentBranchId = if (strategy == ContextContract.Strategy.Branching) {
                    branchingAgent.currentBranchId
                } else {
                    "main"
                },
                lastUsage = when (strategy) {
                    ContextContract.Strategy.SlidingWindow -> slidingAgent.lastUsage?.toInfo()
                    ContextContract.Strategy.Facts -> factsAgent.lastUsage?.toInfo()
                    ContextContract.Strategy.Branching -> branchingAgent.lastUsage?.toInfo()
                },
                windowSize = when (strategy) {
                    ContextContract.Strategy.SlidingWindow -> slidingAgent.windowSize
                    ContextContract.Strategy.Facts -> factsAgent.windowSize
                    ContextContract.Strategy.Branching -> 0
                },
            )
        }
    }

    private fun clearHistory() {
        when (_state.value.strategy) {
            ContextContract.Strategy.SlidingWindow -> {
                slidingAgent.clearHistory()
                _state.update { it.copy(messages = emptyList(), lastUsage = null) }
            }
            ContextContract.Strategy.Facts -> {
                factsAgent.clearHistory()
                _state.update { it.copy(messages = emptyList(), facts = emptyMap(), lastUsage = null) }
            }
            ContextContract.Strategy.Branching -> {
                branchingAgent.clearHistory()
                _state.update {
                    it.copy(
                        messages = emptyList(),
                        branches = branchingAgent.branchList.toContract(),
                        currentBranchId = branchingAgent.currentBranchId,
                        topicShiftSuggested = false,
                        lastUsage = null,
                    )
                }
            }
        }
    }

    private fun createBranch() {
        branchingAgent.createBranch(
            name = _state.value.suggestedBranchName,
            currentBranchName = _state.value.suggestedCurrentBranchName,
        )
        _state.update {
            it.copy(
                messages = branchingAgent.branchHistory.toDisplay(),
                branches = branchingAgent.branchList.toContract(),
                currentBranchId = branchingAgent.currentBranchId,
                topicShiftSuggested = false,
                suggestedBranchName = null,
                suggestedCurrentBranchName = null,
            )
        }
    }

    private fun switchBranch(id: String) {
        branchingAgent.switchBranch(id)
        _state.update {
            it.copy(
                messages = branchingAgent.branchHistory.toDisplay(),
                currentBranchId = branchingAgent.currentBranchId,
            )
        }
    }

    private fun List<ChatMessage>.toDisplay() =
        map { ContextContract.DisplayMessage(role = it.role, content = it.content) }

    private fun Usage.toInfo() =
        ContextContract.UsageInfo(prompt = promptTokens, completion = completionTokens, total = totalTokens)

    private fun List<BranchInfo>.toContract() =
        map { ContextContract.BranchInfo(id = it.id, name = it.name) }
}
