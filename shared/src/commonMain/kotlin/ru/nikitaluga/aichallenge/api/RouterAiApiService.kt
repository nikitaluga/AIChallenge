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
import io.ktor.client.request.preparePost
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.readLine
import kotlinx.serialization.json.Json

class RouterAiApiService {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        // Do not write null fields into the JSON body (e.g. max_tokens, stop)
        explicitNulls = false
    }

    /**
     * Parse an error body and throw a descriptive [Exception].
     *
     * RouteAI may return either:
     * - `{"error": {"message": "...", "type": "..."}}` — structured
     * - `{"error": "401 Unauthorized"}` — flat string
     *
     * Falls back to the raw body when neither parses.
     */
    private fun throwApiError(httpStatus: Int, body: String): Nothing {
        val structured = runCatching { json.decodeFromString<ApiError>(body) }.getOrNull()
        val simple = if (structured == null) {
            runCatching { json.decodeFromString<ApiSimpleError>(body) }.getOrNull()
        } else null
        throw Exception(structured?.error?.message ?: simple?.error ?: "HTTP $httpStatus: $body")
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
        maxTokens: Int = 4096,
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
            throwApiError(response.status.value, response.bodyAsText())
        }

        val chatResponse = response.body<ChatResponse>()
        val assistantMessage = chatResponse.choices.firstOrNull()?.message
            ?: throw Exception("No response from API")

        conversationHistory.add(assistantMessage)
        return assistantMessage.effectiveContent
    }

    fun clearHistory() {
        conversationHistory.clear()
    }

    /**
     * Send an explicit list of messages (stateless, history managed by the caller).
     * Used by ChatAgent to pass its own conversation history.
     *
     * Returns [MessageWithMetrics] so callers can access the API-reported [Usage]
     * for precise token counts (prompt + completion tokens).
     */
    suspend fun sendMessages(
        messages: List<ChatMessage>,
        model: String = "deepseek/deepseek-v3.2",
        maxTokens: Int? = null,
        temperature: Double = 0.7,
    ): MessageWithMetrics {
        val request = ChatRequest(
            model = model,
            messages = messages,
            temperature = temperature,
            maxTokens = maxTokens,
        )

        val response = client.post("https://routerai.ru/api/v1/chat/completions") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer ${getApiKey()}")
            setBody(request)
        }

        if (!response.status.isSuccess()) {
            throwApiError(response.status.value, response.bodyAsText())
        }

        val chatResponse = response.body<ChatResponse>()
        val assistantMessage = chatResponse.choices.firstOrNull()?.message
            ?: throw Exception("No response from API")
        return MessageWithMetrics(
            content = assistantMessage.effectiveContent,
            usage = chatResponse.usage,
        )
    }

    /**
     * Stream an explicit list of messages via Server-Sent Events.
     * Calls [onChunk] for each text fragment as it arrives.
     */
    suspend fun streamMessages(
        messages: List<ChatMessage>,
        model: String = "deepseek/deepseek-v3.2",
        maxTokens: Int = 1024,
        temperature: Double = 0.7,
        onChunk: (String) -> Unit,
    ) {
        val request = ChatRequest(
            model = model,
            messages = messages,
            temperature = temperature,
            maxTokens = maxTokens,
            stream = true,
        )

        client.preparePost("https://routerai.ru/api/v1/chat/completions") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer ${getApiKey()}")
            setBody(request)
        }.execute { response ->
            if (!response.status.isSuccess()) {
                throwApiError(response.status.value, response.bodyAsText())
            }
            val channel = response.bodyAsChannel()
            while (!channel.isClosedForRead) {
                val line = channel.readLine() ?: break
                if (line.startsWith("data: ")) {
                    val data = line.removePrefix("data: ").trim()
                    if (data == "[DONE]") break
                    runCatching {
                        val chunk = json.decodeFromString<ChatStreamChunk>(data)
                        chunk.choices.firstOrNull()?.delta?.content?.let { content ->
                            if (content.isNotEmpty()) onChunk(content)
                        }
                    }
                }
            }
        }
    }

    /**
     * Send a single stateless message to a specific model.
     * Does not use or modify the conversation history.
     *
     * @param model     The model identifier (e.g. "qwen/qwen-2.5-1.5b-instruct").
     * @param prompt    The user's message.
     * @param maxTokens Cap on response tokens.
     * @param temperature Sampling temperature.
     */
    suspend fun sendSingleMessage(
        model: String,
        prompt: String,
        maxTokens: Int = 300,
        temperature: Double = 0.7,
    ): MessageWithMetrics {
        val request = ChatRequest(
            model = model,
            messages = listOf(ChatMessage(role = "user", content = prompt)),
            maxTokens = maxTokens,
            temperature = temperature,
        )

        val response = client.post("https://routerai.ru/api/v1/chat/completions") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer ${getApiKey()}")
            setBody(request)
        }

        if (!response.status.isSuccess()) {
            throwApiError(response.status.value, response.bodyAsText())
        }

        val chatResponse = response.body<ChatResponse>()
        val assistantMessage = chatResponse.choices.firstOrNull()?.message
            ?: throw Exception("No response from API")

        return MessageWithMetrics(
            content = assistantMessage.effectiveContent,
            usage = chatResponse.usage,
        )
    }
}
