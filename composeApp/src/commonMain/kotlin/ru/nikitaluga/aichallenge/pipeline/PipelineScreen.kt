package ru.nikitaluga.aichallenge.pipeline

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.collections.immutable.ImmutableList
import ru.nikitaluga.aichallenge.domain.model.PipelineChatMessage
import ru.nikitaluga.aichallenge.domain.model.PipelineToolStep
import ru.nikitaluga.aichallenge.domain.model.SavedFileInfo

@Composable
fun PipelineScreen(viewModel: PipelineViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.onEvent(PipelineContract.Event.RefreshFiles)
    }

    LaunchedEffect(viewModel.effects) {
        viewModel.effects.collect { effect ->
            when (effect) {
                PipelineContract.Effect.ScrollToBottom ->
                    if (state.messages.isNotEmpty()) listState.animateScrollToItem(state.messages.lastIndex)
            }
        }
    }

    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.onEvent(PipelineContract.Event.DismissError)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {

            // ── Chat messages ────────────────────────────────────────────────
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item { Spacer(modifier = Modifier.height(4.dp)) }
                items(state.messages) { msg ->
                    PipelineChatBubble(message = msg)
                }
                if (state.isLoading) {
                    item { PipelineLoadingRow() }
                }
                item { Spacer(modifier = Modifier.height(4.dp)) }
            }

            // ── Input bar ────────────────────────────────────────────────────
            PipelineInputBar(
                text = state.inputText,
                isLoading = state.isLoading,
                onTextChange = { viewModel.onEvent(PipelineContract.Event.InputChanged(it)) },
                onSend = { viewModel.onEvent(PipelineContract.Event.SendMessage) },
                onClear = { viewModel.onEvent(PipelineContract.Event.ClearHistory) },
            )

            HorizontalDivider()

            // ── Saved files panel ────────────────────────────────────────────
            SavedFilesPanel(
                files = state.savedFiles,
                isLoading = state.isFilesLoading,
                error = state.filesError,
                onRefresh = { viewModel.onEvent(PipelineContract.Event.RefreshFiles) },
                onOpenFile = { viewModel.onEvent(PipelineContract.Event.OpenFile(it)) },
            )
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }

    // ── File viewer dialog ────────────────────────────────────────────────────
    val openedFilename = state.openedFilename
    if (openedFilename != null) {
        AlertDialog(
            onDismissRequest = { viewModel.onEvent(PipelineContract.Event.CloseFile) },
            title = { Text(openedFilename, style = MaterialTheme.typography.titleSmall) },
            text = {
                val content = state.openedFileContent
                if (content == null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .heightIn(max = 320.dp)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        Text(
                            text = content,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.onEvent(PipelineContract.Event.CloseFile) }) {
                    Text("Закрыть")
                }
            },
        )
    }
}

// ── Saved Files Panel ─────────────────────────────────────────────────────────

@Composable
private fun SavedFilesPanel(
    files: ImmutableList<SavedFileInfo>,
    isLoading: Boolean,
    error: String?,
    onRefresh: () -> Unit,
    onOpenFile: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 200.dp)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            } else {
                Text(
                    text = if (files.isEmpty()) "Нет сохранённых файлов" else "Файлы (${files.size})",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
            }
            TextButton(onClick = onRefresh, enabled = !isLoading) {
                Text("Обновить", style = MaterialTheme.typography.labelSmall)
            }
        }

        when {
            error != null -> Text(
                text = error,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 4.dp),
            )
            files.isEmpty() && !isLoading -> Text(
                text = "Попросите агента получить погоду и сохранить сводку, например: «погода в Москве, сохрани»",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
            else -> {
                files.forEach { file ->
                    Spacer(modifier = Modifier.height(6.dp))
                    SavedFileCard(file = file, onClick = { onOpenFile(file.filename) })
                }
                Spacer(modifier = Modifier.height(4.dp))
            }
        }
    }
}

// ── Saved File Card ───────────────────────────────────────────────────────────

@Composable
private fun SavedFileCard(file: SavedFileInfo, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "💾",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = file.filename,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "${file.sizeBytes} байт",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = "›",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ── Chat Bubble ───────────────────────────────────────────────────────────────

@Composable
private fun PipelineChatBubble(message: PipelineChatMessage) {
    val isUser = message.role == "user"

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = if (isUser) 12.dp else 4.dp,
                topEnd = if (isUser) 4.dp else 12.dp,
                bottomStart = 12.dp,
                bottomEnd = 12.dp,
            ),
            color = if (isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth(0.85f),
        ) {
            Text(
                text = message.content,
                modifier = Modifier.padding(12.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = if (isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // Pipeline step badges — one per tool call
        if (!isUser && message.toolSteps.isNotEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            Column(
                modifier = Modifier.fillMaxWidth(0.85f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                message.toolSteps.forEach { step ->
                    PipelineStepBadge(step = step)
                }
            }
        }
    }
}

// ── Pipeline Step Badge ───────────────────────────────────────────────────────

@Composable
private fun PipelineStepBadge(step: PipelineToolStep) {
    val (emoji, label) = when (step.toolName) {
        "get_weather" -> "🔍" to "get_weather"
        "summarize_weather" -> "📝" to "summarize_weather"
        "save_to_file" -> "💾" to "save_to_file"
        else -> "⚙️" to step.toolName
    }

    Surface(
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = "$emoji $label",
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}

// ── Loading indicator ─────────────────────────────────────────────────────────

@Composable
private fun PipelineLoadingRow() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        Spacer(modifier = Modifier.width(8.dp))
        Text("Пайплайн выполняется...", style = MaterialTheme.typography.bodySmall)
    }
}

// ── Input Bar ─────────────────────────────────────────────────────────────────

@Composable
private fun PipelineInputBar(
    text: String,
    isLoading: Boolean,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    onClear: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = onTextChange,
            modifier = Modifier.weight(1f),
            placeholder = { Text("Погода в Москве, сохрани сводку...") },
            maxLines = 4,
            enabled = !isLoading,
        )
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Button(
                onClick = onSend,
                enabled = text.isNotBlank() && !isLoading,
            ) { Text("→") }
            TextButton(onClick = onClear, enabled = !isLoading) {
                Text(
                    "Сброс",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}
