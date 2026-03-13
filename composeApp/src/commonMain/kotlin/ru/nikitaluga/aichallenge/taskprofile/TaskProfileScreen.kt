package ru.nikitaluga.aichallenge.taskprofile

import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.collections.immutable.ImmutableList
import ru.nikitaluga.aichallenge.domain.model.ProfileStage
import ru.nikitaluga.aichallenge.domain.model.TaskProfile

@Composable
fun TaskProfileScreen(viewModel: TaskProfileViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    LaunchedEffect(viewModel.effects) {
        viewModel.effects.collect { effect ->
            when (effect) {
                TaskProfileContract.Effect.ScrollToBottom ->
                    if (state.messages.isNotEmpty()) listState.animateScrollToItem(state.messages.lastIndex)
            }
        }
    }

    // Ошибки показываем баннером с кнопкой Retry — snackbar не используем

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {

            // ── Шапка: профиль + stepper ────────────────────────────────────
            ProfileHeader(
                profile = state.profile,
                stages = state.stages,
                currentStageIndex = state.currentStageIndex,
            )

            // ── Панель классификации ─────────────────────────────────────────
            if (state.showClassificationPanel) {
                state.classificationResult?.let { result ->
                    ClassificationPanel(
                        result = result,
                        onConfirm = { viewModel.onEvent(TaskProfileContract.Event.ConfirmProfile(result.profile)) },
                        onSelectProfile = { viewModel.onEvent(TaskProfileContract.Event.ConfirmProfile(it)) },
                        onDismiss = { viewModel.onEvent(TaskProfileContract.Event.DismissClassificationPanel) },
                    )
                }
                HorizontalDivider()
            }

            // ── Список сообщений ─────────────────────────────────────────────
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item { Spacer(modifier = Modifier.height(4.dp)) }

                if (state.messages.isEmpty() && !state.isClassifying && !state.isLoading) {
                    item { EmptyState(profileConfirmed = state.isProfileConfirmed) }
                }

                items(state.messages) { msg ->
                    if (msg.isSystemNotice) {
                        InvalidTransitionBubble(content = msg.content)
                    } else {
                        ChatBubble(message = msg)
                    }
                }

                if (state.isClassifying) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Определяю тип задачи...", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }

                if (state.isLoading) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Отвечаю...", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }

                item { Spacer(modifier = Modifier.height(4.dp)) }
            }

            // ── Панель артефакта ─────────────────────────────────────────────
            if (state.showArtifactPanel) {
                ArtifactPanel(
                    pendingArtifact = state.pendingArtifact,
                    approveLabel = state.approveButtonLabel,
                    onArtifactChange = { viewModel.onEvent(TaskProfileContract.Event.ArtifactChanged(it)) },
                    onApprove = { viewModel.onEvent(TaskProfileContract.Event.ApproveArtifact) },
                    onDismiss = { viewModel.onEvent(TaskProfileContract.Event.DismissArtifactPanel) },
                )
                HorizontalDivider()
            }

            // ── Баннер ошибки с кнопкой Retry ───────────────────────────────
            val errorMsg = state.errorMessage
            if (errorMsg != null) {
                ErrorBanner(
                    message = errorMsg,
                    canRetry = state.retryAction != null,
                    onRetry = { viewModel.onEvent(TaskProfileContract.Event.Retry) },
                    onDismiss = { viewModel.onEvent(TaskProfileContract.Event.DismissError) },
                )
            }

            // ── Панель ввода ─────────────────────────────────────────────────
            InputBar(
                text = state.inputText,
                isLoading = state.isLoading || state.isClassifying,
                profileConfirmed = state.isProfileConfirmed,
                currentStage = state.currentStage,
                onTextChange = { viewModel.onEvent(TaskProfileContract.Event.InputChanged(it)) },
                onSend = { viewModel.onEvent(TaskProfileContract.Event.SendMessage) },
                onShowArtifact = { viewModel.onEvent(TaskProfileContract.Event.ShowArtifactPanel) },
                onTryJump = { viewModel.onEvent(TaskProfileContract.Event.TryInvalidTransition) },
                onNewTask = { viewModel.onEvent(TaskProfileContract.Event.NewTask) },
            )
        }

    }
}

// ── Шапка профиля + stepper ──────────────────────────────────────────────────

@Composable
private fun ProfileHeader(
    profile: TaskProfile?,
    stages: ImmutableList<ProfileStage>,
    currentStageIndex: Int,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        if (profile == null) {
            Text(
                text = "День 15 — Контролируемые переходы",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "Введите задачу — агент определит профиль",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Text(
                        text = profile.displayName,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
                stages.getOrNull(currentStageIndex)?.let { stage ->
                    Text(
                        text = stage.actionHint,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            // Stepper
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                stages.forEachIndexed { index, stage ->
                    val isPast = index < currentStageIndex
                    val isCurrent = index == currentStageIndex
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = when {
                            isCurrent -> MaterialTheme.colorScheme.primary
                            isPast -> MaterialTheme.colorScheme.secondaryContainer
                            else -> MaterialTheme.colorScheme.surfaceVariant
                        },
                    ) {
                        Text(
                            text = if (isPast) "✓ ${stage.label}" else stage.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = when {
                                isCurrent -> MaterialTheme.colorScheme.onPrimary
                                isPast -> MaterialTheme.colorScheme.onSecondaryContainer
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                    }
                    if (index < stages.lastIndex) {
                        Text("→", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
    HorizontalDivider()
}

// ── Панель классификации ─────────────────────────────────────────────────────

@Composable
private fun ClassificationPanel(
    result: ru.nikitaluga.aichallenge.domain.model.ClassificationResult,
    onConfirm: () -> Unit,
    onSelectProfile: (TaskProfile) -> Unit,
    onDismiss: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "Определил профиль задачи",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "Профиль: ${result.profile.displayName}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "Стадии: ${result.profile.stages.joinToString(" → ") { it.label }}",
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                text = "Причина: ${result.reason}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onConfirm) { Text("✓ Подтвердить") }
                TextButton(onClick = onDismiss) { Text("Отмена") }
            }
            Text(
                text = "Или выберите профиль вручную:",
                style = MaterialTheme.typography.labelSmall,
            )
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                TaskProfile.entries.forEach { profile ->
                    val isSelected = profile == result.profile
                    if (isSelected) {
                        Button(
                            onClick = { onSelectProfile(profile) },
                            modifier = Modifier.height(32.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp),
                        ) {
                            Text(profile.displayName, style = MaterialTheme.typography.labelSmall)
                        }
                    } else {
                        OutlinedButton(
                            onClick = { onSelectProfile(profile) },
                            modifier = Modifier.height(32.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp),
                        ) {
                            Text(profile.displayName, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }
}

// ── Сообщения ────────────────────────────────────────────────────────────────

@Composable
private fun ChatBubble(message: TaskProfileContract.DisplayMessage) {
    if (message.role == "system") return
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
                color = if (isUser) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun InvalidTransitionBubble(content: String) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        shape = RoundedCornerShape(8.dp),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = content,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun EmptyState(profileConfirmed: Boolean) {
    Box(
        modifier = Modifier.fillMaxWidth().padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = if (profileConfirmed)
                "Напишите сообщение чтобы начать диалог"
            else
                "Опишите задачу — агент определит профиль и стадии",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

// ── Панель артефакта ─────────────────────────────────────────────────────────

@Composable
private fun ArtifactPanel(
    pendingArtifact: String,
    approveLabel: String,
    onArtifactChange: (String) -> Unit,
    onApprove: () -> Unit,
    onDismiss: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = "Артефакт стадии (отредактируйте при необходимости):",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
        )
        OutlinedTextField(
            value = pendingArtifact,
            onValueChange = onArtifactChange,
            modifier = Modifier.fillMaxWidth(),
            minLines = 3,
            maxLines = 6,
            placeholder = { Text("Содержимое артефакта...") },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onApprove, enabled = pendingArtifact.isNotBlank()) {
                Text("✓ $approveLabel")
            }
            TextButton(onClick = onDismiss) {
                Text("✗ Продолжить диалог")
            }
        }
    }
}

// ── Баннер ошибки ────────────────────────────────────────────────────────────

@Composable
private fun ErrorBanner(
    message: String,
    canRetry: Boolean,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        shape = RoundedCornerShape(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f),
            )
            if (canRetry) {
                Button(
                    onClick = onRetry,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                ) { Text("Повторить", style = MaterialTheme.typography.labelSmall) }
            }
            TextButton(onClick = onDismiss) {
                Text("✕", color = MaterialTheme.colorScheme.onErrorContainer)
            }
        }
    }
}

// ── Панель ввода ─────────────────────────────────────────────────────────────

@Composable
private fun InputBar(
    text: String,
    isLoading: Boolean,
    profileConfirmed: Boolean,
    currentStage: ProfileStage,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    onShowArtifact: () -> Unit,
    onTryJump: () -> Unit,
    onNewTask: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
        // Кнопки действий (показываются когда профиль подтверждён и не DONE)
        if (profileConfirmed && currentStage != ProfileStage.DONE) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                OutlinedButton(
                    onClick = onShowArtifact,
                    enabled = !isLoading,
                    modifier = Modifier.weight(1f).height(36.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp),
                ) {
                    Text("✓ Готово к переходу", style = MaterialTheme.typography.labelSmall)
                }
                OutlinedButton(
                    onClick = onTryJump,
                    enabled = !isLoading,
                    modifier = Modifier.height(36.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp, MaterialTheme.colorScheme.error,
                    ),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp),
                ) {
                    Text("⛔ Прыжок", style = MaterialTheme.typography.labelSmall)
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = text,
                onValueChange = onTextChange,
                modifier = Modifier.weight(1f),
                placeholder = {
                    Text(
                        if (profileConfirmed) "Сообщение..."
                        else "Опишите задачу...",
                    )
                },
                maxLines = 4,
                enabled = !isLoading,
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Button(
                    onClick = onSend,
                    enabled = text.isNotBlank() && !isLoading,
                ) { Text("→") }
                TextButton(onClick = onNewTask, enabled = !isLoading) {
                    Text(
                        "Сброс",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}