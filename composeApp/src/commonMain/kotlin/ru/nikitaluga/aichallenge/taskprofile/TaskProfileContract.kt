package ru.nikitaluga.aichallenge.taskprofile

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import ru.nikitaluga.aichallenge.domain.model.ClassificationResult
import ru.nikitaluga.aichallenge.domain.model.InvalidTransitionAttempt
import ru.nikitaluga.aichallenge.domain.model.ProfileStage
import ru.nikitaluga.aichallenge.domain.model.TaskProfile

object TaskProfileContract {

    /** Сохранённое действие для повтора после ошибки сети */
    sealed interface RetryAction {
        data class SendMessage(val text: String) : RetryAction
        data class Classify(val description: String) : RetryAction
        data class AutoStart(val stage: ProfileStage) : RetryAction
    }

    data class State(
        val profile: TaskProfile? = null,
        val classificationResult: ClassificationResult? = null,
        val showClassificationPanel: Boolean = false,
        val isClassifying: Boolean = false,
        val stages: ImmutableList<ProfileStage> = persistentListOf(),
        val currentStageIndex: Int = 0,
        val messages: List<DisplayMessage> = emptyList(),
        val inputText: String = "",
        val isLoading: Boolean = false,
        val pendingArtifact: String = "",
        val showArtifactPanel: Boolean = false,
        val invalidTransition: InvalidTransitionAttempt? = null,
        val errorMessage: String? = null,
        val retryAction: RetryAction? = null,
    ) {
        val currentStage: ProfileStage
            get() = stages.getOrElse(currentStageIndex) { ProfileStage.DONE }

        val isProfileConfirmed: Boolean
            get() = profile != null && !showClassificationPanel

        val approveButtonLabel: String
            get() = when (currentStage) {
                ProfileStage.PLANNING -> "Утвердить план"
                ProfileStage.EXECUTION -> "Утвердить результат"
                ProfileStage.VALIDATION -> "Завершить задачу"
                ProfileStage.RESEARCH -> "Утвердить исследование"
                ProfileStage.SYNTHESIS -> "Завершить синтез"
                ProfileStage.DONE -> "Новая задача"
            }
    }

    data class DisplayMessage(
        val role: String,
        val content: String,
        val stage: ProfileStage,
        /** true = системное уведомление о недопустимом переходе */
        val isSystemNotice: Boolean = false,
    )

    sealed interface Event {
        data class InputChanged(val text: String) : Event
        data object SendMessage : Event
        /** Подтвердить профиль (возможно, изменённый пользователем) */
        data class ConfirmProfile(val profile: TaskProfile) : Event
        data object DismissClassificationPanel : Event
        data class ArtifactChanged(val text: String) : Event
        /** Показать панель артефакта (берём последний ответ ассистента) */
        data object ShowArtifactPanel : Event
        /** Утвердить артефакт и перейти к следующей стадии */
        data object ApproveArtifact : Event
        /** Скрыть панель артефакта */
        data object DismissArtifactPanel : Event
        /** DEMO: попытка перепрыгнуть через стадию */
        data object TryInvalidTransition : Event
        /** Повторить последнее упавшее действие */
        data object Retry : Event
        data object DismissError : Event
        data object NewTask : Event
    }

    sealed interface Effect {
        data object ScrollToBottom : Effect
    }
}
