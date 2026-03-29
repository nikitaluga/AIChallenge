package ru.nikitaluga.aichallenge.day29

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ru.nikitaluga.aichallenge.domain.model.JudgeResult
import ru.nikitaluga.aichallenge.domain.model.LocalChatResult
import ru.nikitaluga.aichallenge.domain.model.OllamaOptions

@Composable
fun Day29Screen(viewModel: Day29ViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.error) {
        state.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.onEvent(Day29Contract.Event.DismissError)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { Spacer(Modifier.height(4.dp)) }

            // ── Заголовок ───────────────────────────────────────────────────
            item {
                Text(
                    text = "День 29 — Оптимизация LLM",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
                Text(
                    text = "Сравниваем baseline (стандартные параметры) с оптимизированной конфигурацией",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // ── Параметры: До и После (две карточки рядом) ──────────────────
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ParamsCard(
                        modifier = Modifier.weight(1f),
                        title = "ДО (baseline)",
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        model = state.beforeModel,
                        availableModels = state.availableModels,
                        options = state.beforeOptions,
                        systemPrompt = state.beforeSystemPrompt,
                        expanded = state.beforeExpanded,
                        onToggleExpand = { viewModel.onEvent(Day29Contract.Event.ToggleBeforeExpanded) },
                        onModelChanged = { viewModel.onEvent(Day29Contract.Event.BeforeModelChanged(it)) },
                        onOptionsChanged = { viewModel.onEvent(Day29Contract.Event.BeforeOptionsChanged(it)) },
                        onSystemPromptChanged = { viewModel.onEvent(Day29Contract.Event.BeforeSystemPromptChanged(it)) },
                    )
                    ParamsCard(
                        modifier = Modifier.weight(1f),
                        title = "ПОСЛЕ (optimized)",
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        model = state.afterModel,
                        availableModels = state.availableModels,
                        options = state.afterOptions,
                        systemPrompt = state.afterSystemPrompt,
                        expanded = state.afterExpanded,
                        onToggleExpand = { viewModel.onEvent(Day29Contract.Event.ToggleAfterExpanded) },
                        onModelChanged = { viewModel.onEvent(Day29Contract.Event.AfterModelChanged(it)) },
                        onOptionsChanged = { viewModel.onEvent(Day29Contract.Event.AfterOptionsChanged(it)) },
                        onSystemPromptChanged = { viewModel.onEvent(Day29Contract.Event.AfterSystemPromptChanged(it)) },
                    )
                }
            }

            // ── Примеры вопросов ────────────────────────────────────────────
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(Day29Contract.SAMPLE_QUERIES) { q ->
                        SuggestionChip(
                            onClick = { viewModel.onEvent(Day29Contract.Event.SelectQuery(q)) },
                            label = { Text(q, style = MaterialTheme.typography.labelSmall) },
                        )
                    }
                }
            }

            // ── Ввод запроса ────────────────────────────────────────────────
            item {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = { viewModel.onEvent(Day29Contract.Event.QueryChanged(it)) },
                    label = { Text("Запрос для тестирования") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3,
                )
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { viewModel.onEvent(Day29Contract.Event.RunBenchmark) },
                        enabled = state.query.isNotBlank() && !state.isRunning,
                        modifier = Modifier.weight(1f),
                    ) {
                        if (state.isRunning) {
                            CircularProgressIndicator(modifier = Modifier.width(18.dp).height(18.dp), strokeWidth = 2.dp)
                        } else {
                            Text("Запустить тест")
                        }
                    }
                    if (state.result != null) {
                        OutlinedButton(
                            onClick = { viewModel.onEvent(Day29Contract.Event.ClearResults) },
                        ) {
                            Text("Сбросить")
                        }
                    }
                }
            }

            // ── Результаты ──────────────────────────────────────────────────
            state.result?.let { result ->
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        ResultCard(
                            modifier = Modifier.weight(1f),
                            title = "ДО",
                            result = result.before,
                            scoreLabel = state.judgeResult?.let { "Оценка: ${it.scoreA}/5" },
                        )
                        ResultCard(
                            modifier = Modifier.weight(1f),
                            title = "ПОСЛЕ",
                            result = result.after,
                            scoreLabel = state.judgeResult?.let { "Оценка: ${it.scoreB}/5" },
                            highlight = true,
                        )
                    }
                }

                // ── LLM-Judge ───────────────────────────────────────────────
                item {
                    JudgeSection(
                        judgeResult = state.judgeResult,
                        isJudging = state.isJudging,
                        onRunJudge = { viewModel.onEvent(Day29Contract.Event.RunJudge) },
                    )
                }
            }

            item { Spacer(Modifier.height(16.dp)) }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

// ─── ParamsCard ───────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ParamsCard(
    modifier: Modifier = Modifier,
    title: String,
    containerColor: androidx.compose.ui.graphics.Color,
    model: String,
    availableModels: List<String>,
    options: OllamaOptions,
    systemPrompt: String,
    expanded: Boolean,
    onToggleExpand: () -> Unit,
    onModelChanged: (String) -> Unit,
    onOptionsChanged: (OllamaOptions) -> Unit,
    onSystemPromptChanged: (String) -> Unit,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = containerColor),
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onToggleExpand) {
                    Text(if (expanded) "Свернуть" else "Изменить", style = MaterialTheme.typography.labelSmall)
                }
            }

            // Always show current temp summary
            Text(
                text = "temp=${options.temperature} | ctx=${options.numCtx} | max=${if (options.numPredict == -1) "∞" else options.numPredict.toString()}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "Модель: $model",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            AnimatedVisibility(visible = expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {

                    // Model dropdown
                    if (availableModels.isNotEmpty()) {
                        var modelExpanded by remember { mutableStateOf(false) }
                        ExposedDropdownMenuBox(
                            expanded = modelExpanded,
                            onExpandedChange = { modelExpanded = it },
                        ) {
                            OutlinedTextField(
                                value = model,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Модель") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(modelExpanded) },
                                modifier = Modifier.menuAnchor().fillMaxWidth(),
                                textStyle = MaterialTheme.typography.bodySmall,
                            )
                            ExposedDropdownMenu(expanded = modelExpanded, onDismissRequest = { modelExpanded = false }) {
                                availableModels.forEach { m ->
                                    DropdownMenuItem(
                                        text = { Text(m, style = MaterialTheme.typography.bodySmall) },
                                        onClick = { onModelChanged(m); modelExpanded = false },
                                    )
                                }
                            }
                        }
                    }

                    // Temperature slider
                    Text("Temperature: ${"%.1f".format(options.temperature)}", style = MaterialTheme.typography.labelSmall)
                    Slider(
                        value = options.temperature,
                        onValueChange = { onOptionsChanged(options.copy(temperature = it)) },
                        valueRange = 0f..2f,
                        steps = 19,
                    )

                    // num_predict
                    OutlinedTextField(
                        value = if (options.numPredict == -1) "" else options.numPredict.toString(),
                        onValueChange = { v ->
                            val n = v.toIntOrNull() ?: -1
                            onOptionsChanged(options.copy(numPredict = n))
                        },
                        label = { Text("Max tokens (-1 = ∞)") },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        textStyle = MaterialTheme.typography.bodySmall,
                        singleLine = true,
                    )

                    // num_ctx
                    OutlinedTextField(
                        value = options.numCtx.toString(),
                        onValueChange = { v ->
                            val n = v.toIntOrNull() ?: options.numCtx
                            onOptionsChanged(options.copy(numCtx = n))
                        },
                        label = { Text("Context window") },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        textStyle = MaterialTheme.typography.bodySmall,
                        singleLine = true,
                    )

                    // top_p
                    OutlinedTextField(
                        value = options.topP.toString(),
                        onValueChange = { v ->
                            val f = v.toFloatOrNull() ?: options.topP
                            onOptionsChanged(options.copy(topP = f))
                        },
                        label = { Text("top_p") },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        textStyle = MaterialTheme.typography.bodySmall,
                        singleLine = true,
                    )

                    // top_k
                    OutlinedTextField(
                        value = options.topK.toString(),
                        onValueChange = { v ->
                            val n = v.toIntOrNull() ?: options.topK
                            onOptionsChanged(options.copy(topK = n))
                        },
                        label = { Text("top_k") },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        textStyle = MaterialTheme.typography.bodySmall,
                        singleLine = true,
                    )

                    // System prompt
                    OutlinedTextField(
                        value = systemPrompt,
                        onValueChange = onSystemPromptChanged,
                        label = { Text("System prompt") },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 4,
                        textStyle = MaterialTheme.typography.bodySmall,
                        placeholder = { Text("Пусто = нет system prompt", style = MaterialTheme.typography.bodySmall) },
                    )
                }
            }
        }
    }
}

// ─── ResultCard ───────────────────────────────────────────────────────────────

@Composable
private fun ResultCard(
    modifier: Modifier = Modifier,
    title: String,
    result: LocalChatResult,
    scoreLabel: String?,
    highlight: Boolean = false,
) {
    val containerColor = if (highlight)
        MaterialTheme.colorScheme.primaryContainer
    else
        MaterialTheme.colorScheme.surfaceVariant

    ElevatedCard(modifier = modifier, colors = CardDefaults.elevatedCardColors(containerColor = containerColor)) {
        Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(title, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                if (scoreLabel != null) {
                    Text(
                        scoreLabel,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Text(
                text = "${result.latencyMs} ms · ${result.backend}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = result.reply,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

// ─── JudgeSection ─────────────────────────────────────────────────────────────

@Composable
private fun JudgeSection(
    judgeResult: JudgeResult?,
    isJudging: Boolean,
    onRunJudge: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("LLM-Judge (GPT-4o-mini)", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                FilledTonalButton(
                    onClick = onRunJudge,
                    enabled = !isJudging,
                ) {
                    if (isJudging) {
                        CircularProgressIndicator(modifier = Modifier.width(16.dp).height(16.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Оценить")
                    }
                }
            }

            if (judgeResult != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    ScoreDisplay("ДО", judgeResult.scoreA)
                    ScoreDisplay("ПОСЛЕ", judgeResult.scoreB)
                }
                Text(
                    text = judgeResult.reasoning,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else if (!isJudging) {
                Text(
                    text = "Нажмите «Оценить» для автоматической оценки качества ответов",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ScoreDisplay(label: String, score: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            text = "$score/5",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = if (score >= 4) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = "★".repeat(score) + "☆".repeat(5 - score),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
