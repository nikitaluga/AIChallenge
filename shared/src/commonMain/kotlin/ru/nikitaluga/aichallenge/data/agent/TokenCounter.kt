package ru.nikitaluga.aichallenge.data.agent

import ru.nikitaluga.aichallenge.api.ChatMessage

/**
 * Estimates token counts for OpenAI-compatible models.
 *
 * Since BPE tokenizers (tiktoken, HuggingFace) are not available in commonMain,
 * we use a character-based heuristic:
 * - Whitespace: not counted
 * - Non-ASCII characters (Cyrillic, CJK, Arabic, etc.): ~2 chars per token
 * - ASCII letters / digits / punctuation: ~4 chars per token
 *
 * Each message adds 4 tokens overhead for role + formatting delimiters.
 * The conversation itself adds 3 tokens of framing overhead (OpenAI convention).
 *
 * Accuracy: typically ±20% for Russian/English mixed text.
 */
object TokenCounter {

    private const val MESSAGE_OVERHEAD = 4

    /**
     * Estimate token count for a raw text string.
     */
    fun countTokens(text: String): Int {
        if (text.isBlank()) return 0
        var nonAscii = 0
        var ascii = 0
        for (c in text) {
            when {
                c.isWhitespace() -> Unit
                c.code > 127 -> nonAscii++
                else -> ascii++
            }
        }
        // Non-ASCII: every 2 chars ≈ 1 token; ASCII: every 4 chars ≈ 1 token
        val nonAsciiTokens = (nonAscii + 1) / 2
        val asciiTokens = (ascii + 3) / 4
        return maxOf(1, nonAsciiTokens + asciiTokens)
    }

    /** Estimate tokens for a single [ChatMessage] including role/delimiter overhead. */
    fun countMessageTokens(message: ChatMessage): Int =
        countTokens(message.effectiveContent) + MESSAGE_OVERHEAD

    /**
     * Estimate total tokens for a list of messages (e.g. full conversation context).
     * Includes per-message overhead and 3-token conversation framing.
     */
    fun countMessagesTokens(messages: List<ChatMessage>): Int =
        if (messages.isEmpty()) 0
        else messages.sumOf { countMessageTokens(it) } + 3
}
