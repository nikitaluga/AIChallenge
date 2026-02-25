package ru.nikitaluga.aichallenge.reasoning

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlin.math.roundToInt

@Composable
fun ReasoningScreen(
    viewModel: ReasoningViewModel = viewModel { ReasoningViewModel() },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val difficultyInt = state.difficulty.roundToInt()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "Сложность задачи",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Уровень:",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = difficultyLabel(difficultyInt),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Slider(
                    value = state.difficulty,
                    onValueChange = { viewModel.onEvent(ReasoningContract.Event.DifficultyChanged(it)) },
                    valueRange = 1f..5f,
                    steps = 3,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !state.isGeneratingTask && !state.isSolving,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    repeat(5) { i ->
                        Text(
                            text = "${i + 1}",
                            fontSize = 12.sp,
                            color = if (difficultyInt == i + 1)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = if (difficultyInt == i + 1) FontWeight.Bold else FontWeight.Normal,
                        )
                    }
                }
            }
        }

        Button(
            onClick = { viewModel.onEvent(ReasoningContract.Event.GenerateTask) },
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.isGeneratingTask && !state.isSolving,
        ) {
            if (state.isGeneratingTask) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
                Spacer(Modifier.width(8.dp))
                Text("Генерирую задачу…")
            } else {
                Text("Сгенерировать задачу")
            }
        }

        if (state.isGeneratingTask || state.generatedTask != null) {
            Text(
                text = "Задача:",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            ) {
                if (state.isGeneratingTask) {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                            Text(
                                text = "Генерирую задачу сложности $difficultyInt…",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                    }
                } else if (state.generatedTask != null) {
                    Text(
                        text = state.generatedTask!!,
                        modifier = Modifier.padding(12.dp),
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
        }

        if (state.generatedTask != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = { viewModel.onEvent(ReasoningContract.Event.SolveAll) },
                    enabled = !state.isSolving,
                    modifier = Modifier.weight(1f),
                ) {
                    if (state.isSolving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(if (state.isSolving) "Решаю…" else "Решить всеми способами")
                }
                OutlinedButton(
                    onClick = { viewModel.onEvent(ReasoningContract.Event.Reset) },
                    enabled = !state.isSolving && !state.isGeneratingTask,
                ) {
                    Text("Сброс")
                }
            }
        }

        if (state.hasSolvingData) {
            MethodCard(title = "Способ 1: Прямой ответ", tag = "Без системного промпта — задача задаётся как есть", state = state.method1)
            MethodCard(title = "Способ 2: Пошагово", tag = "System: \"Решай пошагово, объясняя каждый шаг…\"", state = state.method2)
            Method3Card(state = state.method3)
            MethodCard(title = "Способ 4: Экспертная группа", tag = "Три роли: Аналитик + Инженер + Критик", state = state.method4)
        }

        if (state.hasComparisonData) {
            ComparisonAnalysisCard(state = state.comparison)
        }
    }
}

private fun difficultyLabel(level: Int): String = when (level) {
    1 -> "1 — Очень лёгкая"
    2 -> "2 — Лёгкая"
    3 -> "3 — Средняя"
    4 -> "4 — Сложная"
    5 -> "5 — Экспертная"
    else -> "$level"
}

@Composable
private fun MethodCard(title: String, tag: String, state: ReasoningContract.MethodState) {
    var expanded by remember { mutableStateOf(true) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Text(
                text = tag,
                fontSize = 11.sp,
                fontStyle = FontStyle.Italic,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 4.dp),
            )
            ResultBlock(state = state, expanded = expanded)
            if (state.result != null || state.error != null) {
                TextButton(onClick = { expanded = !expanded }, modifier = Modifier.fillMaxWidth()) {
                    Text(text = if (expanded) "▲ Свернуть" else "▼ Развернуть", fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun Method3Card(state: ReasoningContract.MethodState) {
    var resultExpanded by remember { mutableStateOf(true) }
    var promptExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("Способ 3: Сгенерированный промпт", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Text(
                text = "Шаг 1 — модель создаёт промпт; Шаг 2 — решает задачу с его помощью",
                fontSize = 11.sp,
                fontStyle = FontStyle.Italic,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 4.dp),
            )

            if (state.generatedPrompt != null) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Сгенерированный промпт",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { promptExpanded = !promptExpanded }) {
                        Text(text = if (promptExpanded) "▲ Скрыть" else "▼ Показать", fontSize = 11.sp)
                    }
                }
                if (promptExpanded) {
                    Text(
                        text = state.generatedPrompt,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.tertiaryContainer)
                            .padding(10.dp),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                Text(
                    text = "Решение по промпту:",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }

            ResultBlock(state = state, expanded = resultExpanded)
            if (state.result != null || state.error != null) {
                TextButton(onClick = { resultExpanded = !resultExpanded }, modifier = Modifier.fillMaxWidth()) {
                    Text(text = if (resultExpanded) "▲ Свернуть" else "▼ Развернуть", fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun ComparisonAnalysisCard(state: ReasoningContract.MethodState) {
    var expanded by remember { mutableStateOf(true) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "Сравнительный анализ",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Text(
                text = "Оценка по 6 критериям: точность, полнота, ясность, логика, креативность, лаконичность",
                fontSize = 11.sp,
                fontStyle = FontStyle.Italic,
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f),
                modifier = Modifier.padding(bottom = 8.dp),
            )

            when {
                state.isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                            Text(
                                text = "Анализирую ответы…",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                        }
                    }
                }

                state.error != null -> {
                    Text(
                        text = "Ошибка: ${state.error}",
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.errorContainer)
                            .padding(10.dp),
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }

                state.result != null -> {
                    Text(
                        text = state.result,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(10.dp),
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = if (expanded) Int.MAX_VALUE else 5,
                        overflow = if (expanded) TextOverflow.Clip else TextOverflow.Ellipsis,
                    )
                    TextButton(onClick = { expanded = !expanded }, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = if (expanded) "▲ Свернуть" else "▼ Развернуть",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ResultBlock(state: ReasoningContract.MethodState, expanded: Boolean) {
    when {
        state.isLoading -> {
            Box(
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Text(text = "Запрос к модели…", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        state.error != null -> {
            Text(
                text = "Ошибка: ${state.error}",
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(10.dp),
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
        }

        state.result != null -> {
            Text(
                text = state.result,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(10.dp),
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = if (expanded) Int.MAX_VALUE else 4,
                overflow = if (expanded) TextOverflow.Clip else TextOverflow.Ellipsis,
            )
        }
    }
}
