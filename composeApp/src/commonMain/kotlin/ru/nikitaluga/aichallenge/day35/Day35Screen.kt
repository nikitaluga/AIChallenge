package ru.nikitaluga.aichallenge.day35

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ru.nikitaluga.aichallenge.context.CtxIconSend

@Composable
fun Day35Screen(viewModel: Day35ViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is Effect.CopyToClipboard -> Unit // платформенная реализация через expect/actual
                Effect.ScrollToResults ->
                    if (state.results.isNotEmpty()) listState.animateScrollToItem(0)
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {

            // ── Header ────────────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Git Commit Generator",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
            }

            HorizontalDivider()

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {

                // ── Quick prompts ─────────────────────────────────────────────
                item {
                    Text(
                        "Примеры диффов",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(4.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        items(QUICK_DIFFS) { diff ->
                            SuggestionChip(
                                onClick = { viewModel.onEvent(Event.QuickPromptSelected(diff)) },
                                enabled = !state.isLoading,
                                label = {
                                    Text(
                                        diff.lines().first().take(30),
                                        style = MaterialTheme.typography.labelSmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                },
                            )
                        }
                    }
                }

                // ── Diff input ────────────────────────────────────────────────
                item {
                    OutlinedTextField(
                        value = state.diff,
                        onValueChange = { viewModel.onEvent(Event.DiffChanged(it)) },
                        label = { Text("Git diff *", style = MaterialTheme.typography.bodySmall) },
                        placeholder = { Text("+ добавленные строки\n- удалённые строки", style = MaterialTheme.typography.bodySmall) },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 4,
                        maxLines = 10,
                        shape = RoundedCornerShape(12.dp),
                        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        enabled = !state.isLoading,
                    )
                }

                // ── Context input ─────────────────────────────────────────────
                item {
                    OutlinedTextField(
                        value = state.context,
                        onValueChange = { viewModel.onEvent(Event.ContextChanged(it)) },
                        label = { Text("Контекст (опционально)", style = MaterialTheme.typography.bodySmall) },
                        placeholder = { Text("Например: рефакторинг авторизации, исправление бага #123", style = MaterialTheme.typography.bodySmall) },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 3,
                        shape = RoundedCornerShape(12.dp),
                        textStyle = MaterialTheme.typography.bodySmall,
                        enabled = !state.isLoading,
                    )
                }

                // ── Generate button ───────────────────────────────────────────
                item {
                    Button(
                        onClick = { viewModel.onEvent(Event.Generate) },
                        enabled = state.diff.isNotBlank() && !state.isLoading,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (state.isLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                        } else {
                            Icon(imageVector = CtxIconSend, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                        Text(" Сгенерировать", style = MaterialTheme.typography.labelMedium)
                    }
                }

                // ── Results ───────────────────────────────────────────────────
                if (state.results.isNotEmpty()) {
                    item {
                        Text(
                            "Варианты commit message",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    items(state.results) { message ->
                        CommitMessageCard(
                            message = message,
                            isCopied = state.copiedMessage == message,
                            onCopy = { viewModel.onEvent(Event.CopyMessage(message)) },
                        )
                    }
                }
            }
        }

        // ── Snackbar ──────────────────────────────────────────────────────────
        if (state.error != null) {
            Snackbar(
                modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp),
                action = { TextButton(onClick = { viewModel.onEvent(Event.DismissError) }) { Text("OK") } },
            ) { Text(state.error ?: "", style = MaterialTheme.typography.bodySmall) }
        }

        if (state.copiedMessage != null) {
            Snackbar(
                modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp),
                action = { TextButton(onClick = { viewModel.onEvent(Event.DismissCopied) }) { Text("OK") } },
            ) { Text("Скопировано!", style = MaterialTheme.typography.bodySmall) }
        }
    }
}

// ── Private composables ───────────────────────────────────────────────────────

@Composable
private fun CommitMessageCard(
    message: String,
    isCopied: Boolean,
    onCopy: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isCopied)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onCopy, modifier = Modifier.size(32.dp)) {
                Text(if (isCopied) "✓" else "📋", fontSize = 14.sp)
            }
        }
    }
}
