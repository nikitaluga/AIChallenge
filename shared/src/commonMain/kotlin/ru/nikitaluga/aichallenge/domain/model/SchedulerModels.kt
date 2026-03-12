package ru.nikitaluga.aichallenge.domain.model

/** Active schedule info displayed in the UI. */
data class ScheduleInfo(
    val id: String,
    val city: String,
    val hour: Int,
    val minute: Int,
    val lastReport: String?,
)

/** A single message in the scheduler chat UI. */
data class SchedulerChatMessage(
    val role: String,               // "user" | "assistant"
    val content: String,
    val toolCallMade: Boolean = false,
    val toolName: String? = null,
)

/** Result returned by SchedulerAgent.sendMessage(). */
data class SchedulerAgentResult(
    val content: String,
    val toolCallMade: Boolean,
    val toolName: String? = null,
)
