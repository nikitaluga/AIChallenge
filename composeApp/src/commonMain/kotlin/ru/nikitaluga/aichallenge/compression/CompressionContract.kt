package ru.nikitaluga.aichallenge.compression

object CompressionContract {

    data class State(
        val exchanges: List<Exchange> = emptyList(),
        val isLoading: Boolean = false,
        val inputText: String = "",
        /** Количество суммаризаций, выполненных агентом. */
        val compressionCount: Int = 0,
        /** Показывать ли лог метрик. */
        val showLog: Boolean = false,
        val logEntries: List<LogEntry> = emptyList(),
        /** Конфигурация агента (только для отображения). */
        val rawWindowSize: Int = 6,
        val compressionBatchSize: Int = 10,
        /** Средняя экономия токенов по всем запросам в процентах. */
        val averageSavedPercent: Float = 0f,
    )

    /** Один обмен «запрос → ответ(ы)». */
    data class Exchange(
        val userMessage: String,
        // Ответы
        val compressedResponse: String = "",
        val fullResponse: String = "",
        // Метрики токенов — сжатый контекст
        val compressedPromptTokens: Int = 0,
        val compressedCompletionTokens: Int = 0,
        val compressedTotalTokens: Int = 0,
        // Метрики токенов — полный контекст
        val fullPromptTokens: Int = 0,
        val fullCompletionTokens: Int = 0,
        val fullTotalTokens: Int = 0,
        /** Экономия в % = (full − compressed) / full × 100. */
        val savingsPercent: Float = 0f,
        /** true если после этого обмена была запущена суммаризация. */
        val compressionTriggered: Boolean = false,
        /**
         * true — summary ещё не было, отправлялся один запрос, сравнение недоступно.
         * false — summary есть, отправлялось два параллельных запроса.
         */
        val isSingleMode: Boolean = true,
        val isLoading: Boolean = true,
    )

    /** Строка лога метрик для одного обмена. */
    data class LogEntry(
        val index: Int,
        val fullPromptTokens: Int,
        val compressedPromptTokens: Int,
        val fullCompletionTokens: Int,
        val compressedCompletionTokens: Int,
        val fullTotalTokens: Int,
        val compressedTotalTokens: Int,
        val savedPercent: Float,
    )

    sealed interface Event {
        data class InputChanged(val text: String) : Event
        data object SendMessage : Event
        data object ClearHistory : Event
        data object ToggleLog : Event
    }

    sealed interface Effect {
        data object ScrollToBottom : Effect
    }
}
