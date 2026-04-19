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
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import ru.nikitaluga.aichallenge.api.ChatMessage
import ru.nikitaluga.aichallenge.api.RouterAiApiService
import ru.nikitaluga.aichallenge.api.ToolCall
import ru.nikitaluga.aichallenge.api.ToolDefinition
import ru.nikitaluga.aichallenge.api.ToolFunction
import ru.nikitaluga.aichallenge.api.ToolParameters
import ru.nikitaluga.aichallenge.api.ToolProperty
import ru.nikitaluga.aichallenge.domain.model.PipelineAgentResult
import ru.nikitaluga.aichallenge.domain.model.PipelineToolStep
import ru.nikitaluga.aichallenge.domain.model.SavedFileInfo
import ru.nikitaluga.aichallenge.util.AgentConfig

/**
 * День 19 — Pipeline Agent.
 *
 * Демонстрирует композицию MCP-инструментов: LLM автоматически вызывает
 * несколько инструментов в правильном порядке для выполнения задачи:
 *
 *   1. get_weather(city)          — получить данные о погоде из OWM
 *   2. summarize_weather(data)    — сформировать компактную сводку
 *   3. save_to_file(name, text)   — сохранить результат на сервер
 *
 * Агент работает в цикле: после каждого вызова инструмента результат
 * добавляется в историю, и LLM снова получает управление — до тех пор,
 * пока не вернёт финальный текстовый ответ (не tool_calls).
 */
class PipelineAgent(
    private val apiService: RouterAiApiService,
    private val serverBaseUrl: String = AgentConfig.DEFAULT_SERVER_URL,
    private val model: String = DEFAULT_MODEL,
) {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
    private val history = mutableListOf<ChatMessage>()

    private val client = HttpClient {
        install(HttpTimeout) { requestTimeoutMillis = 30_000L }
        install(ContentNegotiation) { json(json) }
    }

    suspend fun sendMessage(text: String): PipelineAgentResult {
        history.add(ChatMessage(role = "user", content = text))
        return try {
            runPipeline()
        } catch (e: Exception) {
            history.removeLast()
            throw e
        }
    }

    suspend fun loadSavedFiles(): List<SavedFileInfo> =
        client.get("$serverBaseUrl/pipeline/files")
            .body<FilesResponseDto>()
            .files
            .map { SavedFileInfo(filename = it.filename, sizeBytes = it.sizeBytes) }

    suspend fun loadFileContent(filename: String): String =
        client.get("$serverBaseUrl/pipeline/files/$filename").body()

    fun clearHistory() = history.clear()

    // ── Private ───────────────────────────────────────────────────────────────

    private suspend fun runPipeline(): PipelineAgentResult {
        val toolSteps = mutableListOf<PipelineToolStep>()
        val maxIterations = 5

        repeat(maxIterations) {
            val messages = buildList {
                add(ChatMessage(role = "system", content = SYSTEM_PROMPT))
                addAll(history)
            }

            val result = apiService.sendMessagesWithTools(
                messages = messages,
                tools = pipelineTools(),
                model = model,
            )

            if (result.finishReason == "tool_calls" && !result.toolCalls.isNullOrEmpty()) {
                val toolCall = result.toolCalls.first()
                val toolResult = executeToolCall(toolCall)

                toolSteps.add(
                    PipelineToolStep(
                        toolName = toolCall.function.name,
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
                        name = toolCall.function.name,
                    )
                )
                // Continue loop — LLM will decide whether to call another tool
                return@repeat
            }

            // LLM returned a final text answer — pipeline complete
            val content = result.content ?: ""
            history.add(ChatMessage(role = "assistant", content = content))
            return PipelineAgentResult(content = content, toolSteps = toolSteps)
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
        return PipelineAgentResult(content = content, toolSteps = toolSteps)
    }

    private suspend fun executeToolCall(toolCall: ToolCall): String {
        val args = runCatching {
            json.parseToJsonElement(toolCall.function.arguments).jsonObject
        }.getOrDefault(emptyMap())

        return when (toolCall.function.name) {
            "get_weather" -> {
                val city = args["city"]?.jsonPrimitive?.content
                    ?: return "Ошибка: не указан город"
                callPipelineTool("get_weather", mapOf("city" to city))
            }

            "summarize_weather" -> {
                val data = args["weather_data"]?.jsonPrimitive?.content
                    ?: return "Ошибка: не указаны данные о погоде"
                callPipelineTool("summarize_weather", mapOf("weather_data" to data))
            }

            "save_to_file" -> {
                val filename = args["filename"]?.jsonPrimitive?.content
                    ?: return "Ошибка: не указано имя файла"
                val content = args["content"]?.jsonPrimitive?.content
                    ?: return "Ошибка: не указано содержимое"
                callPipelineTool("save_to_file", mapOf("filename" to filename, "content" to content))
            }

            else -> "Неизвестный инструмент: ${toolCall.function.name}"
        }
    }

    private suspend fun callPipelineTool(name: String, input: Map<String, String>): String =
        runCatching {
            client.post("$serverBaseUrl/pipeline/tools/call") {
                contentType(ContentType.Application.Json)
                setBody(ToolCallRequestDto(name = name, input = input))
            }.body<ToolCallResponseDto>().result
        }.getOrElse { "Ошибка вызова инструмента $name: ${it.message}" }

    private fun pipelineTools() = listOf(
        ToolDefinition(
            function = ToolFunction(
                name = "get_weather",
                description = "Получить текущую погоду для указанного города через OpenWeatherMap",
                parameters = ToolParameters(
                    properties = mapOf(
                        "city" to ToolProperty("string", "Название города, например: Москва, London, Tokyo"),
                    ),
                    required = listOf("city"),
                ),
            )
        ),
        ToolDefinition(
            function = ToolFunction(
                name = "summarize_weather",
                description = "Сформировать компактную однострочную сводку из сырых данных о погоде",
                parameters = ToolParameters(
                    properties = mapOf(
                        "weather_data" to ToolProperty("string", "Многострочный текст с данными о погоде, полученный от get_weather"),
                    ),
                    required = listOf("weather_data"),
                ),
            )
        ),
        ToolDefinition(
            function = ToolFunction(
                name = "save_to_file",
                description = "Сохранить текстовый контент в файл в папке reports/ на сервере",
                parameters = ToolParameters(
                    properties = mapOf(
                        "filename" to ToolProperty("string", "Имя файла, например: moscow.txt или weather_report.txt"),
                        "content" to ToolProperty("string", "Текстовое содержимое для сохранения в файл"),
                    ),
                    required = listOf("filename", "content"),
                ),
            )
        ),
    )

    // ── Private DTOs ──────────────────────────────────────────────────────────

    @Serializable
    private data class ToolCallRequestDto(val name: String, val input: Map<String, String>)

    @Serializable
    private data class ToolCallResponseDto(val result: String)

    @Serializable
    private data class FilesResponseDto(val files: List<FileInfoDto>)

    @Serializable
    private data class FileInfoDto(val filename: String, val sizeBytes: Long)

    companion object {
        private const val DEFAULT_MODEL = "deepseek/deepseek-v3.2"
        private const val SYSTEM_PROMPT =
            "Ты пайплайн-агент для работы с погодой. " +
            "У тебя есть три инструмента, которые нужно вызывать по цепочке: " +
            "1) get_weather — получить данные о погоде для города; " +
            "2) summarize_weather — передать полученные данные и получить компактную сводку; " +
            "3) save_to_file — сохранить сводку в файл (имя файла: <город>.txt). " +
            "Правило: ВСЕГДА выполняй все три шага цепочки последовательно, не пропуская ни одного. " +
            "Только после save_to_file дай финальный текстовый ответ пользователю на русском языке."
    }
}
