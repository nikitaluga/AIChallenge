package ru.nikitaluga.aichallenge.day25

import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import ru.nikitaluga.aichallenge.domain.model.ChunkingStrategy
import ru.nikitaluga.aichallenge.domain.model.RagCitation
import ru.nikitaluga.aichallenge.domain.model.RagSource
import ru.nikitaluga.aichallenge.domain.model.TaskMemory

object Day25Contract {

    // Сценарий 1: RAG-архитектура (вопросы 1–8)
    // Сценарий 2: MVI + Clean Architecture (вопросы 9–15)
    val SAMPLE_QUESTIONS = listOf(
        "Как работает RAG в этом проекте?",
        "Какие стратегии chunking поддерживаются и чем они отличаются?",
        "Как вычисляется косинусное сходство при поиске чанков?",
        "Что такое threshold и зачем он нужен в RAG?",
        "Как работает query rewrite и когда он применяется?",
        "Какие документы и файлы индексируются в RAG?",
        "Что возвращает /rag/chat/v2 и как парсятся citations?",
        "Как устроен RagRepository и как он защищает от race condition?",
        "Объясни MVI паттерн — что такое State, Event, Effect?",
        "Как ViewModel получает события и обновляет State?",
        "Что такое Effect и чем он отличается от State?",
        "Как добавить новый экран по правилам проекта?",
        "Какова роль shared-модуля в Clean Architecture?",
        "Как работают use cases в domain слое и что они возвращают?",
        "Как сервер Ktor интегрирован с shared-модулем?",
    )

    @Immutable
    data class Message(
        val role: String, // "user" | "assistant"
        val content: String,
        val sources: ImmutableList<RagSource> = persistentListOf(),
        val citations: ImmutableList<RagCitation> = persistentListOf(),
        val belowThreshold: Boolean = false,
    )

    data class State(
        val messages: ImmutableList<Message> = persistentListOf(),
        val inputText: String = "",
        val isLoading: Boolean = false,
        val taskMemory: TaskMemory = TaskMemory(),
        val topK: Int = 5,
        val threshold: Float = 0.35f,
        val strategy: ChunkingStrategy = ChunkingStrategy.STRUCTURAL,
        val isIndexing: Boolean = false,
        val errorMessage: String? = null,
    )

    sealed interface Event {
        data class InputChanged(val text: String) : Event
        data object SendMessage : Event
        data object ClearHistory : Event
        data class ThresholdChanged(val value: Float) : Event
        data class TopKChanged(val k: Int) : Event
        data object BuildIndex : Event
        data class SelectQuestion(val question: String) : Event
        data object DismissError : Event
    }

    sealed interface Effect {
        data object ScrollToBottom : Effect
    }
}
