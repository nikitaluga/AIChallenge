package ru.nikitaluga.aichallenge.dev

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import ru.nikitaluga.aichallenge.api.ChatMessage
import ru.nikitaluga.aichallenge.api.RouterAiApiService
import ru.nikitaluga.aichallenge.api.ToolDefinition
import ru.nikitaluga.aichallenge.api.ToolFunction
import ru.nikitaluga.aichallenge.api.ToolParameters
import ru.nikitaluga.aichallenge.api.ToolProperty
import java.io.File

private const val CHAT_MODEL = "openai/gpt-4o-mini"
private const val MAX_TOOL_ITERATIONS = 3
private const val THRESHOLD = 0.30f

// ── HTTP models ───────────────────────────────────────────────────────────────

@Serializable
data class DevDocsStatsResponse(
    val hasIndex: Boolean,
    val totalChunks: Int = 0,
    val docsIndexed: Int = 0,
    val model: String = "",
    val createdAt: String = "",
)

@Serializable
private data class DevIndexResponse(
    val success: Boolean,
    val totalChunks: Int,
    val docsIndexed: Int,
    val message: String,
)

@Serializable
data class DevToolInfo(
    val name: String,
    val description: String,
    val inputSchema: JsonObject,
)

@Serializable
data class DevMcpToolsListResponse(val tools: List<DevToolInfo>)

@Serializable
data class DevMcpCallRequest(val name: String, val input: Map<String, String> = emptyMap())

@Serializable
data class DevMcpCallResponse(val result: String)

@Serializable
data class DevChatHistoryMessage(val role: String, val content: String)

@Serializable
data class DevChatRequest(
    val query: String,
    val history: List<DevChatHistoryMessage> = emptyList(),
    val k: Int = 5,
    val useMcp: Boolean = true,
)

@Serializable
data class DevChatSource(val source: String, val section: String? = null, val preview: String)

@Serializable
data class DevChatResponse(
    val answer: String,
    val sources: List<DevChatSource> = emptyList(),
    val toolsUsed: List<String> = emptyList(),
)

@Serializable
private data class ErrorResponse(val error: String)

// ── Route installer ───────────────────────────────────────────────────────────

fun Application.installDevAssistantRoutes(
    repository: DevDocsRepository,
    indexer: DevDocsIndexer,
    apiService: RouterAiApiService,
) {
    routing {
        route("/dev") {

            // ── GET /dev/docs/stats ──────────────────────────────────────────
            get("/docs/stats") {
                val index = repository.load()
                if (index == null) {
                    call.respond(DevDocsStatsResponse(hasIndex = false))
                    return@get
                }
                val sources = index.chunks.map { it.source }.toSet().size
                call.respond(
                    DevDocsStatsResponse(
                        hasIndex = true,
                        totalChunks = index.chunks.size,
                        docsIndexed = sources,
                        model = index.model,
                        createdAt = index.createdAt,
                    )
                )
            }

            // ── POST /dev/docs/index ─────────────────────────────────────────
            post("/docs/index") {
                val result = runCatching { indexer.buildIndex() }.getOrElse { e ->
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        DevIndexResponse(success = false, totalChunks = 0, docsIndexed = 0, message = "Ошибка: ${e.message}")
                    )
                    return@post
                }
                call.respond(
                    DevIndexResponse(
                        success = result.success,
                        totalChunks = result.totalChunks,
                        docsIndexed = result.docsIndexed,
                        message = result.message,
                    )
                )
            }

            // ── GET /dev/mcp/tools ───────────────────────────────────────────
            get("/mcp/tools") {
                call.respond(DevMcpToolsListResponse(tools = buildToolInfoList()))
            }

            // ── POST /dev/mcp/call ───────────────────────────────────────────
            post("/mcp/call") {
                val request = runCatching { call.receive<DevMcpCallRequest>() }.getOrElse {
                    call.respond(HttpStatusCode.BadRequest, DevMcpCallResponse("Неверный формат запроса"))
                    return@post
                }
                val result = executeGitTool(request.name, request.input)
                if (result == null) {
                    call.respond(HttpStatusCode.NotFound, DevMcpCallResponse("Инструмент '${request.name}' не найден"))
                } else {
                    call.respond(DevMcpCallResponse(result = result))
                }
            }

            // ── POST /dev/chat ───────────────────────────────────────────────
            post("/chat") {
                val request = runCatching { call.receive<DevChatRequest>() }.getOrElse {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("Неверный формат запроса"))
                    return@post
                }
                if (request.query.isBlank()) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("Параметр 'query' обязателен"))
                    return@post
                }

                val result = runCatching {
                    buildDevAnswer(request, repository, indexer, apiService)
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

private suspend fun buildDevAnswer(
    request: DevChatRequest,
    repository: DevDocsRepository,
    indexer: DevDocsIndexer,
    apiService: RouterAiApiService,
): DevChatResponse {
    // RAG search
    val ragChunks = indexer.search(request.query, request.k)
    val topScore = ragChunks.maxOfOrNull { it.score } ?: 0f
    val hasRelevantChunks = ragChunks.isNotEmpty() && topScore >= THRESHOLD

    val ragContext = if (hasRelevantChunks) {
        ragChunks.joinToString("\n\n---\n\n") { chunk ->
            buildString {
                append("Источник: ${chunk.source}")
                if (chunk.section != null) append(" / ${chunk.section}")
                append("\n")
                append(chunk.text.take(600))
            }
        }
    } else ""

    val systemPrompt = buildString {
        append("Ты — ассистент разработчика проекта AIChallenge (Kotlin Multiplatform).\n")
        append("Используй инструменты git для получения актуального контекста репозитория.\n")
        if (ragContext.isNotEmpty()) {
            append("При ответе опирайся на документацию ниже — указывай название файла-источника.\n")
            append("\n=== ДОКУМЕНТАЦИЯ ПРОЕКТА ===\n$ragContext\n===\n")
        } else {
            append("Документация по этой теме не найдена — отвечай из общих знаний о KMP/Ktor/Compose.\n")
        }
        append("Отвечай на русском языке. Будь конкретным, ссылайся на файлы и классы.")
    }

    // Build message history
    val messages = mutableListOf<ChatMessage>()
    messages.add(ChatMessage(role = "system", content = systemPrompt))
    request.history.takeLast(10).forEach { msg ->
        messages.add(ChatMessage(role = msg.role, content = msg.content))
    }
    messages.add(ChatMessage(role = "user", content = request.query))

    val tools = if (request.useMcp) buildToolDefinitions() else emptyList()
    val toolsUsed = mutableListOf<String>()

    // Tool-calling loop
    var iteration = 0
    var currentMessages = messages.toMutableList()

    while (iteration < MAX_TOOL_ITERATIONS) {
        iteration++
        val result = if (tools.isNotEmpty()) {
            apiService.sendMessagesWithTools(messages = currentMessages, tools = tools, model = CHAT_MODEL)
        } else {
            val plainResult = apiService.sendMessages(messages = currentMessages, model = CHAT_MODEL)
            // Wrap in ToolCallResult-like structure — just get content
            return DevChatResponse(
                answer = plainResult.content ?: "",
                sources = buildSources(ragChunks, hasRelevantChunks),
                toolsUsed = toolsUsed,
            )
        }

        val currentToolCalls = result.toolCalls
        if (result.finishReason == "tool_calls" && !currentToolCalls.isNullOrEmpty()) {
            // Add assistant message with tool_calls
            currentMessages.add(ChatMessage(role = "assistant", content = result.content, toolCalls = currentToolCalls))

            // Execute each tool call
            currentToolCalls.forEach { toolCall ->
                val toolName = toolCall.function.name
                toolsUsed.add(toolName)
                val inputMap = runCatching {
                    json.decodeFromString<Map<String, String>>(toolCall.function.arguments)
                }.getOrDefault(emptyMap())

                val toolResult = executeGitTool(toolName, inputMap) ?: "Инструмент '$toolName' не найден"
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
            // Final answer
            val answer = result.content ?: ""
            return DevChatResponse(
                answer = answer,
                sources = buildSources(ragChunks, hasRelevantChunks),
                toolsUsed = toolsUsed,
            )
        }
    }

    // Max iterations reached — do a final plain call
    val finalResult = apiService.sendMessages(messages = currentMessages, model = CHAT_MODEL)
    return DevChatResponse(
        answer = finalResult.content ?: "",
        sources = buildSources(ragChunks, hasRelevantChunks),
        toolsUsed = toolsUsed,
    )
}

private fun buildSources(chunks: List<DevSearchResult>, hasRelevant: Boolean): List<DevChatSource> {
    if (!hasRelevant) return emptyList()
    return chunks.take(3).map { chunk ->
        DevChatSource(
            source = chunk.source,
            section = chunk.section,
            preview = chunk.text.take(120),
        )
    }
}

// ── Git tool execution ────────────────────────────────────────────────────────

private fun executeGitTool(name: String, input: Map<String, String>): String? = when (name) {
    "git_branch" -> runShell("git", "rev-parse", "--abbrev-ref", "HEAD")
    "git_status" -> runShell("git", "status", "--short")
    "git_diff" -> runShell("git", "diff", "--stat", "HEAD")
    "list_files" -> {
        val path = input["path"]?.ifBlank { null } ?: "."
        listProjectFiles(path)
    }
    else -> null
}

private fun runShell(vararg args: String): String {
    val projectRoot = detectProjectRoot()
    return runCatching {
        ProcessBuilder(*args)
            .directory(projectRoot)
            .redirectErrorStream(true)
            .start()
            .inputStream.bufferedReader()
            .readText()
            .trim()
            .take(2000)
            .ifEmpty { "(нет изменений)" }
    }.getOrElse { "Ошибка выполнения команды: ${it.message}" }
}

private fun listProjectFiles(relativePath: String): String {
    val root = detectProjectRoot()
    val target = File(root, relativePath).canonicalFile
    if (!target.exists()) return "Директория не найдена: $relativePath"
    return target.walkTopDown()
        .maxDepth(2)
        .filter { it.isFile }
        .take(50)
        .map { it.relativeTo(root).path }
        .sorted()
        .joinToString("\n")
        .ifEmpty { "(нет файлов)" }
}

private fun detectProjectRoot(): File {
    val cwd = File(".").canonicalFile
    return when {
        File(cwd, ".specs").exists() -> cwd
        File(cwd.parentFile, ".specs").exists() -> cwd.parentFile
        else -> cwd
    }
}

// ── Tool definitions ──────────────────────────────────────────────────────────

private fun buildToolDefinitions(): List<ToolDefinition> = listOf(
    ToolDefinition(
        function = ToolFunction(
            name = "git_branch",
            description = "Получить текущую git-ветку репозитория",
            parameters = ToolParameters(properties = emptyMap(), required = emptyList()),
        )
    ),
    ToolDefinition(
        function = ToolFunction(
            name = "git_status",
            description = "Получить список изменённых файлов (git status --short)",
            parameters = ToolParameters(properties = emptyMap(), required = emptyList()),
        )
    ),
    ToolDefinition(
        function = ToolFunction(
            name = "git_diff",
            description = "Получить статистику изменений от HEAD (git diff --stat HEAD)",
            parameters = ToolParameters(properties = emptyMap(), required = emptyList()),
        )
    ),
    ToolDefinition(
        function = ToolFunction(
            name = "list_files",
            description = "Получить список файлов в указанной директории проекта (глубина 2 уровня)",
            parameters = ToolParameters(
                properties = mapOf(
                    "path" to ToolProperty(
                        type = "string",
                        description = "Относительный путь от корня проекта, например 'server/src' или 'composeApp'",
                    )
                ),
                required = emptyList(),
            ),
        )
    ),
)

private fun buildToolInfoList(): List<DevToolInfo> {
    val noParams = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {}
        put("required", buildJsonArray {})
    }
    val pathParam = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("path") {
                put("type", "string")
                put("description", "Относительный путь от корня проекта")
            }
        }
        put("required", buildJsonArray {})
    }
    return listOf(
        DevToolInfo("git_branch", "Получить текущую git-ветку", noParams),
        DevToolInfo("git_status", "Список изменённых файлов", noParams),
        DevToolInfo("git_diff", "Статистика изменений от HEAD", noParams),
        DevToolInfo("list_files", "Список файлов в директории проекта", pathParam),
    )
}
