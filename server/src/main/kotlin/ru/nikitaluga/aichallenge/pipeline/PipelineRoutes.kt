package ru.nikitaluga.aichallenge.pipeline

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import ru.nikitaluga.aichallenge.mcp.WeatherService
import java.io.File

// ── Request / Response models ─────────────────────────────────────────────────

@Serializable
data class PipelineToolCallRequest(
    val name: String,
    val input: Map<String, String>,
)

@Serializable
data class PipelineToolCallResponse(val result: String)

@Serializable
data class PipelineToolInfo(
    val name: String,
    val description: String,
    val inputSchema: JsonObject,
)

@Serializable
data class PipelineToolsListResponse(val tools: List<PipelineToolInfo>)

@Serializable
data class SavedFileInfo(val filename: String, val sizeBytes: Long)

@Serializable
data class SavedFilesResponse(val files: List<SavedFileInfo>)

// ── Route installer ───────────────────────────────────────────────────────────

fun Application.installPipelineRoutes() {
    val weatherService = WeatherService()
    val reportsDir = File("reports").also { it.mkdirs() }

    routing {
        route("/pipeline") {

            get("/tools/list") {
                call.respond(PipelineToolsListResponse(tools = buildToolList()))
            }

            post("/tools/call") {
                val request = runCatching { call.receive<PipelineToolCallRequest>() }.getOrElse {
                    call.respond(HttpStatusCode.BadRequest, PipelineToolCallResponse("Неверный формат запроса"))
                    return@post
                }

                val result = when (request.name) {
                    "get_weather" -> {
                        val city = request.input["city"]
                        if (city.isNullOrBlank()) {
                            call.respond(HttpStatusCode.BadRequest, PipelineToolCallResponse("Параметр 'city' обязателен"))
                            return@post
                        }
                        runCatching { weatherService.getWeather(city) }
                            .getOrElse { "Ошибка получения погоды: ${it.message}" }
                    }

                    "summarize_weather" -> {
                        val data = request.input["weather_data"]
                        if (data.isNullOrBlank()) {
                            call.respond(HttpStatusCode.BadRequest, PipelineToolCallResponse("Параметр 'weather_data' обязателен"))
                            return@post
                        }
                        summarizeWeather(data)
                    }

                    "save_to_file" -> {
                        val filename = request.input["filename"]
                        val content = request.input["content"]
                        if (filename.isNullOrBlank() || content.isNullOrBlank()) {
                            call.respond(HttpStatusCode.BadRequest, PipelineToolCallResponse("Параметры 'filename' и 'content' обязательны"))
                            return@post
                        }
                        runCatching {
                            val safe = filename.replace(Regex("[^a-zA-Z0-9а-яА-Я._-]"), "_")
                            val file = File(reportsDir, safe)
                            file.writeText(content)
                            "Сохранено: reports/$safe (${file.length()} байт)"
                        }.getOrElse { "Ошибка сохранения: ${it.message}" }
                    }

                    else -> {
                        call.respond(HttpStatusCode.NotFound, PipelineToolCallResponse("Инструмент '${request.name}' не найден"))
                        return@post
                    }
                }

                call.respond(PipelineToolCallResponse(result = result))
            }

            get("/files") {
                val files = reportsDir.listFiles()
                    ?.filter { it.isFile }
                    ?.map { SavedFileInfo(filename = it.name, sizeBytes = it.length()) }
                    ?.sortedByDescending { it.filename }
                    ?: emptyList()
                call.respond(SavedFilesResponse(files = files))
            }

            get("/files/{filename}") {
                val filename = call.parameters["filename"] ?: run {
                    call.respond(HttpStatusCode.BadRequest, "Имя файла обязательно")
                    return@get
                }
                val safe = filename.replace(Regex("[^a-zA-Z0-9а-яА-Я._-]"), "_")
                val file = File(reportsDir, safe)
                if (!file.exists()) {
                    call.respond(HttpStatusCode.NotFound, "Файл не найден: $safe")
                    return@get
                }
                call.respond(file.readText())
            }
        }
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

private fun summarizeWeather(data: String): String {
    val lines = data.lines()
    fun extract(prefix: String) = lines
        .firstOrNull { it.startsWith(prefix) }
        ?.removePrefix(prefix)
        ?.trim()

    val city = extract("Город:") ?: "Неизвестный город"
    val temp = extract("Температура:") ?: "—"
    val desc = extract("Описание:") ?: "—"
    val humidity = extract("Влажность:") ?: "—"
    val wind = extract("Ветер:") ?: "—"

    return "$city: $temp, $desc, влажность $humidity, ветер $wind"
}

private fun buildToolList(): List<PipelineToolInfo> {
    fun schema(vararg props: Pair<String, String>, required: List<String> = props.map { it.first }): JsonObject =
        buildJsonObject {
            put("type", "object")
            putJsonObject("properties") {
                props.forEach { (name, desc) ->
                    putJsonObject(name) {
                        put("type", "string")
                        put("description", desc)
                    }
                }
            }
            put("required", buildJsonArray { required.forEach { add(JsonPrimitive(it)) } })
        }

    return listOf(
        PipelineToolInfo(
            name = "get_weather",
            description = "Получить текущую погоду для указанного города через OpenWeatherMap",
            inputSchema = schema("city" to "Название города, например: Москва, London, Tokyo"),
        ),
        PipelineToolInfo(
            name = "summarize_weather",
            description = "Сформировать компактную однострочную сводку из сырых данных о погоде",
            inputSchema = schema("weather_data" to "Многострочный текст с данными о погоде, полученный от get_weather"),
        ),
        PipelineToolInfo(
            name = "save_to_file",
            description = "Сохранить текстовый контент в файл в папке reports/ на сервере",
            inputSchema = schema(
                "filename" to "Имя файла, например: moscow.txt или weather_report.txt",
                "content" to "Текстовое содержимое для сохранения в файл",
            ),
        ),
    )
}
