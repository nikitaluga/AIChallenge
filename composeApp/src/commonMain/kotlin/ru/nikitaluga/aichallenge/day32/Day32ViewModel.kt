package ru.nikitaluga.aichallenge.day32

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import ru.nikitaluga.aichallenge.data.agent.ReviewAgent

private val SAMPLE_DIFF = """
diff --git a/shared/src/commonMain/kotlin/ru/nikitaluga/aichallenge/domain/usecase/GetMessagesUseCase.kt b/shared/src/commonMain/kotlin/ru/nikitaluga/aichallenge/domain/usecase/GetMessagesUseCase.kt
index 1234567..abcdefg 100644
--- a/shared/src/commonMain/kotlin/ru/nikitaluga/aichallenge/domain/usecase/GetMessagesUseCase.kt
+++ b/shared/src/commonMain/kotlin/ru/nikitaluga/aichallenge/domain/usecase/GetMessagesUseCase.kt
@@ -10,7 +10,12 @@ class GetMessagesUseCase(private val repository: ChatRepository) {
     suspend fun execute(): Result<List<ChatMessage>> {
-        return repository.getMessages()
+        val messages = repository.getMessages().getOrNull() ?: emptyList()
+        return Result.success(messages)
     }
 }
""".trimIndent()

class Day32ViewModel : ViewModel() {
    private val agent = ReviewAgent()

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private val _effects = Channel<Effect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    fun onEvent(event: Event) {
        when (event) {
            is Event.DiffChanged -> _state.value = _state.value.copy(diffInput = event.text, error = null)
            is Event.TitleChanged -> _state.value = _state.value.copy(prTitle = event.text)
            Event.SubmitReview -> submitReview()
            Event.ClearResult -> _state.value = _state.value.copy(review = null, error = null)
            Event.InsertSampleDiff -> _state.value = _state.value.copy(diffInput = SAMPLE_DIFF, error = null)
        }
    }

    private fun submitReview() {
        val currentState = _state.value
        if (currentState.diffInput.isBlank()) {
            _state.value = currentState.copy(error = "Вставьте git diff для анализа")
            return
        }
        if (currentState.isLoading) return

        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null, review = null)
            runCatching {
                agent.reviewPr(
                    diff = _state.value.diffInput,
                    title = _state.value.prTitle,
                )
            }.onSuccess { dto ->
                _state.value = _state.value.copy(
                    isLoading = false,
                    review = ReviewResult(
                        bugs = dto.bugs,
                        architecture = dto.architecture,
                        recommendations = dto.recommendations,
                        summary = dto.summary,
                        diffLength = dto.diffLength,
                    ),
                )
                _effects.send(Effect.ScrollToResult)
            }.onFailure { e ->
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = "Ошибка: ${e.message}",
                )
            }
        }
    }
}
