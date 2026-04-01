package ru.nikitaluga.aichallenge.day33

data class State(
    val users: List<UserItem> = emptyList(),
    val selectedUserId: String = "",
    val selectedUserName: String = "",
    val messages: List<ChatMsg> = emptyList(),
    val tickets: List<TicketItem> = emptyList(),
    val selectedTicket: TicketItem? = null,
    val suggestedQuestions: List<String> = DEFAULT_QUESTIONS,
    val inputText: String = "",
    val isLoading: Boolean = false,
    val isLoadingUsers: Boolean = false,
    val isLoadingTickets: Boolean = false,
    val error: String? = null,
)

data class UserItem(val id: String, val name: String, val plan: String)

data class TicketItem(
    val id: String,
    val subject: String,
    val status: String,
    val description: String = "",
    val createdAt: String = "",
)

data class ChatMsg(val role: String, val content: String)

sealed interface Event {
    data class UserSelected(val userId: String, val userName: String) : Event
    data class InputChanged(val text: String) : Event
    data object SendMessage : Event
    data class TicketClicked(val ticket: TicketItem) : Event
    data object DismissTicketDialog : Event
    data class QuestionSelected(val question: String) : Event
    data object ClearChat : Event
    data object DismissError : Event
}

val DEFAULT_QUESTIONS = listOf(
    "Какие у меня тикеты?",
    "Помоги войти в аккаунт",
    "Проблема с оплатой",
)

val TICKET_QUESTIONS = mapOf(
    "T-001" to listOf("Почему не работает авторизация?", "Как сбросить пароль?", "Что делать если аккаунт заблокирован?"),
    "T-002" to listOf("Почему отклонили платёж?", "Как вернуть деньги?", "Как изменить способ оплаты?"),
    "T-003" to listOf("Почему падает приложение?", "Как очистить кеш приложения?", "Когда выйдет исправление?"),
    "T-004" to listOf("Как экспортировать данные?", "Есть ли API для выгрузки?", "Какие форматы поддерживаются?"),
)

sealed interface Effect {
    data object ScrollToBottom : Effect
}
