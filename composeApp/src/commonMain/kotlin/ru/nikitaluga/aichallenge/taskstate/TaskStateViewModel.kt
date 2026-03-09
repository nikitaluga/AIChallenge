package ru.nikitaluga.aichallenge.taskstate

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
import ru.nikitaluga.aichallenge.data.agent.TaskStateMachineAgent
import ru.nikitaluga.aichallenge.domain.model.TaskStage

class TaskStateViewModel : ViewModel() {

    private val apiService = RouterAiApiService()
    private val agent = TaskStateMachineAgent(apiService = apiService)

    private val _state = MutableStateFlow(TaskStateContract.State())
    val state: StateFlow<TaskStateContract.State> = _state.asStateFlow()

    private val _effects = Channel<TaskStateContract.Effect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    init {
        syncFromAgent()
    }

    fun onEvent(event: TaskStateContract.Event) {
        when (event) {
            is TaskStateContract.Event.InputChanged ->
                _state.update { it.copy(inputText = event.text) }

            TaskStateContract.Event.SendMessage -> sendMessage()

            is TaskStateContract.Event.ArtifactChanged ->
                _state.update { it.copy(pendingArtifact = event.text) }

            TaskStateContract.Event.ShowArtifactPanel -> {
                val lastAssistantMsg = agent.currentHistory
                    .lastOrNull { it.role == "assistant" }?.content ?: ""
                _state.update { it.copy(showArtifactPanel = true, pendingArtifact = lastAssistantMsg) }
            }

            TaskStateContract.Event.ApproveArtifact -> approveArtifact()

            TaskStateContract.Event.DismissArtifactPanel ->
                _state.update { it.copy(showArtifactPanel = false, pendingArtifact = "") }

            TaskStateContract.Event.DismissError ->
                _state.update { it.copy(errorMessage = null) }

            TaskStateContract.Event.NewTask -> {
                agent.reset()
                _state.update { TaskStateContract.State() }
            }
        }
    }

    // ── Чат ──────────────────────────────────────────────────────────────────

    private fun sendMessage() {
        val text = _state.value.inputText.trim()
        if (text.isEmpty() || _state.value.isLoading) return
        _state.update { it.copy(inputText = "", isLoading = true) }

        viewModelScope.launch {
            try {
                val snapshot = agent.currentSnapshot
                val response = if (snapshot.taskDescription.isEmpty() && snapshot.stage == TaskStage.PLANNING) {
                    agent.startTask(text)
                } else {
                    agent.sendMessage(text)
                }
                syncFromAgent()
                _state.update { it.copy(isLoading = false) }
                _effects.send(TaskStateContract.Effect.ScrollToBottom)
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, errorMessage = apiService.friendlyError(e)) }
            }
        }
    }

    // ── Переход стадии ────────────────────────────────────────────────────────

    private fun approveArtifact() {
        val artifact = _state.value.pendingArtifact
        if (_state.value.stage == TaskStage.DONE) {
            agent.reset()
            _state.update { TaskStateContract.State() }
            return
        }

        val newStage = agent.advance(artifact)
        syncFromAgent()
        _state.update { it.copy(showArtifactPanel = false, pendingArtifact = "") }

        // Если переходим в Execution или Validation — сразу запускаем автоматический старт стадии
        if (newStage == TaskStage.EXECUTION || newStage == TaskStage.VALIDATION) {
            autoStartStage(newStage)
        }
    }

    /**
     * При входе в Execution и Validation агент сразу начинает работу без ввода пользователя.
     */
    private fun autoStartStage(stage: TaskStage) {
        val prompt = when (stage) {
            TaskStage.EXECUTION -> "Приступи к выполнению задачи по плану."
            TaskStage.VALIDATION -> "Проведи валидацию результата."
            else -> return
        }
        _state.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            try {
                agent.sendMessage(prompt)
                syncFromAgent()
                _state.update { it.copy(isLoading = false) }
                _effects.send(TaskStateContract.Effect.ScrollToBottom)
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, errorMessage = apiService.friendlyError(e)) }
            }
        }
    }

    // ── Синхронизация состояния ───────────────────────────────────────────────

    private fun syncFromAgent() {
        val snapshot = agent.currentSnapshot
        val history = agent.currentHistory
        _state.update { current ->
            current.copy(
                stage = snapshot.stage,
                taskDescription = snapshot.taskDescription,
                planArtifact = snapshot.planArtifact,
                executionArtifact = snapshot.executionArtifact,
                validationArtifact = snapshot.validationArtifact,
                messages = history.map {
                    TaskStateContract.DisplayMessage(
                        role = it.role,
                        content = it.content,
                        stage = snapshot.stage,
                    )
                },
            )
        }
    }
}
