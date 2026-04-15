package ru.nikitaluga.aichallenge.day36

data class State(
    val query: String = "",
    val rubric: String = "",
    val isLoading: Boolean = false,
    val iterations: List<IterationMsg> = emptyList(),
    val finalAnswer: String? = null,
    val error: String? = null,
)

data class IterationMsg(
    val attempt: Int,
    val draft: String,
    val critique: String,
    val score: Int,
    val isExpanded: Boolean = false,
)

sealed interface Event {
    data class QueryChanged(val text: String) : Event
    data class RubricChanged(val text: String) : Event
    data object Generate : Event
    data object Clear : Event
    data object DismissError : Event
    data class ToggleIteration(val attempt: Int) : Event
}

sealed interface Effect {
    data object ScrollToBottom : Effect
}

val QUICK_QUERIES = listOf(
    "Объясни корутины в Kotlin: зачем нужны и как работают",
    "Напиши алгоритм бинарного поиска с объяснением",
    "В чём разница между MVI и MVVM архитектурой?",
)

val QUICK_RUBRICS = listOf(
    "Точность и техническая глубина",
    "Краткость и ясность изложения",
    "Наличие конкретных примеров кода",
)