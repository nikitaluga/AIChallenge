package ru.nikitaluga.aichallenge.domain.model

import kotlinx.serialization.Serializable

enum class TaskStage {
    PLANNING, EXECUTION, VALIDATION, DONE;

    fun next(): TaskStage = when (this) {
        PLANNING -> EXECUTION
        EXECUTION -> VALIDATION
        VALIDATION -> DONE
        DONE -> PLANNING
    }

    val label: String get() = when (this) {
        PLANNING -> "Планирование"
        EXECUTION -> "Выполнение"
        VALIDATION -> "Валидация"
        DONE -> "Готово"
    }

    val actionHint: String get() = when (this) {
        PLANNING -> "Опишите задачу — агент поможет составить план"
        EXECUTION -> "Агент выполняет задачу по плану"
        VALIDATION -> "Агент проверяет соответствие результата плану"
        DONE -> "Задача завершена. Нажмите «Новая задача» для сброса"
    }
}

@Serializable
data class TaskStateSnapshot(
    val stage: TaskStage = TaskStage.PLANNING,
    val taskDescription: String = "",
    val planArtifact: String = "",
    val executionArtifact: String = "",
    val validationArtifact: String = "",
)
