package ru.nikitaluga.aichallenge.memory

import ru.nikitaluga.aichallenge.domain.model.PendingFact

object MemoryContract {

    enum class MemoryTab(val label: String) {
        Dialog("Диалог"),
        Task("Задача"),
        Profile("Профиль"),
    }

    data class State(
        val activeTab: MemoryTab = MemoryTab.Dialog,
        val messages: List<DisplayMessage> = emptyList(),
        val inputText: String = "",
        val isLoading: Boolean = false,

        /** Факты, предложенные LLM — ожидают подтверждения пользователя. */
        val pendingFacts: List<PendingFact> = emptyList(),

        /** Слой 2: рабочая память (только подтверждённые факты). */
        val taskFacts: Map<String, String> = emptyMap(),

        /** Слой 3: долговременная память (только подтверждённые факты). */
        val profileFacts: Map<String, String> = emptyMap(),

        val windowSize: Int = 10,
        val lastUsagePrompt: Int = 0,
        val lastUsageCompletion: Int = 0,
        val lastUsageTotal: Int = 0,
        val showUsage: Boolean = false,
    )

    data class DisplayMessage(val role: String, val content: String)

    sealed interface Event {
        data class InputChanged(val text: String) : Event
        data object SendMessage : Event
        data class SwitchTab(val tab: MemoryTab) : Event
        data object StartNewTask : Event
        data object ClearProfile : Event

        /** Подтвердить один факт и сохранить его в память. */
        data class ConfirmFact(val fact: PendingFact) : Event

        /** Отклонить один факт — не сохранять. */
        data class RejectFact(val fact: PendingFact) : Event

        /** Подтвердить все предложенные факты сразу. */
        data object ConfirmAllFacts : Event

        /** Отклонить все предложенные факты. */
        data object RejectAllFacts : Event
    }

    sealed interface Effect {
        data object ScrollToBottom : Effect
    }
}
