package ru.nikitaluga.aichallenge.temperature

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

private const val RUNS_PER_TEMP = 3

@Composable
fun TemperatureScreen(
    viewModel: TemperatureViewModel = viewModel { TemperatureViewModel() },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
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
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            ) {
                Text(
                    text = "Напиши краткий рассказ о путешествии во времени (3-4 предложения).",
                    modifier = Modifier.padding(12.dp),
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }

        item {
            Button(
                onClick = { viewModel.onEvent(TemperatureContract.Event.RunAll) },
                enabled = !state.isRunningAll,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (state.isRunningAll) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Выполняется… (${state.slots.count { it.result != null }}/9)")
                } else {
                    Text("Запустить все тесты (9 запросов)")
                }
            }
        }

        itemsIndexed(state.groups) { groupIdx, group ->
            val groupSlots = List(RUNS_PER_TEMP) { runIdx ->
                state.slots.getOrElse(groupIdx * RUNS_PER_TEMP + runIdx) { TemperatureContract.SlotState() }
            }
            TemperatureGroupCard(group = group, slots = groupSlots)
        }

        if (state.allDone) {
            item { AnalysisCard() }
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }
    }
}

@Composable
private fun TemperatureGroupCard(
    group: TemperatureContract.TemperatureGroup,
    slots: List<TemperatureContract.SlotState>,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(group.color.copy(alpha = 0.12f))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            ) {
                Text(text = group.label, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = group.color)
                Text(text = group.description, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Spacer(modifier = Modifier.height(10.dp))

            slots.forEachIndexed { runIdx, slot ->
                if (runIdx > 0) HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                    Text(
                        text = "# ${runIdx + 1}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = group.color,
                        modifier = Modifier.width(36.dp).padding(top = 4.dp),
                    )
                    when {
                        slot.isLoading -> {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(4.dp),
                                contentAlignment = Alignment.CenterStart,
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = group.color,
                                )
                            }
                        }

                        slot.result != null -> {
                            Text(
                                text = slot.result,
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
                                modifier = Modifier.weight(1f).padding(4.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AnalysisCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
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
                color = androidx.compose.ui.graphics.Color(0xFF1565C0),
                title = "Для каких задач подходит:",
                text = "Факты, код, математика, перевод, структурированные ответы (JSON/XML). " +
                    "Ответы практически идентичны при повторных запросах — минимальная вариативность.",
            )
            AnalysisRow(
                temp = "t = 0.7",
                color = androidx.compose.ui.graphics.Color(0xFF2E7D32),
                title = "Для каких задач подходит:",
                text = "Чат-боты, объяснения, резюме, умеренно творческие тексты. " +
                    "Оптимальный баланс: ответы связные и точные, но каждый раз немного разные.",
            )
            AnalysisRow(
                temp = "t = 1.2",
                color = androidx.compose.ui.graphics.Color(0xFF6A1B9A),
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
    color: androidx.compose.ui.graphics.Color,
    title: String,
    text: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(text = temp, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = color)
            Text(text = title, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onTertiaryContainer)
        }
        Text(
            text = text,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
            modifier = Modifier.padding(start = 4.dp),
        )
    }
}
