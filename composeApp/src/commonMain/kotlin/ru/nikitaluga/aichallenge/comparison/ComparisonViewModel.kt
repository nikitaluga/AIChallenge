package ru.nikitaluga.aichallenge.comparison

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.nikitaluga.aichallenge.data.repository.ChatRepositoryImpl
import ru.nikitaluga.aichallenge.domain.usecase.SendMessageUseCase

private const val JSON_DEMO_PROMPT = "Перечисли 3 преимущества бега"

private val JSON_SYSTEM_PROMPT = """
    Ты отвечаешь ТОЛЬКО на русском языке и ТОЛЬКО в формате JSON.

    Строгая схема ответа:
    {
      "benefits": [
        {
          "title": "короткий заголовок преимущества",
          "description": "развернутое описание на 2-3 предложения"
        }
      ],
      "total_count": 3
    }

    Правила:
    1. НЕ добавляй НИКАКОГО текста до или после JSON
    2. НЕ используй markdown (```json)
    3. НЕ пиши пояснения вроде "Вот ваш JSON:"
    4. Только чистый JSON объект
    5. Все ключи и строки в двойных кавычках
    6. total_count должен соответствовать количеству элементов в benefits
""".trimIndent()

private val COMPARISON_CASES = listOf(
    ComparisonContract.ComparisonCase(
        label = "Без ограничений",
        systemPrompt = null,
        stop = null,
    ),
    ComparisonContract.ComparisonCase(
        label = "JSON ответ (со strict system prompt)",
        systemPrompt = JSON_SYSTEM_PROMPT,
        prompt = JSON_DEMO_PROMPT,
        stop = null,
        validateJson = true,
    ),
    ComparisonContract.ComparisonCase(
        label = "С ограничением длины",
        systemPrompt = "Ответь кратко.",
        maxTokens = 30,
        stop = null,
    ),
    ComparisonContract.ComparisonCase(
        label = "С условием завершения",
        systemPrompt = "Перечисли 5 преимуществ бега.",
        stop = listOf("3."),
    ),
)

class ComparisonViewModel(
    private val sendMessageUseCase: SendMessageUseCase = SendMessageUseCase(ChatRepositoryImpl()),
) : ViewModel() {

    private val _state = MutableStateFlow(
        ComparisonContract.State(
            cases = COMPARISON_CASES,
            cards = List(COMPARISON_CASES.size) { ComparisonContract.CardState() },
        ),
    )
    val state: StateFlow<ComparisonContract.State> = _state.asStateFlow()

    fun onEvent(event: ComparisonContract.Event) {
        when (event) {
            is ComparisonContract.Event.SendCard -> sendCard(event.index)
        }
    }

    private fun sendCard(index: Int) {
        val case = _state.value.cases.getOrNull(index) ?: return

        _state.update { state ->
            state.copy(cards = state.cards.toMutableList().also { it[index] = ComparisonContract.CardState(isLoading = true) })
        }

        viewModelScope.launch {
            sendMessageUseCase.clearHistory()
            val result = sendMessageUseCase(
                prompt = case.prompt,
                systemPrompt = case.systemPrompt,
                maxTokens = case.maxTokens,
                stopSequences = case.stop,
            )
            _state.update { state ->
                state.copy(
                    cards = state.cards.toMutableList().also {
                        it[index] = ComparisonContract.CardState(
                            isLoading = false,
                            result = result.getOrElse { e -> "Ошибка: ${e.message}" },
                        )
                    },
                )
            }
        }
    }
}
