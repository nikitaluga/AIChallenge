package ru.nikitaluga.aichallenge.day27

import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

/**
 * День 27 — Чат с локальной LLM: стриминг токенов + динамический выбор модели Ollama.
 *
 * - POST /local/stream → SSE-стриминг токенов из Ollama
 * - GET /local/models  → список установленных моделей
 */
@Composable
fun Day27Screen(viewModel: Day27ViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                Day27Contract.Effect.ScrollToBottom -> {
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
            if (count > 0) listState.scrollToItem(count - 1)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {

            // ── Header ────────────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "Локальная LLM (стриминг)",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ModelSelector(
                        models = state.models,
                        selectedModel = state.selectedModel,
                        isLoading = state.isLoadingModels,
                        onSelect = { viewModel.onEvent(Day27Contract.Event.SelectModel(it)) },
                        onReload = { viewModel.onEvent(Day27Contract.Event.LoadModels) },
                    )
                    IconButton(
                        onClick = { viewModel.onEvent(Day27Contract.Event.ClearHistory) },
                        enabled = !state.isStreaming,
                    ) {
                        Text("🗑", fontSize = 18.sp)
                    }
                }
            }

            HorizontalDivider()

            // ── Messages ──────────────────────────────────────────────────────
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 8.dp),
            ) {
                items(state.messages) { msg ->
                    MessageBubble(msg)
                }
                if (state.isStreaming) {
                    item {
                        StreamingBubble(text = state.streamingText)
                    }
                }
            }

            // ── Sample questions carousel ─────────────────────────────────────
            LazyRow(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(horizontal = 8.dp),
            ) {
                items(Day27Contract.SAMPLE_QUESTIONS) { question ->
                    SuggestionChip(
                        onClick = { viewModel.onEvent(Day27Contract.Event.InputChanged(question)) },
                        enabled = !state.isStreaming,
                        label = {
                            Text(
                                text = question,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.widthIn(max = 200.dp),
                            )
                        },
                    )
                }
            }

            HorizontalDivider()

            // ── Input ─────────────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                OutlinedTextField(
                    value = state.inputText,
                    onValueChange = { viewModel.onEvent(Day27Contract.Event.InputChanged(it)) },
                    placeholder = { Text("Введите сообщение…") },
                    modifier = Modifier.weight(1f),
                    maxLines = 4,
                    shape = RoundedCornerShape(16.dp),
                    enabled = !state.isStreaming,
                )
                FilledIconButton(
                    onClick = { viewModel.onEvent(Day27Contract.Event.SendMessage) },
                    enabled = state.inputText.isNotBlank() && !state.isStreaming,
                ) {
                    Icon(
                        imageVector = ru.nikitaluga.aichallenge.context.CtxIconSend,
                        contentDescription = "Отправить",
                    )
                }
            }
        }

        // ── Error snackbar ────────────────────────────────────────────────────
        if (state.error != null) {
            Snackbar(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp),
                action = {
                    TextButton(onClick = { viewModel.onEvent(Day27Contract.Event.DismissError) }) {
                        Text("OK")
                    }
                },
            ) {
                Text(state.error ?: "")
            }
        }
    }
}

@Composable
private fun ModelSelector(
    models: List<String>,
    selectedModel: String,
    isLoading: Boolean,
    onSelect: (String) -> Unit,
    onReload: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(
            onClick = { if (models.isEmpty()) onReload() else expanded = true },
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
        ) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
            } else {
                Text(
                    text = selectedModel.substringBefore(":").take(12),
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                )
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            models.forEach { model ->
                DropdownMenuItem(
                    text = { Text(model) },
                    onClick = {
                        onSelect(model)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun MessageBubble(msg: Day27Contract.Message) {
    val isUser = msg.role == "user"
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Card(
            modifier = Modifier.widthIn(max = 300.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isUser)
                    MaterialTheme.colorScheme.primaryContainer
                else
                    MaterialTheme.colorScheme.surfaceVariant,
            ),
            shape = RoundedCornerShape(
                topStart = 16.dp, topEnd = 16.dp,
                bottomStart = if (isUser) 16.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 16.dp,
            ),
        ) {
            Text(
                text = msg.content,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
    }
}

/** Пузырёк ассистента с живым курсором во время стриминга. */
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
        Card(
            modifier = Modifier.widthIn(max = 300.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 4.dp, bottomEnd = 16.dp),
        ) {
            Text(
                text = buildAnnotatedString {
                    if (text.isEmpty()) {
                        append("●  ●  ●")
                    } else {
                        append(text)
                        withStyle(SpanStyle(color = Color.Gray.copy(alpha = cursorAlpha))) {
                            append("▌")
                        }
                    }
                },
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
    }
}
