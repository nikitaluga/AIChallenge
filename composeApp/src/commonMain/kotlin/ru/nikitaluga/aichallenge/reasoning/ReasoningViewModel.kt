package ru.nikitaluga.aichallenge.reasoning

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.nikitaluga.aichallenge.data.repository.ChatRepositoryImpl
import ru.nikitaluga.aichallenge.domain.usecase.SendMessageUseCase
import kotlin.math.roundToInt

private val TASK_GENERATION_SYSTEM = """
    Ты — генератор учебных задач. Твоя задача — создать интересную задачу указанного типа и сложности.

    Правила генерации:
    - Задача должна быть оригинальной (не бери классические задачи типа "8 шаров" или "палиндром")
    - Задача должна быть сформулирована четко и однозначно
    - Не давай решение задачи в условии
    - Задача должна быть на русском языке
    - Сложность 1 — очень простая, может решить даже новичок
    - Сложность 5 — экспертная, требует глубокого анализа
""".trimIndent()

private const val STEP_BY_STEP_SYSTEM =
    "Решай пошагово, объясняя каждый шаг. Сначала опиши план, потом реализуй его шаг за шагом."

private const val COMPARISON_SYSTEM_PROMPT =
    "Ты — аналитик, который сравнивает различные способы решения одной и той же задачи. " +
    "Проанализируй предоставленные 4 ответа от языковой модели (каждый получен разным способом) " +
    "и составь сравнительный анализ в несколько предложений."

private val EXPERT_GROUP_SYSTEM = """
    Ты — группа из трёх экспертов: Аналитик, Инженер и Критик.
    Каждый эксперт должен предложить своё решение следующей задачи, исходя из своей роли:
    - Аналитик: подходит логически, разбивает задачу на подзадачи, использует дедукцию.
    - Инженер: предлагает практическое, пошаговое решение, возможно с аналогиями или примерами.
    - Критик: анализирует оба предложенных решения, указывает на сильные и слабые стороны, выбирает лучшее и объясняет почему.

    Представь ответ в формате:
    Аналитик: <решение аналитика>
    Инженер: <решение инженера>
    Критик: <анализ и итоговое решение>
""".trimIndent()

class ReasoningViewModel(
    private val sendMessageUseCase: SendMessageUseCase = SendMessageUseCase(ChatRepositoryImpl()),
) : ViewModel() {

    private val _state = MutableStateFlow(ReasoningContract.State())
    val state: StateFlow<ReasoningContract.State> = _state.asStateFlow()

    fun onEvent(event: ReasoningContract.Event) {
        when (event) {
            is ReasoningContract.Event.DifficultyChanged -> _state.update { it.copy(difficulty = event.value) }
            ReasoningContract.Event.GenerateTask -> generateTask()
            ReasoningContract.Event.SolveAll -> solveAll()
            ReasoningContract.Event.Reset -> reset()
        }
    }

    private fun generateTask() {
        val difficulty = _state.value.difficulty.roundToInt()
        _state.update {
            it.copy(
                isGeneratingTask = true,
                generatedTask = null,
                method1 = ReasoningContract.MethodState(),
                method2 = ReasoningContract.MethodState(),
                method3 = ReasoningContract.MethodState(),
                method4 = ReasoningContract.MethodState(),
                comparison = ReasoningContract.MethodState(),
            )
        }
        viewModelScope.launch {
            val userPrompt = "Сгенерируй задачу одного из следующих типов " +
                "(выбери сам рандомно): логическая, алгоритмическая, аналитическая.\n" +
                "Сложность задачи: $difficulty из 5.\n\n" +
                "Верни ТОЛЬКО текст задачи, без пояснений, без комментариев, " +
                "без вариантов ответа. Только условие задачи."
            sendMessageUseCase.clearHistory()
            val result = sendMessageUseCase(
                prompt = userPrompt,
                systemPrompt = TASK_GENERATION_SYSTEM,
                maxTokens = 512,
            )
            _state.update { it.copy(isGeneratingTask = false, generatedTask = result.getOrNull()) }
        }
    }

    private fun solveAll() {
        val task = _state.value.generatedTask ?: return
        _state.update { it.copy(isSolving = true) }

        viewModelScope.launch {
            // Method 1: Direct answer
            _state.update { it.copy(method1 = ReasoningContract.MethodState(isLoading = true)) }
            sendMessageUseCase.clearHistory()
            val m1 = sendMessageUseCase(prompt = task, maxTokens = 1024)
            _state.update {
                it.copy(
                    method1 = if (m1.isSuccess)
                        ReasoningContract.MethodState(result = m1.getOrNull())
                    else
                        ReasoningContract.MethodState(error = m1.exceptionOrNull()?.message ?: "Ошибка"),
                )
            }

            // Method 2: Step-by-step
            _state.update { it.copy(method2 = ReasoningContract.MethodState(isLoading = true)) }
            sendMessageUseCase.clearHistory()
            val m2 = sendMessageUseCase(prompt = task, systemPrompt = STEP_BY_STEP_SYSTEM, maxTokens = 1024)
            _state.update {
                it.copy(
                    method2 = if (m2.isSuccess)
                        ReasoningContract.MethodState(result = m2.getOrNull())
                    else
                        ReasoningContract.MethodState(error = m2.exceptionOrNull()?.message ?: "Ошибка"),
                )
            }

            // Method 3: Generated prompt → solution
            _state.update { it.copy(method3 = ReasoningContract.MethodState(isLoading = true)) }
            sendMessageUseCase.clearHistory()
            val promptGenRequest = "Составь подробный промпт (инструкцию), который поможет правильно решить " +
                "следующую задачу. Промпт должен содержать четкие указания, которые приведут " +
                "к верному решению. Задача: $task. " +
                "Ответ дай в виде готового промпта (только текст промпта, без пояснений)."
            val genPromptResult = sendMessageUseCase(prompt = promptGenRequest, maxTokens = 1024)
            val generatedPrompt = genPromptResult.getOrNull()
            if (generatedPrompt != null && generatedPrompt.isNotBlank()) {
                sendMessageUseCase.clearHistory()
                val solution = sendMessageUseCase(prompt = task, systemPrompt = generatedPrompt, maxTokens = 1024)
                _state.update {
                    it.copy(
                        method3 = if (solution.isSuccess)
                            ReasoningContract.MethodState(result = solution.getOrNull(), generatedPrompt = generatedPrompt)
                        else
                            ReasoningContract.MethodState(error = solution.exceptionOrNull()?.message ?: "Ошибка", generatedPrompt = generatedPrompt),
                    )
                }
            } else {
                _state.update {
                    it.copy(
                        method3 = ReasoningContract.MethodState(
                            error = genPromptResult.exceptionOrNull()?.message ?: "Модель вернула пустой промпт",
                        ),
                    )
                }
            }

            // Method 4: Expert group
            _state.update { it.copy(method4 = ReasoningContract.MethodState(isLoading = true)) }
            sendMessageUseCase.clearHistory()
            val m4 = sendMessageUseCase(prompt = task, systemPrompt = EXPERT_GROUP_SYSTEM, maxTokens = 1024)
            _state.update {
                it.copy(
                    method4 = if (m4.isSuccess)
                        ReasoningContract.MethodState(result = m4.getOrNull())
                    else
                        ReasoningContract.MethodState(error = m4.exceptionOrNull()?.message ?: "Ошибка"),
                )
            }

            // Comparative analysis (only if all methods succeeded)
            val s = _state.value
            val allSucceeded = listOf(s.method1, s.method2, s.method3, s.method4).all { it.result != null }
            if (allSucceeded) {
                _state.update { it.copy(comparison = ReasoningContract.MethodState(isLoading = true)) }
                val comparisonPrompt = buildString {
                    appendLine("Задача: $task")
                    appendLine()
                    appendLine("Способ 1 (Прямой ответ, без системного промпта):")
                    appendLine(s.method1.result)
                    appendLine()
                    appendLine("Способ 2 (Пошаговое решение):")
                    appendLine(s.method2.result)
                    appendLine()
                    appendLine("Способ 3 (Решение через сгенерированный промпт):")
                    appendLine(s.method3.result)
                    appendLine()
                    appendLine("Способ 4 (Группа экспертов: Аналитик + Инженер + Критик):")
                    appendLine(s.method4.result)
                    appendLine()
                    appendLine("Проанализируй и сравни эти 4 ответа по следующим критериям:")
                    appendLine("1. Точность решения (правильность ответа)")
                    appendLine("2. Полнота (все ли аспекты задачи раскрыты)")
                    appendLine("3. Ясность и понятность объяснения")
                    appendLine("4. Логичность и структурированность")
                    appendLine("5. Креативность / нестандартный подход")
                    appendLine("6. Длина ответа (лаконичность)")
                }
                sendMessageUseCase.clearHistory()
                val comparison = sendMessageUseCase(
                    prompt = comparisonPrompt,
                    systemPrompt = COMPARISON_SYSTEM_PROMPT,
                    maxTokens = 1024,
                )
                _state.update {
                    it.copy(
                        comparison = if (comparison.isSuccess)
                            ReasoningContract.MethodState(result = comparison.getOrNull())
                        else
                            ReasoningContract.MethodState(error = comparison.exceptionOrNull()?.message ?: "Ошибка"),
                    )
                }
            }

            _state.update { it.copy(isSolving = false) }
        }
    }

    private fun reset() {
        _state.update {
            it.copy(
                generatedTask = null,
                method1 = ReasoningContract.MethodState(),
                method2 = ReasoningContract.MethodState(),
                method3 = ReasoningContract.MethodState(),
                method4 = ReasoningContract.MethodState(),
                comparison = ReasoningContract.MethodState(),
            )
        }
    }
}
