package ru.nikitaluga.aichallenge.day28

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ru.nikitaluga.aichallenge.domain.model.LocalRagChatResult
import ru.nikitaluga.aichallenge.domain.model.LocalRagStats

/**
 * День 28 — Полностью локальный RAG-пайплайн.
 *
 * - Локальный индекс: nomic-embed-text (Ollama) → local_rag_index.json
 * - Локальная генерация: Ollama chat
 * - Side-by-side сравнение с облачным RAG (OpenAI embeddings + GPT)
 */
@Composable
fun Day28Screen(viewModel: Day28ViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scrollState = rememberScrollState()

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                Day28Contract.Effect.ScrollToResults -> scrollState.animateScrollTo(scrollState.maxValue)
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // ── Header ────────────────────────────────────────────────────────
            Text(
                text = "День 28 — Local LLM + RAG",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )

            // ── Index status card ─────────────────────────────────────────────
            IndexStatusCard(
                stats = state.localStats,
                isIndexing = state.isIndexing,
                selectedModel = state.selectedModel,
                availableModels = state.availableModels,
                onBuildIndex = { viewModel.onEvent(Day28Contract.Event.BuildIndex) },
                onModelSelected = { viewModel.onEvent(Day28Contract.Event.ModelSelected(it)) },
            )

            // ── Sample questions ──────────────────────────────────────────────
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(horizontal = 2.dp),
            ) {
                items(Day28Contract.SAMPLE_QUESTIONS) { question ->
                    SuggestionChip(
                        onClick = { viewModel.onEvent(Day28Contract.Event.QueryChanged(question)) },
                        enabled = !state.isLoading,
                        label = {
                            Text(
                                text = question,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                    )
                }
            }

            // ── Query input ───────────────────────────────────────────────────
            OutlinedTextField(
                value = state.query,
                onValueChange = { viewModel.onEvent(Day28Contract.Event.QueryChanged(it)) },
                placeholder = { Text("Введите вопрос…") },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 4,
                shape = RoundedCornerShape(12.dp),
                enabled = !state.isLoading,
            )

            Button(
                onClick = { viewModel.onEvent(Day28Contract.Event.Compare) },
                modifier = Modifier.fillMaxWidth(),
                enabled = state.query.isNotBlank() && !state.isLoading &&
                    (state.localStats?.hasIndex == true),
            ) {
                if (state.isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("Сравниваю…")
                } else {
                    Text("Сравнить Local vs Cloud")
                }
            }

            // ── Side-by-side results ──────────────────────────────────────────
            if (state.localResult != null || state.cloudResult != null) {
                HorizontalDivider()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ResultCard(
                        title = "Local RAG",
                        subtitle = "nomic-embed-text + ${state.selectedModel.substringBefore(":")}",
                        result = state.localResult,
                        isLoading = state.isLoading,
                        modifier = Modifier.weight(1f),
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    )
                    ResultCard(
                        title = "Cloud RAG",
                        subtitle = "text-embedding-3-small + GPT",
                        result = state.cloudResult,
                        isLoading = state.isLoading,
                        modifier = Modifier.weight(1f),
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
        }

        // ── Error snackbar ────────────────────────────────────────────────────
        if (state.error != null) {
            Snackbar(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp),
                action = {
                    TextButton(onClick = { viewModel.onEvent(Day28Contract.Event.DismissError) }) {
                        Text("OK")
                    }
                },
            ) {
                Text(state.error ?: "")
            }
        }
    }
}

@Composable
private fun IndexStatusCard(
    stats: LocalRagStats?,
    isIndexing: Boolean,
    selectedModel: String,
    availableModels: List<String>,
    onBuildIndex: () -> Unit,
    onModelSelected: (String) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = if (stats?.hasIndex == true) "Локальный индекс готов" else "Локальный индекс отсутствует",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (stats?.hasIndex == true)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.error,
                    )
                    if (stats?.hasIndex == true) {
                        Text(
                            text = "${stats.chunkCount} чанков · ${stats.model}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                FilledTonalButton(
                    onClick = onBuildIndex,
                    enabled = !isIndexing,
                ) {
                    if (isIndexing) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(6.dp))
                        Text("Индексирую…")
                    } else {
                        Text(if (stats?.hasIndex == true) "Переиндексировать" else "Построить индекс")
                    }
                }
            }

            // ── Model selector ────────────────────────────────────────────────
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Модель генерации:",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(end = 8.dp),
                )
                ModelDropdown(
                    models = availableModels,
                    selectedModel = selectedModel,
                    onSelect = onModelSelected,
                )
            }

            Text(
                text = "Эмбеддинги: nomic-embed-text (Ollama) → local_rag_index.json (768-dim)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ModelDropdown(
    models: List<String>,
    selectedModel: String,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(
            onClick = { expanded = true },
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
        ) {
            Text(
                text = selectedModel.substringBefore(":").take(14),
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
            )
        }
        androidx.compose.material3.DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            if (models.isEmpty()) {
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text("Нет доступных моделей") },
                    onClick = { expanded = false },
                )
            } else {
                models.forEach { model ->
                    androidx.compose.material3.DropdownMenuItem(
                        text = { Text(model) },
                        onClick = { onSelect(model); expanded = false },
                    )
                }
            }
        }
    }
}

@Composable
private fun ResultCard(
    title: String,
    subtitle: String,
    result: LocalRagChatResult?,
    isLoading: Boolean,
    modifier: Modifier = Modifier,
    containerColor: androidx.compose.ui.graphics.Color,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(text = title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            HorizontalDivider()

            when {
                isLoading && result == null -> {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    }
                }
                result != null -> {
                    // Latency badge
                    Text(
                        text = "${result.latencyMs} мс",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                    )

                    // Answer
                    Text(
                        text = result.answer,
                        style = MaterialTheme.typography.bodySmall,
                    )

                    // Sources
                    if (result.sources.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Источники:",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        result.sources.take(3).forEach { source ->
                            Text(
                                text = "• ${source.substringAfterLast('/')}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                else -> {
                    Text(
                        text = "Нет результата",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
