package ru.nikitaluga.aichallenge.day32

data class State(
    val diffInput: String = "",
    val prTitle: String = "",
    val isLoading: Boolean = false,
    val review: ReviewResult? = null,
    val error: String? = null,
)

data class ReviewResult(
    val bugs: List<String>,
    val architecture: List<String>,
    val recommendations: List<String>,
    val summary: String,
    val diffLength: Int,
)

sealed interface Event {
    data class DiffChanged(val text: String) : Event
    data class TitleChanged(val text: String) : Event
    data object SubmitReview : Event
    data object ClearResult : Event
    data object InsertSampleDiff : Event
}

sealed interface Effect {
    data object ScrollToResult : Effect
}
