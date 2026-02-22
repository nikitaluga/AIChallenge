package ru.nikitaluga.aichallenge.modelscomparison

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ru.nikitaluga.aichallenge.domain.model.ModelQueryResult
import ru.nikitaluga.aichallenge.domain.model.ModelTier

@Composable
fun ModelsComparisonScreen(
    viewModel: ModelsComparisonViewModel = viewModel<ModelsComparisonViewModel>(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 12.dp),
    ) {
        item {
            Column {
                Text(
                    text = "День 5: Сравнение моделей",
                    style = MaterialTheme.typography.headlineSmall,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Один и тот же запрос отправляется трём моделям разного уровня.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Запрос:", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = COMPARISON_PROMPT,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }

        item {
            val doneCount = state.slots.count { it.result != null || it.error != null }
            Button(
                onClick = { viewModel.onEvent(ModelsComparisonContract.Event.RunComparison) },
                enabled = !state.isRunning,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (state.isRunning) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Выполняется… ($doneCount/3)")
                } else {
                    Text("Запустить сравнение (3 запроса)")
                }
            }
        }

        itemsIndexed(state.slots) { _, slot ->
            ModelCard(slot)
        }

        if (state.allDone) {
            item {
                SummaryCard(state.slots)
            }
        }
    }
}

@Composable
private fun ModelCard(slot: ModelsComparisonContract.ModelSlotState) {
    val tierColor = when (slot.config.tier) {
        ModelTier.WEAK -> Color(0xFF1565C0)
        ModelTier.MEDIUM -> Color(0xFF2E7D32)
        ModelTier.STRONG -> Color(0xFF6A1B9A)
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = tierColor,
                    shape = MaterialTheme.shapes.extraSmall,
                ) {
                    Text(
                        text = slot.config.tier.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    text = slot.config.displayName,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Spacer(Modifier.height(2.dp))
            Text(
                text = slot.config.url,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(8.dp))
            when {
                slot.isLoading -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Запрос выполняется…", style = MaterialTheme.typography.bodySmall)
                    }
                }
                slot.error != null -> {
                    Text(
                        text = "Ошибка: ${slot.error}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                slot.result != null -> {
                    ResultSection(slot.result)
                }
                else -> {
                    Text(
                        text = "Ожидает запуска",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun ResultSection(result: ModelQueryResult) {
    val sec = result.responseTimeMs / 1000
    val tenths = (result.responseTimeMs % 1000) / 100
    val timeText = "$sec.${tenths} с"

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        MetricChip(label = "Время", value = timeText)
        MetricChip(label = "Вход", value = "${result.inputTokens} тк")
        MetricChip(label = "Выход", value = "${result.outputTokens} тк")
    }

    Spacer(Modifier.height(4.dp))
    val costText = if (result.inputTokens > 0 || result.outputTokens > 0) {
        val microDollars = (result.estimatedCostUsd * 1_000_000).toLong()
        val nanoFrac = ((result.estimatedCostUsd * 1_000_000 - microDollars) * 1000).toLong()
        "≈\$$microDollars.${nanoFrac.toString().padStart(3, '0')} × 10⁻⁶"
    } else {
        "Н/Д (usage не возвращён API)"
    }
    Text(
        text = "Стоимость: $costText",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    Spacer(Modifier.height(8.dp))
    Text("Ответ модели:", style = MaterialTheme.typography.labelMedium)
    Spacer(Modifier.height(4.dp))
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = result.responseText,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(8.dp),
        )
    }

    Spacer(Modifier.height(8.dp))
    val qualityText = when (result.config.tier) {
        ModelTier.WEAK -> "2/5 — Базовый уровень: объяснение упрощённое, возможны неточности"
        ModelTier.MEDIUM -> "4/5 — Хороший уровень: точное и понятное объяснение"
        ModelTier.STRONG -> "5/5 — Высокий уровень: развёрнутое, точное, с аналогиями"
    }
    Text(
        text = "Оценка качества: $qualityText",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun MetricChip(label: String, value: String) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = MaterialTheme.shapes.extraSmall,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SummaryCard(slots: List<ModelsComparisonContract.ModelSlotState>) {
    val doneSlots = slots.filter { it.result != null }
    if (doneSlots.isEmpty()) return

    val fastest = doneSlots.minByOrNull { it.result!!.responseTimeMs }
    val mostEconomical = doneSlots
        .filter { it.result!!.estimatedCostUsd > 0.0 }
        .minByOrNull { it.result!!.estimatedCostUsd }
        ?: doneSlots.minByOrNull { it.result!!.outputTokens }
    val bestQuality = doneSlots.maxByOrNull { it.config.tier.ordinal }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "ВЫВОДЫ",
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(8.dp))

            SummaryRow(
                title = "Лучшее качество:",
                value = "${bestQuality?.config?.displayName} — наибольший размер модели " +
                    "обеспечивает более развёрнутые и точные ответы.",
            )
            Spacer(Modifier.height(6.dp))
            val fastestSec = fastest?.result?.responseTimeMs?.let {
                "${it / 1000}.${(it % 1000) / 100} с"
            } ?: "—"
            SummaryRow(
                title = "Самая быстрая:",
                value = "${fastest?.config?.displayName} — $fastestSec.",
            )
            Spacer(Modifier.height(6.dp))
            SummaryRow(
                title = "Самая экономичная:",
                value = "${mostEconomical?.config?.displayName} — " +
                    "наименьшая стоимость за запрос.",
            )
            Spacer(Modifier.height(6.dp))
            SummaryRow(
                title = "Стоит ли переплачивать?",
                value = "Для простых объяснений средняя модель — оптимальный выбор: " +
                    "хорошее качество при низкой стоимости. Слабая модель подходит " +
                    "для тривиальных задач. Сильная оправдана для сложных, " +
                    "многоэтапных запросов.",
            )
        }
    }
}

@Composable
private fun SummaryRow(title: String, value: String) {
    Column {
        Text(text = title, style = MaterialTheme.typography.labelLarge)
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}
