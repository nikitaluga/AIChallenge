package ru.nikitaluga.aichallenge.token

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ru.nikitaluga.aichallenge.domain.model.TokenStats

@Composable
fun TokenScreen(viewModel: TokenViewModel = viewModel { TokenViewModel() }) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                TokenContract.Effect.ScrollToBottom -> {
                    val count = state.messages.size + if (state.isStreaming) 1 else 0
                    if (count > 0) listState.animateScrollToItem(count - 1)
                }
            }
        }
    }

    val streamLen = state.streamingText.length
    LaunchedEffect(streamLen) {
        if (state.isStreaming && streamLen > 0) {
            val count = state.messages.size + 1
            listState.scrollToItem(count - 1)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {

        // ── Top bar ────────────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "День 8 · Токены",
                style = MaterialTheme.typography.headlineSmall,
            )
            Row {
                IconButton(onClick = { viewModel.onEvent(TokenContract.Event.ToggleStats) }) {
                    Icon(
                        imageVector = if (state.showStats) IconExpandLess else IconExpandMore,
                        contentDescription = if (state.showStats) "Скрыть статистику" else "Показать статистику",
                    )
                }
                IconButton(
                    onClick = { viewModel.onEvent(TokenContract.Event.ClearHistory) },
                    enabled = !state.isStreaming,
                ) {
                    Icon(
                        imageVector = IconAdd,
                        contentDescription = "Новый чат",
                    )
                }
            }
        }

        // ── Message list ───────────────────────────────────────────────────────
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(state.messages) { message ->
                TokenMessageBubble(message)
            }
            if (state.isStreaming) {
                item {
                    TokenStreamingBubble(text = state.streamingText)
                }
            }
        }

        // ── Token stats panel ──────────────────────────────────────────────────
        AnimatedVisibility(
            visible = state.showStats,
            enter = expandVertically(),
            exit = shrinkVertically(),
        ) {
            TokenStatsPanel(stats = state.tokenStats)
        }

        // ── Scenario quick-fill buttons ────────────────────────────────────────
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(TokenContract.Scenario.entries) { scenario ->
                OutlinedButton(
                    onClick = { viewModel.onEvent(TokenContract.Event.LoadScenario(scenario)) },
                    enabled = !state.isStreaming,
                ) {
                    Text(scenario.label, fontSize = 12.sp)
                }
            }
        }

        // ── Input row ──────────────────────────────────────────────────────────
        TextField(
            value = state.inputText,
            onValueChange = { viewModel.onEvent(TokenContract.Event.InputChanged(it)) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            placeholder = { Text("Введите сообщение…") },
            enabled = !state.isStreaming,
            singleLine = true,
            trailingIcon = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (state.inputText.isNotBlank()) {
                        Text(
                            text = "~${estimateTokens(state.inputText)}т",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.padding(start = 4.dp),
                        )
                    }
                    IconButton(
                        onClick = { viewModel.onEvent(TokenContract.Event.SendMessage) },
                        enabled = !state.isStreaming && state.inputText.isNotBlank(),
                    ) {
                        Icon(
                            imageVector = IconSend,
                            contentDescription = "Отправить",
                        )
                    }
                }
            },
        )
    }
}

// ── Token stats panel ──────────────────────────────────────────────────────────

@Composable
private fun TokenStatsPanel(stats: TokenStats) {
    val fillPct = stats.contextFillPercent
    val barColor = when {
        fillPct >= 0.8f -> MaterialTheme.colorScheme.error
        fillPct >= 0.6f -> Color(0xFFFFB300)
        else -> MaterialTheme.colorScheme.primary
    }

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            HorizontalDivider()
            Spacer(Modifier.height(6.dp))

            Text(
                text = "Статистика токенов",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(4.dp))

            // Row 1: last exchange
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                StatChip(label = "Запрос", value = "${stats.lastRequestTokens}т")
                StatChip(label = "Ответ", value = "${stats.lastResponseTokens}т")
                StatChip(label = "Сессия всего", value = "${stats.totalSessionTokens}т")
            }
            Spacer(Modifier.height(2.dp))

            // Row 2: user/assistant split
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                StatChip(label = "user", value = "${stats.totalUserTokens}т")
                StatChip(label = "assistant", value = "${stats.totalAssistantTokens}т")
                StatChip(label = "Сообщений", value = "${stats.messageCount}")
            }
            Spacer(Modifier.height(6.dp))

            // Context window progress bar
            val pctInt = (fillPct * 100).toInt()
            Text(
                text = "Контекст: ${stats.currentContextTokens} / ${stats.contextWindowLimit} токенов ($pctInt%)",
                style = MaterialTheme.typography.labelSmall,
                color = barColor,
            )
            Spacer(Modifier.height(2.dp))
            LinearProgressIndicator(
                progress = { fillPct },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = barColor,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            )

            // Warning / error banner
            if (stats.isOverLimit) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Контекст переполнен! Старые сообщения автоматически удалены.",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                )
            } else if (stats.isNearLimit) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Контекст заполнен на $pctInt% — скоро автоудаление старых сообщений.",
                    color = Color(0xFFFFB300),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}

@Composable
private fun StatChip(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline,
        )
    }
}

// ── Message bubbles ────────────────────────────────────────────────────────────

@Composable
private fun TokenMessageBubble(message: TokenContract.DisplayMessage) {
    val isUser = message.role == "user"
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Column(
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
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
            if (message.tokenCount > 0) {
                Text(
                    text = "~${message.tokenCount} токенов",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                    textAlign = if (isUser) TextAlign.End else TextAlign.Start,
                )
            }
        }
    }
}

/** Assistant bubble that shows a blinking cursor while text streams in. */
@Composable
private fun TokenStreamingBubble(text: String) {
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

/** Cheap estimate used only for the live input counter — no need to call the ViewModel. */
private fun estimateTokens(text: String): Int {
    var nonAscii = 0; var ascii = 0
    for (c in text) when {
        c.isWhitespace() -> Unit
        c.code > 127 -> nonAscii++
        else -> ascii++
    }
    return maxOf(1, (nonAscii + 1) / 2 + (ascii + 3) / 4)
}
