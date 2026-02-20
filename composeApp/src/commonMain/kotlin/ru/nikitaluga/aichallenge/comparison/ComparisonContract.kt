package ru.nikitaluga.aichallenge.comparison

private const val DEFAULT_PROMPT = "Напиши краткое руководство по бегу для начинающих"

object ComparisonContract {

    data class ComparisonCase(
        val label: String,
        val systemPrompt: String?,
        val maxTokens: Int = 1024,
        val stop: List<String>?,
        val prompt: String = DEFAULT_PROMPT,
        val validateJson: Boolean = false,
    ) {
        val paramsInfo: String = run {
            val sysText = systemPrompt
                ?.let {
                    val cut = it.indexOf('.').takeIf { idx -> idx in 0..79 }
                        ?.let { idx -> it.take(idx + 1) }
                        ?: it.take(80)
                    "\"$cut${if (cut.length < it.length) "…" else ""}\""
                }
                ?: "—"
            val stopText = stop
                ?.joinToString(", ", "[", "]") { "\"$it\"" }
                ?: "—"
            val promptLine = if (prompt != DEFAULT_PROMPT) "\nprompt: \"${prompt.take(40)}\"" else ""
            "system: $sysText\nmax_tokens: $maxTokens  •  stop: $stopText$promptLine"
        }
    }

    data class CardState(
        val isLoading: Boolean = false,
        val result: String? = null,
    )

    data class State(
        val fixedQuery: String = DEFAULT_PROMPT,
        val cases: List<ComparisonCase> = emptyList(),
        val cards: List<CardState> = emptyList(),
    )

    sealed interface Event {
        data class SendCard(val index: Int) : Event
    }
}
