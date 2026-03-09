package ru.nikitaluga.aichallenge.data.agent

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import ru.nikitaluga.aichallenge.api.ChatMessage
import ru.nikitaluga.aichallenge.api.RouterAiApiService
import ru.nikitaluga.aichallenge.data.storage.PlatformStorage
import ru.nikitaluga.aichallenge.domain.model.ClassificationResult
import ru.nikitaluga.aichallenge.domain.model.InvalidTransitionAttempt
import ru.nikitaluga.aichallenge.domain.model.ProfileStage
import ru.nikitaluga.aichallenge.domain.model.ProfiledSnapshot
import ru.nikitaluga.aichallenge.domain.model.TaskProfile

/**
 * День 15 — Profiled Task Agent.
 *
 * Агент с контролируемым жизненным циклом задачи:
 *  - Авто-классификация задачи в один из профилей (DEV_TASK / RESEARCH_TASK / SIMPLE_TASK)
 *  - Каждый профиль имеет свои стадии с запрещёнными переходами
 *  - [tryAdvance] блокирует попытки перепрыгнуть через стадию
 *  - Каждая стадия — отдельная история диалога + свой system prompt
 */
class ProfiledTaskAgent(
    private val apiService: RouterAiApiService,
    private val model: String = DEFAULT_MODEL,
    private val storageKey: String = "day15_profiled_task",
) {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    private var _snapshot: ProfiledSnapshot = ProfiledSnapshot()
    val currentSnapshot: ProfiledSnapshot get() = _snapshot

    private val histories: MutableMap<ProfileStage, MutableList<ChatMessage>> =
        ProfileStage.entries.associateWith { mutableListOf<ChatMessage>() }.toMutableMap()

    val currentStage: ProfileStage
        get() {
            val snap = _snapshot
            val profile = snap.profile ?: return ProfileStage.DONE
            return profile.stages.getOrElse(snap.currentStageIndex) { ProfileStage.DONE }
        }

    val currentHistory: List<ChatMessage>
        get() = histories[currentStage]?.toList() ?: emptyList()

    val allowedNextStage: ProfileStage?
        get() {
            val snap = _snapshot
            val profile = snap.profile ?: return null
            val nextIndex = snap.currentStageIndex + 1
            return profile.stages.getOrNull(nextIndex)
        }

    init {
        loadFromStorage()
    }

    // ── Публичный API ─────────────────────────────────────────────────────────

    /**
     * Шаг 1: LLM классифицирует задачу в один из профилей.
     * Сохраняет описание задачи в snapshot, но не подтверждает профиль.
     */
    suspend fun classifyTask(description: String): ClassificationResult {
        _snapshot = _snapshot.copy(taskDescription = description)
        saveToStorage()

        val prompt = """
            Classify the following task into exactly one profile. Reply ONLY with valid JSON, no extra text.

            Profiles:
            - DEV_TASK: software development, coding, programming, architecture, debugging, implementation
            - RESEARCH_TASK: information gathering, research, comparison, analysis, study
            - SIMPLE_TASK: simple question, quick factual answer, short reply needed

            Task: "$description"

            JSON format: {"profile":"DEV_TASK","reason":"brief reason in Russian"}
        """.trimIndent()

        val response = apiService.sendMessages(
            messages = listOf(ChatMessage(role = "user", content = prompt)),
            model = model,
            maxTokens = 200,
            temperature = 0.2,
        )

        val raw = response.content.trim()
            .removePrefix("```json").removePrefix("```").removeSuffix("```").trim()

        return runCatching { json.decodeFromString<ClassificationResult>(raw) }.getOrElse {
            // Fallback: если JSON не распарсился — SimpleTask
            ClassificationResult(
                profile = TaskProfile.SIMPLE_TASK,
                reason = "Не удалось определить тип задачи, используется SimpleTask",
            )
        }
    }

    /**
     * Шаг 2: Пользователь подтверждает (или меняет) профиль.
     * Переводит автомат в первую стадию выбранного профиля.
     */
    fun confirmProfile(profile: TaskProfile) {
        _snapshot = _snapshot.copy(
            profile = profile,
            classificationConfirmed = true,
            currentStageIndex = 0,
        )
        saveToStorage()
    }

    /**
     * Шаг 3: Отправить сообщение в текущей стадии.
     */
    suspend fun sendMessage(text: String): String {
        val stage = currentStage
        val history = histories[stage] ?: return ""
        history.add(ChatMessage(role = "user", content = text))
        saveHistoryForStage(stage)

        return try {
            val messages = buildList {
                add(ChatMessage(role = "system", content = buildSystemPrompt()))
                addAll(history)
            }
            val result = apiService.sendMessages(messages = messages, model = model)
            history.add(ChatMessage(role = "assistant", content = result.content))
            saveHistoryForStage(stage)
            result.content
        } catch (e: Exception) {
            history.removeLast()
            saveHistoryForStage(stage)
            throw e
        }
    }

    /**
     * Шаг 4: Попытка перехода к следующей стадии.
     *
     * @return null если переход выполнен успешно, [InvalidTransitionAttempt] если переход запрещён.
     *
     * Переход запрещён если:
     *  - Профиль не подтверждён
     *  - Уже на финальной стадии DONE
     *  - (В нормальном флоу не используется для "прыжков" — они только через [tryJump])
     */
    fun tryAdvance(artifact: String): InvalidTransitionAttempt? {
        val snap = _snapshot

        if (!snap.classificationConfirmed || snap.profile == null) {
            return InvalidTransitionAttempt(
                fromStage = currentStage,
                toStage = ProfileStage.DONE,
                reason = "Профиль задачи не подтверждён — сначала подтвердите профиль",
            )
        }

        val stages = snap.profile.stages
        val nextIndex = snap.currentStageIndex + 1

        if (nextIndex >= stages.size) {
            return InvalidTransitionAttempt(
                fromStage = currentStage,
                toStage = ProfileStage.DONE,
                reason = "Задача уже завершена",
            )
        }

        // Успешный переход: сохраняем артефакт, переходим к следующей стадии
        val updatedArtifacts = snap.artifacts + (currentStage.name to artifact)
        _snapshot = snap.copy(
            currentStageIndex = nextIndex,
            artifacts = updatedArtifacts,
        )
        saveToStorage()
        return null
    }

    /**
     * Demo: попытка перепрыгнуть через одну стадию вперёд.
     * Всегда возвращает [InvalidTransitionAttempt] — используется для демонстрации блокировки.
     */
    fun tryJump(): InvalidTransitionAttempt {
        val snap = _snapshot
        val profile = snap.profile

        if (profile == null || !snap.classificationConfirmed) {
            return InvalidTransitionAttempt(
                fromStage = currentStage,
                toStage = ProfileStage.DONE,
                reason = "Профиль не подтверждён",
            )
        }

        val stages = profile.stages
        val jumpIndex = snap.currentStageIndex + 2  // прыгаем через одну
        val targetStage = stages.getOrNull(jumpIndex) ?: ProfileStage.DONE
        val nextStage = stages.getOrNull(snap.currentStageIndex + 1) ?: ProfileStage.DONE

        return InvalidTransitionAttempt(
            fromStage = currentStage,
            toStage = targetStage,
            reason = "Нельзя перейти к «${targetStage.label}» — " +
                "сначала нужно завершить «${nextStage.label}» и получить утверждение",
        )
    }

    /**
     * Полный сброс.
     */
    fun reset() {
        _snapshot = ProfiledSnapshot()
        histories.values.forEach { it.clear() }
        ProfileStage.entries.forEach {
            PlatformStorage.remove("${storageKey}_history_${it.name}")
        }
        PlatformStorage.remove("${storageKey}_snapshot")
    }

    // ── System prompts ────────────────────────────────────────────────────────

    private fun buildSystemPrompt(): String {
        val snap = _snapshot
        return when (currentStage) {
            ProfileStage.PLANNING -> """
                Ты планировщик задачи разработки. Пользователь описал задачу — твоя цель:
                задать уточняющие вопросы, детализировать требования и сформулировать чёткий
                план выполнения с пронумерованными шагами.
                Когда план готов — предложи его в виде структурированного текста.
                Не выполняй задачу — только планируй.
            """.trimIndent()

            ProfileStage.EXECUTION -> """
                Ты исполнитель задачи разработки. Работай строго по утверждённому плану:

                === ПЛАН ===
                ${snap.artifacts[ProfileStage.PLANNING.name] ?: "(план не найден)"}
                ============

                Выполняй шаги последовательно. Отмечай прогресс (напр: «Шаг 1: выполнен»).
                Если нужны уточнения — спроси. Когда задача выполнена — предоставь итоговый результат.
            """.trimIndent()

            ProfileStage.VALIDATION -> """
                Ты валидатор задачи разработки. Проверь соответствие результата плану.

                === ПЛАН ===
                ${snap.artifacts[ProfileStage.PLANNING.name] ?: "(план не найден)"}
                ============

                === РЕЗУЛЬТАТ ВЫПОЛНЕНИЯ ===
                ${snap.artifacts[ProfileStage.EXECUTION.name] ?: "(результат не найден)"}
                ============================

                Составь структурированный отчёт:
                1. Что выполнено корректно
                2. Что отклоняется от плана
                3. Итоговый вердикт: ПРОЙДЕНО / ТРЕБУЕТ ДОРАБОТКИ
            """.trimIndent()

            ProfileStage.RESEARCH -> """
                Ты аналитик-исследователь. Пользователь хочет собрать информацию по теме.
                Задавай уточняющие вопросы, чтобы лучше понять запрос, и собирай факты.
                Когда информации достаточно — предложи собранные данные в структурированном виде.
                Не делай финальных выводов — только собирай информацию.
            """.trimIndent()

            ProfileStage.SYNTHESIS -> """
                Ты синтетизатор информации. На основе собранных данных сформируй итоговый анализ.

                === СОБРАННАЯ ИНФОРМАЦИЯ ===
                ${snap.artifacts[ProfileStage.RESEARCH.name] ?: "(информация не найдена)"}
                ===========================

                Систематизируй факты, выдели ключевые выводы и предоставь чёткий структурированный итог.
            """.trimIndent()

            ProfileStage.DONE -> "Задача завершена. Поздравляю с успешным выполнением!"
        }
    }

    // ── Персистентность ───────────────────────────────────────────────────────

    private fun saveToStorage() {
        runCatching {
            PlatformStorage.save(
                "${storageKey}_snapshot",
                json.encodeToString<ProfiledSnapshot>(_snapshot),
            )
        }
        ProfileStage.entries.forEach { saveHistoryForStage(it) }
    }

    private fun saveHistoryForStage(stage: ProfileStage) {
        runCatching {
            val msgs = histories[stage]?.map { StoredMsg(it.role, it.content) } ?: return@runCatching
            PlatformStorage.save(
                "${storageKey}_history_${stage.name}",
                json.encodeToString<List<StoredMsg>>(msgs),
            )
        }
    }

    private fun loadFromStorage() {
        runCatching {
            PlatformStorage.load("${storageKey}_snapshot")?.let {
                _snapshot = json.decodeFromString<ProfiledSnapshot>(it)
            }
        }
        ProfileStage.entries.forEach { stage ->
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