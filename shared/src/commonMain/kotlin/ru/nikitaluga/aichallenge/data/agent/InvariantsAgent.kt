package ru.nikitaluga.aichallenge.data.agent

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import ru.nikitaluga.aichallenge.api.ChatMessage
import ru.nikitaluga.aichallenge.api.RouterAiApiService
import ru.nikitaluga.aichallenge.domain.model.Invariant
import ru.nikitaluga.aichallenge.domain.model.InvariantAgentResult
import ru.nikitaluga.aichallenge.domain.model.InvariantViolation
import ru.nikitaluga.aichallenge.domain.model.ValidationResult

/**
 * День 14 — Агент с инвариантами.
 *
 * Инварианты инжектируются в system prompt при каждом запросе.
 * После получения ответа LLM — отдельный LLM-вызов валидирует ответ на соответствие.
 * При нарушении — автоматический retry с указанием нарушений.
 *
 * Поток:
 *   user → [основной запрос с инвариантами в system] → ответ LLM
 *       → [валидация: отдельный LLM-вызов] → ValidationResult
 *       → Pass: вернуть ответ
 *       → Fail: retry с указанием нарушений → вернуть retried-ответ + violations
 */
class InvariantsAgent(
    private val apiService: RouterAiApiService,
    private val model: String = DEFAULT_MODEL,
    private val baseSystemPrompt: String = "Ты полезный ассистент.",
    private val windowSize: Int = 20,
) {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
    private val _history = mutableListOf<ChatMessage>()
    val history: List<ChatMessage> get() = _history.toList()

    private var _invariants: List<Invariant> = emptyList()

    fun setInvariants(invariants: List<Invariant>) {
        _invariants = invariants
    }

    suspend fun sendMessage(text: String, invariants: List<Invariant>): InvariantAgentResult {
        _invariants = invariants
        _history.add(ChatMessage(role = "user", content = text))

        return try {
            val response = callMain()

            val activeInvariants = invariants.filter { it.enabled }
            val validation = if (activeInvariants.isEmpty()) {
                ValidationResult(passed = true, violations = emptyList())
            } else {
                validateResponse(response, activeInvariants)
            }

            if (validation.passed) {
                _history.add(ChatMessage(role = "assistant", content = response))
                InvariantAgentResult(content = response, violations = emptyList(), wasRetried = false)
            } else {
                // Retry с указанием нарушений
                val retryResponse = callRetry(originalQuestion = text, violations = validation.violations)
                _history.add(ChatMessage(role = "assistant", content = retryResponse))
                InvariantAgentResult(
                    content = retryResponse,
                    violations = validation.violations,
                    wasRetried = true,
                )
            }
        } catch (e: Exception) {
            _history.removeLast()
            throw e
        }
    }

    fun clearHistory() {
        _history.clear()
    }

    // ── Приватные методы ──────────────────────────────────────────────────────

    private suspend fun callMain(): String {
        val messages = buildList {
            add(ChatMessage(role = "system", content = buildSystemPrompt()))
            addAll(_history.takeLast(windowSize))
        }
        return apiService.sendMessages(messages = messages, model = model).content
    }

    private suspend fun callRetry(originalQuestion: String, violations: List<InvariantViolation>): String {
        val violationsList = violations.joinToString("\n") { "- [${it.invariantName}]: ${it.explanation}" }
        val retryPrompt = """
Предыдущий ответ нарушил следующие инварианты:
$violationsList

Дай новый ответ, строго соблюдая все инварианты. Если запрос противоречит инварианту — объясни это пользователю.
Исходный вопрос: $originalQuestion
        """.trimIndent()

        val messages = buildList {
            add(ChatMessage(role = "system", content = buildSystemPrompt()))
            // история без последнего user-сообщения (оно будет в retryPrompt)
            addAll(_history.dropLast(1).takeLast(windowSize - 1))
            add(ChatMessage(role = "user", content = retryPrompt))
        }
        return apiService.sendMessages(messages = messages, model = model).content
    }

    private suspend fun validateResponse(response: String, invariants: List<Invariant>): ValidationResult {
        val invariantsList = invariants.joinToString("\n") { "[${it.name}] ${it.rule}" }
        val prompt = """
Ты — валидатор ответов ассистента. Проверь, нарушает ли ответ какой-либо из инвариантов.

=== ИНВАРИАНТЫ ===
$invariantsList
==================

=== ОТВЕТ АССИСТЕНТА ===
$response
========================

Верни ТОЛЬКО JSON без пояснений и markdown:
{"passed": true, "violations": []}

или если есть нарушения:
{"passed": false, "violations": [{"invariantId": "id_инварианта", "invariantName": "Название", "explanation": "Что именно нарушено"}]}

Нарушение — это когда ответ предлагает, рекомендует или использует то, что запрещено инвариантом.
        """.trimIndent()

        return try {
            val result = apiService.sendMessages(
                messages = listOf(ChatMessage(role = "user", content = prompt)),
                model = model,
                temperature = 0.1,
            )
            val raw = result.content.trim()
            val start = raw.indexOf('{')
            val end = raw.lastIndexOf('}')
            if (start < 0 || end <= start) return ValidationResult(passed = true, violations = emptyList())
            val dto = json.decodeFromString<ValidationResultDto>(raw.substring(start, end + 1))
            ValidationResult(
                passed = dto.passed,
                violations = dto.violations.map {
                    InvariantViolation(
                        invariantId = it.invariantId,
                        invariantName = it.invariantName,
                        explanation = it.explanation,
                    )
                },
            )
        } catch (e: Exception) {
            // При ошибке валидации не блокируем ответ
            ValidationResult(passed = true, violations = emptyList())
        }
    }

    private fun buildSystemPrompt(): String {
        val activeInvariants = _invariants.filter { it.enabled }
        if (activeInvariants.isEmpty()) return baseSystemPrompt

        val invariantsSection = buildString {
            appendLine("\n=== ОБЯЗАТЕЛЬНЫЕ ИНВАРИАНТЫ (НЕЛЬЗЯ НАРУШАТЬ) ===")
            activeInvariants.forEach { appendLine("[${it.name}] ${it.rule}") }
            appendLine("===================================================")
            appendLine("\nСтрого соблюдай инварианты в каждом ответе. Если запрос противоречит инварианту — объясни это пользователю.")
        }
        return baseSystemPrompt + invariantsSection
    }

    // ── DTO для парсинга JSON валидации ───────────────────────────────────────

    @Serializable
    private data class ValidationResultDto(
        val passed: Boolean = true,
        val violations: List<ViolationDto> = emptyList(),
    )

    @Serializable
    private data class ViolationDto(
        val invariantId: String = "",
        val invariantName: String = "",
        val explanation: String = "",
    )

    companion object {
        private const val DEFAULT_MODEL = "deepseek/deepseek-v3.2"
    }
}
