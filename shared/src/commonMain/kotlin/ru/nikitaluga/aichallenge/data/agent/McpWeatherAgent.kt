package ru.nikitaluga.aichallenge.data.agent

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import ru.nikitaluga.aichallenge.api.ChatMessage
import ru.nikitaluga.aichallenge.api.RouterAiApiService
import ru.nikitaluga.aichallenge.api.ToolDefinition
import ru.nikitaluga.aichallenge.api.ToolFunction
import ru.nikitaluga.aichallenge.api.ToolParameters
import ru.nikitaluga.aichallenge.api.ToolProperty
import ru.nikitaluga.aichallenge.domain.model.McpAgentResult
import ru.nikitaluga.aichallenge.util.AgentConfig

/**
 * День 17 — MCP Weather Agent.
 *
 * Когда MCP подключён:
 *   1. Отправляет сообщения в LLM вместе с определением инструмента get_weather
 *   2. Если LLM возвращает tool_calls → агент вызывает MCP-сервер
 *   3. Результат погоды возвращается LLM для финального ответа
 *
 * Когда MCP отключён:
 *   - Отправляет запрос без инструментов → LLM отвечает из общих знаний
 */
class McpWeatherAgent(
    private val apiService: RouterAiApiService,
    private val mcpBaseUrl: String = AgentConfig.DEFAULT_SERVER_URL,
    private val model: String = DEFAULT_MODEL,
) {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
    private val history = mutableListOf<ChatMessage>()

    private val mcpClient = HttpClient {
        install(HttpTimeout) { requestTimeoutMillis = 30_000L }
        install(ContentNegotiation) { json(json) }
    }

    suspend fun sendMessage(text: String, isMcpConnected: Boolean): McpAgentResult {
        history.add(ChatMessage(role = "user", content = text))
        return try {
            if (isMcpConnected) sendWithTools() else sendWithoutTools()
        } catch (e: Exception) {
            history.removeLast()
            throw e
        }
    }

    fun clearHistory() = history.clear()

    // ── Private ───────────────────────────────────────────────────────────────

    private suspend fun sendWithTools(): McpAgentResult {
        val messages = buildList {
            add(ChatMessage(role = "system", content = SYSTEM_WITH_TOOLS))
            addAll(history)
        }

        val result = apiService.sendMessagesWithTools(
            messages = messages,
            tools = listOf(getWeatherTool()),
            model = model,
        )

        if (result.finishReason == "tool_calls" && !result.toolCalls.isNullOrEmpty()) {
            val toolCall = result.toolCalls.first()
            val toolInput = toolCall.function.arguments

            // Parse city from JSON arguments
            val city = runCatching {
                json.decodeFromString<Map<String, String>>(toolInput)["city"] ?: "Unknown"
            }.getOrDefault("Unknown")

            // Call MCP server
            val toolResult = callMcpTool("get_weather", mapOf("city" to city))

            // Add assistant tool-call message + tool result to history
            history.add(ChatMessage(role = "assistant", content = null, toolCalls = result.toolCalls))
            history.add(
                ChatMessage(
                    role = "tool",
                    content = toolResult,
                    toolCallId = toolCall.id,
                    name = toolCall.function.name,
                )
            )

            // Final LLM call to convert tool result into natural language
            val finalMessages = buildList {
                add(ChatMessage(role = "system", content = SYSTEM_WITH_TOOLS))
                addAll(history)
            }
            val finalResult = apiService.sendMessages(messages = finalMessages, model = model)
            history.add(ChatMessage(role = "assistant", content = finalResult.content))

            return McpAgentResult(
                content = finalResult.content,
                toolCallMade = true,
                toolName = toolCall.function.name,
                toolInput = toolInput,
                toolResult = toolResult,
            )
        }

        // LLM answered directly without invoking a tool
        history.add(ChatMessage(role = "assistant", content = result.content))
        return McpAgentResult(content = result.content, toolCallMade = false)
    }

    private suspend fun sendWithoutTools(): McpAgentResult {
        val messages = buildList {
            add(ChatMessage(role = "system", content = SYSTEM_WITHOUT_TOOLS))
            addAll(history)
        }
        val result = apiService.sendMessages(messages = messages, model = model)
        history.add(ChatMessage(role = "assistant", content = result.content))
        return McpAgentResult(content = result.content, toolCallMade = false)
    }

    private suspend fun callMcpTool(name: String, input: Map<String, String>): String {
        return runCatching {
            val response = mcpClient.post("$mcpBaseUrl/mcp/tools/call") {
                contentType(ContentType.Application.Json)
                setBody(McpCallRequest(name = name, input = input))
            }
            response.body<McpCallResponse>().result
        }.getOrElse { "Ошибка вызова MCP: ${it.message}" }
    }

    private fun getWeatherTool() = ToolDefinition(
        function = ToolFunction(
            name = "get_weather",
            description = "Получить текущую погоду для указанного города",
            parameters = ToolParameters(
                properties = mapOf(
                    "city" to ToolProperty(
                        type = "string",
                        description = "Название города, например: Москва, London, Tokyo",
                    )
                ),
                required = listOf("city"),
            ),
        )
    )

    // ── MCP request/response DTOs ─────────────────────────────────────────────

    @Serializable
    private data class McpCallRequest(val name: String, val input: Map<String, String>)

    @Serializable
    private data class McpCallResponse(val result: String)

    companion object {
        private const val DEFAULT_MODEL = "deepseek/deepseek-v3.2"
        private const val SYSTEM_WITH_TOOLS =
            "Ты метеорологический ассистент. Используй инструмент get_weather для получения актуальных данных о погоде. Отвечай на русском языке."
        private const val SYSTEM_WITHOUT_TOOLS =
            "Ты метеорологический ассистент. MCP-сервер отключён — отвечай из общих знаний, предупредив пользователя, что данные могут быть неактуальными. Отвечай на русском языке."
    }
}
