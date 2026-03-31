package ru.nikitaluga.aichallenge.review

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import ru.nikitaluga.aichallenge.api.ChatMessage
import ru.nikitaluga.aichallenge.api.RouterAiApiService
import ru.nikitaluga.aichallenge.dev.DevDocsIndexer
import ru.nikitaluga.aichallenge.dev.DevDocsRepository

private const val REVIEW_MODEL = "openai/gpt-4o-mini"
private const val RAG_K = 5
private const val RAG_THRESHOLD = 0.28f

// День 32 — ReviewService.
// Анализирует git diff: RAG по документации + LLM → структурированное ревью.
class ReviewService(
    private val repository: DevDocsRepository,
    private val indexer: DevDocsIndexer,
    private val apiService: RouterAiApiService,
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    suspend fun review(request: ReviewRequest): ReviewResponse {
        val trimmedDiff = request.diff.take(request.maxDiffLength.coerceIn(1000, 12000))

        // RAG: ищем релевантные архитектурные правила по diff
        val ragChunks = runCatching { indexer.search(trimmedDiff.take(500), RAG_K) }
            .getOrDefault(emptyList())
        val relevantChunks = ragChunks.filter { it.score >= RAG_THRESHOLD }

        val ragContext = if (relevantChunks.isNotEmpty()) {
            relevantChunks.joinToString("\n\n---\n\n") { chunk ->
                buildString {
                    append("Источник: ${chunk.source}")
                    if (chunk.section != null) append(" / ${chunk.section}")
                    append("\n")
                    append(chunk.text.take(500))
                }
            }
        } else ""

        val systemPrompt = buildString {
            append("Ты — эксперт по code review Kotlin Multiplatform проектов.\n")
            append("Проект использует MVI + Clean Architecture, Compose Multiplatform, Ktor.\n\n")
            if (ragContext.isNotEmpty()) {
                append("Архитектурные правила из документации проекта:\n")
                append(ragContext)
                append("\n\n")
            }
            append(
                """Проанализируй git diff и верни ТОЛЬКО валидный JSON без markdown-блоков:
{
  "bugs": ["описание потенциального бага 1"],
  "architecture": ["архитектурная проблема 1"],
  "recommendations": ["рекомендация 1"],
  "summary": "Краткое резюме (2-3 предложения)"
}

Правила анализа:
- bugs: null pointer, race condition, утечки ресурсов, неправильная обработка Result/ошибок
- architecture: нарушение MVI (изменение State вне ViewModel), нарушение Clean Architecture (UI импортирует data-слой), KMP-специфичные проблемы (platform-зависимый код в commonMain)
- recommendations: улучшения читаемости, производительности, соответствия паттернам проекта
- Если нет проблем в категории — пустой массив []
- Отвечай на русском языке
- НЕ добавляй ничего кроме JSON"""
            )
        }

        val titleLine = if (request.title.isNotBlank()) "PR: ${request.title}\n\n" else ""
        val descLine = if (request.description.isNotBlank()) "Описание: ${request.description}\n\n" else ""
        val userMessage = "${titleLine}${descLine}```diff\n${trimmedDiff}\n```"

        val messages = listOf(
            ChatMessage(role = "system", content = systemPrompt),
            ChatMessage(role = "user", content = userMessage),
        )

        val llmResult = apiService.sendMessages(messages = messages, model = REVIEW_MODEL)
        val rawContent = llmResult.content ?: "{}"

        val parsed = runCatching {
            // strip possible markdown code fences
            val cleaned = rawContent
                .removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
            json.decodeFromString<ReviewJson>(cleaned)
        }.getOrElse {
            ReviewJson(summary = "Не удалось разобрать ответ LLM: ${it.message}")
        }

        return ReviewResponse(
            bugs = parsed.bugs,
            architecture = parsed.architecture,
            recommendations = parsed.recommendations,
            summary = parsed.summary,
            model = REVIEW_MODEL,
            diffLength = trimmedDiff.length,
        )
    }
}

@Serializable
private data class ReviewJson(
    val bugs: List<String> = emptyList(),
    val architecture: List<String> = emptyList(),
    val recommendations: List<String> = emptyList(),
    val summary: String = "",
)
