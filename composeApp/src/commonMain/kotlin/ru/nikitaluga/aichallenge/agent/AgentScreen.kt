package ru.nikitaluga.aichallenge.agent

import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun AgentScreen(viewModel: AgentViewModel = viewModel<AgentViewModel>()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    // Scroll to bottom when a new complete message arrives
    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                AgentContract.Effect.ScrollToBottom -> {
                    val count = state.messages.size + if (state.isStreaming) 1 else 0
                    if (count > 0) listState.animateScrollToItem(count - 1)
                }
            }
        }
    }

    // Auto-scroll as streaming text grows
    val streamLen = state.streamingText.length
    LaunchedEffect(streamLen) {
        if (state.isStreaming && streamLen > 0) {
            val count = state.messages.size + 1
            listState.scrollToItem(count - 1)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            OutlinedButton(
                onClick = { viewModel.onEvent(AgentContract.Event.ClearHistory) },
                enabled = !state.isStreaming,
            ) {
                Text("Новый чат")
            }
        }

        // Message list
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(state.messages) { message ->
                MessageBubble(message)
            }
            // Live streaming bubble
            if (state.isStreaming) {
                item {
                    StreamingBubble(text = state.streamingText)
                }
            }
        }

        // Input row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TextField(
                value = state.inputText,
                onValueChange = { viewModel.onEvent(AgentContract.Event.InputChanged(it)) },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Введите сообщение...") },
                enabled = !state.isStreaming,
                singleLine = true,
            )
            Button(
                onClick = { viewModel.onEvent(AgentContract.Event.SendMessage) },
                enabled = !state.isStreaming && state.inputText.isNotBlank(),
            ) {
                Text("Отправить")
            }
        }
    }
}

@Composable
private fun MessageBubble(message: AgentContract.DisplayMessage) {
    val isUser = message.role == "user"
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Text(
            text = message.content,
            modifier = Modifier
                .widthIn(max = 280.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(
                    if (isUser) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.secondaryContainer,
                )
                .padding(12.dp),
            color = if (isUser) MaterialTheme.colorScheme.onPrimaryContainer
            else MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}

/** Assistant bubble that shows a blinking cursor while text streams in. */
@Composable
private fun StreamingBubble(text: String) {
    val infiniteTransition = rememberInfiniteTransition()
    val cursorAlpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 1000
                1f at 0
                1f at 400
                0f at 500
                0f at 900
                1f at 1000
            },
        ),
    )

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text = buildAnnotatedString {
                if (text.isEmpty()) {
                    // Waiting for first chunk — show animated dots
                    append("●  ●  ●")
                } else {
                    append(text)
                    withStyle(SpanStyle(color = Color.Gray.copy(alpha = cursorAlpha))) {
                        append("▌")
                    }
                }
            },
            modifier = Modifier
                .widthIn(max = 280.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.secondaryContainer)
                .padding(12.dp),
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}
