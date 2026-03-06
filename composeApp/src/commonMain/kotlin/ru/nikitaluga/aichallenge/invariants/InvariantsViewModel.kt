package ru.nikitaluga.aichallenge.invariants

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import ru.nikitaluga.aichallenge.api.RouterAiApiService
import ru.nikitaluga.aichallenge.data.agent.InvariantsAgent
import ru.nikitaluga.aichallenge.data.storage.PlatformStorage
import ru.nikitaluga.aichallenge.domain.model.Invariant
import ru.nikitaluga.aichallenge.domain.model.InvariantChatMessage

private const val STORAGE_INVARIANTS_KEY = "day14_invariants"
private const val BASE_SYSTEM_PROMPT = "Ты полезный технический ассистент."

val DEFAULT_INVARIANTS = listOf(
    Invariant(
        id = "stack_kotlin",
        name = "StackOnly",
        rule = "Используй только Kotlin и Ktor. Никаких Java, Spring Boot, Python или других языков и фреймворков.",
    ),
    Invariant(
        id = "arch_mvi",
        name = "Architecture MVI",
        rule = "Архитектура — строго MVI + Clean Architecture. Не предлагай MVVM, MVP, Redux или другие паттерны.",
    ),
    Invariant(
        id = "no_java",
        name = "NoJava",
        rule = "Категорически запрещено предлагать Java-код, Java-библиотеки или Java-специфичные решения.",
    ),
)

class InvariantsViewModel : ViewModel() {

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
    private val apiService = RouterAiApiService()
    private val agent = InvariantsAgent(
        apiService = apiService,
        baseSystemPrompt = BASE_SYSTEM_PROMPT,
    )

    private val _state = MutableStateFlow(InvariantsContract.State())
    val state: StateFlow<InvariantsContract.State> = _state.asStateFlow()

    private val _effects = Channel<InvariantsContract.Effect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    init {
        loadInvariantsFromStorage()
    }

    fun onEvent(event: InvariantsContract.Event) {
        when (event) {
            InvariantsContract.Event.AddInvariant ->
                _state.update { it.copy(showDialog = true, editingInvariant = null) }

            is InvariantsContract.Event.EditInvariant -> {
                val inv = _state.value.invariants.firstOrNull { it.id == event.id }
                _state.update { it.copy(showDialog = true, editingInvariant = inv) }
            }

            is InvariantsContract.Event.DeleteInvariant -> deleteInvariant(event.id)

            is InvariantsContract.Event.ToggleInvariant -> toggleInvariant(event.id)

            is InvariantsContract.Event.SaveInvariant -> saveInvariant(event.invariant)

            InvariantsContract.Event.DismissDialog ->
                _state.update { it.copy(showDialog = false, editingInvariant = null) }

            is InvariantsContract.Event.InputChanged ->
                _state.update { it.copy(inputText = event.text) }

            InvariantsContract.Event.SendMessage -> sendMessage()

            InvariantsContract.Event.ClearHistory -> {
                agent.clearHistory()
                _state.update { it.copy(messages = emptyList()) }
            }

            InvariantsContract.Event.DismissError ->
                _state.update { it.copy(errorMessage = null) }
        }
    }

    // ── Управление инвариантами ───────────────────────────────────────────────

    private fun toggleInvariant(id: String) {
        val updated = _state.value.invariants.map {
            if (it.id == id) it.copy(enabled = !it.enabled) else it
        }
        persistInvariants(updated)
        _state.update { it.copy(invariants = updated) }
    }

    private fun deleteInvariant(id: String) {
        val updated = _state.value.invariants.filter { it.id != id }
        persistInvariants(updated)
        _state.update { it.copy(invariants = updated) }
    }

    private fun saveInvariant(invariant: Invariant) {
        val current = _state.value.invariants.toMutableList()
        val idx = current.indexOfFirst { it.id == invariant.id }
        if (idx >= 0) current[idx] = invariant else current.add(invariant)
        persistInvariants(current)
        _state.update { it.copy(invariants = current, showDialog = false, editingInvariant = null) }
    }

    // ── Чат ──────────────────────────────────────────────────────────────────

    private fun sendMessage() {
        val text = _state.value.inputText.trim()
        if (text.isEmpty() || _state.value.isLoading) return
        val invariants = _state.value.invariants

        _state.update { current ->
            current.copy(
                inputText = "",
                isLoading = true,
                messages = current.messages + InvariantChatMessage(role = "user", content = text),
            )
        }

        viewModelScope.launch {
            try {
                val result = agent.sendMessage(text, invariants)
                val assistantMsg = InvariantChatMessage(
                    role = "assistant",
                    content = result.content,
                    violations = result.violations,
                    wasRetried = result.wasRetried,
                )
                _state.update { it.copy(isLoading = false, messages = it.messages + assistantMsg) }
                _effects.send(InvariantsContract.Effect.ScrollToBottom)
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        isLoading = false,
                        messages = it.messages.dropLast(1),
                        errorMessage = apiService.friendlyError(e),
                    )
                }
            }
        }
    }

    // ── Хранилище ─────────────────────────────────────────────────────────────

    private fun loadInvariantsFromStorage() {
        val stored = runCatching {
            PlatformStorage.load(STORAGE_INVARIANTS_KEY)?.let {
                json.decodeFromString<List<Invariant>>(it)
            }
        }.getOrNull()

        val invariants = if (stored.isNullOrEmpty()) DEFAULT_INVARIANTS else stored
        if (stored.isNullOrEmpty()) persistInvariants(invariants)
        _state.update { it.copy(invariants = invariants) }
    }

    private fun persistInvariants(invariants: List<Invariant>) {
        runCatching {
            PlatformStorage.save(STORAGE_INVARIANTS_KEY, json.encodeToString<List<Invariant>>(invariants))
        }
    }
}
