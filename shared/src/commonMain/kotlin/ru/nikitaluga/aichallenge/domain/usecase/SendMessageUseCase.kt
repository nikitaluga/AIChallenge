package ru.nikitaluga.aichallenge.domain.usecase

import ru.nikitaluga.aichallenge.domain.repository.ChatRepository

class SendMessageUseCase(private val repository: ChatRepository) {
    suspend operator fun invoke(
        prompt: String,
        systemPrompt: String? = null,
        maxTokens: Int = 2048,
        stopSequences: List<String>? = null,
        temperature: Double = 0.7,
    ): Result<String> = runCatching {
        repository.sendMessage(prompt, systemPrompt, maxTokens, stopSequences, temperature)
    }

    fun clearHistory() = repository.clearHistory()
}
