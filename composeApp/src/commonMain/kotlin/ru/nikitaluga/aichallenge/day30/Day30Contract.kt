package ru.nikitaluga.aichallenge.day30

import androidx.compose.runtime.Immutable

object Day30Contract {

    enum class HealthStatus { Loading, Ok, Unavailable }

    @Immutable
    data class ChatEntry(val role: String, val content: String)

    @Immutable
    data class State(
        val healthStatus: HealthStatus = HealthStatus.Loading,
        val ollamaUrl: String = "",
        val models: List<String> = emptyList(),
        val activeRequests: Int = 0,
        val maxConcurrent: Int = 2,
        val selectedModel: String = "llama3.2:3b",
        val messages: List<ChatEntry> = emptyList(),
        val inputText: String = "",
        val isLoading: Boolean = false,
        val isRefreshingHealth: Boolean = false,
        val error: String? = null,
    )

    sealed interface Event {
        data object RefreshHealth : Event
        data class SelectModel(val model: String) : Event
        data class InputChanged(val text: String) : Event
        data object SendMessage : Event
        data object ClearChat : Event
        data object DismissError : Event
    }
}
