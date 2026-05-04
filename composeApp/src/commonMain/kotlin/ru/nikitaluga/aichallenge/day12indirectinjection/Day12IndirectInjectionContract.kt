package ru.nikitaluga.aichallenge.day12indirectinjection

data class State(
    val vectorType: VectorType = VectorType.EMAIL,
    val defenseModePerVector: Map<VectorType, DefenseMode> = emptyMap(),
    val isLoading: Boolean = false,
    val result: AttackResult? = null,
    val error: String? = null,
) {
    val defenseMode: DefenseMode get() = defenseModePerVector[vectorType] ?: DefenseMode.NONE
}

data class AttackResult(
    val vectorType: VectorType,
    val defenseMode: DefenseMode,
    val hiddenPayload: String,
    val visibleContent: String,
    val sanitizedContent: String,
    val agentOutput: String,
    val verdict: String,       // "BREACHED" | "HELD"
    val judgeReasoning: String,
)

enum class VectorType(val label: String, val apiKey: String, val description: String) {
    EMAIL("Email", "email", "HTML-комментарий в письме"),
    DOCUMENT("Документ", "document", "Zero-width символы в предложении"),
    WEBPAGE("Веб-страница", "webpage", "Скрытый div с инструкцией"),
    COPILOT("Copilot", "copilot", "Инъекция через комментарий в коде"),
}

enum class DefenseMode(val label: String, val apiKey: String) {
    NONE("Без защиты", "none"),
    SANITIZE("Санация", "sanitize"),
    BOUNDARY("Граница", "boundary"),
    FULL("Полная", "full"),
}

sealed interface Event {
    data class SelectVector(val type: VectorType) : Event
    data class SelectDefense(val mode: DefenseMode) : Event
    data object RunAttack : Event
    data object Clear : Event
    data object DismissError : Event
}

sealed interface Effect {
    data object ScrollToResult : Effect
}
