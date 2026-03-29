package ru.nikitaluga.aichallenge.day30

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.nikitaluga.aichallenge.data.agent.LocalLlmAgent
import ru.nikitaluga.aichallenge.data.agent.LocalServiceAgent
import ru.nikitaluga.aichallenge.domain.model.LocalChatMessage

class Day30ViewModel : ViewModel() {

    private val serviceAgent = LocalServiceAgent()
    private val llmAgent = LocalLlmAgent()

    private val _state = MutableStateFlow(Day30Contract.State())
    val state: StateFlow<Day30Contract.State> = _state.asStateFlow()

    init {
        loadHealth()
    }

    fun onEvent(event: Day30Contract.Event) {
        when (event) {
            Day30Contract.Event.RefreshHealth -> loadHealth()
            is Day30Contract.Event.SelectModel -> _state.update { it.copy(selectedModel = event.model) }
            is Day30Contract.Event.InputChanged -> _state.update { it.copy(inputText = event.text) }
            Day30Contract.Event.SendMessage -> sendMessage()
            Day30Contract.Event.ClearChat -> _state.update { it.copy(messages = emptyList(), error = null) }
            Day30Contract.Event.DismissError -> _state.update { it.copy(error = null) }
        }
    }

    private fun loadHealth() {
        _state.update { it.copy(isRefreshingHealth = true, healthStatus = Day30Contract.HealthStatus.Loading) }
        viewModelScope.launch {
            runCatching { serviceAgent.health() }
                .onSuccess { health ->
                    _state.update {
                        it.copy(
                            isRefreshingHealth = false,
                            healthStatus = if (health.isOk) Day30Contract.HealthStatus.Ok else Day30Contract.HealthStatus.Unavailable,
                            ollamaUrl = health.ollamaUrl,
                            models = health.models,
                            activeRequests = health.activeRequests,
                            maxConcurrent = health.maxConcurrent,
                            selectedModel = if (it.selectedModel in health.models) it.selectedModel
                                else health.models.firstOrNull() ?: it.selectedModel,
                        )
                    }
                }
                .onFailure {
                    _state.update {
                        it.copy(
                            isRefreshingHealth = false,
                            healthStatus = Day30Contract.HealthStatus.Unavailable,
                        )
                    }
                }
        }
    }

    private fun sendMessage() {
        val s = _state.value
        val text = s.inputText.trim()
        if (text.isEmpty() || s.isLoading) return

        val userEntry = Day30Contract.ChatEntry(role = "user", content = text)
        _state.update { it.copy(messages = it.messages + userEntry, inputText = "", isLoading = true) }

        viewModelScope.launch {
            runCatching {
                val history = (_state.value.messages.dropLast(0))
                    .map { LocalChatMessage(role = it.role, content = it.content) }
                llmAgent.chat(messages = history, model = s.selectedModel)
            }.onSuccess { result ->
                val assistantEntry = Day30Contract.ChatEntry(role = "assistant", content = result.reply)
                _state.update { it.copy(isLoading = false, messages = it.messages + assistantEntry) }
            }.onFailure { e ->
                _state.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }
}
