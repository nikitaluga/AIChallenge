package ru.nikitaluga.aichallenge.day33

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import ru.nikitaluga.aichallenge.data.agent.SupportAgent

class Day33ViewModel : ViewModel() {

    private val agent = SupportAgent()

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private val _effects = Channel<Effect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    init {
        loadUsers()
    }

    fun onEvent(event: Event) {
        when (event) {
            is Event.UserSelected -> selectUser(event.userId, event.userName)
            is Event.InputChanged -> _state.value = _state.value.copy(inputText = event.text, error = null)
            Event.SendMessage -> sendMessage()
            is Event.TicketClicked -> _state.value = _state.value.copy(
                selectedTicket = event.ticket,
                suggestedQuestions = TICKET_QUESTIONS[event.ticket.id] ?: DEFAULT_QUESTIONS,
            )
            Event.DismissTicketDialog -> _state.value = _state.value.copy(selectedTicket = null)
            is Event.QuestionSelected -> {
                _state.value = _state.value.copy(inputText = event.question)
                sendMessage()
            }
            Event.ClearChat -> _state.value = _state.value.copy(messages = emptyList(), inputText = "")
            Event.DismissError -> _state.value = _state.value.copy(error = null)
        }
    }

    private fun loadUsers() {
        _state.value = _state.value.copy(isLoadingUsers = true)
        viewModelScope.launch {
            runCatching { agent.getUsers() }
                .onSuccess { users ->
                    val items = users.map { UserItem(it.id, it.name, it.plan) }
                    _state.value = _state.value.copy(users = items, isLoadingUsers = false)
                    if (items.isNotEmpty()) {
                        val first = items.first()
                        selectUser(first.id, first.name)
                    }
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(
                        isLoadingUsers = false,
                        error = "Не удалось загрузить пользователей: ${e.message}",
                    )
                }
        }
    }

    private fun selectUser(userId: String, userName: String) {
        _state.value = _state.value.copy(
            selectedUserId = userId,
            selectedUserName = userName,
            selectedTicket = null,
            isLoadingTickets = true,
        )
        viewModelScope.launch {
            runCatching { agent.getTickets(userId) }
                .onSuccess { tickets ->
                    _state.value = _state.value.copy(
                        tickets = tickets.map { TicketItem(it.id, it.subject, it.status, it.description, it.createdAt) },
                        isLoadingTickets = false,
                    )
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(
                        isLoadingTickets = false,
                        error = "Ошибка загрузки тикетов: ${e.message}",
                    )
                }
        }
    }

    private fun sendMessage() {
        val text = _state.value.inputText.trim()
        if (text.isBlank() || _state.value.isLoading) return
        val userId = _state.value.selectedUserId
        if (userId.isBlank()) {
            _state.value = _state.value.copy(error = "Выберите пользователя")
            return
        }

        val userMsg = ChatMsg(role = "user", content = text)
        val history = _state.value.messages.map { SupportAgent.SupportHistoryMsg(it.role, it.content) }
        _state.value = _state.value.copy(
            messages = _state.value.messages + userMsg,
            inputText = "",
            isLoading = true,
        )
        _effects.trySend(Effect.ScrollToBottom)

        viewModelScope.launch {
            runCatching { agent.chat(userId, text, history) }
                .onSuccess { response ->
                    val assistantMsg = ChatMsg(role = "assistant", content = response.answer)
                    // Refresh tickets in case status was updated by the assistant
                    val updatedTickets = runCatching { agent.getTickets(userId) }
                        .getOrNull()
                        ?.map { TicketItem(it.id, it.subject, it.status, it.description, it.createdAt) }
                        ?: _state.value.tickets

                    _state.value = _state.value.copy(
                        messages = _state.value.messages + assistantMsg,
                        isLoading = false,
                        tickets = updatedTickets,
                    )
                    _effects.trySend(Effect.ScrollToBottom)
                }
                .onFailure { e ->
                    val errMsg = ChatMsg(role = "assistant", content = "Ошибка: ${e.message}")
                    _state.value = _state.value.copy(
                        messages = _state.value.messages + errMsg,
                        isLoading = false,
                        error = e.message ?: "Ошибка запроса",
                    )
                }
        }
    }
}
