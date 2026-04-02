package ru.nikitaluga.aichallenge.day34

data class State(
    val messages: List<ChatMsg> = emptyList(),
    val inputText: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
)

data class ChatMsg(
    val role: String,
    val content: String,
    val toolsUsed: List<String> = emptyList(),
)

sealed interface Event {
    data class InputChanged(val text: String) : Event
    data object SendMessage : Event
    data class QuickPromptSelected(val text: String) : Event
    data object ClearChat : Event
    data object DismissError : Event
}

sealed interface Effect {
    data object ScrollToBottom : Effect
}

val QUICK_PROMPTS = listOf(
    "Найди все usages RouterAiApiService",
    "Проверь MVI-инварианты composeApp",
    "Сгенерируй CHANGELOG",
    "Обнови CLAUDE.md — добавь секцию Day 34",
)
