package ru.nikitaluga.aichallenge.day12indirectinjection

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Snackbar
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun Day12IndirectInjectionScreen(viewModel: Day12IndirectInjectionViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                Effect.ScrollToResult -> {
                    val count = listState.layoutInfo.totalItemsCount
                    if (count > 0) listState.animateScrollToItem(count - 1)
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {

            // ── Header ─────────────────────────────────────────────────────────
            item {
                Text(
                    "Indirect Injection — День 12",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "Payload спрятан внутри данных (email, документ, веб-страница, код). Агент читает данные и выполняет задачу — но payload пытается перехватить управление.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // ── Vector selector ────────────────────────────────────────────────
            item {
                Text(
                    "Вектор атаки",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    VectorType.entries.forEach { type ->
                        ToggleChip(
                            label = type.label,
                            selected = state.vectorType == type,
                            enabled = !state.isLoading,
                            modifier = Modifier.weight(1f),
                            onClick = { viewModel.onEvent(Event.SelectVector(type)) },
                        )
                    }
                }
                Text(
                    state.vectorType.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            // ── Defense selector ───────────────────────────────────────────────
            item {
                Text(
                    "Защита",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    DefenseMode.entries.forEach { mode ->
                        ToggleChip(
                            label = mode.label,
                            selected = state.defenseMode == mode,
                            enabled = !state.isLoading,
                            modifier = Modifier.weight(1f),
                            onClick = { viewModel.onEvent(Event.SelectDefense(mode)) },
                        )
                    }
                }
            }

            // ── Run button ─────────────────────────────────────────────────────
            item {
                Button(
                    onClick = { viewModel.onEvent(Event.RunAttack) },
                    enabled = !state.isLoading,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (state.isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                        Text("  Атакую…", style = MaterialTheme.typography.labelMedium)
                    } else {
                        Text("Атаковать", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }

            // ── Result ─────────────────────────────────────────────────────────
            state.result?.let { result ->
                item {
                    HorizontalDivider()
                    Spacer(Modifier.height(4.dp))
                    IndirectAttackResultCard(
                        result = result,
                        onClear = { viewModel.onEvent(Event.Clear) },
                    )
                }
            }

            item { Spacer(Modifier.height(80.dp)) }
        }

        if (state.error != null) {
            Snackbar(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(12.dp),
                action = {
                    TextButton(onClick = { viewModel.onEvent(Event.DismissError) }) { Text("OK") }
                },
            ) {
                Text(state.error ?: "", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

// ── Private composables ───────────────────────────────────────────────────────

@Composable
private fun ToggleChip(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    if (selected) {
        Button(
            onClick = onClick,
            enabled = enabled,
            modifier = modifier,
            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp),
        ) {
            Text(label, style = MaterialTheme.typography.labelSmall)
        }
    } else {
        OutlinedButton(
            onClick = onClick,
            enabled = enabled,
            modifier = modifier,
            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp),
        ) {
            Text(label, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun IndirectAttackResultCard(result: AttackResult, onClear: () -> Unit) {
    val breached = result.verdict == "BREACHED"
    val verdictColor = if (breached) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    val verdictText = if (breached) "BREACHED" else "HELD"

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {

        // Verdict + clear
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Card(
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(containerColor = verdictColor),
            ) {
                Text(
                    "$verdictText  [${result.vectorType.label} / ${result.defenseMode.label}]",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (breached) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onPrimary,
                )
            }
            TextButton(onClick = onClear) {
                Text("Очистить", style = MaterialTheme.typography.labelSmall)
            }
        }

        // Judge reasoning
        Text(
            result.judgeReasoning,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // Agent output
        LabeledCodeCard(
            label = "Вывод агента:",
            content = result.agentOutput,
            containerColor = if (breached) MaterialTheme.colorScheme.errorContainer
            else MaterialTheme.colorScheme.surfaceVariant,
            contentColor = if (breached) MaterialTheme.colorScheme.onErrorContainer
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // Side-by-side: original vs sanitized
        if (result.defenseMode != DefenseMode.NONE && result.sanitizedContent.isNotBlank()) {
            SideBySideCards(
                leftLabel = "Оригинал (с payload)",
                leftContent = result.visibleContent + "\n\n[payload hidden above]",
                rightLabel = "После санации",
                rightContent = result.sanitizedContent,
            )
        }

        // Hidden payload
        ExpandableCard(
            label = "Скрытый payload (${result.vectorType.description}):",
            content = result.hiddenPayload,
            dangerColor = true,
        )
    }
}

@Composable
private fun LabeledCodeCard(
    label: String,
    content: String,
    containerColor: androidx.compose.ui.graphics.Color,
    contentColor: androidx.compose.ui.graphics.Color,
) {
    Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
    ) {
        Text(
            content,
            modifier = Modifier.padding(10.dp),
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = contentColor,
        )
    }
}

@Composable
private fun SideBySideCards(
    leftLabel: String,
    leftContent: String,
    rightLabel: String,
    rightContent: String,
) {
    Text(
        "Контент до / после санации:",
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
    )
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(leftLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
            ) {
                Text(
                    leftContent.take(300),
                    modifier = Modifier.padding(8.dp),
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = MaterialTheme.typography.bodySmall.fontSize * 0.85f),
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(rightLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            ) {
                Text(
                    rightContent.take(300),
                    modifier = Modifier.padding(8.dp),
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = MaterialTheme.typography.bodySmall.fontSize * 0.85f),
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
    }
}

@Composable
private fun ExpandableCard(
    label: String,
    content: String,
    dangerColor: Boolean,
) {
    var expanded by remember { mutableStateOf(false) }
    val containerColor = if (dangerColor) MaterialTheme.colorScheme.errorContainer
    else MaterialTheme.colorScheme.surfaceVariant
    val contentColor = if (dangerColor) MaterialTheme.colorScheme.onErrorContainer
    else MaterialTheme.colorScheme.onSurfaceVariant

    TextButton(
        onClick = { expanded = !expanded },
        contentPadding = PaddingValues(0.dp),
    ) {
        Text(
            if (expanded) "▼ $label" else "▶ $label",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = if (dangerColor) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
        )
    }
    if (expanded) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(containerColor = containerColor),
        ) {
            Text(
                content,
                modifier = Modifier.padding(10.dp),
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = contentColor,
            )
        }
    }
}
