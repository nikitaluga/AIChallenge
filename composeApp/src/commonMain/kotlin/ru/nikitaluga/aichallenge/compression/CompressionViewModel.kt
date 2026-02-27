package ru.nikitaluga.aichallenge.compression

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.nikitaluga.aichallenge.api.RouterAiApiService
import ru.nikitaluga.aichallenge.data.agent.CompressingAgent

class CompressionViewModel : ViewModel() {

    private val apiService = RouterAiApiService()
    private val agent = CompressingAgent(
        apiService = apiService,
        model = "openai/gpt-3.5-turbo-0613",
        systemPrompt = "Ты полезный ассистент. Отвечай на том языке, на котором пишет пользователь.",
        rawWindowSize = 6,
        compressionBatchSize = 10,
        storageKey = "compression_agent_day9",
    )

    private val _state = MutableStateFlow(
        CompressionContract.State(
            exchanges = buildRestoredExchanges(),
            compressionCount = agent.compressionCount,
            rawWindowSize = agent.rawWindowSize,
            compressionBatchSize = agent.compressionBatchSize,
        ),
    )
    val state: StateFlow<CompressionContract.State> = _state.asStateFlow()

    private val _effects = Channel<CompressionContract.Effect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    /** Восстанавливает список обменов из сохранённой истории агента. */
    private fun buildRestoredExchanges(): List<CompressionContract.Exchange> {
        val history = agent.rawHistory
        val result = mutableListOf<CompressionContract.Exchange>()
        var i = 0
        while (i < history.size - 1) {
            if (history[i].role == "user" && history[i + 1].role == "assistant") {
                result.add(
                    CompressionContract.Exchange(
                        userMessage = history[i].content,
                        compressedResponse = history[i + 1].content,
                        isLoading = false,
                        isSingleMode = true,
                    ),
                )
                i += 2
            } else {
                i++
            }
        }
        return result
    }

    fun onEvent(event: CompressionContract.Event) {
        when (event) {
            is CompressionContract.Event.InputChanged ->
                _state.update { it.copy(inputText = event.text) }
            CompressionContract.Event.SendMessage -> sendMessage()
            CompressionContract.Event.ClearHistory -> clearHistory()
            CompressionContract.Event.ToggleLog ->
                _state.update { it.copy(showLog = !it.showLog) }
        }
    }

    private fun sendMessage() {
        val text = _state.value.inputText.trim()
        if (text.isEmpty() || _state.value.isLoading) return

        _state.update { state ->
            state.copy(
                inputText = "",
                isLoading = true,
                exchanges = state.exchanges + CompressionContract.Exchange(
                    userMessage = text,
                    isLoading = true,
                ),
            )
        }

        viewModelScope.launch {
            try {
                val result = agent.sendMessage(text)

                val compressedTotal = result.compressedUsage?.totalTokens ?: 0
                val fullTotal = result.fullUsage?.totalTokens ?: 0
                val savedPercent = if (!result.isSingleMode && fullTotal > 0) {
                    ((fullTotal - compressedTotal).toFloat() / fullTotal * 100f).coerceAtLeast(0f)
                } else 0f

                val newExchange = CompressionContract.Exchange(
                    userMessage = text,
                    compressedResponse = result.compressedContent,
                    fullResponse = result.fullContent ?: "",
                    compressedPromptTokens = result.compressedUsage?.promptTokens ?: 0,
                    compressedCompletionTokens = result.compressedUsage?.completionTokens ?: 0,
                    compressedTotalTokens = compressedTotal,
                    fullPromptTokens = result.fullUsage?.promptTokens ?: 0,
                    fullCompletionTokens = result.fullUsage?.completionTokens ?: 0,
                    fullTotalTokens = fullTotal,
                    savingsPercent = savedPercent,
                    compressionTriggered = result.compressionTriggered,
                    isSingleMode = result.isSingleMode,
                    isLoading = false,
                )

                _state.update { state ->
                    val updatedExchanges = state.exchanges.toMutableList()
                        .also { it[it.lastIndex] = newExchange }

                    // В лог попадают только двойные запросы (есть что сравнивать)
                    val newLog = if (!result.isSingleMode) {
                        state.logEntries + CompressionContract.LogEntry(
                            index = updatedExchanges.size,
                            fullPromptTokens = result.fullUsage?.promptTokens ?: 0,
                            compressedPromptTokens = result.compressedUsage?.promptTokens ?: 0,
                            fullCompletionTokens = result.fullUsage?.completionTokens ?: 0,
                            compressedCompletionTokens = result.compressedUsage?.completionTokens ?: 0,
                            fullTotalTokens = fullTotal,
                            compressedTotalTokens = compressedTotal,
                            savedPercent = savedPercent,
                        )
                    } else {
                        state.logEntries
                    }

                    val avgSaved = if (newLog.isNotEmpty()) {
                        newLog.map { it.savedPercent }.average().toFloat()
                    } else 0f

                    state.copy(
                        isLoading = false,
                        exchanges = updatedExchanges,
                        compressionCount = agent.compressionCount,
                        logEntries = newLog,
                        averageSavedPercent = avgSaved,
                    )
                }
            } catch (e: Exception) {
                _state.update { state ->
                    val exchanges = state.exchanges.toMutableList().also { list ->
                        if (list.isNotEmpty()) {
                            list[list.lastIndex] = list.last().copy(
                                compressedResponse = "Ошибка: ${e.message}",
                                isLoading = false,
                            )
                        }
                    }
                    state.copy(isLoading = false, exchanges = exchanges)
                }
            }
            _effects.send(CompressionContract.Effect.ScrollToBottom)
        }
    }

    private fun clearHistory() {
        agent.clearHistory()
        _state.update {
            CompressionContract.State(
                rawWindowSize = agent.rawWindowSize,
                compressionBatchSize = agent.compressionBatchSize,
            )
        }
    }
}
