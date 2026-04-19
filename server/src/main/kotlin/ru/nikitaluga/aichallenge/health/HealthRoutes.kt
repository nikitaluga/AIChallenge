package ru.nikitaluga.aichallenge.health

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import kotlinx.serialization.Serializable

@Serializable
data class HealthResponse(val status: String, val uptimeSeconds: Long)

private val startTime = System.currentTimeMillis()

fun Application.installHealthRoutes() {
    routing {
        route("/api") {
            get("/health") {
                val uptime = (System.currentTimeMillis() - startTime) / 1000
                call.respond(HttpStatusCode.OK, HealthResponse(status = "ok", uptimeSeconds = uptime))
            }
        }
    }
}
