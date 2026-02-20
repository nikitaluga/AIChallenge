package ru.nikitaluga.aichallenge.domain.repository

interface ChatRepository {
    suspend fun sendMessage(
        prompt: String,
        systemPrompt: String? = null,
        maxTokens: Int = 200,
        stopSequences: List<String>? = null,
        temperature: Double = 0.7,
    ): String

    fun clearHistory()
}
