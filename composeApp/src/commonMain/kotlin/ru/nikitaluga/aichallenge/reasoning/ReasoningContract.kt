package ru.nikitaluga.aichallenge.reasoning

object ReasoningContract {

    data class MethodState(
        val isLoading: Boolean = false,
        val result: String? = null,
        val error: String? = null,
        val generatedPrompt: String? = null,
    )

    data class State(
        val difficulty: Float = 3f,
        val generatedTask: String? = null,
        val isGeneratingTask: Boolean = false,
        val isSolving: Boolean = false,
        val method1: MethodState = MethodState(),
        val method2: MethodState = MethodState(),
        val method3: MethodState = MethodState(),
        val method4: MethodState = MethodState(),
        val comparison: MethodState = MethodState(),
    ) {
        val hasSolvingData: Boolean
            get() = listOf(method1, method2, method3, method4).any {
                it.isLoading || it.result != null || it.error != null
            }

        val hasComparisonData: Boolean
            get() = comparison.isLoading || comparison.result != null || comparison.error != null
    }

    sealed interface Event {
        data class DifficultyChanged(val value: Float) : Event
        data object GenerateTask : Event
        data object SolveAll : Event
        data object Reset : Event
    }
}
