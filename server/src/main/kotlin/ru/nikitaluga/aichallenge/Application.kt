package ru.nikitaluga.aichallenge

import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.serialization.json.Json
import kotlinx.coroutines.launch
import ru.nikitaluga.aichallenge.api.RouterAiApiService
import ru.nikitaluga.aichallenge.local.installLocalLlmRoutes
import ru.nikitaluga.aichallenge.mcp.WeatherService
import ru.nikitaluga.aichallenge.calc.installCalcMcpRoutes
import ru.nikitaluga.aichallenge.mcp.installMcpRoutes
import ru.nikitaluga.aichallenge.mcp.installWeatherMcpRoutes
import ru.nikitaluga.aichallenge.notes.NoteRepository
import ru.nikitaluga.aichallenge.notes.installNotesMcpRoutes
import ru.nikitaluga.aichallenge.pipeline.installPipelineRoutes
import ru.nikitaluga.aichallenge.rag.RagIndexer
import ru.nikitaluga.aichallenge.rag.RagRepository
import ru.nikitaluga.aichallenge.rag.installRagRoutes
import ru.nikitaluga.aichallenge.scheduler.ScheduleRepository
import ru.nikitaluga.aichallenge.scheduler.WeatherSchedulerService
import ru.nikitaluga.aichallenge.scheduler.installSchedulerRoutes

fun main() {
    embeddedServer(Netty, port = SERVER_PORT, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}

fun Application.module() {
    install(ContentNegotiation) {
        json(Json { ignoreUnknownKeys = true; encodeDefaults = true })
    }
    routing {
        get("/") {
            call.respondText("Ktor: ${Greeting().greet()}")
        }
    }
    installMcpRoutes()
    installWeatherMcpRoutes()
    val noteRepo = NoteRepository()
    installNotesMcpRoutes(noteRepo)
    installCalcMcpRoutes()
    installPipelineRoutes()

    val sharedApiService = RouterAiApiService()
    installLocalLlmRoutes(sharedApiService)

    val ragRepository = RagRepository()
    val ragIndexer = RagIndexer(repository = ragRepository, apiService = sharedApiService)
    installRagRoutes(ragRepository, ragIndexer)

    val scheduleRepo = ScheduleRepository()
    val schedulerService = WeatherSchedulerService(
        repo = scheduleRepo,
        weatherService = WeatherService(),
        apiService = RouterAiApiService(),
    )
    installSchedulerRoutes(scheduleRepo, schedulerService)

    launch {
        if (ragRepository.load() == null) {
            runCatching { ragIndexer.buildIndex(chunkSize = 300, overlap = 50) }
        }
    }

    launch {
        val restored = scheduleRepo.load()
        schedulerService.startAll(restored)
    }
}
