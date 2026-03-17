package ru.nikitaluga.aichallenge.rag

import androidx.compose.animation.AnimatedVisibility
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
import ru.nikitaluga.aichallenge.domain.model.RagChunkResult
import ru.nikitaluga.aichallenge.domain.model.RagIndexStats
import ru.nikitaluga.aichallenge.domain.model.SampleChunkInfo
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

                RagContract.RagTab.COMPARE -> {
                    RagCompareTab(
                        stats = state.stats,
                        compareStrategy = state.compareStrategy,
                        onStrategyChange = { viewModel.onEvent(RagContract.Event.CompareStrategyChanged(it)) },
                        onRefresh = { viewModel.onEvent(RagContract.Event.LoadStats) },
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
    stats: RagIndexStats?,
    compareStrategy: ChunkingStrategy,
    onStrategyChange: (ChunkingStrategy) -> Unit,
    onRefresh: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        if (stats == null || !stats.hasIndex) {
            Text(
                "Индекс не создан. Перейдите на вкладку Чат и нажмите «Переиндексировать».",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Column
        }

        // Strategy selector
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(ChunkingStrategy.FIXED, ChunkingStrategy.STRUCTURAL).forEach { strategy ->
                val selected = compareStrategy == strategy
                if (selected) {
                    Button(onClick = { onStrategyChange(strategy) }) {
                        Text(strategy.displayName, style = MaterialTheme.typography.labelSmall)
                    }
                } else {
                    OutlinedButton(onClick = { onStrategyChange(strategy) }) {
                        Text(strategy.displayName, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            TextButton(onClick = onRefresh) {
                Text("Обновить", style = MaterialTheme.typography.labelSmall)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Stats for selected strategy
        val (count, avgSize, samples) = when (compareStrategy) {
            ChunkingStrategy.FIXED -> Triple(stats.fixedChunks, stats.avgFixedSize, stats.sampleFixed)
            ChunkingStrategy.STRUCTURAL -> Triple(stats.structuralChunks, stats.avgStructuralSize, stats.sampleStructural)
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    "${compareStrategy.displayName} chunking",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text("Чанков: $count", style = MaterialTheme.typography.bodySmall)
                Text("Средний размер: $avgSize слов", style = MaterialTheme.typography.bodySmall)
                Text("Модель: ${stats.model}", style = MaterialTheme.typography.bodySmall)
                Text("Chunk size: ${stats.chunkSize} / Overlap: ${stats.overlap}", style = MaterialTheme.typography.bodySmall)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Comparison note
        if (compareStrategy == ChunkingStrategy.FIXED) {
            Text(
                "Fixed: равные по размеру блоки с overlap. Простая реализация, не учитывает структуру документа.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Text(
                "Structural: блоки по заголовкам (Markdown) / функциям (Kotlin). Сохраняет семантическую связность.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            "Примеры чанков (первые ${samples.size}):",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.height(4.dp))

        samples.forEachIndexed { idx, sample ->
            SampleChunkCard(index = idx + 1, sample = sample)
            Spacer(modifier = Modifier.height(6.dp))
        }
    }
}

@Composable
private fun SampleChunkCard(index: Int, sample: SampleChunkInfo) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    "#$index ${sample.source}",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                    overflow = TextOverflow.Ellipsis,
                    maxLines = 1,
                )
                sample.section?.let {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        it,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                sample.textPreview,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
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
