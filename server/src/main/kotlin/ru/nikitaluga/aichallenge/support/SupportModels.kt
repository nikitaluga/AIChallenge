package ru.nikitaluga.aichallenge.support

import kotlinx.serialization.Serializable

// ── CRM data models ───────────────────────────────────────────────────────────

@Serializable
data class SupportUser(
    val id: String,
    val name: String,
    val email: String,
    val plan: String,          // "free" | "pro" | "enterprise"
    val registeredAt: String,
)

@Serializable
data class SupportTicket(
    val id: String,
    val userId: String,
    val subject: String,
    val description: String,
    val status: String,        // "open" | "in_progress" | "resolved"
    val createdAt: String,
    val messages: List<TicketMessage> = emptyList(),
)

@Serializable
data class TicketMessage(
    val role: String,
    val content: String,
    val createdAt: String,
)

// ── HTTP models ───────────────────────────────────────────────────────────────

@Serializable
data class SupportChatHistoryMessage(val role: String, val content: String)

@Serializable
data class SupportChatRequest(
    val userId: String,
    val query: String,
    val history: List<SupportChatHistoryMessage> = emptyList(),
)

@Serializable
data class SupportChatSource(
    val source: String,
    val section: String? = null,
    val preview: String,
)

@Serializable
data class SupportChatResponse(
    val answer: String,
    val sources: List<SupportChatSource> = emptyList(),
    val toolsUsed: List<String> = emptyList(),
)

@Serializable
data class UserInfoDto(val id: String, val name: String, val plan: String)

@Serializable
data class TicketInfoDto(
    val id: String,
    val subject: String,
    val status: String,
    val description: String = "",
    val createdAt: String = "",
)
