package ru.nikitaluga.aichallenge.day29

import androidx.compose.runtime.Immutable
import ru.nikitaluga.aichallenge.domain.model.BenchmarkResult
import ru.nikitaluga.aichallenge.domain.model.JudgeResult
import ru.nikitaluga.aichallenge.domain.model.OllamaOptions

object Day29Contract {

    const val KMP_SYSTEM_PROMPT =
        "You are an expert Kotlin Multiplatform developer.\n" +
        "Specialize in: KMP, Compose Multiplatform, Ktor, Coroutines.\n" +
        "Answer concisely and with code examples when relevant.\n" +
        "Respond in Russian."

    val SAMPLE_QUERIES = listOf(
        "Что такое MVI и зачем он нужен?",
        "Объясни Kotlin Coroutines в двух предложениях",
        "Напиши простой ViewModel для KMP",
        "Чем collectAsStateWithLifecycle лучше collectAsState?",
        "Что такое expect/actual в KMP?",
    )

    val DEFAULT_BEFORE_OPTIONS = OllamaOptions(
        temperature = 0.8f,
        topP = 0.9f,
        topK = 40,
        numPredict = -1,
        numCtx = 2048,
    )

    val DEFAULT_AFTER_OPTIONS = OllamaOptions(
        temperature = 0.3f,
        topP = 0.9f,
        topK = 40,
        numPredict = 512,
        numCtx = 4096,
    )

    @Immutable
    data class State(
        // Параметры "До" (baseline)
        val beforeModel: String = "llama3.2:3b",
        val beforeOptions: OllamaOptions = DEFAULT_BEFORE_OPTIONS,
        val beforeSystemPrompt: String = "",
        val beforeExpanded: Boolean = false,

        // Параметры "После" (optimized)
        val afterModel: String = "phi3:mini",
        val afterOptions: OllamaOptions = DEFAULT_AFTER_OPTIONS,
        val afterSystemPrompt: String = KMP_SYSTEM_PROMPT,
        val afterExpanded: Boolean = true,

        // Запрос и выполнение
        val query: String = "",
        val isRunning: Boolean = false,
        val result: BenchmarkResult? = null,
        val isJudging: Boolean = false,
        val judgeResult: JudgeResult? = null,

        // Модели
        val availableModels: List<String> = emptyList(),
        val error: String? = null,
    )

    sealed interface Event {
        data class QueryChanged(val text: String) : Event
        data object RunBenchmark : Event
        data object RunJudge : Event
        data object ClearResults : Event
        data class SelectQuery(val query: String) : Event
        data object DismissError : Event

        // Before params
        data class BeforeModelChanged(val model: String) : Event
        data class BeforeOptionsChanged(val options: OllamaOptions) : Event
        data class BeforeSystemPromptChanged(val text: String) : Event
        data object ToggleBeforeExpanded : Event

        // After params
        data class AfterModelChanged(val model: String) : Event
        data class AfterOptionsChanged(val options: OllamaOptions) : Event
        data class AfterSystemPromptChanged(val text: String) : Event
        data object ToggleAfterExpanded : Event
    }
}
