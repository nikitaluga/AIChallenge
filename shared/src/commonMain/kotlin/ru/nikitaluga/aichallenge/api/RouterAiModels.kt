package ru.nikitaluga.aichallenge.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ChatMessage(
    val role: String,
    val content: String,
)

@Serializable
data class ChatRequest(
    val model: String = "deepseek/deepseek-v3.2",
    val messages: List<ChatMessage>,
    val temperature: Double = 0.7,
    // null → omitted from JSON via explicitNulls=false in the service
    @SerialName("max_tokens") val maxTokens: Int = 1024,
    val stop: List<String>? = null,
)

@Serializable
data class ChatResponse(
    val choices: List<ChatChoice>,
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