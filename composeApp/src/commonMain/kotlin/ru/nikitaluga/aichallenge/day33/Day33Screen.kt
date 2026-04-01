package ru.nikitaluga.aichallenge.day33

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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ru.nikitaluga.aichallenge.context.CtxIconSend

@Composable
fun Day33Screen(viewModel: Day33ViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                Effect.ScrollToBottom ->
                    if (state.messages.isNotEmpty()) listState.animateScrollToItem(state.messages.lastIndex)
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {

            // ── Toolbar ───────────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Поддержка", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    UserDropdown(
                        users = state.users,
                        selectedName = state.selectedUserName,
                        isLoading = state.isLoadingUsers,
                        onSelect = { user -> viewModel.onEvent(Event.UserSelected(user.id, user.name)) },
                    )
                }
                IconButton(onClick = { viewModel.onEvent(Event.ClearChat) }, enabled = !state.isLoading) {
                    Text("🗑", fontSize = 18.sp)
                }
            }

            // ── Ticket chips ──────────────────────────────────────────────────
            if (state.tickets.isNotEmpty() || state.isLoadingTickets) {
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    if (state.isLoadingTickets) {
                        item {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        }
                    } else {
                        items(state.tickets) { ticket ->
                            FilterChip(
                                selected = false,
                                onClick = { viewModel.onEvent(Event.TicketClicked(ticket)) },
                                label = {
                                    Text(
                                        text = "${statusEmoji(ticket.status)} ${ticket.id}",
                                        style = MaterialTheme.typography.labelSmall,
                                        maxLines = 1,
                                    )
                                },
                            )
                        }
                    }
                }
                HorizontalDivider()
            } else {
                HorizontalDivider()
            }

            // ── Messages ──────────────────────────────────────────────────────
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            ) {
                if (state.messages.isEmpty()) {
                    item {
                        Text(
                            text = if (state.selectedUserName.isEmpty()) "Выберите пользователя"
                            else "Привет, ${state.selectedUserName}! Чем могу помочь?",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(4.dp),
                        )
                    }
                }
                items(state.messages) { msg -> SupportChatBubble(msg) }
                if (state.isLoading) {
                    item { LoadingIndicator() }
                }
            }

            HorizontalDivider()

            // ── Suggested questions ───────────────────────────────────────────
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
            ) {
                items(state.suggestedQuestions) { question ->
                    SuggestionChip(
                        onClick = { viewModel.onEvent(Event.QuestionSelected(question)) },
                        enabled = !state.isLoading,
                        label = {
                            Text(
                                text = question,
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.widthIn(max = 180.dp),
                            )
                        },
                    )
                }
            }

            // ── Input bar ─────────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                OutlinedTextField(
                    value = state.inputText,
                    onValueChange = { viewModel.onEvent(Event.InputChanged(it)) },
                    placeholder = { Text("Введите вопрос…", style = MaterialTheme.typography.bodySmall) },
                    modifier = Modifier.weight(1f),
                    maxLines = 3,
                    shape = RoundedCornerShape(12.dp),
                    enabled = !state.isLoading,
                    textStyle = MaterialTheme.typography.bodySmall,
                )
                FilledIconButton(
                    onClick = { viewModel.onEvent(Event.SendMessage) },
                    enabled = state.inputText.isNotBlank() && !state.isLoading,
                    modifier = Modifier.size(40.dp),
                ) {
                    Icon(imageVector = CtxIconSend, contentDescription = "Отправить")
                }
            }
        }

        // ── Ticket dialog ─────────────────────────────────────────────────────
        val ticket = state.selectedTicket
        if (ticket != null) {
            AlertDialog(
                onDismissRequest = { viewModel.onEvent(Event.DismissTicketDialog) },
                title = {
                    Text("${statusEmoji(ticket.status)} ${ticket.id}", style = MaterialTheme.typography.titleMedium)
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(ticket.subject, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            text = "Статус: ${ticket.status}",
                            style = MaterialTheme.typography.labelMedium,
                            color = statusColor(ticket.status),
                        )
                        if (ticket.description.isNotBlank()) {
                            Spacer(Modifier.height(2.dp))
                            Text(ticket.description, style = MaterialTheme.typography.bodySmall)
                        }
                        if (ticket.createdAt.isNotBlank()) {
                            Text(
                                "Создан: ${ticket.createdAt}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { viewModel.onEvent(Event.DismissTicketDialog) }) {
                        Text("Закрыть")
                    }
                },
            )
        }

        // ── Error snackbar ────────────────────────────────────────────────────
        if (state.error != null) {
            Snackbar(
                modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp),
                action = {
                    TextButton(onClick = { viewModel.onEvent(Event.DismissError) }) { Text("OK") }
                },
            ) {
                Text(state.error ?: "", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

// ── Chat bubble ───────────────────────────────────────────────────────────────

@Composable
private fun SupportChatBubble(msg: ChatMsg) {
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
                topStart = 12.dp, topEnd = 12.dp,
                bottomStart = if (isUser) 12.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 12.dp,
            ),
        ) {
            Text(
                text = msg.content,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            )
        }
    }
}

@Composable
private fun LoadingIndicator() {
    Row(
        modifier = Modifier.padding(4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
        Text("Обрабатываю запрос...", style = MaterialTheme.typography.bodySmall)
    }
}

// ── User dropdown ─────────────────────────────────────────────────────────────

@Composable
private fun UserDropdown(
    users: List<UserItem>,
    selectedName: String,
    isLoading: Boolean,
    onSelect: (UserItem) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(
            onClick = { if (users.isNotEmpty()) expanded = true },
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
        ) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 2.dp)
            } else {
                Text(
                    text = selectedName.ifEmpty { "Выбрать..." },
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                )
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            users.forEach { user ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(user.name, style = MaterialTheme.typography.bodyMedium)
                            Text(user.plan, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        }
                    },
                    onClick = { onSelect(user); expanded = false },
                )
            }
        }
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

private fun statusEmoji(status: String) = when (status) {
    "open" -> "🔴"
    "in_progress" -> "🟡"
    "resolved" -> "🟢"
    else -> "⚪"
}

@Composable
private fun statusColor(status: String) = when (status) {
    "open" -> MaterialTheme.colorScheme.error
    "in_progress" -> MaterialTheme.colorScheme.tertiary
    "resolved" -> MaterialTheme.colorScheme.primary
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}
