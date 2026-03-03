package ru.nikitaluga.aichallenge.memory

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
import ru.nikitaluga.aichallenge.data.agent.MemoryAgent
import ru.nikitaluga.aichallenge.domain.model.PendingFact

private const val SYSTEM_PROMPT = """Ты умный ассистент с памятью о пользователе.
Используй информацию из профиля и текущей задачи для персонализированных ответов.
Обращайся к пользователю по имени если знаешь его."""

class MemoryViewModel : ViewModel() {

    private val apiService = RouterAiApiService()

    private val agent = MemoryAgent(
        apiService = apiService,
        windowSize = 10,
        systemPrompt = SYSTEM_PROMPT,
        storageKey = "memory_day11",
    )

    private val _state = MutableStateFlow(
        MemoryContract.State(
            messages = agent.shortTermHistory.toDisplay(),
            taskFacts = agent.taskMemory,
            profileFacts = agent.profileMemory,
            windowSize = agent.windowSize,
        ),
    )
    val state: StateFlow<MemoryContract.State> = _state.asStateFlow()

    private val _effects = Channel<MemoryContract.Effect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    fun onEvent(event: MemoryContract.Event) {
        when (event) {
            is MemoryContract.Event.InputChanged ->
                _state.update { it.copy(inputText = event.text) }

            MemoryContract.Event.SendMessage -> sendMessage()

            is MemoryContract.Event.SwitchTab ->
                _state.update { it.copy(activeTab = event.tab) }

            MemoryContract.Event.StartNewTask -> startNewTask()
            MemoryContract.Event.ClearProfile -> clearProfile()

            is MemoryContract.Event.ConfirmFact -> confirmFact(event.fact)
            is MemoryContract.Event.RejectFact -> rejectFact(event.fact)
            MemoryContract.Event.ConfirmAllFacts -> confirmAllFacts()
            MemoryContract.Event.RejectAllFacts ->
                _state.update { it.copy(pendingFacts = emptyList()) }
        }
    }

    private fun sendMessage() {
        val text = _state.value.inputText.trim()
        if (text.isEmpty() || _state.value.isLoading) return
        _state.update { it.copy(inputText = "", isLoading = true) }

        viewModelScope.launch {
            try {
                val result = agent.sendMessage(text)
                _state.update {
                    it.copy(
                        isLoading = false,
                        messages = agent.shortTermHistory.toDisplay(),
                        pendingFacts = result.pendingFacts,
                        taskFacts = result.taskMemory,
                        profileFacts = result.profileMemory,
                        lastUsagePrompt = result.usage?.promptTokens ?: 0,
                        lastUsageCompletion = result.usage?.completionTokens ?: 0,
                        lastUsageTotal = result.usage?.totalTokens ?: 0,
                        showUsage = result.usage != null,
                    )
                }
                _effects.send(MemoryContract.Effect.ScrollToBottom)
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false) }
            }
        }
    }

    private fun confirmFact(fact: PendingFact) {
        agent.applyFacts(listOf(fact))
        _state.update {
            it.copy(
                pendingFacts = it.pendingFacts - fact,
                taskFacts = agent.taskMemory,
                profileFacts = agent.profileMemory,
            )
        }
    }

    private fun rejectFact(fact: PendingFact) {
        _state.update { it.copy(pendingFacts = it.pendingFacts - fact) }
    }

    private fun confirmAllFacts() {
        agent.applyFacts(_state.value.pendingFacts)
        _state.update {
            it.copy(
                pendingFacts = emptyList(),
                taskFacts = agent.taskMemory,
                profileFacts = agent.profileMemory,
            )
        }
    }

    private fun startNewTask() {
        agent.startNewTask()
        _state.update {
            it.copy(
                messages = emptyList(),
                taskFacts = emptyMap(),
                pendingFacts = emptyList(),
                showUsage = false,
            )
        }
    }

    private fun clearProfile() {
        agent.clearProfile()
        _state.update { it.copy(profileFacts = emptyMap()) }
    }

    private fun List<ru.nikitaluga.aichallenge.api.ChatMessage>.toDisplay() =
        map { MemoryContract.DisplayMessage(role = it.role, content = it.content) }
}
