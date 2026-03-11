package ru.nikitaluga.aichallenge.mcp

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

// ── Request / Response models ─────────────────────────────────────────────────

@Serializable
data class McpToolCallRequest(
    val name: String,
    val input: Map<String, String>,
)

@Serializable
data class McpToolCallResponse(val result: String)

@Serializable
data class McpToolInfo(
    val name: String,
    val description: String,
    val inputSchema: JsonObject,
)

@Serializable
data class McpToolsListResponse(val tools: List<McpToolInfo>)

// ── Route installer ───────────────────────────────────────────────────────────

fun Application.installMcpRoutes() {
    val weatherService = WeatherService()

    routing {
        route("/mcp/tools") {

            get("/list") {
                val schema = buildJsonObject {
                    put("type", "object")
                    putJsonObject("properties") {
                        putJsonObject("city") {
                            put("type", "string")
                            put("description", "Название города на английском или русском языке")
                        }
                    }
                    put("required", buildJsonArray { add(JsonPrimitive("city")) })
                }
                call.respond(
                    McpToolsListResponse(
                        tools = listOf(
                            McpToolInfo(
                                name = "get_weather",
                                description = "Получить текущую погоду для указанного города",
                                inputSchema = schema,
                            )
                        )
                    )
                )
            }

            post("/call") {
                val request = runCatching { call.receive<McpToolCallRequest>() }.getOrElse {
                    call.respond(HttpStatusCode.BadRequest, McpToolCallResponse("Неверный формат запроса"))
                    return@post
                }

                when (request.name) {
                    "get_weather" -> {
                        val city = request.input["city"]
                        if (city.isNullOrBlank()) {
                            call.respond(HttpStatusCode.BadRequest, McpToolCallResponse("Параметр 'city' обязателен"))
                            return@post
                        }
                        val result = runCatching { weatherService.getWeather(city) }.getOrElse {
                            "Ошибка получения погоды: ${it.message}"
                        }
                        call.respond(McpToolCallResponse(result = result))
                    }
                    else -> call.respond(HttpStatusCode.NotFound, McpToolCallResponse("Инструмент '${request.name}' не найден"))
                }
            }
        }
    }
}
