package ru.nikitaluga.aichallenge.rag

import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import ru.nikitaluga.aichallenge.domain.model.ChunkingStrategy
import ru.nikitaluga.aichallenge.domain.model.RagChunkResult
import ru.nikitaluga.aichallenge.domain.model.RagIndexStats

object RagContract {

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

        // Compare tab: which strategy's samples to show
        val compareStrategy: ChunkingStrategy = ChunkingStrategy.FIXED,

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
        data class CompareStrategyChanged(val strategy: ChunkingStrategy) : Event
        data object BuildIndex : Event
        data object LoadStats : Event
        data object DismissError : Event
    }

    sealed interface Effect {
        data object ScrollToBottom : Effect
    }
}
