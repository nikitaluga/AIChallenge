package ru.nikitaluga.aichallenge.data.agent

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import ru.nikitaluga.aichallenge.util.CommonJson

// День 34 — Files Assistant Agent.
// HTTP-клиент к /files/chat эндпоинту.
class FilesAgent(
    private val serverBaseUrl: String = "http://10.0.2.2:8080",
) {
    private val jsonConfig = CommonJson
    private val client = HttpClient {
        install(HttpTimeout) { requestTimeoutMillis = 30_000L }
        install(ContentNegotiation) { json(jsonConfig) }
    }

    suspend fun chat(query: String, history: List<FilesHistoryMsg>, useDryRun: Boolean = true): FilesResponseDto {
        val response = client.post("$serverBaseUrl/files/chat") {
            contentType(ContentType.Application.Json)
            setBody(FilesChatRequestDto(query = query, history = history, useDryRun = useDryRun))
        }
        if (!response.status.isSuccess()) throw Exception("Ошибка сервера ${response.status.value}")
        return response.body()
    }

    @Serializable
    data class FilesHistoryMsg(val role: String, val content: String)

    @Serializable
    data class FilesResponseDto(
        val answer: String = "",
        val toolsUsed: List<String> = emptyList(),
        val diffs: List<FileDiffDto> = emptyList(),
    )

    @Serializable
    data class FileDiffDto(val path: String, val diff: String)

    @Serializable
    private data class FilesChatRequestDto(
        val query: String,
        val history: List<FilesHistoryMsg> = emptyList(),
        val useDryRun: Boolean = true,
    )
}
