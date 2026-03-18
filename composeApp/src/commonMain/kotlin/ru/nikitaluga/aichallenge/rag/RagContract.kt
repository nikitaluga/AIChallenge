package ru.nikitaluga.aichallenge.rag

import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import ru.nikitaluga.aichallenge.domain.model.ChunkingStrategy
import ru.nikitaluga.aichallenge.domain.model.RagChunkResult
import ru.nikitaluga.aichallenge.domain.model.RagCompareResult
import ru.nikitaluga.aichallenge.domain.model.RagIndexStats
import ru.nikitaluga.aichallenge.domain.model.SampleChunkInfo

object RagContract {

    val CONTROL_QUESTIONS = listOf(
        "Как работает PipelineAgent и какие инструменты он использует?",
        "Что такое MVI и как он реализован в этом проекте?",
        "Какие стратегии chunking поддерживаются в RAG и чем они отличаются?",
        "Как устроен SchedulerAgent и как создаются расписания?",
        "Как добавить новый экран в приложение по правилам проекта?",
        "Какие MCP-серверы используются в оркестрации?",
        "Как вычисляется cosine similarity для поиска чанков?",
        "Что делает WeatherSchedulerService и как он восстанавливается?",
        "Как устроена архитектура Clean Architecture в shared-модуле?",
        "Какой формат rag_index.json и что в нём хранится?",
    )

    enum class RagTab { CHAT, COMPARE }

    @Immutable
    data class RagMessage(
        val role: String,
        val content: String,
        val usedChunks: ImmutableList<RagChunkResult> = persistentListOf(),
    )

    data class State(
        // Navigation
        val tab: RagTab = RagTab.CHAT,

        // Chat
        val messages: ImmutableList<RagMessage> = persistentListOf(),
        val inputText: String = "",
        val isLoading: Boolean = false,

        // Configuration
        val chunkSize: Int = 300,
        val overlap: Int = 50,
        val topK: Int = 5,
        val activeStrategy: ChunkingStrategy = ChunkingStrategy.STRUCTURAL,

        // Index state
        val stats: RagIndexStats? = null,
        val isIndexing: Boolean = false,
        val indexMessage: String? = null,

        // Compare tab
        val compareInput: String = "",
        val compareResult: RagCompareResult? = null,
        val isComparing: Boolean = false,

        val errorMessage: String? = null,
    )

    sealed interface Event {
        data class InputChanged(val text: String) : Event
        data object SendMessage : Event
        data object ClearHistory : Event
        data class TabSelected(val tab: RagTab) : Event
        data class StrategyChanged(val strategy: ChunkingStrategy) : Event
        data class ChunkSizeChanged(val size: Int) : Event
        data class OverlapChanged(val overlap: Int) : Event
        data class TopKChanged(val k: Int) : Event
        data class CompareInputChanged(val text: String) : Event
        data object RunCompare : Event
        data class SelectControlQuestion(val question: String) : Event
        data object BuildIndex : Event
        data object LoadStats : Event
        data object DismissError : Event
    }

    sealed interface Effect {
        data object ScrollToBottom : Effect
    }
}
