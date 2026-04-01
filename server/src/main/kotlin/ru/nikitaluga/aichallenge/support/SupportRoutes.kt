package ru.nikitaluga.aichallenge.support

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import ru.nikitaluga.aichallenge.api.ChatMessage
import ru.nikitaluga.aichallenge.api.RouterAiApiService
import ru.nikitaluga.aichallenge.api.ToolDefinition
import ru.nikitaluga.aichallenge.api.ToolFunction
import ru.nikitaluga.aichallenge.api.ToolParameters
import ru.nikitaluga.aichallenge.api.ToolProperty

private const val CHAT_MODEL = "openai/gpt-4o-mini"
private const val MAX_TOOL_ITERATIONS = 3
private const val THRESHOLD = 0.30f

@Serializable
private data class ErrorResponse(val error: String)

fun Application.installSupportRoutes(
    repository: SupportRepository,
    indexer: SupportDocsIndexer,
    apiService: RouterAiApiService,
) {
    routing {
        route("/support") {

            // ── GET /support/users ────────────────────────────────────────────
            get("/users") {
                val users = repository.getAllUsers().map { UserInfoDto(it.id, it.name, it.plan) }
                call.respond(users)
            }

            // ── GET /support/tickets?userId=X ─────────────────────────────────
            get("/tickets") {
                val userId = call.request.queryParameters["userId"] ?: ""
                val tickets = repository.getTicketsForUser(userId).map {
                    TicketInfoDto(it.id, it.subject, it.status, it.description, it.createdAt)
                }
                call.respond(tickets)
            }

            // ── POST /support/chat ────────────────────────────────────────────
            post("/chat") {
                val request = runCatching { call.receive<SupportChatRequest>() }.getOrElse {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("Неверный формат запроса"))
                    return@post
                }
                if (request.query.isBlank()) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("Параметр 'query' обязателен"))
                    return@post
                }
                val result = runCatching {
                    buildSupportAnswer(request, repository, indexer, apiService)
                }.getOrElse { e ->
                    call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Ошибка: ${e.message}"))
                    return@post
                }
                call.respond(result)
            }
        }
    }
}

// ── Chat logic ────────────────────────────────────────────────────────────────

private val json = Json { ignoreUnknownKeys = true; explicitNulls = false; encodeDefaults = true }

private suspend fun buildSupportAnswer(
    request: SupportChatRequest,
    repository: SupportRepository,
    indexer: SupportDocsIndexer,
    apiService: RouterAiApiService,
): SupportChatResponse {
    // RAG search
    val ragChunks = indexer.search(request.query, k = 5)
    val topScore = ragChunks.maxOfOrNull { it.score } ?: 0f
    val hasRelevantChunks = ragChunks.isNotEmpty() && topScore >= THRESHOLD
    val ragContext = if (hasRelevantChunks) {
        ragChunks.joinToString("\n\n---\n\n") { chunk ->
            buildString {
                if (chunk.section != null) append("## ${chunk.section}\n")
                append(chunk.text.take(500))
            }
        }
    } else ""

    val systemPrompt = buildString {
        append("Ты — ассистент поддержки пользователей продукта AIChallenge.\n")
        append("Используй инструменты (get_user, list_tickets, get_ticket) для получения информации о пользователе и его тикетах.\n")
        append("Если пользователь упоминает конкретную проблему — проверь его тикеты через list_tickets и отвечай с учётом контекста.\n")
        append("Будь вежлив, конкретен и помогай пошагово.\n")
        if (ragContext.isNotEmpty()) {
            append("\n=== FAQ / ДОКУМЕНТАЦИЯ ===\n$ragContext\n===\n")
        }
        append("\nID текущего пользователя: ${request.userId}\n")
        append("Отвечай на русском языке.")
    }

    val messages = mutableListOf<ChatMessage>()
    messages.add(ChatMessage(role = "system", content = systemPrompt))
    request.history.takeLast(10).forEach { msg ->
        messages.add(ChatMessage(role = msg.role, content = msg.content))
    }
    messages.add(ChatMessage(role = "user", content = request.query))

    val tools = buildToolDefinitions()
    val toolsUsed = mutableListOf<String>()
    val currentMessages = messages.toMutableList()

    var iteration = 0
    while (iteration < MAX_TOOL_ITERATIONS) {
        iteration++
        val result = apiService.sendMessagesWithTools(messages = currentMessages, tools = tools, model = CHAT_MODEL)

        val toolCalls = result.toolCalls
        if (result.finishReason == "tool_calls" && !toolCalls.isNullOrEmpty()) {
            currentMessages.add(ChatMessage(role = "assistant", content = result.content, toolCalls = toolCalls))
            for (toolCall in toolCalls) {
                val toolName = toolCall.function.name
                toolsUsed.add(toolName)
                val inputMap = runCatching {
                    json.decodeFromString<Map<String, String>>(toolCall.function.arguments)
                }.getOrDefault(emptyMap())
                val toolResult = executeSupportTool(toolName, inputMap, repository)
                    ?: "Инструмент '$toolName' не найден"
                currentMessages.add(
                    ChatMessage(
                        role = "tool",
                        content = toolResult,
                        toolCallId = toolCall.id,
                        name = toolCall.function.name,
                    )
                )
            }
        } else {
            return SupportChatResponse(
                answer = result.content ?: "",
                sources = buildSources(ragChunks, hasRelevantChunks),
                toolsUsed = toolsUsed,
            )
        }
    }

    val finalResult = apiService.sendMessages(messages = currentMessages, model = CHAT_MODEL)
    return SupportChatResponse(
        answer = finalResult.content ?: "",
        sources = buildSources(ragChunks, hasRelevantChunks),
        toolsUsed = toolsUsed,
    )
}

private fun buildSources(chunks: List<SupportSearchResult>, hasRelevant: Boolean): List<SupportChatSource> {
    if (!hasRelevant) return emptyList()
    return chunks.take(3).map { chunk ->
        SupportChatSource(source = chunk.source, section = chunk.section, preview = chunk.text.take(120))
    }
}

// ── Tool execution ────────────────────────────────────────────────────────────

private suspend fun executeSupportTool(
    name: String,
    input: Map<String, String>,
    repo: SupportRepository,
): String? = when (name) {
    "get_user" -> {
        val user = repo.getUser(input["userId"] ?: "")
        if (user != null) json.encodeToString(SupportUser.serializer(), user)
        else "Пользователь не найден"
    }
    "get_ticket" -> {
        val ticket = repo.getTicket(input["ticketId"] ?: "")
        if (ticket != null) json.encodeToString(SupportTicket.serializer(), ticket)
        else "Тикет не найден"
    }
    "list_tickets" -> {
        val tickets = repo.getTicketsForUser(input["userId"] ?: "")
        json.encodeToString(ListSerializer(SupportTicket.serializer()), tickets)
    }
    "update_ticket_status" -> {
        val ticketId = input["ticketId"] ?: return null
        val status = input["status"] ?: return null
        val ok = repo.updateTicketStatus(ticketId, status)
        if (ok) "Статус тикета $ticketId обновлён до '$status'" else "Тикет $ticketId не найден"
    }
    else -> null
}

// ── Tool definitions ──────────────────────────────────────────────────────────

private fun buildToolDefinitions(): List<ToolDefinition> = listOf(
    ToolDefinition(
        function = ToolFunction(
            name = "get_user",
            description = "Получить профиль пользователя по его ID: имя, email, тарифный план, дата регистрации",
            parameters = ToolParameters(
                properties = mapOf("userId" to ToolProperty(type = "string", description = "ID пользователя")),
                required = listOf("userId"),
            ),
        )
    ),
    ToolDefinition(
        function = ToolFunction(
            name = "get_ticket",
            description = "Получить детали конкретного тикета поддержки по его ID",
            parameters = ToolParameters(
                properties = mapOf("ticketId" to ToolProperty(type = "string", description = "ID тикета, например T-001")),
                required = listOf("ticketId"),
            ),
        )
    ),
    ToolDefinition(
        function = ToolFunction(
            name = "list_tickets",
            description = "Получить список всех тикетов пользователя",
            parameters = ToolParameters(
                properties = mapOf("userId" to ToolProperty(type = "string", description = "ID пользователя")),
                required = listOf("userId"),
            ),
        )
    ),
    ToolDefinition(
        function = ToolFunction(
            name = "update_ticket_status",
            description = "Обновить статус тикета поддержки",
            parameters = ToolParameters(
                properties = mapOf(
                    "ticketId" to ToolProperty(type = "string", description = "ID тикета"),
                    "status" to ToolProperty(type = "string", description = "Новый статус: open, in_progress или resolved"),
                ),
                required = listOf("ticketId", "status"),
            ),
        )
    ),
)
