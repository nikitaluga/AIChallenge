package ru.nikitaluga.aichallenge.taskstate

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ru.nikitaluga.aichallenge.domain.model.TaskStage

@Composable
fun TaskStateScreen(viewModel: TaskStateViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel.effects) {
        viewModel.effects.collect { effect ->
            when (effect) {
                TaskStateContract.Effect.ScrollToBottom ->
                    if (state.messages.isNotEmpty()) listState.animateScrollToItem(state.messages.lastIndex)
            }
        }
    }

    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.onEvent(TaskStateContract.Event.DismissError)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
    Column(modifier = Modifier.fillMaxSize()) {
        // ── Заголовок и прогресс ──────────────────────────────────────────────
        StageProgressBar(currentStage = state.stage)

        // Подпись текущей стадии
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(
                text = state.stage.label,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = state.stage.actionHint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // ── Список сообщений ──────────────────────────────────────────────────
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { Spacer(modifier = Modifier.height(4.dp)) }
            items(state.messages) { msg ->
                MessageBubble(message = msg)
            }
            if (state.isLoading) {
                item {
                    Row(modifier = Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.Start) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Агент думает...", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            item { Spacer(modifier = Modifier.height(4.dp)) }
        }

        // ── Панель артефакта ──────────────────────────────────────────────────
        if (state.showArtifactPanel) {
            ArtifactPanel(
                artifact = state.pendingArtifact,
                approveLabel = state.approveButtonLabel,
                onArtifactChanged = { viewModel.onEvent(TaskStateContract.Event.ArtifactChanged(it)) },
                onApprove = { viewModel.onEvent(TaskStateContract.Event.ApproveArtifact) },
                onDismiss = { viewModel.onEvent(TaskStateContract.Event.DismissArtifactPanel) },
            )
        }

        // ── Панель ввода ──────────────────────────────────────────────────────
        InputPanel(
            state = state,
            onInputChanged = { viewModel.onEvent(TaskStateContract.Event.InputChanged(it)) },
            onSend = { viewModel.onEvent(TaskStateContract.Event.SendMessage) },
            onShowArtifact = { viewModel.onEvent(TaskStateContract.Event.ShowArtifactPanel) },
            onNewTask = { viewModel.onEvent(TaskStateContract.Event.NewTask) },
        )
    }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

// ── Компоненты ─────────────────────────────────────────────────────────────────

@Composable
private fun StageProgressBar(currentStage: TaskStage) {
    val stages = TaskStage.entries
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        stages.forEachIndexed { idx, stage ->
            val isPast = stage.ordinal < currentStage.ordinal
            val isCurrent = stage == currentStage

            val bgColor = when {
                isCurrent -> MaterialTheme.colorScheme.primary
                isPast -> MaterialTheme.colorScheme.primaryContainer
                else -> MaterialTheme.colorScheme.surfaceVariant
            }
            val textColor = when {
                isCurrent -> MaterialTheme.colorScheme.onPrimary
                isPast -> MaterialTheme.colorScheme.onPrimaryContainer
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(6.dp))
                    .background(bgColor)
                    .padding(vertical = 6.dp, horizontal = 2.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (isPast) "✓ ${stage.label}" else stage.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = textColor,
                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1,
                )
            }

            if (idx < stages.lastIndex) {
                Text("→", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun MessageBubble(message: TaskStateContract.DisplayMessage) {
    val isUser = message.role == "user"
    val isSystem = message.role == "system"
    if (isSystem) return

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
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
    }
}

@Composable
private fun ArtifactPanel(
    artifact: String,
    approveLabel: String,
    onArtifactChanged: (String) -> Unit,
    onApprove: () -> Unit,
    onDismiss: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.secondaryContainer,
        tonalElevation = 4.dp,
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "Артефакт стадии — отредактируйте при необходимости:",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = artifact,
                onValueChange = onArtifactChanged,
                modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp, max = 200.dp),
                textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                placeholder = { Text("Артефакт текущей стадии...") },
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onApprove,
                    enabled = artifact.isNotBlank(),
                    modifier = Modifier.weight(1f),
                ) {
                    Text(approveLabel)
                }
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                    Text("Доработать")
                }
            }
        }
    }
}

@Composable
private fun InputPanel(
    state: TaskStateContract.State,
    onInputChanged: (String) -> Unit,
    onSend: () -> Unit,
    onShowArtifact: () -> Unit,
    onNewTask: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
        // Строка чата
        if (state.stage != TaskStage.DONE) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = state.inputText,
                    onValueChange = onInputChanged,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Сообщение...") },
                    maxLines = 4,
                    enabled = !state.isLoading,
                )
                Button(
                    onClick = onSend,
                    enabled = state.inputText.isNotBlank() && !state.isLoading,
                ) {
                    Text("→")
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
        }

        // Нижние кнопки управления
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (state.stage != TaskStage.DONE && !state.showArtifactPanel && state.messages.isNotEmpty()) {
                OutlinedButton(
                    onClick = onShowArtifact,
                    modifier = Modifier.weight(1f),
                    enabled = !state.isLoading,
                ) {
                    Text(state.approveButtonLabel)
                }
            }
            if (state.stage == TaskStage.DONE) {
                Button(
                    onClick = onNewTask,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Новая задача")
                }
            }
            TextButton(onClick = onNewTask, enabled = !state.isLoading) {
                Text("Сброс", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
