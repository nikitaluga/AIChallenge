package ru.nikitaluga.aichallenge.context

object ContextContract {

    enum class Strategy(val label: String) {
        SlidingWindow("Окно"),
        Facts("Факты"),
        Branching("Ветки"),
    }

    data class State(
        val strategy: Strategy = Strategy.SlidingWindow,
        val messages: List<DisplayMessage> = emptyList(),
        val inputText: String = "",
        val isLoading: Boolean = false,

        // Facts strategy
        val facts: Map<String, String> = emptyMap(),
        val showFacts: Boolean = false,

        // Branching strategy
        val branches: List<BranchInfo> = emptyList(),
        val currentBranchId: String = "main",
        val topicShiftSuggested: Boolean = false,
        val suggestedBranchName: String? = null,
        val suggestedCurrentBranchName: String? = null,

        // Token stats
        val lastUsage: UsageInfo? = null,

        // Strategy-specific info
        val windowSize: Int = 10,
    )

    data class DisplayMessage(val role: String, val content: String)

    data class BranchInfo(val id: String, val name: String)

    data class UsageInfo(val prompt: Int, val completion: Int, val total: Int)

    sealed interface Event {
        data class InputChanged(val text: String) : Event
        data object SendMessage : Event
        data class SwitchStrategy(val strategy: Strategy) : Event
        data object ClearHistory : Event
        data object ToggleFacts : Event
        data object ConfirmCreateBranch : Event
        data object DismissTopicShift : Event
        data class SwitchBranch(val id: String) : Event
    }

    sealed interface Effect {
        data object ScrollToBottom : Effect
    }
}
