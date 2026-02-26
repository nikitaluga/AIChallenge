package ru.nikitaluga.aichallenge.token

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
import ru.nikitaluga.aichallenge.data.agent.ChatAgent

class TokenViewModel : ViewModel() {

    private val apiService = RouterAiApiService()
    private val agent = ChatAgent(
        model = "openai/gpt-3.5-turbo-0613",
        apiService = apiService,
        systemPrompt = "Ты полезный ассистент. Давай развёрнутые ответы. Отвечай на том языке, на котором пишет пользователь.",
        contextWindowLimit = Int.MAX_VALUE,
        storageKey = "token_agent_history",
    )

    // Track which message index to send for each scenario
    private val scenarioIndices = mutableMapOf<TokenContract.Scenario, Int>()

    private val _state = MutableStateFlow(
        TokenContract.State(
            messages = agent.history.map { msg ->
                TokenContract.DisplayMessage(
                    role = msg.role,
                    content = msg.effectiveContent,
                    tokenCount = agent.countTokens(msg.effectiveContent),
                )
            },
            tokenStats = agent.getTokenStats(),
        ),
    )
    val state: StateFlow<TokenContract.State> = _state.asStateFlow()

    private val _effects = Channel<TokenContract.Effect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    fun onEvent(event: TokenContract.Event) {
        when (event) {
            is TokenContract.Event.InputChanged ->
                _state.update { it.copy(inputText = event.text) }

            TokenContract.Event.SendMessage -> sendMessage()
            TokenContract.Event.ClearHistory -> clearHistory()
            TokenContract.Event.ToggleStats ->
                _state.update { it.copy(showStats = !it.showStats) }

            is TokenContract.Event.LoadScenario -> loadScenario(event.scenario)
        }
    }

    private fun sendMessage() {
        val text = _state.value.inputText.trim()
        if (text.isEmpty() || _state.value.isStreaming) return

        val estimatedRequestTokens = agent.countTokens(text)

        _state.update { state ->
            state.copy(
                inputText = "",
                isStreaming = true,
                streamingText = "",
                messages = state.messages + TokenContract.DisplayMessage(
                    role = "user",
                    content = text,
                    tokenCount = estimatedRequestTokens,
                ),
            )
        }

        viewModelScope.launch {
            try {
                val finalText = agent.sendMessage(text)
                val responseTokens = agent.countTokens(finalText)
                _state.update { state ->
                    state.copy(
                        isStreaming = false,
                        messages = state.messages + TokenContract.DisplayMessage(
                            role = "assistant",
                            content = finalText,
                            tokenCount = responseTokens,
                        ),
                        tokenStats = agent.getTokenStats(),
                    )
                }
            } catch (e: Exception) {
                _state.update { state ->
                    state.copy(
                        isStreaming = false,
                        messages = state.messages + TokenContract.DisplayMessage(
                            role = "assistant",
                            content = "Ошибка: ${e.message}",
                            tokenCount = 0,
                        ),
                        tokenStats = agent.getTokenStats(),
                    )
                }
            }
            _effects.send(TokenContract.Effect.ScrollToBottom)
        }
    }

    /** Fill the input with the next message from the chosen scenario sequence. */
    private fun loadScenario(scenario: TokenContract.Scenario) {
        val idx = scenarioIndices.getOrElse(scenario) { 0 }
        if (idx >= scenario.messages.size) return
        _state.update { it.copy(inputText = scenario.messages[idx]) }
        scenarioIndices[scenario] = idx + 1
    }

    private fun clearHistory() {
        agent.clearHistory()
        scenarioIndices.clear()
        _state.update { TokenContract.State() }
    }
}
