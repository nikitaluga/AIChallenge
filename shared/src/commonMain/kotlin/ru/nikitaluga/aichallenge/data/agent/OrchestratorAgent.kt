package ru.nikitaluga.aichallenge.data.agent

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import ru.nikitaluga.aichallenge.api.ChatMessage
import ru.nikitaluga.aichallenge.api.RouterAiApiService
import ru.nikitaluga.aichallenge.api.ToolDefinition
import ru.nikitaluga.aichallenge.api.ToolFunction
import ru.nikitaluga.aichallenge.api.ToolParameters
import ru.nikitaluga.aichallenge.api.ToolProperty
import ru.nikitaluga.aichallenge.domain.model.McpServerConfig
import ru.nikitaluga.aichallenge.domain.model.McpServerInfo
import ru.nikitaluga.aichallenge.domain.model.McpToolSummary
import ru.nikitaluga.aichallenge.domain.model.OrchestratorResult
import ru.nikitaluga.aichallenge.domain.model.OrchestratorToolStep

class OrchestratorAgent(
    private val apiService: RouterAiApiService,
    private val serverBaseUrl: String = "http://10.0.2.2:8080",
    private val model: String = "deepseek/deepseek-v3.2",
) {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
    private val history = mutableListOf<ChatMessage>()
    private val client = HttpClient {
        install(ContentNegotiation) { json(json) }
        install(HttpTimeout) {
            connectTimeoutMillis = 10_000
            requestTimeoutMillis = 30_000
            socketTimeoutMillis = 30_000
        }
    }

    private val servers = listOf(
        McpServerConfig("weather", "Weather MCP", "\uD83C\uDF24", "$serverBaseUrl/mcp/weather"),
        McpServerConfig("notes", "Notes MCP", "\uD83D\uDCDD", "$serverBaseUrl/mcp/notes"),
        McpServerConfig("calc", "Calc MCP", "\u2699\uFE0F", "$serverBaseUrl/mcp/calc"),
    )

    private val toolToServer = mutableMapOf<String, McpServerConfig>()
    private val allTools = mutableListOf<ToolDefinition>()

    suspend fun discoverTools(): List<McpServerInfo> {
        toolToServer.clear()
        allTools.clear()

        return servers.map { server ->
            runCatching {
                val response = client.get("${server.baseUrl}/tools/list").body<ToolsListResponse>()
                response.tools.forEach { dto ->
                    val toolDef = ToolDefinition(
                        function = ToolFunction(
                            name = dto.name,
                            description = dto.description,
                            parameters = parseParameters(dto.inputSchema),
                        )
                    )
                    allTools.add(toolDef)
                    toolToServer[dto.name] = server
                }
                McpServerInfo(
                    id = server.id,
                    displayName = server.displayName,
                    emoji = server.emoji,
                    toolCount = response.tools.size,
                    isOnline = true,
                    tools = response.tools.map { dto ->
                        McpToolSummary(
                            name = dto.name,
                            description = dto.description,
                            example = buildExample(dto.name, dto.inputSchema),
                        )
                    },
                )
            }.getOrElse {
                McpServerInfo(
                    id = server.id,
                    displayName = server.displayName,
                    emoji = server.emoji,
                    toolCount = 0,
                    isOnline = false,
                )
            }
        }
    }

    suspend fun sendMessage(text: String): OrchestratorResult {
        if (allTools.isEmpty()) {
            throw IllegalStateException("Серверы не обнаружены. Нажмите «Обновить» для повторного подключения.")
        }
        history.add(ChatMessage(role = "user", content = text))
        return try {
            runOrchestration()
        } catch (e: Exception) {
            history.removeLast()
            throw e
        }
    }

    fun clearHistory() = history.clear()

    // ── Private ───────────────────────────────────────────────────────────────

    private suspend fun runOrchestration(): OrchestratorResult {
        val toolSteps = mutableListOf<OrchestratorToolStep>()
        val maxIterations = 8

        repeat(maxIterations) {
            val messages = buildList {
                add(ChatMessage(role = "system", content = SYSTEM_PROMPT))
                addAll(history)
            }

            val tools = if (allTools.isNotEmpty()) allTools.toList() else emptyList()

            val result = apiService.sendMessagesWithTools(
                messages = messages,
                tools = tools,
                model = model,
            )

            if (result.finishReason == "tool_calls" && !result.toolCalls.isNullOrEmpty()) {
                val toolCall = result.toolCalls.first()
                val toolName = toolCall.function.name
                val server = toolToServer[toolName]

                val toolResult = if (server != null) {
                    val args = runCatching {
                        json.parseToJsonElement(toolCall.function.arguments).jsonObject
                            .entries.associate { (k, v) -> k to v.jsonPrimitive.content }
                    }.getOrDefault(emptyMap())
                    callMcpTool(server, toolName, args)
                } else {
                    "Ошибка: инструмент '$toolName' не найден ни на одном сервере"
                }

                toolSteps.add(
                    OrchestratorToolStep(
                        serverId = server?.id ?: "unknown",
                        serverEmoji = server?.emoji ?: "?",
                        toolName = toolName,
                        toolInput = toolCall.function.arguments,
                        toolResult = toolResult,
                    )
                )

                history.add(ChatMessage(role = "assistant", content = null, toolCalls = result.toolCalls))
                history.add(
                    ChatMessage(
                        role = "tool",
                        content = toolResult,
                        toolCallId = toolCall.id,
                        name = toolName,
                    )
                )
                return@repeat
            }

            val content = result.content ?: ""
            history.add(ChatMessage(role = "assistant", content = content))
            return OrchestratorResult(content = content, toolSteps = toolSteps)
        }

        // Max iterations reached — request final summary
        val finalMessages = buildList {
            add(ChatMessage(role = "system", content = SYSTEM_PROMPT))
            addAll(history)
            add(ChatMessage(role = "user", content = "Подведи итог выполненных шагов."))
        }
        val finalResult = apiService.sendMessages(messages = finalMessages, model = model)
        val content = finalResult.content ?: ""
        history.add(ChatMessage(role = "assistant", content = content))
        return OrchestratorResult(content = content, toolSteps = toolSteps)
    }

    private suspend fun callMcpTool(server: McpServerConfig, name: String, input: Map<String, String>): String =
        runCatching {
            client.post("${server.baseUrl}/tools/call") {
                contentType(ContentType.Application.Json)
                setBody(ToolCallRequestDto(name = name, input = input))
            }.body<ToolCallResponseDto>().result
        }.getOrElse { "Ошибка вызова инструмента $name на ${server.id}: ${it.message}" }

    private fun buildExample(toolName: String, schema: JsonObject): String {
        val required = schema["required"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()
        if (required.isEmpty()) return "$toolName()"
        val exampleValues = mapOf(
            "city" to "Москва",
            "title" to "заметка",
            "content" to "текст",
            "value" to "5",
            "from_unit" to "celsius",
            "to_unit" to "fahrenheit",
            "expression" to "2 + 3 * 4",
        )
        val args = required.joinToString(", ") { param ->
            "$param: ${exampleValues[param] ?: param}"
        }
        return "$toolName($args)"
    }

    private fun parseParameters(schema: JsonObject): ToolParameters {
        val props = schema["properties"]?.jsonObject ?: return ToolParameters(properties = emptyMap())
        val required = schema["required"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()
        val properties = props.mapValues { (_, v) ->
            val obj = v.jsonObject
            ToolProperty(
                type = obj["type"]?.jsonPrimitive?.content ?: "string",
                description = obj["description"]?.jsonPrimitive?.content ?: "",
            )
        }
        return ToolParameters(properties = properties, required = required)
    }

    // ── Private DTOs ──────────────────────────────────────────────────────────

    @Serializable
    private data class ToolsListResponse(val tools: List<ToolInfoDto>)

    @Serializable
    private data class ToolInfoDto(val name: String, val description: String, val inputSchema: JsonObject)

    @Serializable
    private data class ToolCallRequestDto(val name: String, val input: Map<String, String>)

    @Serializable
    private data class ToolCallResponseDto(val result: String)

    companion object {
        private const val SYSTEM_PROMPT =
            "Ты агент-оркестратор с доступом к нескольким MCP-серверам: " +
            "Weather (погода), Notes (заметки), Calc (вычисления и конвертация единиц). " +
            "Используй инструменты с нужных серверов для выполнения запроса пользователя. " +
            "Можешь вызывать инструменты с разных серверов в любом порядке. " +
            "Отвечай на русском языке."
    }
}
