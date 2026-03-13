package ru.nikitaluga.aichallenge.notes

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

fun Application.installNotesMcpRoutes(noteRepo: NoteRepository) {

    routing {
        route("/mcp/notes/tools") {

            get("/list") {
                val createNoteSchema = buildJsonObject {
                    put("type", "object")
                    putJsonObject("properties") {
                        putJsonObject("title") {
                            put("type", "string")
                            put("description", "Заголовок заметки")
                        }
                        putJsonObject("content") {
                            put("type", "string")
                            put("description", "Содержимое заметки")
                        }
                    }
                    put("required", buildJsonArray {
                        add(JsonPrimitive("title"))
                        add(JsonPrimitive("content"))
                    })
                }

                val listNotesSchema = buildJsonObject {
                    put("type", "object")
                    putJsonObject("properties") {}
                    put("required", buildJsonArray {})
                }

                val readNoteSchema = buildJsonObject {
                    put("type", "object")
                    putJsonObject("properties") {
                        putJsonObject("title") {
                            put("type", "string")
                            put("description", "Заголовок заметки для чтения")
                        }
                    }
                    put("required", buildJsonArray { add(JsonPrimitive("title")) })
                }

                call.respond(
                    McpToolsListResponse(
                        tools = listOf(
                            McpToolInfo(
                                name = "create_note",
                                description = "Создать или перезаписать заметку с указанным заголовком и содержимым",
                                inputSchema = createNoteSchema,
                            ),
                            McpToolInfo(
                                name = "list_notes",
                                description = "Получить список всех сохранённых заметок",
                                inputSchema = listNotesSchema,
                            ),
                            McpToolInfo(
                                name = "read_note",
                                description = "Прочитать полное содержимое заметки по заголовку",
                                inputSchema = readNoteSchema,
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
                    "create_note" -> {
                        val title = request.input["title"]
                        val content = request.input["content"]
                        if (title.isNullOrBlank()) {
                            call.respond(HttpStatusCode.BadRequest, McpToolCallResponse("Параметр 'title' обязателен"))
                            return@post
                        }
                        if (content == null) {
                            call.respond(HttpStatusCode.BadRequest, McpToolCallResponse("Параметр 'content' обязателен"))
                            return@post
                        }
                        runCatching { noteRepo.save(Note(title = title, content = content)) }.getOrElse {
                            call.respond(McpToolCallResponse("Ошибка сохранения заметки: ${it.message}"))
                            return@post
                        }
                        call.respond(McpToolCallResponse(result = "Заметка '$title' сохранена"))
                    }

                    "list_notes" -> {
                        val notes = runCatching { noteRepo.list() }.getOrDefault(emptyList())
                        val result = if (notes.isEmpty()) {
                            "Нет заметок"
                        } else {
                            val items = notes.joinToString("\n") { note ->
                                val preview = note.content.take(50).let { if (note.content.length > 50) "$it..." else it }
                                "- ${note.title}: $preview"
                            }
                            "Заметки (${notes.size}):\n$items"
                        }
                        call.respond(McpToolCallResponse(result = result))
                    }

                    "read_note" -> {
                        val title = request.input["title"]
                        if (title.isNullOrBlank()) {
                            call.respond(HttpStatusCode.BadRequest, McpToolCallResponse("Параметр 'title' обязателен"))
                            return@post
                        }
                        val note = runCatching { noteRepo.read(title) }.getOrNull()
                        val result = note?.content ?: "Заметка '$title' не найдена"
                        call.respond(McpToolCallResponse(result = result))
                    }

                    else -> call.respond(HttpStatusCode.NotFound, McpToolCallResponse("Инструмент '${request.name}' не найден"))
                }
            }
        }
    }
}
