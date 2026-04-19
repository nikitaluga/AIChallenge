package ru.nikitaluga.aichallenge.data.agent

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import ru.nikitaluga.aichallenge.api.ChatMessage
import ru.nikitaluga.aichallenge.api.RouterAiApiService
import ru.nikitaluga.aichallenge.api.ToolCall
import ru.nikitaluga.aichallenge.api.ToolDefinition
import ru.nikitaluga.aichallenge.api.ToolFunction
import ru.nikitaluga.aichallenge.api.ToolParameters
import ru.nikitaluga.aichallenge.api.ToolProperty
import ru.nikitaluga.aichallenge.domain.model.ScheduleInfo
import ru.nikitaluga.aichallenge.domain.model.SchedulerAgentResult

/**
 * День 18 — Scheduler Agent.
 *
 * Пользователь описывает желаемое расписание естественным языком.
 * LLM разбирает намерение и вызывает один из 3 инструментов:
 *   - create_schedule(city, hour, minute)
 *   - list_schedules()
 *   - delete_schedule(id)
 *
 * Агент выполняет соответствующий REST-запрос к серверу и возвращает
 * финальный LLM-ответ на русском языке.
 */
class SchedulerAgent(
    private val apiService: RouterAiApiService,
    private val serverBaseUrl: String = "http://10.0.2.2:8080",
    private val model: String = DEFAULT_MODEL,
) {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
    private val history = mutableListOf<ChatMessage>()

    private val client = HttpClient {
        install(HttpTimeout) { requestTimeoutMillis = 30_000L }
        install(ContentNegotiation) { json(json) }
    }

    suspend fun sendMessage(text: String): SchedulerAgentResult {
        history.add(ChatMessage(role = "user", content = text))
        return try {
            processWithTools()
        } catch (e: Exception) {
            history.removeLast()
            throw e
        }
    }

    suspend fun loadSchedules(): List<ScheduleInfo> = runCatching {
        client.get("$serverBaseUrl/scheduler/list")
            .body<ScheduleListDto>()
            .schedules
            .map { it.toScheduleInfo() }
    }.getOrDefault(emptyList())

    suspend fun deleteSchedule(id: String) {
        runCatching { client.delete("$serverBaseUrl/scheduler/$id") }
    }

    fun clearHistory() = history.clear()

    // ── Private ───────────────────────────────────────────────────────────────

    private suspend fun processWithTools(): SchedulerAgentResult {
        val messages = buildList {
            add(ChatMessage(role = "system", content = SYSTEM_PROMPT))
            addAll(history)
        }

        val result = apiService.sendMessagesWithTools(
            messages = messages,
            tools = schedulerTools(),
            model = model,
        )

        if (result.finishReason == "tool_calls" && !result.toolCalls.isNullOrEmpty()) {
            val toolCall = result.toolCalls.first()
            val toolResult = executeToolCall(toolCall)

            history.add(ChatMessage(role = "assistant", content = null, toolCalls = result.toolCalls))
            history.add(
                ChatMessage(
                    role = "tool",
                    content = toolResult,
                    toolCallId = toolCall.id,
                    name = toolCall.function.name,
                )
            )

            val finalMessages = buildList {
                add(ChatMessage(role = "system", content = SYSTEM_PROMPT))
                addAll(history)
            }
            val finalResult = apiService.sendMessages(messages = finalMessages, model = model)
            history.add(ChatMessage(role = "assistant", content = finalResult.content))

            return SchedulerAgentResult(
                content = finalResult.content,
                toolCallMade = true,
                toolName = toolCall.function.name,
            )
        }

        // LLM answered with text instead of tool — retry with explicit reminder
        val retryMessages = buildList {
            add(ChatMessage(role = "system", content = SYSTEM_PROMPT))
            addAll(history)
            add(ChatMessage(role = "assistant", content = result.content))
            add(ChatMessage(role = "user", content = "Пожалуйста, выполни запрос с помощью инструмента."))
        }
        val retryResult = apiService.sendMessagesWithTools(
            messages = retryMessages,
            tools = schedulerTools(),
            model = model,
        )
        if (retryResult.finishReason == "tool_calls" && !retryResult.toolCalls.isNullOrEmpty()) {
            val toolCall = retryResult.toolCalls.first()
            val toolResult = executeToolCall(toolCall)
            history.add(ChatMessage(role = "assistant", content = null, toolCalls = retryResult.toolCalls))
            history.add(ChatMessage(role = "tool", content = toolResult, toolCallId = toolCall.id, name = toolCall.function.name))
            val finalMessages = buildList {
                add(ChatMessage(role = "system", content = SYSTEM_PROMPT))
                addAll(history)
            }
            val finalResult = apiService.sendMessages(messages = finalMessages, model = model)
            history.add(ChatMessage(role = "assistant", content = finalResult.content))
            return SchedulerAgentResult(content = finalResult.content, toolCallMade = true, toolName = toolCall.function.name)
        }

        history.add(ChatMessage(role = "assistant", content = result.content))
        return SchedulerAgentResult(content = result.content, toolCallMade = false)
    }

    private suspend fun executeToolCall(toolCall: ToolCall): String {
        val args = runCatching {
            json.parseToJsonElement(toolCall.function.arguments).jsonObject
        }.getOrDefault(emptyMap())

        return when (toolCall.function.name) {
            "create_schedule" -> {
                val city = args["city"]?.jsonPrimitive?.content
                    ?: return "Ошибка: не указан город"
                val hour = args["hour"]?.jsonPrimitive?.intOrNull
                    ?: return "Ошибка: не указан час"
                val minute = args["minute"]?.jsonPrimitive?.intOrNull ?: 0
                runCatching {
                    val entry = client.post("$serverBaseUrl/scheduler/create") {
                        contentType(ContentType.Application.Json)
                        setBody(CreateScheduleDto(city = city, hour = hour, minute = minute))
                    }.body<ScheduleEntryDto>()
                    "Расписание создано: ${entry.city} — ${entry.hour.pad()}:${entry.minute.pad()} ежедневно (id: ${entry.id.take(8)})"
                }.getOrElse { "Ошибка создания расписания: ${it.message}" }
            }

            "list_schedules" -> {
                runCatching {
                    val list = client.get("$serverBaseUrl/scheduler/list").body<ScheduleListDto>()
                    if (list.schedules.isEmpty()) {
                        "Нет активных расписаний."
                    } else {
                        list.schedules.joinToString("\n") { s ->
                            val last = s.reports.lastOrNull()?.summary ?: "ещё нет отчётов"
                            "• ${s.city} — ${s.hour.pad()}:${s.minute.pad()} | Последний: $last (id: ${s.id.take(8)})"
                        }
                    }
                }.getOrElse { "Ошибка получения расписаний: ${it.message}" }
            }

            "delete_schedule" -> {
                val id = args["id"]?.jsonPrimitive?.content
                    ?: return "Ошибка: не указан id"
                runCatching {
                    client.delete("$serverBaseUrl/scheduler/$id")
                    "Расписание удалено."
                }.getOrElse { "Ошибка удаления: ${it.message}" }
            }

            else -> "Неизвестный инструмент: ${toolCall.function.name}"
        }
    }

    private fun schedulerTools() = listOf(
        ToolDefinition(
            function = ToolFunction(
                name = "create_schedule",
                description = "Создать расписание для ежедневного погодного отчёта по заданному городу",
                parameters = ToolParameters(
                    properties = mapOf(
                        "city" to ToolProperty("string", "Название города, например: Москва, London"),
                        "hour" to ToolProperty("integer", "Час срабатывания по местному времени (0–23)"),
                        "minute" to ToolProperty("integer", "Минута срабатывания (0–59), по умолчанию 0"),
                    ),
                    required = listOf("city", "hour"),
                ),
            )
        ),
        ToolDefinition(
            function = ToolFunction(
                name = "list_schedules",
                description = "Показать все активные расписания погодных отчётов с последними данными",
                parameters = ToolParameters(properties = emptyMap()),
            )
        ),
        ToolDefinition(
            function = ToolFunction(
                name = "delete_schedule",
                description = "Удалить расписание погодного отчёта по его идентификатору",
                parameters = ToolParameters(
                    properties = mapOf(
                        "id" to ToolProperty("string", "Идентификатор расписания (первые 8 символов или полный id)"),
                    ),
                    required = listOf("id"),
                ),
            )
        ),
    )

    private fun Int.pad() = toString().padStart(2, '0')

    // ── Private DTOs ──────────────────────────────────────────────────────────

    @Serializable
    private data class CreateScheduleDto(val city: String, val hour: Int, val minute: Int)

    @Serializable
    private data class ScheduleListDto(val schedules: List<ScheduleEntryDto>)

    @Serializable
    private data class ScheduleEntryDto(
        val id: String,
        val city: String,
        val hour: Int,
        val minute: Int,
        val reports: List<WeatherReportDto> = emptyList(),
    ) {
        fun toScheduleInfo() = ScheduleInfo(
            id = id,
            city = city,
            hour = hour,
            minute = minute,
            lastReport = reports.lastOrNull()?.summary,
        )
    }

    @Serializable
    private data class WeatherReportDto(val ts: Long, val summary: String)

    companion object {
        private const val DEFAULT_MODEL = "deepseek/deepseek-v3.2"
        private const val SYSTEM_PROMPT =
            "Ты ассистент-планировщик погодных отчётов. " +
            "У тебя есть три инструмента: create_schedule, list_schedules, delete_schedule. " +
            "Правило: ВСЕГДА отвечай вызовом инструмента, никогда не имитируй его выполнение текстом. " +
            "Если пользователь хочет создать расписание — вызови create_schedule. " +
            "Если хочет увидеть список — вызови list_schedules. " +
            "Если хочет удалить — вызови delete_schedule. " +
            "Отвечай на русском языке."
    }
}
