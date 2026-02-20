package ru.nikitaluga.aichallenge.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.nikitaluga.aichallenge.data.repository.ChatRepositoryImpl
import ru.nikitaluga.aichallenge.domain.model.Message
import ru.nikitaluga.aichallenge.domain.usecase.SendMessageUseCase

class ChatViewModel(
    private val sendMessageUseCase: SendMessageUseCase = SendMessageUseCase(ChatRepositoryImpl()),
) : ViewModel() {

    private val _state = MutableStateFlow(ChatContract.State())
    val state: StateFlow<ChatContract.State> = _state.asStateFlow()

    private val _effects = Channel<ChatContract.Effect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    fun onEvent(event: ChatContract.Event) {
        when (event) {
            is ChatContract.Event.InputChanged -> _state.update { it.copy(inputText = event.text) }
            ChatContract.Event.SendMessage -> sendMessage()
        }
    }

    private fun sendMessage() {
        val text = _state.value.inputText.trim()
        if (text.isEmpty()) return

        _state.update { state ->
            state.copy(
                inputText = "",
                isLoading = true,
                messages = state.messages + Message("user", text),
            )
        }

        viewModelScope.launch {
            val result = sendMessageUseCase(text)
            _state.update { state ->
                state.copy(
                    isLoading = false,
                    messages = state.messages + Message(
                        role = "assistant",
                        content = result.getOrElse { "Ошибка: ${it.message}" },
                    ),
                )
            }
            _effects.send(ChatContract.Effect.ScrollToBottom)
        }
    }
}
