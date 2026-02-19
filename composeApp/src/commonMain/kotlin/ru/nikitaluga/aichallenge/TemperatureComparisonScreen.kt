package ru.nikitaluga.aichallenge

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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import ru.nikitaluga.aichallenge.api.RouterAiApiService

// ---------------------------------------------------------------------------
// Constants
// ---------------------------------------------------------------------------

private const val TEMP_PROMPT =
    "Напиши краткий рассказ о путешествии во времени (3-4 предложения)."

private const val RUNS_PER_TEMP = 3

// ---------------------------------------------------------------------------
// Data model
// ---------------------------------------------------------------------------

private data class TemperatureGroup(
    val temperature: Double,
    val label: String,
    val description: String,
    val color: Color,
)

private val TEMPERATURE_GROUPS = listOf(
    TemperatureGroup(
        temperature = 0.0,
        label = "temperature = 0.0",
        description = "Минимальная креативность, максимальная детерминированность",
        color = Color(0xFF1565C0),
    ),
    TemperatureGroup(
        temperature = 0.7,
        label = "temperature = 0.7",
        description = "Стандартное значение, баланс точности и креативности",
        color = Color(0xFF2E7D32),
    ),
    TemperatureGroup(
        temperature = 1.2,
        label = "temperature = 1.2",
        description = "Высокая креативность и случайность",
        color = Color(0xFF6A1B9A),
    ),
)

// ---------------------------------------------------------------------------
// Screen
// ---------------------------------------------------------------------------

@Composable
fun TemperatureComparisonScreen() {
    val coroutineScope = rememberCoroutineScope()

    // 3 groups × 3 runs = 9 slots; null = not yet fetched
    val results = remember { mutableStateListOf<String?>().apply { repeat(9) { add(null) } } }
    val loadingStates = remember { mutableStateListOf<Boolean>().apply { repeat(9) { add(false) } } }
    var isRunningAll by remember { mutableStateOf(false) }
    var allDone by remember { mutableStateOf(false) }

    fun slotIndex(groupIdx: Int, runIdx: Int) = groupIdx * RUNS_PER_TEMP + runIdx

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // ── Header ────────────────────────────────────────────────────────
        item {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Исследование параметра temperature",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Запрос для всех тестов:",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                ),
            ) {
                Text(
                    text = TEMP_PROMPT,
                    modifier = Modifier.padding(12.dp),
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }

        // ── Run all button ─────────────────────────────────────────────────
        item {
            Button(
                onClick = {
                    coroutineScope.launch {
                        isRunningAll = true
                        allDone = false
                        repeat(9) { i ->
                            results[i] = null
                            loadingStates[i] = false
                        }
                        TEMPERATURE_GROUPS.forEachIndexed { groupIdx, group ->
                            repeat(RUNS_PER_TEMP) { runIdx ->
                                val slot = slotIndex(groupIdx, runIdx)
                                loadingStates[slot] = true
                                val service = RouterAiApiService()
                                results[slot] = runCatching {
                                    service.sendMessage(
                                        prompt = TEMP_PROMPT,
                                        temperature = group.temperature,
                                        maxTokens = 300,
                                    )
                                }.getOrElse { e -> "Ошибка: ${e.message}" }
                                loadingStates[slot] = false
                            }
                        }
                        isRunningAll = false
                        allDone = true
                    }
                },
                enabled = !isRunningAll,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (isRunningAll) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Выполняется… (${results.count { it != null }}/9)")
                } else {
                    Text("Запустить все тесты (9 запросов)")
                }
            }
        }

        // ── Temperature group cards ────────────────────────────────────────
        itemsIndexed(TEMPERATURE_GROUPS) { groupIdx, group ->
            TemperatureGroupCard(
                group = group,
                results = List(RUNS_PER_TEMP) { runIdx -> results[slotIndex(groupIdx, runIdx)] },
                loadingStates = List(RUNS_PER_TEMP) { runIdx -> loadingStates[slotIndex(groupIdx, runIdx)] },
            )
        }

        // ── Analysis section (shown after all results arrive) ──────────────
        if (allDone) {
            item {
                AnalysisCard()
            }
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }
    }
}

// ---------------------------------------------------------------------------
// Temperature group card
// ---------------------------------------------------------------------------

@Composable
private fun TemperatureGroupCard(
    group: TemperatureGroup,
    results: List<String?>,
    loadingStates: List<Boolean>,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {

            // ── Group header ───────────────────────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(group.color.copy(alpha = 0.12f))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            ) {
                Text(
                    text = group.label,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = group.color,
                )
                Text(
                    text = group.description,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // ── Individual runs ────────────────────────────────────────────
            results.forEachIndexed { runIdx, result ->
                if (runIdx > 0) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top,
                ) {
                    Text(
                        text = "# ${runIdx + 1}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = group.color,
                        modifier = Modifier
                            .width(36.dp)
                            .padding(top = 4.dp),
                    )
                    when {
                        loadingStates[runIdx] -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(4.dp),
                                contentAlignment = Alignment.CenterStart,
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = group.color,
                                )
                            }
                        }

                        result != null -> {
                            Text(
                                text = result,
                                fontSize = 13.sp,
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .padding(8.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        else -> {
                            Text(
                                text = "—",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(4.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Analysis card
// ---------------------------------------------------------------------------

@Composable
private fun AnalysisCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "Выводы по результатам",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.2f))

            AnalysisRow(
                temp = "t = 0.0",
                color = Color(0xFF1565C0),
                title = "Для каких задач подходит:",
                text = "Факты, код, математика, перевод, структурированные ответы (JSON/XML). " +
                    "Ответы практически идентичны при повторных запросах — минимальная вариативность.",
            )
            AnalysisRow(
                temp = "t = 0.7",
                color = Color(0xFF2E7D32),
                title = "Для каких задач подходит:",
                text = "Чат-боты, объяснения, резюме, умеренно творческие тексты. " +
                    "Оптимальный баланс: ответы связные и точные, но каждый раз немного разные.",
            )
            AnalysisRow(
                temp = "t = 1.2",
                color = Color(0xFF6A1B9A),
                title = "Для каких задач подходит:",
                text = "Брейнсторминг, поэзия, художественные тексты, генерация идей. " +
                    "Высокая оригинальность, но возможны неожиданные обороты и снижение связности.",
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.2f))

            Text(
                text = "Наблюдения о поведении при экстремальных значениях",
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
            Text(
                text = "• t = 0: три попытки дают почти одинаковый текст — детерминированность на максимуме.\n" +
                    "• t = 1.2: каждая попытка уникальна; могут появляться нестандартные метафоры и структуры, " +
                    "но иногда логика повествования нарушается.\n" +
                    "• Точность (соответствие запросу) наиболее высока при t = 0 и t = 0.7.\n" +
                    "• Разнообразие ответов растёт пропорционально temperature.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
        }
    }
}

@Composable
private fun AnalysisRow(
    temp: String,
    color: Color,
    title: String,
    text: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = temp,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = color,
            )
            Text(
                text = title,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
        }
        Text(
            text = text,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
            modifier = Modifier.padding(start = 4.dp),
        )
    }
}
