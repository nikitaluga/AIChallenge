package ru.nikitaluga.aichallenge.domain.model

data class McpServerConfig(
    val id: String,
    val displayName: String,
    val emoji: String,
    val baseUrl: String,
)

data class OrchestratorToolStep(
    val serverId: String,
    val serverEmoji: String,
    val toolName: String,
    val toolInput: String,
    val toolResult: String,
)

data class OrchestratorMessage(
    val role: String,
    val content: String,
    val toolSteps: List<OrchestratorToolStep> = emptyList(),
)

data class OrchestratorResult(
    val content: String,
    val toolSteps: List<OrchestratorToolStep>,
)

data class McpToolSummary(
    val name: String,
    val description: String,
    val example: String,
)

data class McpServerInfo(
    val id: String,
    val displayName: String,
    val emoji: String,
    val toolCount: Int,
    val isOnline: Boolean,
    val tools: List<McpToolSummary> = emptyList(),
)
