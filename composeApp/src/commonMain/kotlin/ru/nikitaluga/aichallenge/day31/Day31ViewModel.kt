package ru.nikitaluga.aichallenge.day31

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.nikitaluga.aichallenge.data.agent.DevAssistantAgent

class Day31ViewModel : ViewModel() {

    private val agent = DevAssistantAgent()

    private val _state = MutableStateFlow(Day31Contract.State())
    val state: StateFlow<Day31Contract.State> = _state.asStateFlow()

    private val _effects = Channel<Day31Contract.Effect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    init {
        loadStats()
    }

    fun onEvent(event: Day31Contract.Event) {
        when (event) {
            is Day31Contract.Event.InputChanged -> {
                val suggestions = if (event.text.startsWith("/")) {
                    val query = event.text.removePrefix("/").lowercase()
                    SLASH_COMMANDS.filter { query.isEmpty() || it.name.removePrefix("/").startsWith(query) }
                } else emptyList()
                _state.update { it.copy(inputText = event.text, commandSuggestions = suggestions) }
            }
            Day31Contract.Event.SendMessage -> sendMessage()
            Day31Contract.Event.ClearHistory -> {
                agent.clearHistory()
                _state.update { it.copy(messages = emptyList(), error = null) }
            }
            Day31Contract.Event.BuildIndex -> buildIndex()
            is Day31Contract.Event.SelectSlashCommand -> {
                _state.update { it.copy(inputText = event.command.prompt, commandSuggestions = emptyList()) }
                sendMessage()
            }
            is Day31Contract.Event.ToggleMcp -> _state.update { it.copy(useMcp = event.enabled) }
            Day31Contract.Event.DismissError -> _state.update { it.copy(error = null) }
        }
    }

    companion object {
        val SLASH_COMMANDS = listOf(
            Day31Contract.SlashCommand(
                name = "/help",
                description = "Архитектура проекта",
                prompt = "Расскажи об архитектуре проекта",
            ),
            Day31Contract.SlashCommand(
                name = "/status",
                description = "Изменённые файлы в репозитории",
                prompt = "Какие файлы сейчас изменены в репозитории?",
            ),
            Day31Contract.SlashCommand(
                name = "/branch",
                description = "Текущая git-ветка",
                prompt = "На какой ветке я сейчас нахожусь?",
            ),
            Day31Contract.SlashCommand(
                name = "/files",
                description = "Структура файлов проекта",
                prompt = "Покажи структуру файлов проекта",
            ),
            Day31Contract.SlashCommand(
                name = "/mvi",
                description = "Как работает MVI в проекте",
                prompt = "Объясни как работает MVI-паттерн в этом проекте",
            ),
            Day31Contract.SlashCommand(
                name = "/rag",
                description = "Как устроен RAG в проекте",
                prompt = "Как устроен RAG-пайплайн в проекте?",
            ),
        )
    }

    private fun loadStats() {
        viewModelScope.launch {
            runCatching { agent.getStats() }
                .onSuccess { stats ->
                    _state.update {
                        it.copy(
                            indexStats = Day31Contract.IndexStats(
                                hasIndex = stats.hasIndex,
                                totalChunks = stats.totalChunks,
                                docsIndexed = stats.docsIndexed,
                                createdAt = stats.createdAt.take(10),
                            )
                        )
                    }
                }
        }
    }

    private fun sendMessage() {
        val text = _state.value.inputText.trim()
        if (text.isBlank() || _state.value.isLoading) return

        val userEntry = Day31Contract.ChatEntry(role = "user", content = text)
        _state.update { it.copy(messages = it.messages + userEntry, inputText = "", isLoading = true, error = null) }
        viewModelScope.launch { _effects.send(Day31Contract.Effect.ScrollToBottom) }

        viewModelScope.launch {
            runCatching { agent.sendMessage(text, useMcp = _state.value.useMcp) }
                .onSuccess { result ->
                    val assistantEntry = Day31Contract.ChatEntry(
                        role = "assistant",
                        content = result.answer,
                        sources = result.sources.map {
                            Day31Contract.ChatSource(source = it.source, section = it.section, preview = it.preview)
                        },
                        toolsUsed = result.toolsUsed,
                    )
                    _state.update { it.copy(messages = it.messages + assistantEntry, isLoading = false) }
                    _effects.send(Day31Contract.Effect.ScrollToBottom)
                }
                .onFailure { e ->
                    _state.update { it.copy(isLoading = false, error = "Ошибка: ${e.message}") }
                }
        }
    }

    private fun buildIndex() {
        _state.update { it.copy(isIndexing = true, error = null) }
        viewModelScope.launch {
            runCatching { agent.buildIndex() }
                .onSuccess { msg ->
                    _state.update { it.copy(isIndexing = false) }
                    loadStats()
                }
                .onFailure { e ->
                    _state.update { it.copy(isIndexing = false, error = "Ошибка индексации: ${e.message}") }
                }
        }
    }
}
