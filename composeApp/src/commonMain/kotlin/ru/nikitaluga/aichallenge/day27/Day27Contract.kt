package ru.nikitaluga.aichallenge.day27

import androidx.compose.runtime.Immutable

object Day27Contract {

    val SAMPLE_QUESTIONS = listOf(
        "Привет! Кто ты?",
        "Объясни Kotlin Multiplatform в двух предложениях",
        "Напиши функцию Фибоначчи с мемоизацией на Kotlin",
        "Что такое MVI и зачем он нужен?",
        "Чем локальная LLM отличается от облачной?",
        "Напиши простой HTTP сервер на Ktor",
        "Объясни разницу между coroutine и thread",
        "Что такое RAG и как он помогает LLM?",
    )

    @Immutable
    data class Message(
        val role: String,   // "user" | "assistant"
        val content: String,
    )

    data class State(
        val messages: List<Message> = emptyList(),
        val streamingText: String = "",
        val isStreaming: Boolean = false,
        val inputText: String = "",
        val models: List<String> = emptyList(),
        val selectedModel: String = "llama3.2:3b",
        val isLoadingModels: Boolean = false,
        val error: String? = null,
    )

    sealed interface Event {
        data class InputChanged(val text: String) : Event
        data object SendMessage : Event
        data object ClearHistory : Event
        data class SelectModel(val model: String) : Event
        data object LoadModels : Event
        data object DismissError : Event
    }

    sealed interface Effect {
        data object ScrollToBottom : Effect
    }
}
