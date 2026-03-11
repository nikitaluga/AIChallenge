package ru.nikitaluga.aichallenge.taskprofile

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
import ru.nikitaluga.aichallenge.data.agent.ProfiledTaskAgent
import ru.nikitaluga.aichallenge.domain.model.ProfileStage
import ru.nikitaluga.aichallenge.domain.model.TaskProfile

class TaskProfileViewModel : ViewModel() {

    private val apiService = RouterAiApiService()
    private val agent = ProfiledTaskAgent(apiService = apiService)

    private val _state = MutableStateFlow(TaskProfileContract.State())
    val state: StateFlow<TaskProfileContract.State> = _state.asStateFlow()

    private val _effects = Channel<TaskProfileContract.Effect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    init {
        syncFromAgent()
    }

    fun onEvent(event: TaskProfileContract.Event) {
        when (event) {
            is TaskProfileContract.Event.InputChanged ->
                _state.update { it.copy(inputText = event.text) }

            TaskProfileContract.Event.SendMessage -> sendMessage()

            is TaskProfileContract.Event.ConfirmProfile -> confirmProfile(event.profile)

            TaskProfileContract.Event.DismissClassificationPanel ->
                _state.update { it.copy(showClassificationPanel = false, classificationResult = null) }

            is TaskProfileContract.Event.ArtifactChanged ->
                _state.update { it.copy(pendingArtifact = event.text) }

            TaskProfileContract.Event.ShowArtifactPanel -> {
                val lastAssistantMsg = agent.currentHistory
                    .lastOrNull { it.role == "assistant" }?.content
                    ?: agent.currentSnapshot.taskDescription
                _state.update { it.copy(showArtifactPanel = true, pendingArtifact = lastAssistantMsg) }
            }

            TaskProfileContract.Event.ApproveArtifact -> approveArtifact()

            TaskProfileContract.Event.DismissArtifactPanel ->
                _state.update { it.copy(showArtifactPanel = false, pendingArtifact = "") }

            TaskProfileContract.Event.TryInvalidTransition -> tryInvalidTransition()

            TaskProfileContract.Event.Retry -> retry()

            TaskProfileContract.Event.DismissError ->
                _state.update { it.copy(errorMessage = null, retryAction = null) }

            TaskProfileContract.Event.NewTask -> {
                agent.reset()
                _state.update { TaskProfileContract.State() }
            }
        }
    }

    // ── Чат ──────────────────────────────────────────────────────────────────

    private fun sendMessage() {
        val text = _state.value.inputText.trim()
        if (text.isEmpty() || _state.value.isLoading || _state.value.isClassifying) return
        _state.update { it.copy(inputText = "", errorMessage = null, retryAction = null) }

        if (!_state.value.isProfileConfirmed) {
            classifyAndShow(text)
        } else {
            sendToAgent(text)
        }
    }

    private fun classifyAndShow(description: String) {
        _state.update { it.copy(isClassifying = true, errorMessage = null, retryAction = null) }
        viewModelScope.launch {
            try {
                val result = agent.classifyTask(description)
                _state.update { it.copy(
                    isClassifying = false,
                    classificationResult = result,
                    showClassificationPanel = true,
                ) }
            } catch (e: Exception) {
                _state.update { it.copy(
                    isClassifying = false,
                    errorMessage = apiService.friendlyError(e),
                    retryAction = TaskProfileContract.RetryAction.Classify(description),
                ) }
            }
        }
    }

    private fun sendToAgent(text: String) {
        _state.update { it.copy(isLoading = true, errorMessage = null, retryAction = null) }
        viewModelScope.launch {
            try {
                agent.sendMessage(text)
                syncFromAgent()
                _state.update { it.copy(isLoading = false) }
                _effects.send(TaskProfileContract.Effect.ScrollToBottom)
            } catch (e: Exception) {
                _state.update { it.copy(
                    isLoading = false,
                    errorMessage = apiService.friendlyError(e),
                    retryAction = TaskProfileContract.RetryAction.SendMessage(text),
                ) }
            }
        }
    }

    // ── Профиль ───────────────────────────────────────────────────────────────

    private fun confirmProfile(profile: TaskProfile) {
        agent.confirmProfile(profile)
        syncFromAgent()
        _state.update { it.copy(showClassificationPanel = false, classificationResult = null) }

        val description = agent.currentSnapshot.taskDescription
        val prompt = when (profile) {
            TaskProfile.SIMPLE_TASK -> "Ответь на следующий вопрос напрямую и кратко: $description"
            TaskProfile.DEV_TASK -> description
            TaskProfile.RESEARCH_TASK -> description
        }
        sendToAgent(prompt)
    }

    // ── Переход стадии ────────────────────────────────────────────────────────

    private fun approveArtifact() {
        val artifact = _state.value.pendingArtifact

        if (_state.value.currentStage == ProfileStage.DONE) {
            agent.reset()
            _state.update { TaskProfileContract.State() }
            return
        }

        val error = agent.tryAdvance(artifact)
        if (error != null) {
            addSystemNotice(error.fromStage, error.toStage, error.reason)
            _state.update { it.copy(showArtifactPanel = false, pendingArtifact = "", invalidTransition = error) }
            return
        }

        syncFromAgent()
        _state.update { it.copy(showArtifactPanel = false, pendingArtifact = "") }

        val newStage = agent.currentStage
        if (newStage in listOf(ProfileStage.EXECUTION, ProfileStage.VALIDATION, ProfileStage.SYNTHESIS)) {
            autoStartStage(newStage)
        }
    }

    private fun autoStartStage(stage: ProfileStage) {
        val prompt = when (stage) {
            ProfileStage.EXECUTION -> "Приступи к выполнению задачи строго по утверждённому плану."
            ProfileStage.VALIDATION -> "Проведи валидацию: сравни результат с планом и вынеси вердикт."
            ProfileStage.SYNTHESIS -> "Систематизируй собранную информацию и сформируй структурированный итог."
            else -> return
        }
        _state.update { it.copy(isLoading = true, errorMessage = null, retryAction = null) }
        viewModelScope.launch {
            try {
                agent.sendMessage(prompt)
                syncFromAgent()
                _state.update { it.copy(isLoading = false) }
                _effects.send(TaskProfileContract.Effect.ScrollToBottom)
            } catch (e: Exception) {
                _state.update { it.copy(
                    isLoading = false,
                    errorMessage = apiService.friendlyError(e),
                    retryAction = TaskProfileContract.RetryAction.AutoStart(stage),
                ) }
            }
        }
    }

    // ── Retry ─────────────────────────────────────────────────────────────────

    private fun retry() {
        val action = _state.value.retryAction ?: return
        _state.update { it.copy(errorMessage = null, retryAction = null) }
        when (action) {
            is TaskProfileContract.RetryAction.SendMessage -> sendToAgent(action.text)
            is TaskProfileContract.RetryAction.Classify -> classifyAndShow(action.description)
            is TaskProfileContract.RetryAction.AutoStart -> autoStartStage(action.stage)
        }
    }

    // ── Demo: недопустимый переход ────────────────────────────────────────────

    private fun tryInvalidTransition() {
        val attempt = agent.tryJump()
        addSystemNotice(attempt.fromStage, attempt.toStage, attempt.reason)
        _state.update { it.copy(invalidTransition = attempt) }
        viewModelScope.launch {
            _effects.send(TaskProfileContract.Effect.ScrollToBottom)
        }
    }

    private fun addSystemNotice(from: ProfileStage, to: ProfileStage, reason: String) {
        val notice = TaskProfileContract.DisplayMessage(
            role = "system",
            content = "⛔ Недопустимый переход: ${from.label} → ${to.label}\nПричина: $reason",
            stage = from,
            isSystemNotice = true,
        )
        _state.update { it.copy(messages = it.messages + notice) }
    }

    // ── Синхронизация ─────────────────────────────────────────────────────────

    private fun syncFromAgent() {
        val snap = agent.currentSnapshot
        val profile = snap.profile
        val stages = profile?.stages ?: emptyList()
        val history = agent.currentHistory

        val existingNotices = _state.value.messages.filter { it.isSystemNotice }

        _state.update { current ->
            current.copy(
                profile = profile,
                stages = stages,
                currentStageIndex = snap.currentStageIndex,
                messages = history.map {
                    TaskProfileContract.DisplayMessage(
                        role = it.role,
                        content = it.content ?: "",
                        stage = agent.currentStage,
                    )
                } + existingNotices,
            )
        }
    }
}
