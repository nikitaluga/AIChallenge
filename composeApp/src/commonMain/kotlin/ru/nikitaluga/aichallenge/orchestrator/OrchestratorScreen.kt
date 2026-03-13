package ru.nikitaluga.aichallenge.orchestrator

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
import ru.nikitaluga.aichallenge.domain.model.McpServerInfo
import ru.nikitaluga.aichallenge.domain.model.McpToolSummary
import ru.nikitaluga.aichallenge.domain.model.OrchestratorMessage
import ru.nikitaluga.aichallenge.domain.model.OrchestratorToolStep

@Composable
fun OrchestratorScreen(viewModel: OrchestratorViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel.effects) {
        viewModel.effects.collect { effect ->
            when (effect) {
                OrchestratorContract.Effect.ScrollToBottom ->
                    if (state.messages.isNotEmpty()) listState.animateScrollToItem(state.messages.lastIndex)
            }
        }
    }

    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.onEvent(OrchestratorContract.Event.DismissError)
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
                    OrchestratorChatBubble(message = msg)
                }
                if (state.isLoading) {
                    item { OrchestratorLoadingRow() }
                }
                item { Spacer(modifier = Modifier.height(4.dp)) }
            }

            // ── Input bar ────────────────────────────────────────────────────
            OrchestratorInputBar(
                text = state.inputText,
                isLoading = state.isLoading,
                onTextChange = { viewModel.onEvent(OrchestratorContract.Event.InputChanged(it)) },
                onSend = { viewModel.onEvent(OrchestratorContract.Event.SendMessage) },
                onClear = { viewModel.onEvent(OrchestratorContract.Event.ClearHistory) },
            )

            HorizontalDivider()

            // ── Servers panel ─────────────────────────────────────────────────
            ServersPanel(
                servers = state.servers,
                isDiscovering = state.isDiscovering,
                onRefresh = { viewModel.onEvent(OrchestratorContract.Event.RefreshServers) },
                onSelectServer = { viewModel.onEvent(OrchestratorContract.Event.SelectServer(it)) },
            )
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }

    val selectedServer = state.selectedServer
    if (selectedServer != null) {
        ServerToolsDialog(
            server = selectedServer,
            onDismiss = { viewModel.onEvent(OrchestratorContract.Event.SelectServer(null)) },
        )
    }
}

// ── Servers Panel ─────────────────────────────────────────────────────────────

@Composable
private fun ServersPanel(
    servers: ImmutableList<McpServerInfo>,
    isDiscovering: Boolean,
    onRefresh: () -> Unit,
    onSelectServer: (McpServerInfo) -> Unit,
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
            if (isDiscovering) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            } else {
                Text(
                    text = "Серверы (${servers.size})",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
            }
            TextButton(onClick = onRefresh, enabled = !isDiscovering) {
                Text("Обновить", style = MaterialTheme.typography.labelSmall)
            }
        }

        if (!isDiscovering) {
            servers.forEach { server ->
                Spacer(modifier = Modifier.height(6.dp))
                ServerCard(server = server, onClick = { onSelectServer(server) })
            }
            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}

// ── Server Card ───────────────────────────────────────────────────────────────

@Composable
private fun ServerCard(server: McpServerInfo, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = server.emoji,
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = server.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (server.isOnline) {
                Text(
                    text = "\u2713 ${server.toolCount} инструментов",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            } else {
                Text(
                    text = "\u2717 недоступен",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

// ── Chat Bubble ───────────────────────────────────────────────────────────────

@Composable
private fun OrchestratorChatBubble(message: OrchestratorMessage) {
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

        if (!isUser && message.toolSteps.isNotEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            Column(
                modifier = Modifier.fillMaxWidth(0.85f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                message.toolSteps.forEach { step ->
                    OrchestratorStepBadge(step = step)
                }
            }
        }
    }
}

// ── Orchestrator Step Badge ───────────────────────────────────────────────────

@Composable
private fun OrchestratorStepBadge(step: OrchestratorToolStep) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = "${step.serverEmoji} ${step.serverId} \u00B7 ${step.toolName}",
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}

// ── Loading indicator ─────────────────────────────────────────────────────────

@Composable
private fun OrchestratorLoadingRow() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        Spacer(modifier = Modifier.width(8.dp))
        Text("Оркестратор выполняет запрос...", style = MaterialTheme.typography.bodySmall)
    }
}

// ── Server Tools Dialog ───────────────────────────────────────────────────────

@Composable
private fun ServerToolsDialog(server: McpServerInfo, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "${server.emoji} ${server.displayName}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (server.tools.isEmpty()) {
                    Text(
                        text = "Нет данных об инструментах",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    server.tools.forEach { tool ->
                        ToolRow(tool = tool)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Закрыть") }
        },
    )
}

@Composable
private fun ToolRow(tool: McpToolSummary) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(
            text = tool.name,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = tool.description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Surface(
            shape = RoundedCornerShape(4.dp),
            color = MaterialTheme.colorScheme.secondaryContainer,
        ) {
            Text(
                text = tool.example,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                style = MaterialTheme.typography.labelSmall,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}

// ── Input Bar ─────────────────────────────────────────────────────────────────

@Composable
private fun OrchestratorInputBar(
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
            placeholder = { Text("Погода в Москве и сохрани заметку...") },
            maxLines = 4,
            enabled = !isLoading,
        )
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Button(
                onClick = onSend,
                enabled = text.isNotBlank() && !isLoading,
            ) { Text("\u2192") }
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
