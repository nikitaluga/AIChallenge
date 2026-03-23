package ru.nikitaluga.aichallenge.day26

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
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

/**
 * День 26 — Чат с локальной LLM (Ollama llama3.2:3b) и переключателем Cloud / Local.
 *
 * - Local (Ollama): POST /local/chat → Ktor → localhost:11434/api/chat
 * - Cloud (OpenAI gpt-4o-mini): POST /local/cloud → Ktor → routerai.ru
 * - Каждый ответ показывает latency и backend.
 */
@Composable
fun Day26Screen(viewModel: Day26ViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is Day26Contract.Effect.ScrollToBottom ->
                    if (state.messages.isNotEmpty()) listState.animateScrollToItem(state.messages.lastIndex)
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {

        // ── Header: backend toggle ────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "Локальная LLM",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(
                    selected = state.useLocal,
                    onClick = { viewModel.onEvent(Day26Contract.Event.ToggleBackend(true)) },
                    label = { Text("💻 Local") },
                )
                FilterChip(
                    selected = !state.useLocal,
                    onClick = { viewModel.onEvent(Day26Contract.Event.ToggleBackend(false)) },
                    label = { Text("☁ Cloud") },
                )
                IconButton(onClick = { viewModel.onEvent(Day26Contract.Event.ClearHistory) }) {
                    Text("🗑", fontSize = 18.sp)
                }
            }
        }

        // ── Status bar ────────────────────────────────────────────────────────
        val backendLabel = if (state.useLocal) "Ollama llama3.2:3b (local)" else "gpt-4o-mini (cloud)"
        Text(
            text = "Активный бэкенд: $backendLabel",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp),
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

        // ── Messages ──────────────────────────────────────────────────────────
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(state.messages) { msg ->
                MessageBubble(msg)
            }
            if (state.isLoading) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.Start,
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    }
                }
            }
        }

        // ── Question carousel ─────────────────────────────────────────────────
        LazyRow(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            contentPadding = PaddingValues(horizontal = 8.dp),
        ) {
            items(Day26Contract.SAMPLE_QUESTIONS) { question ->
                SuggestionChip(
                    onClick = { viewModel.onEvent(Day26Contract.Event.SelectQuestion(question)) },
                    enabled = !state.isLoading,
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

        // ── Input ─────────────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            OutlinedTextField(
                value = state.inputText,
                onValueChange = { viewModel.onEvent(Day26Contract.Event.InputChanged(it)) },
                placeholder = { Text("Введите сообщение…") },
                modifier = Modifier.weight(1f),
                maxLines = 4,
                shape = RoundedCornerShape(16.dp),
            )
            FilledIconButton(
                onClick = { viewModel.onEvent(Day26Contract.Event.SendMessage) },
                enabled = state.inputText.isNotBlank() && !state.isLoading,
            ) {
                Icon(
                    imageVector = ru.nikitaluga.aichallenge.context.CtxIconSend,
                    contentDescription = "Отправить",
                )
            }
        }
    }
}

@Composable
private fun MessageBubble(msg: Day26Contract.Message) {
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
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                Text(text = msg.content, style = MaterialTheme.typography.bodyMedium)
                if (msg.latencyMs != null) {
                    Text(
                        text = "${msg.backend ?: "?"} · ${msg.latencyMs} ms",
                        style = MaterialTheme.typography.labelSmall,
                        fontStyle = FontStyle.Italic,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
    }
}
