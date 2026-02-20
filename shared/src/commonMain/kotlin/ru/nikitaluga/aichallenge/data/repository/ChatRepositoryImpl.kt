package ru.nikitaluga.aichallenge.data.repository

import ru.nikitaluga.aichallenge.api.RouterAiApiService
import ru.nikitaluga.aichallenge.domain.repository.ChatRepository

class ChatRepositoryImpl(
    private val apiService: RouterAiApiService = RouterAiApiService(),
) : ChatRepository {

    override suspend fun sendMessage(
        prompt: String,
        systemPrompt: String?,
        maxTokens: Int,
        stopSequences: List<String>?,
        temperature: Double,
    ): String = apiService.sendMessage(prompt, systemPrompt, maxTokens, stopSequences, temperature)

    override fun clearHistory() = apiService.clearHistory()
}
