package ru.nikitaluga.aichallenge.domain.model

/** A single message in the MCP weather chat UI. */
data class McpChatMessage(
    val role: String,           // "user" | "assistant"
    val content: String,
    val toolCallMade: Boolean = false,
    val toolName: String? = null,
    val toolResult: String? = null,
)

/** Result returned by McpWeatherAgent.sendMessage(). */
data class McpAgentResult(
    val content: String,
    val toolCallMade: Boolean,
    val toolName: String? = null,
    val toolInput: String? = null,   // raw JSON arguments string from LLM
    val toolResult: String? = null,  // raw text returned by MCP server
)
