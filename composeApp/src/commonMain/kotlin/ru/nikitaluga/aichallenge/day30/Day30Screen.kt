package ru.nikitaluga.aichallenge.day30

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun Day30Screen(viewModel: Day30ViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val listState = rememberLazyListState()

    LaunchedEffect(state.error) {
        state.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.onEvent(Day30Contract.Event.DismissError)
        }
    }

    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) {
            listState.animateScrollToItem(state.messages.size - 1)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { Spacer(Modifier.height(4.dp)) }

            // ── Заголовок ───────────────────────────────────────────────────
            item {
                Text(
                    text = "День 30 — Локальная LLM как сервис",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
                Text(
                    text = "Ollama как приватный HTTP-сервис: health check, rate limiting, сетевой доступ",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // ── Service Dashboard ────────────────────────────────────────────
            item {
                ServiceDashboard(
                    state = state,
                    onRefresh = { viewModel.onEvent(Day30Contract.Event.RefreshHealth) },
                )
            }

            // ── Разделитель ─────────────────────────────────────────────────
            item {
                HorizontalDivider()
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Чат",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // ── Сообщения чата ───────────────────────────────────────────────
            if (state.messages.isEmpty()) {
                item {
                    Text(
                        text = "Начните диалог с локальной LLM",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
            }

            items(state.messages) { entry ->
                ChatBubble(entry)
            }

            if (state.isLoading) {
                item {
                    Row(
                        modifier = Modifier.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Text(
                            text = "Генерирую ответ...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // ── Ввод + отправка ──────────────────────────────────────────────
            item {
                ChatInputRow(state = state, viewModel = viewModel)
                Spacer(Modifier.height(16.dp))
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

// ─── ServiceDashboard ─────────────────────────────────────────────────────────

@Composable
private fun ServiceDashboard(
    state: Day30Contract.State,
    onRefresh: () -> Unit,
) {
    val containerColor = when (state.healthStatus) {
        Day30Contract.HealthStatus.Ok -> MaterialTheme.colorScheme.primaryContainer
        Day30Contract.HealthStatus.Unavailable -> MaterialTheme.colorScheme.errorContainer
        Day30Contract.HealthStatus.Loading -> MaterialTheme.colorScheme.surfaceVariant
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    when (state.healthStatus) {
                        Day30Contract.HealthStatus.Loading ->
                            CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 2.dp)
                        Day30Contract.HealthStatus.Ok ->
                            Box(
                                modifier = Modifier.size(12.dp).background(Color(0xFF4CAF50), CircleShape)
                            )
                        Day30Contract.HealthStatus.Unavailable ->
                            Box(
                                modifier = Modifier.size(12.dp).background(MaterialTheme.colorScheme.error, CircleShape)
                            )
                    }
                    Text(
                        text = when (state.healthStatus) {
                            Day30Contract.HealthStatus.Loading -> "Проверка..."
                            Day30Contract.HealthStatus.Ok -> "Ollama online"
                            Day30Contract.HealthStatus.Unavailable -> "Ollama недоступна"
                        },
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                    )
                }
                FilledTonalButton(
                    onClick = onRefresh,
                    enabled = !state.isRefreshingHealth,
                ) {
                    Text("↻ Refresh", style = MaterialTheme.typography.labelSmall)
                }
            }

            if (state.ollamaUrl.isNotEmpty()) {
                Text(
                    text = "URL: ${state.ollamaUrl}",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                )
            }

            if (state.models.isNotEmpty()) {
                Text(
                    text = "Модели: ${state.models.joinToString(", ")}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (state.healthStatus == Day30Contract.HealthStatus.Ok) {
                Text(
                    text = "Active: ${state.activeRequests}/${state.maxConcurrent}",
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            if (state.healthStatus == Day30Contract.HealthStatus.Unavailable) {
                Spacer(Modifier.height(4.dp))
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = "Для доступа по сети (ngrok):",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        listOf(
                            "1. brew install ngrok",
                            "2. ngrok http 11434",
                            "3. Установи ENV: OLLAMA_BASE_URL=<url>",
                            "4. Перезапусти сервер",
                        ).forEach { step ->
                            Text(
                                text = step,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

// ─── ChatBubble ───────────────────────────────────────────────────────────────

@Composable
private fun ChatBubble(entry: Day30Contract.ChatEntry) {
    val isUser = entry.role == "user"
    val alignment = if (isUser) Alignment.End else Alignment.Start
    val containerColor = if (isUser)
        MaterialTheme.colorScheme.primaryContainer
    else
        MaterialTheme.colorScheme.surfaceVariant

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = alignment,
    ) {
        Text(
            text = if (isUser) "Вы" else "LLM",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        Card(
            modifier = Modifier.fillMaxWidth(0.85f),
            colors = CardDefaults.cardColors(containerColor = containerColor),
        ) {
            Text(
                text = entry.content,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(8.dp),
            )
        }
    }
}

// ─── ChatInputRow ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatInputRow(
    state: Day30Contract.State,
    viewModel: Day30ViewModel,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        // Model selector
        if (state.models.isNotEmpty()) {
            var expanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = it },
            ) {
                OutlinedTextField(
                    value = state.selectedModel,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Модель") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                    modifier = Modifier.menuAnchor().fillMaxWidth(),
                    textStyle = MaterialTheme.typography.bodySmall,
                    singleLine = true,
                )
                ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    state.models.forEach { model ->
                        DropdownMenuItem(
                            text = { Text(model, style = MaterialTheme.typography.bodySmall) },
                            onClick = {
                                viewModel.onEvent(Day30Contract.Event.SelectModel(model))
                                expanded = false
                            },
                        )
                    }
                }
            }
        }

        // Input + buttons
        OutlinedTextField(
            value = state.inputText,
            onValueChange = { viewModel.onEvent(Day30Contract.Event.InputChanged(it)) },
            label = { Text("Сообщение") },
            modifier = Modifier.fillMaxWidth(),
            maxLines = 3,
            enabled = !state.isLoading,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { viewModel.onEvent(Day30Contract.Event.SendMessage) },
                enabled = state.inputText.isNotBlank() && !state.isLoading &&
                    state.healthStatus == Day30Contract.HealthStatus.Ok,
                modifier = Modifier.weight(1f),
            ) {
                if (state.isLoading) {
                    CircularProgressIndicator(modifier = Modifier.width(18.dp).height(18.dp), strokeWidth = 2.dp)
                } else {
                    Text("Отправить")
                }
            }
            if (state.messages.isNotEmpty()) {
                OutlinedButton(onClick = { viewModel.onEvent(Day30Contract.Event.ClearChat) }) {
                    Text("Очистить")
                }
            }
        }
    }
}
