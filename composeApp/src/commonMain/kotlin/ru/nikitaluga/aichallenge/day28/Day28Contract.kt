package ru.nikitaluga.aichallenge.day28

import androidx.compose.runtime.Immutable
import ru.nikitaluga.aichallenge.domain.model.LocalRagChatResult
import ru.nikitaluga.aichallenge.domain.model.LocalRagStats

object Day28Contract {

    @Immutable
    data class State(
        val localStats: LocalRagStats? = null,
        val isIndexing: Boolean = false,
        val isLoading: Boolean = false,
        val query: String = "",
        val localResult: LocalRagChatResult? = null,
        val cloudResult: LocalRagChatResult? = null,
        val selectedModel: String = "llama3.2:3b",
        val availableModels: List<String> = emptyList(),
        val error: String? = null,
    )

    sealed interface Event {
        data object BuildIndex : Event
        data class QueryChanged(val query: String) : Event
        data object Compare : Event
        data class ModelSelected(val model: String) : Event
        data object DismissError : Event
    }

    sealed interface Effect {
        data object ScrollToResults : Effect
    }

    val SAMPLE_QUESTIONS = listOf(
        "Что такое MVI?",
        "Как работает RAG-пайплайн?",
        "Что такое TaskMemory?",
        "Объясни ViewModel в KMP",
        "Как добавить новый экран?",
    )
}
