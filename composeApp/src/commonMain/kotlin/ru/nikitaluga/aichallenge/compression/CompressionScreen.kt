package ru.nikitaluga.aichallenge.compression

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Badge
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch

@Composable
fun CompressionScreen(viewModel: CompressionViewModel = viewModel { CompressionViewModel() }) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    val isAtBottom by remember { derivedStateOf { !listState.canScrollForward } }
    val scrollToEnd = Int.MAX_VALUE / 2

    // При первом появлении сообщений — instant scroll к концу
    val hasExchanges = state.exchanges.isNotEmpty()
    LaunchedEffect(hasExchanges) {
        if (hasExchanges) listState.scrollToItem(0, scrollToEnd)
    }

    // ScrollToBottom effect — после каждой отправки
    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                CompressionContract.Effect.ScrollToBottom ->
                    listState.animateScrollToItem(0, scrollToEnd)
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {

        // ── Шапка ─────────────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "День 9 · Сжатие контекста",
                    style = MaterialTheme.typography.headlineSmall,
                )
                Text(
                    text = "окно: ${state.rawWindowSize} raw · пакет: ${state.compressionBatchSize}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Бейдж — количество суммаризаций
                if (state.compressionCount > 0) {
                    Badge(
                        containerColor = MaterialTheme.colorScheme.tertiary,
                        contentColor = MaterialTheme.colorScheme.onTertiary,
                    ) {
                        Text("∑ ${state.compressionCount}", fontSize = 11.sp)
                    }
                }
                IconButton(onClick = { viewModel.onEvent(CompressionContract.Event.ToggleLog) }) {
                    Icon(
                        imageVector = if (state.showLog) CmpIconExpandLess else CmpIconExpandMore,
                        contentDescription = if (state.showLog) "Скрыть лог" else "Показать лог",
                    )
                }
                IconButton(
                    onClick = { viewModel.onEvent(CompressionContract.Event.ClearHistory) },
                    enabled = !state.isLoading,
                ) {
                    Icon(imageVector = CmpIconAdd, contentDescription = "Новый чат")
                }
            }
        }

        // ── Список обменов ─────────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(state.exchanges) { exchange ->
                    ExchangeCard(exchange)
                }
            }

            // Кнопка «вниз»
            if (!isAtBottom && state.exchanges.isNotEmpty()) {
                Surface(
                    onClick = {
                        coroutineScope.launch {
                            listState.animateScrollToItem(0, scrollToEnd)
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp),
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shadowElevation = 4.dp,
                ) {
                    Icon(
                        imageVector = CmpIconExpandMore,
                        contentDescription = "Вниз",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(8.dp),
                    )
                }
            }
        }

        // ── Лог метрик ────────────────────────────────────────────────────────
        AnimatedVisibility(
            visible = state.showLog,
            enter = expandVertically(),
            exit = shrinkVertically(),
        ) {
            MetricsLogPanel(
                entries = state.logEntries,
                averageSaved = state.averageSavedPercent,
            )
        }

        // ── Поле ввода ────────────────────────────────────────────────────────
        TextField(
            value = state.inputText,
            onValueChange = { viewModel.onEvent(CompressionContract.Event.InputChanged(it)) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            placeholder = { Text("Введите сообщение…") },
            enabled = !state.isLoading,
            singleLine = true,
            trailingIcon = {
                IconButton(
                    onClick = { viewModel.onEvent(CompressionContract.Event.SendMessage) },
                    enabled = !state.isLoading && state.inputText.isNotBlank(),
                ) {
                    Icon(imageVector = CmpIconSend, contentDescription = "Отправить")
                }
            },
        )
    }
}

// Форматирует Float с одним знаком после запятой без java.lang.String.format (недоступен в commonMain)
private fun Float.fmt1f(): String {
    val i = kotlin.math.round(this * 10)
    return "${i / 10}.${kotlin.math.abs(i % 10)}"
}

// ── Карточка обмена ───────────────────────────────────────────────────────────

@Composable
private fun ExchangeCard(exchange: CompressionContract.Exchange) {
    Column(modifier = Modifier.fillMaxWidth()) {

        // Сообщение пользователя — справа
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
            Text(
                text = exchange.userMessage,
                modifier = Modifier
                    .widthIn(max = 280.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .padding(12.dp),
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }

        Spacer(Modifier.height(4.dp))

        // Ответ / карточка сравнения
        if (exchange.isLoading) {
            LoadingBubble()
        } else if (exchange.isSingleMode) {
            SingleResponseBubble(exchange.compressedResponse, exchange.compressionTriggered)
        } else {
            ComparisonCard(exchange)
        }
    }
}

@Composable
private fun LoadingBubble() {
    val infiniteTransition = rememberInfiniteTransition()
    val alpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.2f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 800
                1f at 0; 0.2f at 400; 1f at 800
            },
        ),
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "● ● ●",
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

/** Одиночный ответ без сравнения — summary ещё нет. */
@Composable
private fun SingleResponseBubble(response: String, compressionTriggered: Boolean) {
    Column {
        Text(
            text = response,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.secondaryContainer)
                .padding(12.dp),
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            style = MaterialTheme.typography.bodyMedium,
        )
        if (compressionTriggered) {
            Text(
                text = "∑ суммаризация выполнена — следующие запросы будут сравниваться",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.tertiary,
                fontStyle = FontStyle.Italic,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
            )
        }
    }
}

/** Карточка сравнения — два параллельных ответа с метриками. */
@Composable
private fun ComparisonCard(exchange: CompressionContract.Exchange) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {

            // ── Ответ со сжатием ──────────────────────────────────────────────
            ResponseSection(
                label = "Со сжатием",
                promptTokens = exchange.compressedPromptTokens,
                completionTokens = exchange.compressedCompletionTokens,
                totalTokens = exchange.compressedTotalTokens,
                responseText = exchange.compressedResponse,
                containerColor = Color(0xFFE8F5E9),
                onContainerColor = Color(0xFF1B5E20),
                labelColor = Color(0xFF388E3C),
            )

            Spacer(Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))

            // ── Ответ без сжатия ──────────────────────────────────────────────
            ResponseSection(
                label = "Без сжатия",
                promptTokens = exchange.fullPromptTokens,
                completionTokens = exchange.fullCompletionTokens,
                totalTokens = exchange.fullTotalTokens,
                responseText = exchange.fullResponse,
                containerColor = Color(0xFFFFEBEE),
                onContainerColor = Color(0xFF4E342E),
                labelColor = Color(0xFFC62828),
            )

            // ── Экономия ──────────────────────────────────────────────────────
            Spacer(Modifier.height(6.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val savingColor = when {
                    exchange.savingsPercent >= 20f -> Color(0xFF2E7D32)
                    exchange.savingsPercent > 0f   -> Color(0xFF1565C0)
                    else                           -> MaterialTheme.colorScheme.outline
                }
                Text(
                    text = "Экономия: ${exchange.savingsPercent.fmt1f()}%",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = savingColor,
                )
                if (exchange.compressionTriggered) {
                    Text(
                        text = "∑ суммаризация",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary,
                        fontStyle = FontStyle.Italic,
                    )
                }
            }
        }
    }
}

@Composable
private fun ResponseSection(
    label: String,
    promptTokens: Int,
    completionTokens: Int,
    totalTokens: Int,
    responseText: String,
    containerColor: Color,
    onContainerColor: Color,
    labelColor: Color,
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = labelColor,
            )
            if (totalTokens > 0) {
                Text(
                    text = buildAnnotatedString {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("$totalTokens т") }
                        append("  ↑${promptTokens} ↓${completionTokens}")
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = responseText,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(containerColor)
                .padding(10.dp),
            color = onContainerColor,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

// ── Панель лога метрик ────────────────────────────────────────────────────────

@Composable
private fun MetricsLogPanel(
    entries: List<CompressionContract.LogEntry>,
    averageSaved: Float,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            HorizontalDivider()
            Spacer(Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "Лог метрик",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                )
                if (entries.isNotEmpty()) {
                    Text(
                        text = "Средняя экономия: ${averageSaved.fmt1f()}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
            }

            Spacer(Modifier.height(4.dp))

            if (entries.isEmpty()) {
                Text(
                    text = "Пока нет данных",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            } else {
                // Заголовок таблицы
                LogTableHeader()
                Spacer(Modifier.height(2.dp))
                entries.forEach { entry -> LogTableRow(entry) }
            }

            Spacer(Modifier.height(4.dp))
        }
    }
}

@Composable
private fun LogTableHeader() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        listOf("№", "Полный", "Сжатый", "Экономия").forEach { label ->
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun LogTableRow(entry: CompressionContract.LogEntry) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = "#${entry.index}",
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "${entry.fullTotalTokens}т",
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "${entry.compressedTotalTokens}т",
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "${entry.savedPercent.fmt1f()}%",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = if (entry.savedPercent >= 10f) Color(0xFF2E7D32) else MaterialTheme.colorScheme.outline,
            modifier = Modifier.weight(1f),
        )
    }
}
