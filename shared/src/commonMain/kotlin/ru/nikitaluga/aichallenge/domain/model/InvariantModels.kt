package ru.nikitaluga.aichallenge.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class Invariant(
    val id: String,
    val name: String,
    val rule: String,
    val enabled: Boolean = true,
)

data class InvariantViolation(
    val invariantId: String,
    val invariantName: String,
    val explanation: String,
)

data class ValidationResult(
    val passed: Boolean,
    val violations: List<InvariantViolation>,
)

data class InvariantChatMessage(
    val role: String,
    val content: String,
    val violations: List<InvariantViolation> = emptyList(),
    val wasRetried: Boolean = false,
)

data class InvariantAgentResult(
    val content: String,
    val violations: List<InvariantViolation>,
    val wasRetried: Boolean,
)
