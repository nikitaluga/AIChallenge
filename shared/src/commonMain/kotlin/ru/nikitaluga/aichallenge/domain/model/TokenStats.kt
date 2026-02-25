package ru.nikitaluga.aichallenge.domain.model

/**
 * Snapshot of token usage statistics for a single chat session.
 *
 * @param lastRequestTokens    Estimated tokens in the most recent user message sent.
 * @param lastResponseTokens   Estimated tokens in the most recent assistant reply.
 * @param totalUserTokens      Cumulative user tokens since session start / last clear.
 * @param totalAssistantTokens Cumulative assistant tokens since session start / last clear.
 * @param currentContextTokens Estimated tokens currently occupying the context window.
 * @param contextWindowLimit   Simulated context window ceiling (tokens).
 * @param messageCount         Number of messages in history (user + assistant combined).
 */
data class TokenStats(
    val lastRequestTokens: Int = 0,
    val lastResponseTokens: Int = 0,
    val totalUserTokens: Int = 0,
    val totalAssistantTokens: Int = 0,
    val currentContextTokens: Int = 0,
    val contextWindowLimit: Int = 4096,
    val messageCount: Int = 0,
) {
    /** Total tokens generated this session (user + assistant). */
    val totalSessionTokens: Int get() = totalUserTokens + totalAssistantTokens

    /** Fraction of context window currently in use, clamped to [0, 1]. */
    val contextFillPercent: Float
        get() = (currentContextTokens.toFloat() / contextWindowLimit.coerceAtLeast(1))
            .coerceIn(0f, 1f)

    /** True when context is 80 % or more filled — time to warn the user. */
    val isNearLimit: Boolean get() = contextFillPercent >= 0.8f

    /** True when estimated context tokens meet or exceed the window limit. */
    val isOverLimit: Boolean get() = currentContextTokens >= contextWindowLimit
}
