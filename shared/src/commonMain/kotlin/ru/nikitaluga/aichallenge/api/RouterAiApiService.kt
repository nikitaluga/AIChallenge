package ru.nikitaluga.aichallenge.api

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.DEFAULT
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.logging.SIMPLE
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

class RouterAiApiService {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val client = HttpClient {
        install(ContentNegotiation) {
            json(this@RouterAiApiService.json)
        }
        install(Logging) {
            logger = Logger.SIMPLE
            level = LogLevel.BODY
        }
    }

    private val conversationHistory = mutableListOf<ChatMessage>()

    suspend fun sendMessage(prompt: String): String {
        conversationHistory.add(ChatMessage(role = "user", content = prompt))

        val request = ChatRequest(
            messages = conversationHistory.toList(),
        )

        val response = client.post("https://routerai.ru/api/v1/chat/completions") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer ${getApiKey()}")
            setBody(request)
        }

        if (!response.status.isSuccess()) {
            val errorBody = response.bodyAsText()
            try {
                val apiError = json.decodeFromString<ApiError>(errorBody)
                throw Exception("API error: ${apiError.error.message}")
            } catch (_: Exception) {
                throw Exception("HTTP ${response.status.value}: $errorBody")
            }
        }

        val chatResponse = response.body<ChatResponse>()
        val assistantMessage = chatResponse.choices.firstOrNull()?.message
            ?: throw Exception("No response from API")

        conversationHistory.add(assistantMessage)
        return assistantMessage.content
    }

    fun clearHistory() {
        conversationHistory.clear()
    }
}