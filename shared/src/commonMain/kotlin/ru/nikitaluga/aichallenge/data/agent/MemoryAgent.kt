package ru.nikitaluga.aichallenge.data.agent

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import ru.nikitaluga.aichallenge.api.ChatMessage
import ru.nikitaluga.aichallenge.api.RouterAiApiService
import ru.nikitaluga.aichallenge.api.Usage
import ru.nikitaluga.aichallenge.data.storage.PlatformStorage
import ru.nikitaluga.aichallenge.domain.model.MemoryResult
import ru.nikitaluga.aichallenge.domain.model.PendingFact

/**
 * Агент с тремя явными слоями памяти.
 *
 * **Слой 1 — краткосрочная (диалог)**
 * Скользящее окно из последних [windowSize] сообщений. Сбрасывается при [startNewTask].
 *
 * **Слой 2 — рабочая (задача)**
 * Динамическая карта `key → value`. Сбрасывается при [startNewTask].
 *
 * **Слой 3 — долговременная (профиль)**
 * Динамическая карта `key → value`. Не сбрасывается при смене задачи.
 *
 * **Процесс:**
 * 1. [sendMessage] → LLM генерирует ответ + предлагает факты с маршрутом (task/profile)
 * 2. Факты возвращаются как [MemoryResult.pendingFacts] — ещё НЕ сохранены
 * 3. Пользователь подтверждает/отклоняет каждый факт через [applyFacts]
 */
class MemoryAgent(
    private val apiService: RouterAiApiService,
    private val model: String = DEFAULT_MODEL,
    private val systemPrompt: String? = null,
    val windowSize: Int = 10,
    private val storageKey: String = "memory_agent",
) {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    // ── Слой 1: краткосрочная память ──────────────────────────────────────────
    private val _history = mutableListOf<ChatMessage>()
    val shortTermHistory: List<ChatMessage> get() = _history.toList()

    // ── Слой 2: рабочая память (текущая задача) ───────────────────────────────
    private val _taskMemory = mutableMapOf<String, String>()
    val taskMemory: Map<String, String> get() = _taskMemory.toMap()

    // ── Слой 3: долговременная память (профиль) ───────────────────────────────
    private val _profileMemory = mutableMapOf<String, String>()
    val profileMemory: Map<String, String> get() = _profileMemory.toMap()

    var lastUsage: Usage? = null
        private set

    init {
        loadFromStorage()
    }

    // ── Публичный API ─────────────────────────────────────────────────────────

    /**
     * Отправить сообщение. Параллельно с основным запросом LLM предлагает факты
     * с маршрутом (task/profile). Факты НЕ сохраняются автоматически —
     * только после вызова [applyFacts].
     */
    suspend fun sendMessage(text: String): MemoryResult {
        _history.add(ChatMessage(role = "user", content = text))
        saveToStorage()

        return try {
            coroutineScope {
                val chatDeferred = async { sendChatRequest() }
                val factsDeferred = async { extractAndRouteFacts(text) }

                val chatResult = chatDeferred.await()
                val pendingFacts = factsDeferred.await()

                _history.add(ChatMessage(role = "assistant", content = chatResult.content))
                lastUsage = chatResult.usage
                saveToStorage()

                MemoryResult(
                    content = chatResult.content,
                    usage = chatResult.usage,
                    pendingFacts = pendingFacts,
                    taskMemory = _taskMemory.toMap(),
                    profileMemory = _profileMemory.toMap(),
                )
            }
        } catch (e: Exception) {
            _history.removeLast()
            saveToStorage()
            throw e
        }
    }

    /**
     * Сохранить подтверждённые пользователем факты в соответствующие слои памяти.
     * Вызывается из ViewModel после того, как пользователь нажал «Подтвердить».
     */
    fun applyFacts(facts: List<PendingFact>) {
        facts.forEach { fact ->
            when (fact.layer) {
                "profile" -> _profileMemory[fact.key] = fact.value
                "task" -> _taskMemory[fact.key] = fact.value
            }
        }
        saveToStorage()
    }

    /**
     * Начать новую задачу: очистить диалог и рабочую память.
     * Профиль пользователя **сохраняется**.
     */
    fun startNewTask() {
        _history.clear()
        _taskMemory.clear()
        PlatformStorage.remove("${storageKey}_short")
        PlatformStorage.remove("${storageKey}_task")
    }

    /** Очистить долговременную память (профиль). */
    fun clearProfile() {
        _profileMemory.clear()
        PlatformStorage.remove("${storageKey}_profile")
    }

    // ── Приватные методы ──────────────────────────────────────────────────────

    private suspend fun sendChatRequest(): ru.nikitaluga.aichallenge.api.MessageWithMetrics {
        val messages = buildList {
            add(ChatMessage(role = "system", content = buildSystemPrompt()))
            addAll(_history.takeLast(windowSize))
        }
        return apiService.sendMessages(messages = messages, model = model)
    }

    private fun buildSystemPrompt(): String {
        val base = systemPrompt ?: "Ты полезный ассистент с памятью о пользователе."
        val profileSection = if (_profileMemory.isNotEmpty()) buildString {
            appendLine("\n--- Профиль пользователя ---")
            _profileMemory.forEach { (k, v) -> appendLine("$k: $v") }
        } else ""
        val taskSection = if (_taskMemory.isNotEmpty()) buildString {
            appendLine("\n--- Текущая задача ---")
            _taskMemory.forEach { (k, v) -> appendLine("$k: $v") }
        } else ""
        return base + profileSection + taskSection
    }

    /**
     * LLM извлекает факты из последних сообщений диалога и маршрутизирует каждый в слой.
     * Возвращает пустой список при ошибке — провал не прерывает диалог.
     *
     * Ответ LLM:
     * ```json
     * [{"key":"имя","value":"Никита","layer":"profile"},
     *  {"key":"цель задачи","value":"трекер привычек","layer":"task"}]
     * ```
     */
    private suspend fun extractAndRouteFacts(userMessage: String): List<PendingFact> {
        val currentTask = if (_taskMemory.isEmpty()) "пусто"
        else _taskMemory.entries.joinToString("\n") { "  ${it.key}: ${it.value}" }
        val currentProfile = if (_profileMemory.isEmpty()) "пусто"
        else _profileMemory.entries.joinToString("\n") { "  ${it.key}: ${it.value}" }

        // Берём последние 6 сообщений как контекст — без последнего (оно уже в _history)
        // _history на момент вызова содержит user-сообщение, но ещё нет ответа ассистента
        val recentContext = _history.takeLast(6)
            .joinToString("\n") { "[${it.role}]: ${it.content}" }

        val prompt = """
Ты — менеджер памяти ассистента. Твоя задача: прочитать диалог и извлечь факты, которые стоит запомнить.

=== ТЕКУЩЕЕ СОСТОЯНИЕ ПАМЯТИ ===
Рабочая память (текущая задача):
$currentTask

Профиль пользователя:
$currentProfile

=== НЕДАВНИЙ ДИАЛОГ ===
$recentContext

=== ПРАВИЛА МАРШРУТИЗАЦИИ ===

layer "task" — всё, что относится к конкретной задаче/проекту в этом диалоге:
  • Что хотят построить/сделать (приложение, функция, сервис)
  • Платформа, стек для этой задачи (Android, iOS, React)
  • Требования и ограничения (бюджет, срок, аудитория)
  • Функции и фичи (авторизация, уведомления, оплата)
  • Текущий статус и этапы (что уже сделано, что следующее)
  ВАЖНО: если в памяти уже есть "цель задачи", то детали развивающие эту цель → тоже "task"

layer "profile" — постоянные данные о самом пользователе (не зависят от задачи):
  • Имя, ник, роль (разработчик, дизайнер, PM)
  • Привычный стек и инструменты вообще (Kotlin, Figma, PostgreSQL)
  • Долгосрочные профессиональные цели
  • Предпочтения к стилю ответов (кратко, с примерами, без воды)

=== ПРАВИЛА ИЗВЛЕЧЕНИЯ ===
- Анализируй весь показанный диалог, не только последнее сообщение
- Извлекай только факты с конкретной новой информацией
- Не дублируй то, что уже есть в памяти без изменений
- Если новых фактов нет — верни []
- Ключи (key) — короткие, 2-4 слова, на русском
- Значения (value) — конкретные, информативные

Верни ТОЛЬКО JSON-массив без пояснений, комментариев и markdown:
[{"key":"...", "value":"...", "layer":"profile|task"}]
""".trimIndent()

        return try {
            val result = apiService.sendMessages(
                messages = listOf(ChatMessage(role = "user", content = prompt)),
                model = model,
                temperature = 0.1,
            )
            val raw = result.content.trim()
            val start = raw.indexOf('[')
            val end = raw.lastIndexOf(']')
            if (start < 0 || end <= start) return emptyList()
            json.decodeFromString<List<RoutedFactDto>>(raw.substring(start, end + 1))
                .filter { it.layer == "profile" || it.layer == "task" }
                .map { PendingFact(key = it.key, value = it.value, layer = it.layer) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    // ── Персистентность ───────────────────────────────────────────────────────

    private fun saveToStorage() {
        runCatching {
            val stored = _history.map { StoredMsg(it.role, it.content) }
            PlatformStorage.save("${storageKey}_short", json.encodeToString<List<StoredMsg>>(stored))
        }
        runCatching {
            PlatformStorage.save("${storageKey}_task", json.encodeToString<Map<String, String>>(_taskMemory))
        }
        runCatching {
            PlatformStorage.save("${storageKey}_profile", json.encodeToString<Map<String, String>>(_profileMemory))
        }
    }

    private fun loadFromStorage() {
        runCatching {
            PlatformStorage.load("${storageKey}_short")?.let { encoded ->
                val stored = json.decodeFromString<List<StoredMsg>>(encoded)
                _history.addAll(stored.map { ChatMessage(role = it.role, content = it.content) })
            }
        }
        runCatching {
            PlatformStorage.load("${storageKey}_task")?.let { encoded ->
                _taskMemory.putAll(json.decodeFromString<Map<String, String>>(encoded))
            }
        }
        runCatching {
            PlatformStorage.load("${storageKey}_profile")?.let { encoded ->
                _profileMemory.putAll(json.decodeFromString<Map<String, String>>(encoded))
            }
        }
    }

    // ── Внутренние DTO ────────────────────────────────────────────────────────

    @Serializable
    private data class StoredMsg(val role: String, val content: String)

    @Serializable
    private data class RoutedFactDto(
        val key: String = "",
        val value: String = "",
        val layer: String = "",
    )

    companion object {
        private const val DEFAULT_MODEL = "deepseek/deepseek-v3.2"
    }
}
