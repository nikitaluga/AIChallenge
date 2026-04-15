package ru.nikitaluga.aichallenge.day36

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun Day36Screen(viewModel: Day36ViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                Effect.ScrollToBottom -> {
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
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {

            // ── Header ─────────────────────────────────────────────────────────
            item {
                Text(
                    "Reflection Agent",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "Агент итеративно улучшает ответ через самокритику (до 3 итераций)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // ── Query input ────────────────────────────────────────────────────
            item {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = { viewModel.onEvent(Event.QueryChanged(it)) },
                    label = { Text("Вопрос *", style = MaterialTheme.typography.bodySmall) },
                    placeholder = { Text("Задайте любой вопрос агенту", style = MaterialTheme.typography.bodySmall) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 5,
                    enabled = !state.isLoading,
                    shape = RoundedCornerShape(12.dp),
                )
                Spacer(Modifier.height(4.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(QUICK_QUERIES) { q ->
                        SuggestionChip(
                            onClick = { viewModel.onEvent(Event.QueryChanged(q)) },
                            enabled = !state.isLoading,
                            label = {
                                Text(
                                    q.take(28) + "…",
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            },
                        )
                    }
                }
            }

            // ── Rubric input ───────────────────────────────────────────────────
            item {
                OutlinedTextField(
                    value = state.rubric,
                    onValueChange = { viewModel.onEvent(Event.RubricChanged(it)) },
                    label = { Text("Критерий оценки (опционально)", style = MaterialTheme.typography.bodySmall) },
                    placeholder = { Text("Например: краткость и точность", style = MaterialTheme.typography.bodySmall) },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 2,
                    enabled = !state.isLoading,
                    shape = RoundedCornerShape(12.dp),
                )
                Spacer(Modifier.height(4.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(QUICK_RUBRICS) { r ->
                        SuggestionChip(
                            onClick = { viewModel.onEvent(Event.RubricChanged(r)) },
                            enabled = !state.isLoading,
                            label = { Text(r, style = MaterialTheme.typography.labelSmall) },
                        )
                    }
                }
            }

            // ── Buttons ────────────────────────────────────────────────────────
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { viewModel.onEvent(Event.Generate) },
                        enabled = state.query.isNotBlank() && !state.isLoading,
                        modifier = Modifier.weight(1f),
                    ) {
                        if (state.isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                            Text(" Думаю…", style = MaterialTheme.typography.labelMedium)
                        } else {
                            Text("Запустить рефлексию", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                    if (state.iterations.isNotEmpty() || state.finalAnswer != null) {
                        TextButton(onClick = { viewModel.onEvent(Event.Clear) }) {
                            Text("Очистить", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }

            // ── Iterations ─────────────────────────────────────────────────────
            if (state.iterations.isNotEmpty()) {
                item {
                    HorizontalDivider()
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Итерации самокритики",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                items(state.iterations) { iter ->
                    IterationCard(
                        iter = iter,
                        onToggle = { viewModel.onEvent(Event.ToggleIteration(iter.attempt)) },
                    )
                }
            }

            // ── Final answer ───────────────────────────────────────────────────
            state.finalAnswer?.let { answer ->
                item {
                    HorizontalDivider()
                    Spacer(Modifier.height(4.dp))
                    FinalAnswerCard(
                        answer = answer,
                        score = state.iterations.lastOrNull()?.score ?: 0,
                    )
                }
            }

            item { Spacer(Modifier.height(80.dp)) }
        }

        // ── Error snackbar ─────────────────────────────────────────────────────
        if (state.error != null) {
            Snackbar(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(12.dp),
                action = {
                    TextButton(onClick = { viewModel.onEvent(Event.DismissError) }) {
                        Text("OK")
                    }
                },
            ) {
                Text(state.error ?: "", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

// ── Private composables ───────────────────────────────────────────────────────

@Composable
private fun IterationCard(iter: IterationMsg, onToggle: () -> Unit) {
    Card(
        onClick = onToggle,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Итерация ${iter.attempt}",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ScoreBadge(iter.score)
                    Text(
                        if (iter.isExpanded) "▲" else "▼",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (iter.isExpanded) {
                Spacer(Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))

                Text(
                    "Черновик:",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(2.dp))
                Text(iter.draft, style = MaterialTheme.typography.bodySmall)

                Spacer(Modifier.height(8.dp))

                Text(
                    "Критика:",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    iter.critique,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            } else {
                Spacer(Modifier.height(4.dp))
                Text(
                    iter.critique.take(80) + if (iter.critique.length > 80) "…" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun FinalAnswerCard(answer: String, score: Int) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Финальный ответ",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                ScoreBadge(score)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                answer,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

@Composable
private fun ScoreBadge(score: Int) {
    val containerColor = when {
        score >= 4 -> MaterialTheme.colorScheme.primary
        score == 3 -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.error
    }
    val contentColor = when {
        score >= 4 -> MaterialTheme.colorScheme.onPrimary
        score == 3 -> MaterialTheme.colorScheme.onTertiary
        else -> MaterialTheme.colorScheme.onError
    }
    Card(
        shape = RoundedCornerShape(6.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
    ) {
        Text(
            "$score/5",
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = contentColor,
            fontWeight = FontWeight.Bold,
        )
    }
}
