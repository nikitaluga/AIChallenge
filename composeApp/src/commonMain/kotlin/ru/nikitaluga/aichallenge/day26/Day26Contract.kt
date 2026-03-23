package ru.nikitaluga.aichallenge.day26

import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

object Day26Contract {

    val SAMPLE_QUESTIONS = listOf(
        "Привет! Кто ты?",
        "Объясни Kotlin Multiplatform в двух предложениях",
        "Что такое MVI и зачем он нужен?",
        "Напиши функцию Фибоначчи с мемоизацией на Kotlin",
        "Чем локальная LLM отличается от облачной?",
        "Как работает Ollama? Что такое GGUF?",
        "Напиши простой HTTP сервер на Ktor",
        "Объясни разницу между coroutine и thread",
        "Что такое RAG и как он помогает LLM?",
        "Напиши регулярное выражение для валидации email",
    )

    @Immutable
    data class Message(
        val role: String,      // "user" | "assistant"
        val content: String,
        val latencyMs: Long? = null,
        val backend: String? = null,
    )

    data class State(
        val messages: ImmutableList<Message> = persistentListOf(),
        val inputText: String = "",
        val isLoading: Boolean = false,
        val useLocal: Boolean = true,
        val errorMessage: String? = null,
    )

    sealed interface Event {
        data class InputChanged(val text: String) : Event
        data object SendMessage : Event
        data object ClearHistory : Event
        data class ToggleBackend(val useLocal: Boolean) : Event
        data class SelectQuestion(val question: String) : Event
        data object DismissError : Event
    }

    sealed interface Effect {
        data object ScrollToBottom : Effect
    }
}
