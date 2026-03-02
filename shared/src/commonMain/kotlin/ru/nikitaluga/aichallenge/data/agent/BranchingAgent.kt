package ru.nikitaluga.aichallenge.data.agent

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import ru.nikitaluga.aichallenge.api.ChatMessage
import ru.nikitaluga.aichallenge.api.RouterAiApiService
import ru.nikitaluga.aichallenge.api.Usage
import ru.nikitaluga.aichallenge.data.storage.PlatformStorage

data class BranchingResult(
    val content: String,
    val usage: Usage?,
    val topicShiftDetected: Boolean,
    val suggestedBranchName: String? = null,
    val suggestedCurrentBranchName: String? = null,
)

data class BranchInfo(
    val id: String,
    val name: String,
    val messageCount: Int,
)

/**
 * Агент со стратегией «Branching» (ветки диалога).
 *
 * Архитектура:
 * - [rootMessages] — общая история до checkpoint (shared по всем веткам)
 * - [branches] — карта id → branch-specific сообщения (только то, что написано после checkpoint)
 * - При первом создании ветки все текущие сообщения main переходят в rootMessages;
 *   далее каждая ветка разворачивается независимо от общего checkpoint.
 *
 * LLM детектирует смену темы параллельно с основным запросом.
 * При обнаружении смены темы ViewModel показывает баннер с предложением создать ветку.
 */
class BranchingAgent(
    private val apiService: RouterAiApiService,
    private val model: String = DEFAULT_MODEL,
    private val systemPrompt: String? = null,
    private val storageKey: String = "branching_agent",
) {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    private val rootMessages = mutableListOf<ChatMessage>()
    private val branches = mutableMapOf<String, MutableList<ChatMessage>>()
    private val branchNames = mutableMapOf<String, String>()
    private var _currentBranchId = MAIN_BRANCH_ID
    private var _checkpointSet = false
    private var branchCounter = 0

    var lastUsage: Usage? = null
        private set

    val currentBranchId: String get() = _currentBranchId
    val checkpointSet: Boolean get() = _checkpointSet

    /** Сообщения текущей ветки. Используется для отображения и детекции смены темы. */
    val branchHistory: List<ChatMessage>
        get() = branches[_currentBranchId] ?: emptyList()

    /** Все ветки в порядке создания. */
    val branchList: List<BranchInfo>
        get() = branches.entries.map { (id, msgs) ->
            BranchInfo(id = id, name = branchNames[id] ?: id, messageCount = msgs.size)
        }

    init {
        branches[MAIN_BRANCH_ID] = mutableListOf()
        branchNames[MAIN_BRANCH_ID] = "Главная"
        loadFromStorage()
    }

    suspend fun sendMessage(text: String): BranchingResult {
        val currentBranch = branches.getOrPut(_currentBranchId) { mutableListOf() }
        currentBranch.add(ChatMessage(role = "user", content = text))

        return try {
            coroutineScope {
                val chatDeferred = async {
                    apiService.sendMessages(
                        messages = buildContextMessages(),
                        model = model,
                    )
                }
                val topicDeferred = async { detectTopicShift(text) }

                val chatResult = chatDeferred.await()
                val detection = topicDeferred.await()

                currentBranch.add(ChatMessage(role = "assistant", content = chatResult.content))
                lastUsage = chatResult.usage
                saveToStorage()

                BranchingResult(
                    content = chatResult.content,
                    usage = chatResult.usage,
                    topicShiftDetected = detection.detected,
                    suggestedBranchName = detection.newTopicName,
                    suggestedCurrentBranchName = detection.currentTopicName,
                )
            }
        } catch (e: Exception) {
            currentBranch.removeLast()
            throw e
        }
    }

    /**
     * Создаёт новую ветку.
     *
     * При первом вызове все сообщения текущей ветки переносятся в [rootMessages]
     * (становятся общим checkpoint), текущая ветка начинается заново.
     * Новая ветка тоже начинается пустой от того же checkpoint.
     * Активной становится новая ветка.
     *
     * @return id новой ветки.
     */
    fun createBranch(name: String? = null, currentBranchName: String? = null): String {
        currentBranchName?.let { branchNames[_currentBranchId] = it }
        if (!_checkpointSet) {
            val currentMsgs = branches[_currentBranchId] ?: mutableListOf()
            rootMessages.addAll(currentMsgs)
            branches[_currentBranchId]?.clear()
            _checkpointSet = true
        }
        branchCounter++
        val newId = "branch_$branchCounter"
        val branchName = name ?: "Ветка $branchCounter"
        branches[newId] = mutableListOf()
        branchNames[newId] = branchName
        _currentBranchId = newId
        saveToStorage()
        return newId
    }

    fun switchBranch(id: String) {
        if (branches.containsKey(id)) {
            _currentBranchId = id
        }
    }

    fun clearHistory() {
        rootMessages.clear()
        branches.clear()
        branchNames.clear()
        _currentBranchId = MAIN_BRANCH_ID
        _checkpointSet = false
        branchCounter = 0
        lastUsage = null
        branches[MAIN_BRANCH_ID] = mutableListOf()
        branchNames[MAIN_BRANCH_ID] = "Главная"
        PlatformStorage.remove(storageKey)
    }

    private fun buildContextMessages(): List<ChatMessage> = buildList {
        systemPrompt?.let { add(ChatMessage(role = "system", content = it)) }
        addAll(branches[_currentBranchId] ?: emptyList())
    }

    private data class TopicShiftDetection(
        val detected: Boolean,
        val newTopicName: String?,
        val currentTopicName: String?,
    )

    /**
     * Лёгкий LLM-вызов: детектирует смену темы по последним сообщениям.
     * При смене темы возвращает названия текущей и новой темы (2-4 слова каждое).
     * При любой ошибке возвращает detected=false.
     */
    private suspend fun detectTopicShift(newMessage: String): TopicShiftDetection {
        val recent = branchHistory.takeLast(4)
        if (recent.isEmpty()) return TopicShiftDetection(false, null, null)

        val context = recent.joinToString("\n") { msg ->
            val label = if (msg.role == "user") "Пользователь" else "Ассистент"
            "$label: ${msg.content.take(150)}"
        }
        val prompt = "Последние сообщения диалога:\n$context\n\n" +
            "Новое сообщение пользователя: \"${newMessage.take(200)}\"\n\n" +
            "Если сообщение начинает принципиально новую тему — ответь строго в формате:\n" +
            "YES\nCURRENT: <текущая тема, 2-4 слова>\nNEW: <новая тема, 2-4 слова>\n\n" +
            "Если тема продолжается — ответь только: NO"

        return try {
            val result = apiService.sendMessages(
                messages = listOf(ChatMessage(role = "user", content = prompt)),
                model = model,
                maxTokens = 30,
                temperature = 0.0,
            )
            val text = result.content.trim()
            if (text.uppercase().startsWith("YES")) {
                val lines = text.lines()
                val currentName = lines.firstOrNull { it.uppercase().startsWith("CURRENT:") }
                    ?.substringAfter(":")?.substringAfter(":")?.trim()?.takeIf { it.isNotBlank() }
                val newName = lines.firstOrNull { it.uppercase().startsWith("NEW:") }
                    ?.substringAfter(":")?.substringAfter(":")?.trim()?.takeIf { it.isNotBlank() }
                TopicShiftDetection(detected = true, newTopicName = newName, currentTopicName = currentName)
            } else {
                TopicShiftDetection(detected = false, newTopicName = null, currentTopicName = null)
            }
        } catch (_: Exception) {
            TopicShiftDetection(detected = false, newTopicName = null, currentTopicName = null)
        }
    }

    private fun saveToStorage() {
        runCatching {
            val state = StoredBranchingState(
                rootMessages = rootMessages.map { StoredMsg(it.role, it.content) },
                branches = branches.mapValues { (_, msgs) -> msgs.map { StoredMsg(it.role, it.content) } },
                branchNames = branchNames.toMap(),
                currentBranchId = _currentBranchId,
                checkpointSet = _checkpointSet,
                branchCounter = branchCounter,
            )
            PlatformStorage.save(storageKey, json.encodeToString<StoredBranchingState>(state))
        }
    }

    private fun loadFromStorage() {
        runCatching {
            val encoded = PlatformStorage.load(storageKey) ?: return
            val state = json.decodeFromString<StoredBranchingState>(encoded)
            rootMessages.clear()
            rootMessages.addAll(state.rootMessages.map { ChatMessage(role = it.role, content = it.content) })
            branches.clear()
            state.branches.forEach { (id, msgs) ->
                branches[id] = msgs.map { ChatMessage(role = it.role, content = it.content) }.toMutableList()
            }
            branchNames.clear()
            branchNames.putAll(state.branchNames)
            _currentBranchId = state.currentBranchId
            _checkpointSet = state.checkpointSet
            branchCounter = state.branchCounter
        }
    }

    @Serializable
    private data class StoredMsg(val role: String, val content: String)

    @Serializable
    private data class StoredBranchingState(
        val rootMessages: List<StoredMsg>,
        val branches: Map<String, List<StoredMsg>>,
        val branchNames: Map<String, String>,
        val currentBranchId: String,
        val checkpointSet: Boolean,
        val branchCounter: Int = 0,
    )

    companion object {
        private const val DEFAULT_MODEL = "deepseek/deepseek-v3.2"
        private const val MAIN_BRANCH_ID = "main"
    }
}
