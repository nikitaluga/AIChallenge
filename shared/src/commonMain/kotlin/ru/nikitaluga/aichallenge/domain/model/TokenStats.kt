package ru.nikitaluga.aichallenge.domain.model

/**
 * Snapshot of token usage statistics for a single chat session.
 * All values come exclusively from API-reported usage data.
 *
 * @param lastPromptTokens        Tokens in the full context sent with the last request.
 * @param lastCompletionTokens    Tokens generated in the last response.
 * @param lastTotalTokens         Total tokens for the last exchange (prompt + completion).
 * @param sessionPromptTokens     Cumulative prompt tokens sent across all exchanges this session.
 * @param sessionCompletionTokens Cumulative completion tokens generated this session.
 * @param messageCount            Number of messages in history (user + assistant combined).
 * @param lastModel               Model identifier used for the last response.
 */
data class TokenStats(
    val lastPromptTokens: Int = 0,
    val lastCompletionTokens: Int = 0,
    val lastTotalTokens: Int = 0,
    val sessionPromptTokens: Int = 0,
    val sessionCompletionTokens: Int = 0,
    val messageCount: Int = 0,
    val lastModel: String = "",
) {
    /** Total tokens consumed across all exchanges this session. */
    val sessionTotalTokens: Int get() = sessionPromptTokens + sessionCompletionTokens

    /** True when API token data is available (i.e. at least one successful exchange). */
    val hasApiUsage: Boolean get() = lastTotalTokens > 0
}
