package ru.nikitaluga.aichallenge.calc

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import ru.nikitaluga.aichallenge.mcp.McpToolCallRequest
import ru.nikitaluga.aichallenge.mcp.McpToolCallResponse
import ru.nikitaluga.aichallenge.mcp.McpToolInfo
import ru.nikitaluga.aichallenge.mcp.McpToolsListResponse

fun Application.installCalcMcpRoutes() {

    routing {
        route("/mcp/calc/tools") {

            get("/list") {
                val convertSchema = buildJsonObject {
                    put("type", "object")
                    putJsonObject("properties") {
                        putJsonObject("value") {
                            put("type", "string")
                            put("description", "Числовое значение для конвертации")
                        }
                        putJsonObject("from_unit") {
                            put("type", "string")
                            put("description", "Единица измерения источника (celsius, fahrenheit, kelvin, km, miles, meters, kg, pounds, grams)")
                        }
                        putJsonObject("to_unit") {
                            put("type", "string")
                            put("description", "Целевая единица измерения")
                        }
                    }
                    put("required", buildJsonArray {
                        add(JsonPrimitive("value"))
                        add(JsonPrimitive("from_unit"))
                        add(JsonPrimitive("to_unit"))
                    })
                }

                val calcSchema = buildJsonObject {
                    put("type", "object")
                    putJsonObject("properties") {
                        putJsonObject("expression") {
                            put("type", "string")
                            put("description", "Арифметическое выражение для вычисления (например: 5 * 9 / 5 + 32)")
                        }
                    }
                    put("required", buildJsonArray { add(JsonPrimitive("expression")) })
                }

                call.respond(
                    McpToolsListResponse(
                        tools = listOf(
                            McpToolInfo(
                                name = "convert_units",
                                description = "Конвертировать значение из одной единицы измерения в другую (температура, расстояние, вес)",
                                inputSchema = convertSchema,
                            ),
                            McpToolInfo(
                                name = "calculate",
                                description = "Вычислить арифметическое выражение с операциями +, -, *, /",
                                inputSchema = calcSchema,
                            ),
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
                    "convert_units" -> {
                        val valueStr = request.input["value"]
                        val fromUnit = request.input["from_unit"]?.lowercase()
                        val toUnit = request.input["to_unit"]?.lowercase()

                        if (valueStr.isNullOrBlank() || fromUnit.isNullOrBlank() || toUnit.isNullOrBlank()) {
                            call.respond(HttpStatusCode.BadRequest, McpToolCallResponse("Параметры 'value', 'from_unit', 'to_unit' обязательны"))
                            return@post
                        }

                        val value = valueStr.toDoubleOrNull()
                        if (value == null) {
                            call.respond(McpToolCallResponse("Ошибка: '$valueStr' не является числом"))
                            return@post
                        }

                        val result = runCatching { convertUnits(value, fromUnit, toUnit) }.getOrElse {
                            call.respond(McpToolCallResponse("Ошибка конвертации: ${it.message}"))
                            return@post
                        }

                        val resultStr = if (result == result.toLong().toDouble()) result.toLong().toString() else "%.4f".format(result).trimEnd('0').trimEnd('.')
                        call.respond(McpToolCallResponse(result = "$valueStr $fromUnit = $resultStr $toUnit"))
                    }

                    "calculate" -> {
                        val expression = request.input["expression"]
                        if (expression.isNullOrBlank()) {
                            call.respond(HttpStatusCode.BadRequest, McpToolCallResponse("Параметр 'expression' обязателен"))
                            return@post
                        }

                        val result = runCatching { evalExpression(expression.replace(" ", "")) }.getOrElse {
                            call.respond(McpToolCallResponse("Ошибка вычисления: ${it.message}"))
                            return@post
                        }

                        val resultStr = if (result == result.toLong().toDouble()) result.toLong().toString() else "%.6f".format(result).trimEnd('0').trimEnd('.')
                        call.respond(McpToolCallResponse(result = "$expression = $resultStr"))
                    }

                    else -> call.respond(HttpStatusCode.NotFound, McpToolCallResponse("Инструмент '${request.name}' не найден"))
                }
            }
        }
    }
}

// ── Unit conversion ───────────────────────────────────────────────────────────

private fun convertUnits(value: Double, from: String, to: String): Double {
    if (from == to) return value

    return when {
        // Temperature
        from == "celsius" && to == "fahrenheit" -> value * 9.0 / 5.0 + 32.0
        from == "fahrenheit" && to == "celsius" -> (value - 32.0) * 5.0 / 9.0
        from == "celsius" && to == "kelvin" -> value + 273.15
        from == "kelvin" && to == "celsius" -> value - 273.15
        from == "fahrenheit" && to == "kelvin" -> (value - 32.0) * 5.0 / 9.0 + 273.15
        from == "kelvin" && to == "fahrenheit" -> (value - 273.15) * 9.0 / 5.0 + 32.0

        // Distance
        from == "km" && to == "miles" -> value * 0.621371
        from == "miles" && to == "km" -> value / 0.621371
        from == "km" && to == "meters" -> value * 1000.0
        from == "meters" && to == "km" -> value / 1000.0
        from == "miles" && to == "meters" -> value / 0.621371 * 1000.0
        from == "meters" && to == "miles" -> value / 1000.0 * 0.621371

        // Weight
        from == "kg" && to == "pounds" -> value * 2.20462
        from == "pounds" && to == "kg" -> value / 2.20462
        from == "kg" && to == "grams" -> value * 1000.0
        from == "grams" && to == "kg" -> value / 1000.0
        from == "pounds" && to == "grams" -> value / 2.20462 * 1000.0
        from == "grams" && to == "pounds" -> value / 1000.0 * 2.20462

        else -> throw IllegalArgumentException("Неизвестная конвертация: $from → $to")
    }
}

// ── Simple arithmetic parser ──────────────────────────────────────────────────

private fun evalExpression(expr: String): Double {
    val e = expr.trim()

    // Find last + or - (not at position 0 to skip unary minus)
    var i = e.length - 1
    while (i > 0) {
        val c = e[i]
        if (c == '+' || c == '-') {
            return evalExpression(e.substring(0, i)) + (if (c == '+') 1.0 else -1.0) * evalExpression(e.substring(i + 1))
        }
        i--
    }

    // Find last * or /
    i = e.length - 1
    while (i > 0) {
        val c = e[i]
        if (c == '*' || c == '/') {
            val left = evalExpression(e.substring(0, i))
            val right = evalExpression(e.substring(i + 1))
            return if (c == '*') left * right else left / right
        }
        i--
    }

    return e.toDouble()
}
