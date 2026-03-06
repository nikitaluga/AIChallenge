package ru.nikitaluga.aichallenge.invariants

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ru.nikitaluga.aichallenge.domain.model.Invariant
import ru.nikitaluga.aichallenge.domain.model.InvariantChatMessage
import ru.nikitaluga.aichallenge.domain.model.InvariantViolation

@Composable
fun InvariantsScreen(viewModel: InvariantsViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel.effects) {
        viewModel.effects.collect { effect ->
            when (effect) {
                InvariantsContract.Effect.ScrollToBottom ->
                    if (state.messages.isNotEmpty()) listState.animateScrollToItem(state.messages.lastIndex)
            }
        }
    }

    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.onEvent(InvariantsContract.Event.DismissError)
        }
    }

    if (state.showDialog) {
        InvariantDialog(
            editing = state.editingInvariant,
            onSave = { viewModel.onEvent(InvariantsContract.Event.SaveInvariant(it)) },
            onDismiss = { viewModel.onEvent(InvariantsContract.Event.DismissDialog) },
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // ── Панель инвариантов ──────────────────────────────────────────
            InvariantsPanel(
                invariants = state.invariants,
                onToggle = { viewModel.onEvent(InvariantsContract.Event.ToggleInvariant(it)) },
                onEdit = { viewModel.onEvent(InvariantsContract.Event.EditInvariant(it)) },
                onDelete = { viewModel.onEvent(InvariantsContract.Event.DeleteInvariant(it)) },
                onAdd = { viewModel.onEvent(InvariantsContract.Event.AddInvariant) },
            )

            HorizontalDivider()

            // ── Список сообщений ────────────────────────────────────────────
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item { Spacer(modifier = Modifier.height(4.dp)) }
                items(state.messages) { msg ->
                    ChatBubble(message = msg)
                }
                if (state.isLoading) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(8.dp),
                            horizontalArrangement = Arrangement.Start,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Отправка + валидация...", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                item { Spacer(modifier = Modifier.height(4.dp)) }
            }

            // ── Панель ввода ────────────────────────────────────────────────
            InputBar(
                text = state.inputText,
                isLoading = state.isLoading,
                onTextChange = { viewModel.onEvent(InvariantsContract.Event.InputChanged(it)) },
                onSend = { viewModel.onEvent(InvariantsContract.Event.SendMessage) },
                onClear = { viewModel.onEvent(InvariantsContract.Event.ClearHistory) },
            )
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

// ── Панель инвариантов ──────────────────────────────────────────────────────

@Composable
private fun InvariantsPanel(
    invariants: List<Invariant>,
    onToggle: (String) -> Unit,
    onEdit: (String) -> Unit,
    onDelete: (String) -> Unit,
    onAdd: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "Инварианты",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            TextButton(onClick = onAdd) { Text("+ Добавить") }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            invariants.forEach { inv ->
                InvariantChip(
                    invariant = inv,
                    onToggle = { onToggle(inv.id) },
                    onEdit = { onEdit(inv.id) },
                    onDelete = { onDelete(inv.id) },
                )
            }
        }
    }
}

@Composable
private fun InvariantChip(
    invariant: Invariant,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    var showMenu by remember { mutableStateOf(false) }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        FilterChip(
            selected = invariant.enabled,
            onClick = onToggle,
            label = { Text(invariant.name, style = MaterialTheme.typography.labelSmall) },
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = MaterialTheme.colorScheme.errorContainer,
                selectedLabelColor = MaterialTheme.colorScheme.onErrorContainer,
            ),
        )
        Row {
            TextButton(onClick = onEdit, modifier = Modifier.height(24.dp).padding(0.dp)) {
                Text("ред", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
            TextButton(onClick = onDelete, modifier = Modifier.height(24.dp).padding(0.dp)) {
                Text("удл", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

// ── Сообщение чата ──────────────────────────────────────────────────────────

@Composable
private fun ChatBubble(message: InvariantChatMessage) {
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
                color = if (isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (message.violations.isNotEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            ViolationsBanner(violations = message.violations, wasRetried = message.wasRetried)
        }
    }
}

@Composable
private fun ViolationsBanner(violations: List<InvariantViolation>, wasRetried: Boolean) {
    Surface(
        modifier = Modifier.fillMaxWidth(0.85f),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.errorContainer,
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(
                text = if (wasRetried) "Нарушение обнаружено — ответ исправлен:" else "Нарушение инварианта:",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            violations.forEach { violation ->
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "[${violation.invariantName}] ${violation.explanation}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        }
    }
}

// ── Диалог создания/редактирования инварианта ───────────────────────────────

@Composable
private fun InvariantDialog(
    editing: Invariant?,
    onSave: (Invariant) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember(editing) { mutableStateOf(editing?.name ?: "") }
    var rule by remember(editing) { mutableStateOf(editing?.rule ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (editing == null) "Новый инвариант" else "Редактировать инвариант") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Название (напр. StackOnly)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = rule,
                    onValueChange = { rule = it },
                    label = { Text("Правило") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val inv = editing?.copy(name = name.trim(), rule = rule.trim())
                        ?: Invariant(
                            id = "custom_${name.lowercase().replace(" ", "_")}_${System.currentTimeMillis()}",
                            name = name.trim(),
                            rule = rule.trim(),
                        )
                    onSave(inv)
                },
                enabled = name.isNotBlank() && rule.isNotBlank(),
            ) { Text("Сохранить") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        },
    )
}

// ── Поле ввода ──────────────────────────────────────────────────────────────

@Composable
private fun InputBar(
    text: String,
    isLoading: Boolean,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    onClear: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(8.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = onTextChange,
            modifier = Modifier.weight(1f),
            placeholder = { Text("Сообщение...") },
            maxLines = 4,
            enabled = !isLoading,
        )
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Button(
                onClick = onSend,
                enabled = text.isNotBlank() && !isLoading,
            ) { Text("→") }
            TextButton(onClick = onClear, enabled = !isLoading) {
                Text("Сброс", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
