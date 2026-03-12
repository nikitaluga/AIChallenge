package ru.nikitaluga.aichallenge.scheduler

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
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
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
import ru.nikitaluga.aichallenge.domain.model.ScheduleInfo
import ru.nikitaluga.aichallenge.domain.model.SchedulerChatMessage

@Composable
fun SchedulerScreen(viewModel: SchedulerViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel.effects) {
        viewModel.effects.collect { effect ->
            when (effect) {
                SchedulerContract.Effect.ScrollToBottom ->
                    if (state.messages.isNotEmpty()) listState.animateScrollToItem(state.messages.lastIndex)
            }
        }
    }

    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.onEvent(SchedulerContract.Event.DismissError)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {

            // ── Chat messages (takes all remaining space) ────────────────────
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item { Spacer(modifier = Modifier.height(4.dp)) }
                items(state.messages) { msg ->
                    SchedulerChatBubble(message = msg)
                }
                if (state.isLoading) {
                    item { SchedulerLoadingRow() }
                }
                item { Spacer(modifier = Modifier.height(4.dp)) }
            }

            // ── Input bar ────────────────────────────────────────────────────
            SchedulerInputBar(
                text = state.inputText,
                isLoading = state.isLoading,
                onTextChange = { viewModel.onEvent(SchedulerContract.Event.InputChanged(it)) },
                onSend = { viewModel.onEvent(SchedulerContract.Event.SendMessage) },
                onClear = { viewModel.onEvent(SchedulerContract.Event.ClearHistory) },
            )

            HorizontalDivider()

            // ── Active schedules panel ───────────────────────────────────────
            SchedulesPanel(
                schedules = state.schedules,
                onDelete = { viewModel.onEvent(SchedulerContract.Event.DeleteSchedule(it)) },
                onRefresh = { viewModel.onEvent(SchedulerContract.Event.RefreshSchedules) },
            )
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

// ── Schedules Panel ───────────────────────────────────────────────────────────

@Composable
private fun SchedulesPanel(
    schedules: List<ScheduleInfo>,
    onDelete: (String) -> Unit,
    onRefresh: () -> Unit,
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
            Text(
                text = if (schedules.isEmpty()) "Нет активных расписаний" else "Расписания (${schedules.size})",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            TextButton(onClick = onRefresh) {
                Text("Обновить", style = MaterialTheme.typography.labelSmall)
            }
        }

        if (schedules.isEmpty()) {
            Text(
                text = "Напишите агенту, например: «хочу отчёт по Москве каждый день в 15:00»",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        } else {
            schedules.forEach { schedule ->
                Spacer(modifier = Modifier.height(6.dp))
                ScheduleCard(schedule = schedule, onDelete = { onDelete(schedule.id) })
            }
            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}

// ── Schedule Card ─────────────────────────────────────────────────────────────

@Composable
private fun ScheduleCard(
    schedule: ScheduleInfo,
    onDelete: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${schedule.city} — ${schedule.hour.pad()}:${schedule.minute.pad()} ежедневно",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (schedule.lastReport != null) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Последний: ${schedule.lastReport}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                Text("×", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

// ── Chat Bubble ───────────────────────────────────────────────────────────────

@Composable
private fun SchedulerChatBubble(message: SchedulerChatMessage) {
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

        if (!isUser && message.toolCallMade && message.toolName != null) {
            Spacer(modifier = Modifier.height(4.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(0.85f),
                shape = RoundedCornerShape(6.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
            ) {
                Text(
                    text = "Tool: ${message.toolName}",
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
    }
}

// ── Loading indicator ─────────────────────────────────────────────────────────

@Composable
private fun SchedulerLoadingRow() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        Spacer(modifier = Modifier.width(8.dp))
        Text("Агент обрабатывает запрос...", style = MaterialTheme.typography.bodySmall)
    }
}

// ── Input Bar ─────────────────────────────────────────────────────────────────

@Composable
private fun SchedulerInputBar(
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
            placeholder = { Text("Хочу отчёт по Москве в 15:00...") },
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

private fun Int.pad() = toString().padStart(2, '0')
