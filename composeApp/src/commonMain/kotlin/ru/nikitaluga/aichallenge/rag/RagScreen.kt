package ru.nikitaluga.aichallenge.rag

import androidx.compose.animation.AnimatedVisibility
import kotlinx.collections.immutable.ImmutableList
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.PrimaryTabRow
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
import ru.nikitaluga.aichallenge.domain.model.ChunkingStrategy
import ru.nikitaluga.aichallenge.domain.model.FilterStats
import ru.nikitaluga.aichallenge.domain.model.RagChunkResult
import ru.nikitaluga.aichallenge.domain.model.RagIndexStats
import ru.nikitaluga.aichallenge.domain.model.RagTripleCompareResult
import kotlin.math.roundToInt

@Composable
fun RagScreen(viewModel: RagViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel.effects) {
        viewModel.effects.collect { effect ->
            when (effect) {
                RagContract.Effect.ScrollToBottom ->
                    if (state.messages.isNotEmpty()) listState.animateScrollToItem(state.messages.lastIndex)
            }
        }
    }

    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.onEvent(RagContract.Event.DismissError)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {

            // ── Top bar: tab switcher + index status ─────────────────────────
            RagTopBar(
                selectedTab = state.tab,
                stats = state.stats,
                isIndexing = state.isIndexing,
                onTabSelected = { viewModel.onEvent(RagContract.Event.TabSelected(it)) },
            )

            when (state.tab) {
                RagContract.RagTab.CHAT -> {
                    // ── Settings card ────────────────────────────────────────
                    RagSettingsCard(
                        chunkSize = state.chunkSize,
                        overlap = state.overlap,
                        topK = state.topK,
                        activeStrategy = state.activeStrategy,
                        isIndexing = state.isIndexing,
                        indexMessage = state.indexMessage,
                        onChunkSizeChange = { viewModel.onEvent(RagContract.Event.ChunkSizeChanged(it)) },
                        onOverlapChange = { viewModel.onEvent(RagContract.Event.OverlapChanged(it)) },
                        onTopKChange = { viewModel.onEvent(RagContract.Event.TopKChanged(it)) },
                        onStrategyChange = { viewModel.onEvent(RagContract.Event.StrategyChanged(it)) },
                        onBuildIndex = { viewModel.onEvent(RagContract.Event.BuildIndex) },
                    )

                    HorizontalDivider()

                    // ── Chat messages ────────────────────────────────────────
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        item { Spacer(modifier = Modifier.height(4.dp)) }
                        items(state.messages) { msg ->
                            RagChatBubble(message = msg)
                        }
                        if (state.isLoading) {
                            item { RagLoadingRow() }
                        }
                        item { Spacer(modifier = Modifier.height(4.dp)) }
                    }

                    // ── Input bar ────────────────────────────────────────────
                    RagInputBar(
                        text = state.inputText,
                        isLoading = state.isLoading,
                        onTextChange = { viewModel.onEvent(RagContract.Event.InputChanged(it)) },
                        onSend = { viewModel.onEvent(RagContract.Event.SendMessage) },
                        onClear = { viewModel.onEvent(RagContract.Event.ClearHistory) },
                    )
                }

                RagContract.RagTab.DAY24 -> {
                    RagDay24Tab(
                        results = state.day24Results,
                        isRunning = state.isDay24Running,
                        currentIndex = state.day24CurrentIndex,
                        onRunTest = { viewModel.onEvent(RagContract.Event.RunDay24Test) },
                    )
                }

                RagContract.RagTab.COMPARE -> {
                    RagCompareTab(
                        compareInput = state.compareInput,
                        compareResult = state.compareResult,
                        isComparing = state.isComparing,
                        threshold = state.threshold,
                        topKBefore = state.topKBefore,
                        rewriteEnabled = state.rewriteEnabled,
                        tripleCompareResult = state.tripleCompareResult,
                        isEnhancedComparing = state.isEnhancedComparing,
                        onInputChange = { viewModel.onEvent(RagContract.Event.CompareInputChanged(it)) },
                        onRunCompare = { viewModel.onEvent(RagContract.Event.RunCompare) },
                        onRunEnhancedCompare = { viewModel.onEvent(RagContract.Event.RunEnhancedCompare) },
                        onSelectQuestion = { viewModel.onEvent(RagContract.Event.SelectControlQuestion(it)) },
                        onSelectEnhancedQuestion = { viewModel.onEvent(RagContract.Event.SelectEnhancedQuestion(it)) },
                        onThresholdChange = { viewModel.onEvent(RagContract.Event.ThresholdChanged(it)) },
                        onTopKBeforeChange = { viewModel.onEvent(RagContract.Event.TopKBeforeChanged(it)) },
                        onRewriteToggle = { viewModel.onEvent(RagContract.Event.RewriteToggled(it)) },
                    )
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

// ── Top bar ───────────────────────────────────────────────────────────────────

@Composable
private fun RagTopBar(
    selectedTab: RagContract.RagTab,
    stats: RagIndexStats?,
    isIndexing: Boolean,
    onTabSelected: (RagContract.RagTab) -> Unit,
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PrimaryTabRow(
                selectedTabIndex = selectedTab.ordinal,
                modifier = Modifier.weight(1f),
            ) {
                Tab(
                    selected = selectedTab == RagContract.RagTab.CHAT,
                    onClick = { onTabSelected(RagContract.RagTab.CHAT) },
                    text = { Text("Чат") },
                )
                Tab(
                    selected = selectedTab == RagContract.RagTab.COMPARE,
                    onClick = { onTabSelected(RagContract.RagTab.COMPARE) },
                    text = { Text("Сравнение") },
                )
                Tab(
                    selected = selectedTab == RagContract.RagTab.DAY24,
                    onClick = { onTabSelected(RagContract.RagTab.DAY24) },
                    text = { Text("День 24") },
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Index status badge
            when {
                isIndexing -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Индексация...", style = MaterialTheme.typography.labelSmall)
                    }
                }
                stats == null || !stats.hasIndex -> {
                    Text(
                        "Нет индекса",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                else -> {
                    Text(
                        "Индекс: ${stats.totalChunks} чанков",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

// ── Settings card ─────────────────────────────────────────────────────────────

@Composable
private fun RagSettingsCard(
    chunkSize: Int,
    overlap: Int,
    topK: Int,
    activeStrategy: ChunkingStrategy,
    isIndexing: Boolean,
    indexMessage: String?,
    onChunkSizeChange: (Int) -> Unit,
    onOverlapChange: (Int) -> Unit,
    onTopKChange: (Int) -> Unit,
    onStrategyChange: (ChunkingStrategy) -> Unit,
    onBuildIndex: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Настройки RAG", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                TextButton(onClick = { expanded = !expanded }) {
                    Text(if (expanded) "Скрыть" else "Показать", style = MaterialTheme.typography.labelSmall)
                }
            }

            AnimatedVisibility(visible = expanded) {
                Column {
                    // Strategy toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            "Стратегия: ",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.align(Alignment.CenterVertically),
                        )
                        listOf(ChunkingStrategy.FIXED, ChunkingStrategy.STRUCTURAL).forEach { strategy ->
                            val selected = activeStrategy == strategy
                            if (selected) {
                                Button(
                                    onClick = { onStrategyChange(strategy) },
                                    modifier = Modifier.height(32.dp),
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp),
                                ) { Text(strategy.displayName, style = MaterialTheme.typography.labelSmall) }
                            } else {
                                OutlinedButton(
                                    onClick = { onStrategyChange(strategy) },
                                    modifier = Modifier.height(32.dp),
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp),
                                ) { Text(strategy.displayName, style = MaterialTheme.typography.labelSmall) }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Chunk size slider
                    RagSlider(
                        label = "Chunk size",
                        value = chunkSize,
                        valueRange = 100f..1000f,
                        steps = 17,
                        onValueChange = { onChunkSizeChange(it) },
                    )

                    // Overlap slider
                    RagSlider(
                        label = "Overlap",
                        value = overlap,
                        valueRange = 0f..200f,
                        steps = 19,
                        onValueChange = { onOverlapChange(it) },
                    )

                    // Top-K slider
                    RagSlider(
                        label = "Top-K",
                        value = topK,
                        valueRange = 1f..10f,
                        steps = 8,
                        onValueChange = { onTopKChange(it) },
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (isIndexing) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Индексирование...", style = MaterialTheme.typography.bodySmall)
                            }
                        } else {
                            Button(
                                onClick = onBuildIndex,
                                enabled = !isIndexing,
                            ) {
                                Text("Переиндексировать", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }

                    indexMessage?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RagSlider(
    label: String,
    value: Int,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    onValueChange: (Int) -> Unit,
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(label, style = MaterialTheme.typography.bodySmall)
            Text("$value", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.roundToInt()) },
            valueRange = valueRange,
            steps = steps,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

// ── Compare tab ───────────────────────────────────────────────────────────────

@Composable
private fun RagCompareTab(
    compareInput: String,
    compareResult: ru.nikitaluga.aichallenge.domain.model.RagCompareResult?,
    isComparing: Boolean,
    threshold: Float,
    topKBefore: Int,
    rewriteEnabled: Boolean,
    tripleCompareResult: RagTripleCompareResult?,
    isEnhancedComparing: Boolean,
    onInputChange: (String) -> Unit,
    onRunCompare: () -> Unit,
    onRunEnhancedCompare: () -> Unit,
    onSelectQuestion: (String) -> Unit,
    onSelectEnhancedQuestion: (String) -> Unit,
    onThresholdChange: (Float) -> Unit,
    onTopKBeforeChange: (Int) -> Unit,
    onRewriteToggle: (Boolean) -> Unit,
) {
    var showEnhanced by remember { mutableStateOf(true) }

    Column(modifier = Modifier.fillMaxSize()) {
        // ── Mode toggle ──────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (!showEnhanced) {
                Button(
                    onClick = { showEnhanced = false },
                    modifier = Modifier.height(32.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp),
                ) { Text("RAG vs NoRAG", style = MaterialTheme.typography.labelSmall) }
            } else {
                OutlinedButton(
                    onClick = { showEnhanced = false },
                    modifier = Modifier.height(32.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp),
                ) { Text("RAG vs NoRAG", style = MaterialTheme.typography.labelSmall) }
            }
            if (showEnhanced) {
                Button(
                    onClick = { showEnhanced = true },
                    modifier = Modifier.height(32.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp),
                ) { Text("День 23: 3 режима", style = MaterialTheme.typography.labelSmall) }
            } else {
                OutlinedButton(
                    onClick = { showEnhanced = true },
                    modifier = Modifier.height(32.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp),
                ) { Text("День 23: 3 режима", style = MaterialTheme.typography.labelSmall) }
            }
        }

        // ── Control questions chips ──────────────────────────────────────────
        Text(
            "Контрольные вопросы:",
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(start = 12.dp, top = 4.dp, bottom = 4.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        androidx.compose.foundation.lazy.LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(RagContract.CONTROL_QUESTIONS) { question ->
                androidx.compose.material3.SuggestionChip(
                    onClick = { if (showEnhanced) onSelectEnhancedQuestion(question) else onSelectQuestion(question) },
                    label = {
                        Text(
                            question.take(35) + if (question.length > 35) "…" else "",
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                        )
                    },
                )
            }
        }

        if (showEnhanced) {
            // ── Enhanced filter settings ─────────────────────────────────────
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    Text(
                        "Настройки фильтрации (День 23)",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("Threshold", style = MaterialTheme.typography.bodySmall)
                        Text(
                            "%.2f".format(threshold),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Slider(
                        value = threshold,
                        onValueChange = onThresholdChange,
                        valueRange = 0f..1f,
                        steps = 19,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    RagSlider(
                        label = "topK-before",
                        value = topKBefore,
                        valueRange = 5f..50f,
                        steps = 8,
                        onValueChange = onTopKBeforeChange,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("Query Rewrite (LLM)", style = MaterialTheme.typography.bodySmall)
                        androidx.compose.material3.Switch(
                            checked = rewriteEnabled,
                            onCheckedChange = onRewriteToggle,
                        )
                    }
                }
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

        // ── Results area ────────────────────────────────────────────────────
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp),
        ) {
            if (showEnhanced) {
                // ── Enhanced 3-column compare ────────────────────────────────
                when {
                    isEnhancedComparing -> {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                if (rewriteEnabled) "Rewrite → Filter → RAG × 3..." else "Filter → RAG × 3...",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }

                    tripleCompareResult != null -> {
                        RagTripleCompare(result = tripleCompareResult)
                    }

                    else -> {
                        Text(
                            "Выберите вопрос или введите свой для тройного сравнения.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 16.dp),
                        )
                    }
                }
            } else {
                // ── Classic 2-column compare ─────────────────────────────────
                when {
                    isComparing -> {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("RAG vs no-RAG...", style = MaterialTheme.typography.bodySmall)
                        }
                    }

                    compareResult != null -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Card(
                                modifier = Modifier.weight(1f),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                ),
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text(
                                        "С RAG",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(compareResult.ragAnswer, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                            Card(
                                modifier = Modifier.weight(1f),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer,
                                ),
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text(
                                        "Без RAG",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(compareResult.noRagAnswer, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }

                        if (compareResult.usedChunks.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                "Источники (RAG):",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            compareResult.usedChunks.forEach { chunk ->
                                RagChunkBadge(chunk = chunk)
                                Spacer(modifier = Modifier.height(3.dp))
                            }
                        }
                    }

                    else -> {
                        Text(
                            "Выберите контрольный вопрос или введите свой.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 16.dp),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
        }

        // ── Input bar ────────────────────────────────────────────────────────
        RagInputBar(
            text = compareInput,
            isLoading = isComparing || isEnhancedComparing,
            onTextChange = onInputChange,
            onSend = if (showEnhanced) onRunEnhancedCompare else onRunCompare,
            onClear = { onInputChange("") },
        )
    }
}

@Composable
private fun RagAnswerCard(
    title: String,
    answer: String,
    color: androidx.compose.ui.graphics.Color,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = color),
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(title, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(6.dp))
            Text(answer, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun RagTripleCompare(result: RagTripleCompareResult) {
    val stats = result.filterStats

    // Filter funnel
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(
                "Воронка фильтрации",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.secondary,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Кандидаты: ${stats.candidatesBefore} → прошли порог ${
                    "%.2f".format(stats.threshold)
                }: ${stats.candidatesAfter}",
                style = MaterialTheme.typography.bodySmall,
            )
            stats.rewrittenQuery?.let { rewritten ->
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Rewrite: \"$rewritten\"",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
    }

    // 3 answer cards — stacked vertically
    val enhancedTitle = "RAG+Filter" + if (stats.rewrittenQuery != null) "+Rewrite" else ""
    RagAnswerCard(title = "Без RAG", answer = result.noRagAnswer, color = MaterialTheme.colorScheme.errorContainer)
    Spacer(modifier = Modifier.height(6.dp))
    RagAnswerCard(title = "RAG базовый", answer = result.ragBaselineAnswer, color = MaterialTheme.colorScheme.primaryContainer)
    Spacer(modifier = Modifier.height(6.dp))
    RagAnswerCard(title = enhancedTitle, answer = result.ragEnhancedAnswer, color = MaterialTheme.colorScheme.tertiaryContainer)

    // Enhanced chunks with scores
    if (result.enhancedChunks.isNotEmpty()) {
        Text(
            "Источники (RAG+Filter, ${result.enhancedChunks.size} чанков):",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 4.dp),
        )
        result.enhancedChunks.forEach { chunk ->
            RagChunkBadge(chunk = chunk)
            Spacer(modifier = Modifier.height(3.dp))
        }
    }
}

// ── Chat Bubble ───────────────────────────────────────────────────────────────

@Composable
private fun RagChatBubble(message: RagContract.RagMessage) {
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

        // Chunk badges for assistant messages
        if (!isUser && message.usedChunks.isNotEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            Column(
                modifier = Modifier.fillMaxWidth(0.85f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                message.usedChunks.forEach { chunk ->
                    RagChunkBadge(chunk = chunk)
                }
            }
        }
    }
}

@Composable
private fun RagChunkBadge(chunk: RagChunkResult) {
    val scorePercent = (chunk.score * 100).roundToInt()
    val label = buildString {
        append("📄 ")
        append(chunk.source.substringAfterLast("/").take(25))
        chunk.section?.let { append(" · $it".take(20)) }
        append("  $scorePercent%")
    }

    Surface(
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// ── Day 24 Tab ────────────────────────────────────────────────────────────────

@Composable
private fun RagDay24Tab(
    results: kotlinx.collections.immutable.ImmutableList<RagContract.Day24QuestionResult>,
    isRunning: Boolean,
    currentIndex: Int,
    onRunTest: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Header
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    "День 24: Цитаты, источники и анти-галлюцинации",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.secondary,
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    "Проверка RAG v2: каждый ответ должен содержать источники и цитаты. При слабом контексте — ответ \"не знаю\".",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Spacer(modifier = Modifier.height(8.dp))
                if (isRunning) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Вопрос $currentIndex / ${RagContract.CONTROL_QUESTIONS.size}...",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                } else {
                    Button(onClick = onRunTest, enabled = !isRunning) {
                        Text("Запустить 10 вопросов", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }

        HorizontalDivider()

        if (results.isEmpty() && !isRunning) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "Нажмите «Запустить 10 вопросов» для тестирования.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            // Summary row
            if (results.isNotEmpty()) {
                val withSources = results.count { it.hasSources }
                val withCitations = results.count { it.hasCitations }
                val belowThreshold = results.count { it.belowThreshold }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (withSources == results.size) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.errorContainer,
                    ) {
                        Text(
                            "Источники: $withSources/${results.size}",
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (withCitations == results.size) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.errorContainer,
                    ) {
                        Text(
                            "Цитаты: $withCitations/${results.size}",
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    if (belowThreshold > 0) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                        ) {
                            Text(
                                "Не знаю: $belowThreshold",
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item { Spacer(modifier = Modifier.height(4.dp)) }
                items(results) { result ->
                    RagDay24ResultCard(result = result)
                }
                item { Spacer(modifier = Modifier.height(8.dp)) }
            }
        }
    }
}

@Composable
private fun RagDay24ResultCard(result: RagContract.Day24QuestionResult) {
    val borderColor = when {
        result.belowThreshold -> MaterialTheme.colorScheme.tertiary
        result.hasSources && result.hasCitations -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.error
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(8.dp),
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            // Question
            Text(
                result.question,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(4.dp))

            // Status badges
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (result.belowThreshold) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                    ) {
                        Text(
                            "⚠ Ниже порога",
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                        )
                    }
                }
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = if (result.hasSources) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.errorContainer,
                ) {
                    Text(
                        "${if (result.hasSources) "✓" else "✗"} Источники: ${result.sources.size}",
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = if (result.hasCitations) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.errorContainer,
                ) {
                    Text(
                        "${if (result.hasCitations) "✓" else "✗"} Цитаты: ${result.citations.size}",
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Answer (truncated)
            Text(
                result.answer.take(300) + if (result.answer.length > 300) "…" else "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // First citation if any
            result.citations.firstOrNull()?.let { citation ->
                Spacer(modifier = Modifier.height(6.dp))
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = MaterialTheme.colorScheme.surface,
                ) {
                    Text(
                        "\"${citation.text.take(150)}\"",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

// ── Loading indicator ─────────────────────────────────────────────────────────

@Composable
private fun RagLoadingRow() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        Spacer(modifier = Modifier.width(8.dp))
        Text("RAG поиск + LLM...", style = MaterialTheme.typography.bodySmall)
    }
}

// ── Input Bar ─────────────────────────────────────────────────────────────────

@Composable
private fun RagInputBar(
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
            placeholder = { Text("Спроси о проекте...") },
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
