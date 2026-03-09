package ru.nikitaluga.aichallenge.domain.model

import kotlinx.serialization.Serializable

enum class TaskProfile(val displayName: String, val stages: List<ProfileStage>) {
    DEV_TASK(
        "Разработка",
        listOf(ProfileStage.PLANNING, ProfileStage.EXECUTION, ProfileStage.VALIDATION, ProfileStage.DONE),
    ),
    RESEARCH_TASK(
        "Исследование",
        listOf(ProfileStage.RESEARCH, ProfileStage.SYNTHESIS, ProfileStage.DONE),
    ),
    SIMPLE_TASK(
        "Простой вопрос",
        listOf(ProfileStage.DONE),
    ),
}

enum class ProfileStage(val label: String, val actionHint: String) {
    PLANNING("Планирование", "Составляем план разработки"),
    EXECUTION("Выполнение", "Реализуем по плану"),
    VALIDATION("Валидация", "Проверяем соответствие результата"),
    RESEARCH("Исследование", "Собираем информацию"),
    SYNTHESIS("Синтез", "Формируем структурированный итог"),
    DONE("Готово", "Задача завершена"),
}

@Serializable
data class ClassificationResult(
    val profile: TaskProfile,
    val reason: String,
)

@Serializable
data class ProfiledSnapshot(
    val profile: TaskProfile? = null,
    val classificationConfirmed: Boolean = false,
    val currentStageIndex: Int = 0,
    val taskDescription: String = "",
    val artifacts: Map<String, String> = emptyMap(),
)

data class InvalidTransitionAttempt(
    val fromStage: ProfileStage,
    val toStage: ProfileStage,
    val reason: String,
)