package ru.nikitaluga.aichallenge.scheduler

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
import ru.nikitaluga.aichallenge.data.agent.SchedulerAgent
import ru.nikitaluga.aichallenge.domain.model.SchedulerChatMessage

class SchedulerViewModel : ViewModel() {

    private val apiService = RouterAiApiService()
    private val agent = SchedulerAgent(apiService = apiService)

    private val _state = MutableStateFlow(SchedulerContract.State())
    val state: StateFlow<SchedulerContract.State> = _state.asStateFlow()

    private val _effects = Channel<SchedulerContract.Effect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    init {
        loadSchedules()
    }

    fun onEvent(event: SchedulerContract.Event) {
        when (event) {
            is SchedulerContract.Event.InputChanged ->
                _state.update { it.copy(inputText = event.text) }

            SchedulerContract.Event.SendMessage -> sendMessage()

            is SchedulerContract.Event.DeleteSchedule -> deleteSchedule(event.id)

            SchedulerContract.Event.RefreshSchedules -> loadSchedules()

            SchedulerContract.Event.ClearHistory -> {
                agent.clearHistory()
                _state.update { it.copy(messages = emptyList()) }
            }

            SchedulerContract.Event.DismissError ->
                _state.update { it.copy(errorMessage = null) }
        }
    }

    private fun loadSchedules() {
        viewModelScope.launch {
            val schedules = agent.loadSchedules()
            _state.update { it.copy(schedules = schedules) }
        }
    }

    private fun sendMessage() {
        val text = _state.value.inputText.trim()
        if (text.isEmpty() || _state.value.isLoading) return

        _state.update { current ->
            current.copy(
                inputText = "",
                isLoading = true,
                messages = current.messages + SchedulerChatMessage(role = "user", content = text),
            )
        }

        viewModelScope.launch {
            try {
                val result = agent.sendMessage(text)
                val assistantMsg = SchedulerChatMessage(
                    role = "assistant",
                    content = result.content,
                    toolCallMade = result.toolCallMade,
                    toolName = result.toolName,
                )
                _state.update { it.copy(isLoading = false, messages = it.messages + assistantMsg) }
                _effects.send(SchedulerContract.Effect.ScrollToBottom)
                if (result.toolCallMade) loadSchedules()
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        isLoading = false,
                        messages = it.messages.dropLast(1),
                        errorMessage = apiService.friendlyError(e),
                    )
                }
            }
        }
    }

    private fun deleteSchedule(id: String) {
        viewModelScope.launch {
            try {
                agent.deleteSchedule(id)
                loadSchedules()
            } catch (e: Exception) {
                _state.update { it.copy(errorMessage = "Ошибка удаления: ${e.message}") }
            }
        }
    }
}
