package ru.nikitaluga.aichallenge.data.agent

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import ru.nikitaluga.aichallenge.api.ChatMessage
import ru.nikitaluga.aichallenge.api.RouterAiApiService
import ru.nikitaluga.aichallenge.data.storage.PlatformStorage
import ru.nikitaluga.aichallenge.domain.model.TaskStage
import ru.nikitaluga.aichallenge.domain.model.TaskStateSnapshot

/**
 * День 13 — Task State Machine Agent.
 *
 * Детерминированный конечный автомат задачи:
 *   planning → execution → validation → done
 *
 * Каждая стадия имеет:
 *  - Свою историю диалога (чистый контекст)
 *  - Свой system prompt с инжекцией артефактов предыдущих стадий
 *  - Явный переход через [advance] (никогда не автоматический)
 *
 * Пауза/возобновление: всё состояние персистируется через PlatformStorage.
 */
class TaskStateMachineAgent(
    private val apiService: RouterAiApiService,
    private val model: String = DEFAULT_MODEL,
    private val storageKey: String = "day13_task_state_machine",
) {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    private var _snapshot: TaskStateSnapshot = TaskStateSnapshot()
    val currentSnapshot: TaskStateSnapshot get() = _snapshot

    // Отдельная история для каждой стадии
    private val histories: Map<TaskStage, MutableList<ChatMessage>> = TaskStage.entries
        .associateWith { mutableListOf() }

    val currentHistory: List<ChatMessage>
        get() = histories[_snapshot.stage]?.toList() ?: emptyList()

    init {
        loadFromStorage()
    }

    // ── Публичный API ─────────────────────────────────────────────────────────

    /**
     * Начать новую задачу. Сохраняет описание и отправляет первое сообщение в Planning.
     */
    suspend fun startTask(description: String): String {
        _snapshot = TaskStateSnapshot(
            stage = TaskStage.PLANNING,
            taskDescription = description,
        )
        histories.values.forEach { it.clear() }
        saveToStorage()
        return sendMessageInternal(description)
    }

    /**
     * Продолжить диалог в текущей стадии.
     */
    suspend fun sendMessage(text: String): String {
        return sendMessageInternal(text)
    }

    /**
     * Детерминированный переход к следующей стадии.
     * Вызывается только после явного Approve от пользователя.
     * @return новая стадия
     */
    fun advance(artifact: String): TaskStage {
        _snapshot = when (_snapshot.stage) {
            TaskStage.PLANNING -> _snapshot.copy(
                stage = TaskStage.EXECUTION,
                planArtifact = artifact,
            )
            TaskStage.EXECUTION -> _snapshot.copy(
                stage = TaskStage.VALIDATION,
                executionArtifact = artifact,
            )
            TaskStage.VALIDATION -> _snapshot.copy(
                stage = TaskStage.DONE,
                validationArtifact = artifact,
            )
            TaskStage.DONE -> _snapshot.copy(
                stage = TaskStage.PLANNING,
                taskDescription = "",
                planArtifact = "",
                executionArtifact = "",
                validationArtifact = "",
            )
        }
        saveToStorage()
        return _snapshot.stage
    }

    /**
     * Полный сброс автомата. Аналогично переходу DONE → PLANNING.
     */
    fun reset() {
        _snapshot = TaskStateSnapshot()
        histories.values.forEach { it.clear() }
        TaskStage.entries.forEach { PlatformStorage.remove("${storageKey}_history_${it.name}") }
        PlatformStorage.remove("${storageKey}_snapshot")
    }

    // ── Приватные методы ──────────────────────────────────────────────────────

    private suspend fun sendMessageInternal(text: String): String {
        val history = histories[_snapshot.stage] ?: return ""
        history.add(ChatMessage(role = "user", content = text))
        saveHistoryForStage(_snapshot.stage)

        return try {
            val messages = buildList {
                add(ChatMessage(role = "system", content = buildSystemPrompt()))
                addAll(history)
            }
            val result = apiService.sendMessages(messages = messages, model = model)
            history.add(ChatMessage(role = "assistant", content = result.content))
            saveHistoryForStage(_snapshot.stage)
            result.content
        } catch (e: Exception) {
            history.removeLast()
            saveHistoryForStage(_snapshot.stage)
            throw e
        }
    }

    private fun buildSystemPrompt(): String = when (_snapshot.stage) {
        TaskStage.PLANNING -> """
            Ты планировщик задачи. Пользователь опишет задачу — твоя цель: задать уточняющие вопросы,
            детализировать требования и сформулировать чёткий план выполнения.
            Когда план готов — предложи его в виде структурированного текста с пронумерованными шагами.
            Не выполняй задачу — только планируй.
        """.trimIndent()

        TaskStage.EXECUTION -> """
            Ты исполнитель задачи. Работай строго по плану:

            === ПЛАН ===
            ${_snapshot.planArtifact}
            ============

            Выполняй шаги последовательно. Отмечай прогресс (например: "Шаг 1: выполнен").
            Если нужны уточнения — спроси. Когда задача выполнена — предоставь итоговый результат.
        """.trimIndent()

        TaskStage.VALIDATION -> """
            Ты валидатор задачи. Проверь, соответствует ли результат исходному плану.

            === ПЛАН ===
            ${_snapshot.planArtifact}
            ============

            === РЕЗУЛЬТАТ ВЫПОЛНЕНИЯ ===
            ${_snapshot.executionArtifact}
            ============================

            Составь структурированный отчёт:
            1. Что выполнено корректно (по пунктам плана)
            2. Что отклоняется от плана или не выполнено
            3. Итоговый вердикт: ПРОЙДЕНО / ТРЕБУЕТ ДОРАБОТКИ
            Будь объективным и конкретным.
        """.trimIndent()

        TaskStage.DONE -> "Задача завершена. Поздравляю!"
    }

    // ── Персистентность ───────────────────────────────────────────────────────

    private fun saveToStorage() {
        runCatching {
            PlatformStorage.save("${storageKey}_snapshot", json.encodeToString<TaskStateSnapshot>(_snapshot))
        }
        TaskStage.entries.forEach { saveHistoryForStage(it) }
    }

    private fun saveHistoryForStage(stage: TaskStage) {
        runCatching {
            val msgs = histories[stage]?.map { StoredMsg(it.role, it.content) } ?: return@runCatching
            PlatformStorage.save("${storageKey}_history_${stage.name}", json.encodeToString<List<StoredMsg>>(msgs))
        }
    }

    private fun loadFromStorage() {
        runCatching {
            PlatformStorage.load("${storageKey}_snapshot")?.let {
                _snapshot = json.decodeFromString<TaskStateSnapshot>(it)
            }
        }
        TaskStage.entries.forEach { stage ->
            runCatching {
                PlatformStorage.load("${storageKey}_history_${stage.name}")?.let { encoded ->
                    val stored = json.decodeFromString<List<StoredMsg>>(encoded)
                    histories[stage]?.addAll(stored.map { ChatMessage(role = it.role, content = it.content) })
                }
            }
        }
    }

    @Serializable
    private data class StoredMsg(val role: String, val content: String)

    companion object {
        private const val DEFAULT_MODEL = "deepseek/deepseek-v3.2"
    }
}
