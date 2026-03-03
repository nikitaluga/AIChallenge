package ru.nikitaluga.aichallenge.domain.model

import ru.nikitaluga.aichallenge.api.Usage

/** Факт с маршрутом — предлагается LLM, подтверждается пользователем. */
data class PendingFact(
    val key: String,
    val value: String,
    /** "profile" — долговременная память, "task" — рабочая память. */
    val layer: String,
)

/** Результат одного хода агента с памятью. */
data class MemoryResult(
    val content: String,
    val usage: Usage?,
    /** Факты, предложенные LLM. Ещё НЕ сохранены — ждут подтверждения пользователя. */
    val pendingFacts: List<PendingFact>,
    /** Текущее состояние рабочей памяти (только подтверждённые ранее факты). */
    val taskMemory: Map<String, String>,
    /** Текущее состояние долговременной памяти (только подтверждённые ранее факты). */
    val profileMemory: Map<String, String>,
)
