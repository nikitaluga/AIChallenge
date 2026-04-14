package ru.nikitaluga.aichallenge.day35

data class State(
    val diff: String = "",
    val context: String = "",
    val isLoading: Boolean = false,
    val results: List<String> = emptyList(),
    val error: String? = null,
    val copiedMessage: String? = null,
)

sealed interface Event {
    data class DiffChanged(val text: String) : Event
    data class ContextChanged(val text: String) : Event
    data object Generate : Event
    data class QuickPromptSelected(val diff: String) : Event
    data class CopyMessage(val text: String) : Event
    data object DismissError : Event
    data object DismissCopied : Event
}

sealed interface Effect {
    data class CopyToClipboard(val text: String) : Effect
    data object ScrollToResults : Effect
}

val QUICK_DIFFS = listOf(
    "+ fun validateEmail(email: String) = email.contains('@')",
    "- val timeout = 5000\n+ val timeout = 30_000",
    "+ data class UserSettings(val theme: String, val language: String)",
    "+ @Serializable\n+ data class ErrorResponse(val error: String)",
)
