package ru.nikitaluga.aichallenge.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ChatMessage(
    val role: String,
    // null when the message carries tool_calls instead of text content
    val content: String? = null,
    // Some reasoning models (e.g. nvidia/nemotron-nano-9b-v2) return an empty
    // content and put the actual answer here instead.
    val reasoning: String? = null,
    @SerialName("tool_calls") val toolCalls: List<ToolCall>? = null,
    @SerialName("tool_call_id") val toolCallId: String? = null,
    val name: String? = null,
) {
    val effectiveContent: String get() = (content ?: "").ifBlank { reasoning.orEmpty() }
}

@Serializable
data class ChatRequest(
    val model: String = "deepseek/deepseek-v3.2",
    val messages: List<ChatMessage>,
    val temperature: Double = 0.7,
    // null → omitted from JSON via explicitNulls=false in the service
    @SerialName("max_tokens") val maxTokens: Int? = null,
    val stop: List<String>? = null,
    val stream: Boolean? = null,
    val tools: List<ToolDefinition>? = null,
)

// ── Tool definition (sent to LLM) ─────────────────────────────────────────────

@Serializable
data class ToolDefinition(
    val type: String = "function",
    val function: ToolFunction,
)

@Serializable
data class ToolFunction(
    val name: String,
    val description: String,
    val parameters: ToolParameters,
)

@Serializable
data class ToolParameters(
    val type: String = "object",
    val properties: Map<String, ToolProperty>,
    val required: List<String> = emptyList(),
)

@Serializable
data class ToolProperty(
    val type: String,
    val description: String,
)

// ── Tool call (returned by LLM when it wants to invoke a tool) ─────────────────

@Serializable
data class ToolCall(
    val id: String = "",
    val type: String = "function",
    val function: ToolCallFunction,
)

@Serializable
data class ToolCallFunction(
    val name: String,
    val arguments: String, // JSON string, e.g. {"city":"Moscow"}
)

/** Wraps the LLM response when tools were provided. */
data class ToolCallResult(
    val content: String,
    val finishReason: String?,
    val toolCalls: List<ToolCall>?,
)

// ── Streaming response models (SSE chunks) ────────────────────────────────────

@Serializable
data class ChatStreamChunk(
    val choices: List<StreamChoice> = emptyList(),
)

@Serializable
data class StreamChoice(
    val delta: StreamDelta,
    @SerialName("finish_reason") val finishReason: String? = null,
)

@Serializable
data class StreamDelta(
    val content: String? = null,
    val role: String? = null,
)

@Serializable
data class Usage(
    @SerialName("prompt_tokens") val promptTokens: Int = 0,
    @SerialName("completion_tokens") val completionTokens: Int = 0,
    @SerialName("total_tokens") val totalTokens: Int = 0,
)

@Serializable
data class ChatResponse(
    val choices: List<ChatChoice>,
    val usage: Usage? = null,
)

data class MessageWithMetrics(
    val content: String,
    val usage: Usage?,
)

@Serializable
data class ChatChoice(
    val message: ChatMessage,
    @SerialName("finish_reason") val finishReason: String? = null,
)

@Serializable
data class ApiError(
    val error: ApiErrorDetail,
)

@Serializable
data class ApiErrorDetail(
    val message: String,
    val type: String? = null,
)

/**
 * Some RouteAI error responses use a flat string instead of an object:
 * `{"error": "401 Unauthorized"}`.
 */
@Serializable
data class ApiSimpleError(
    val error: String,
)
