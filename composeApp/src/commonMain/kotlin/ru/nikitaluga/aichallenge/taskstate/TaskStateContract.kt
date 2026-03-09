package ru.nikitaluga.aichallenge.taskstate

import ru.nikitaluga.aichallenge.domain.model.TaskStage

object TaskStateContract {

    data class State(
        val stage: TaskStage = TaskStage.PLANNING,
        val messages: List<DisplayMessage> = emptyList(),
        val inputText: String = "",
        val isLoading: Boolean = false,
        val taskDescription: String = "",
        val planArtifact: String = "",
        val executionArtifact: String = "",
        val validationArtifact: String = "",
        /** Текст артефакта текущей стадии, который пользователь редактирует перед утверждением */
        val pendingArtifact: String = "",
        /** Показывать панель редактирования и утверждения артефакта */
        val showArtifactPanel: Boolean = false,
        /** Текст последней ошибки, null если ошибки нет */
        val errorMessage: String? = null,
    ) {
        val currentArtifact: String get() = when (stage) {
            TaskStage.PLANNING -> planArtifact
            TaskStage.EXECUTION -> executionArtifact
            TaskStage.VALIDATION -> validationArtifact
            TaskStage.DONE -> validationArtifact
        }

        val approveButtonLabel: String get() = when (stage) {
            TaskStage.PLANNING -> "Утвердить план"
            TaskStage.EXECUTION -> "Утвердить результат"
            TaskStage.VALIDATION -> "Завершить задачу"
            TaskStage.DONE -> "Новая задача"
        }
    }

    data class DisplayMessage(
        val role: String,
        val content: String,
        val stage: TaskStage,
    )

    sealed interface Event {
        data class InputChanged(val text: String) : Event
        data object SendMessage : Event
        data class ArtifactChanged(val text: String) : Event
        /** Показать панель артефакта (берём последний ответ ассистента) */
        data object ShowArtifactPanel : Event
        /** Утвердить артефакт и детерминированно перейти к следующей стадии */
        data object ApproveArtifact : Event
        /** Скрыть панель артефакта, продолжить диалог в текущей стадии */
        data object DismissArtifactPanel : Event
        data object DismissError : Event
        /** Полный сброс */
        data object NewTask : Event
    }

    sealed interface Effect {
        data object ScrollToBottom : Effect
    }
}
