package ru.nikitaluga.aichallenge.api

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
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
        // Do not write null fields into the JSON body (e.g. max_tokens, stop)
        explicitNulls = false
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

    /**
     * Send a message to the RouteAI API.
     *
     * @param prompt        The user's message.
     * @param systemPrompt  Optional system instruction prepended before the conversation history.
     * @param maxTokens     Optional cap on the number of tokens in the response.
     * @param stopSequences Optional list of strings that cause the model to stop generation.
     * @param temperature   Sampling temperature (0.0 = deterministic, 1.2+ = highly creative).
     */
    suspend fun sendMessage(
        prompt: String,
        systemPrompt: String? = null,
        maxTokens: Int = 200,
        stopSequences: List<String>? = null,
        temperature: Double = 0.7,
    ): String {
        conversationHistory.add(ChatMessage(role = "user", content = prompt))

        // System message goes first, then the conversation history
        val messages = buildList {
            if (systemPrompt != null) {
                add(ChatMessage(role = "system", content = systemPrompt))
            }
            addAll(conversationHistory)
        }

        val request = ChatRequest(
            messages = messages,
            maxTokens = maxTokens,
            stop = stopSequences,
            temperature = temperature,
        )

        val response = client.post("https://routerai.ru/api/v1/chat/completions") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer ${getApiKey()}")
            setBody(request)
        }

        if (!response.status.isSuccess()) {
            val errorBody = response.bodyAsText()
            val apiError = runCatching { json.decodeFromString<ApiError>(errorBody) }.getOrNull()
            throw Exception(apiError?.error?.message ?: "HTTP ${response.status.value}: $errorBody")
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
