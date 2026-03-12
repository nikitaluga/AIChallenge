package ru.nikitaluga.aichallenge.scheduler

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import java.util.UUID

fun Application.installSchedulerRoutes(
    repo: ScheduleRepository,
    service: WeatherSchedulerService,
) {
    routing {
        route("/scheduler") {

            post("/create") {
                val req = runCatching { call.receive<CreateScheduleRequest>() }.getOrElse {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Неверный формат запроса"))
                    return@post
                }
                val entry = ScheduleEntry(
                    id = UUID.randomUUID().toString(),
                    city = req.city.trim(),
                    hour = req.hour.coerceIn(0, 23),
                    minute = req.minute.coerceIn(0, 59),
                )
                repo.addSchedule(entry)
                service.startSchedule(entry)
                call.respond(HttpStatusCode.Created, entry)
            }

            get("/list") {
                call.respond(ScheduleListResponse(repo.getAll()))
            }

            delete("/{id}") {
                val id = call.parameters["id"] ?: run {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "id обязателен"))
                    return@delete
                }
                repo.removeSchedule(id)
                service.cancelSchedule(id)
                call.respond(HttpStatusCode.NoContent)
            }

            get("/{id}/reports") {
                val id = call.parameters["id"] ?: run {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "id обязателен"))
                    return@get
                }
                val entry = repo.getById(id) ?: run {
                    call.respond(HttpStatusCode.NotFound, mapOf("error" to "Расписание не найдено"))
                    return@get
                }
                call.respond(ScheduleReportsResponse(entry.reports))
            }
        }
    }
}
