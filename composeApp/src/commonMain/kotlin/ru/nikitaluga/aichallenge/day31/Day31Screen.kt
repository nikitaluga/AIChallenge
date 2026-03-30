package ru.nikitaluga.aichallenge.day31

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

private val IconSend: ImageVector by lazy {
    ImageVector.Builder(defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).apply {
        path(fill = SolidColor(Color.Black)) {
            moveTo(2.01f, 21f)
            lineTo(23f, 12f)
            lineTo(2.01f, 3f)
            lineTo(2f, 10f)
            lineToRelative(15f, 2f)
            lineToRelative(-15f, 2f)
            close()
        }
    }.build()
}

/**
 * День 31 — Ассистент разработчика.
 *
 * Чат с RAG по документации проекта (README, CLAUDE.md, .specs/, docs/)
 * + MCP git-инструменты (branch, status, diff, list_files).
 * Кнопка /help вставляет пример вопроса о проекте.
 */
@Composable
fun Day31Screen(viewModel: Day31ViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                Day31Contract.Effect.ScrollToBottom ->
                    if (state.messages.isNotEmpty()) listState.animateScrollToItem(state.messages.lastIndex)
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {

        // ── Index stats panel ─────────────────────────────────────────────────
        IndexPanel(
            stats = state.indexStats,
            isIndexing = state.isIndexing,
            onBuildIndex = { viewModel.onEvent(Day31Contract.Event.BuildIndex) },
        )

        HorizontalDivider()

        // ── MCP toggle ────────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("MCP git-инструменты", style = MaterialTheme.typography.labelMedium)
            Switch(
                checked = state.useMcp,
                onCheckedChange = { viewModel.onEvent(Day31Contract.Event.ToggleMcp(it)) },
            )
        }

        HorizontalDivider()

        // ── Chat messages ─────────────────────────────────────────────────────
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(state.messages) { entry ->
                ChatBubble(entry = entry)
            }
            if (state.isLoading) {
                item {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    }
                }
            }
        }

        // ── Error banner ──────────────────────────────────────────────────────
        if (state.error != null) {
            Text(
                text = state.error!!,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }

        HorizontalDivider()

        // ── Slash command suggestions ─────────────────────────────────────────
        if (state.commandSuggestions.isNotEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                elevation = CardDefaults.cardElevation(4.dp),
            ) {
                Column {
                    state.commandSuggestions.forEachIndexed { index, command ->
                        if (index > 0) HorizontalDivider()
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.onEvent(Day31Contract.Event.SelectSlashCommand(command)) }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = command.name,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                text = command.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }

        // ── Input row ─────────────────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            OutlinedTextField(
                value = state.inputText,
                onValueChange = { viewModel.onEvent(Day31Contract.Event.InputChanged(it)) },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Напиши / для команд или вопрос…", fontSize = 13.sp) },
                maxLines = 3,
            )

            IconButton(
                onClick = { viewModel.onEvent(Day31Contract.Event.SendMessage) },
                enabled = state.inputText.isNotBlank() && !state.isLoading,
            ) {
                Icon(
                    imageVector = IconSend,
                    contentDescription = "Отправить",
                )
            }
        }

        // Clear history button
        TextButton(
            onClick = { viewModel.onEvent(Day31Contract.Event.ClearHistory) },
            modifier = Modifier.align(Alignment.CenterHorizontally),
        ) {
            Text("Очистить историю", style = MaterialTheme.typography.labelSmall)
        }
    }
}

// ── IndexPanel ────────────────────────────────────────────────────────────────

@Composable
private fun IndexPanel(
    stats: Day31Contract.IndexStats?,
    isIndexing: Boolean,
    onBuildIndex: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("Ассистент разработчика", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            if (stats == null) {
                Text("Загрузка статистики…", style = MaterialTheme.typography.bodySmall)
            } else if (!stats.hasIndex) {
                Text("Индекс не построен", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            } else {
                Text(
                    "${stats.totalChunks} чанков · ${stats.docsIndexed} файлов · ${stats.createdAt}",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        if (isIndexing) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp))
        } else {
            Button(
                onClick = onBuildIndex,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
            ) {
                Text("Индексировать", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSecondaryContainer)
            }
        }
    }
}

// ── ChatBubble ────────────────────────────────────────────────────────────────

@Composable
private fun ChatBubble(entry: Day31Contract.ChatEntry) {
    val isUser = entry.role == "user"
    val bubbleColor = if (isUser) MaterialTheme.colorScheme.primaryContainer
    else MaterialTheme.colorScheme.surfaceVariant
    val alignment = if (isUser) Alignment.End else Alignment.Start

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        horizontalAlignment = alignment,
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 320.dp)
                .background(bubbleColor, RoundedCornerShape(12.dp))
                .padding(10.dp),
        ) {
            Text(text = entry.content, style = MaterialTheme.typography.bodyMedium)
        }

        // Tools used chips
        if (entry.toolsUsed.isNotEmpty()) {
            Row(
                modifier = Modifier.padding(top = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                entry.toolsUsed.forEach { tool ->
                    ToolChip(tool)
                }
            }
        }

        // Sources
        if (entry.sources.isNotEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            entry.sources.take(3).forEach { source ->
                SourceCard(source)
            }
        }
    }
}

@Composable
private fun ToolChip(tool: String) {
    Box(
        modifier = Modifier
            .background(MaterialTheme.colorScheme.tertiaryContainer, RoundedCornerShape(8.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(
            text = "⚙ $tool",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
        )
    }
}

@Composable
private fun SourceCard(source: Day31Contract.ChatSource) {
    Card(
        modifier = Modifier.fillMaxWidth().widthIn(max = 320.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(1.dp),
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Text(
                text = buildString {
                    append(source.source)
                    if (source.section != null) append(" / ${source.section}")
                },
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = source.preview,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
