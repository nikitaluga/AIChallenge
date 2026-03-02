package ru.nikitaluga.aichallenge.context

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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
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
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch

/**
 * День 10 — Стратегии управления контекстом.
 *
 * Переключение стратегий — сегментные кнопки вверху экрана.
 * У каждой стратегии свои агент и история (не сбрасываются при переключении).
 *
 * ── Как тестировать ────────────────────────────────────────────────────────
 *
 * 1. SLIDING WINDOW («Окно»)
 *    Сценарий «забывающий агент»:
 *    - Напиши: «Меня зовут Алексей, разрабатываем приложение для доставки»
 *    - Продолжай отправлять сообщения (бюджет, сроки, функции…)
 *    - После 10+ сообщений спроси: «Как меня зовут?»
 *    → Агент может не вспомнить — первые сообщения вышли из окна.
 *    Строка под кнопками показывает «всего: N» и размер окна.
 *
 * 2. STICKY FACTS («Факты»)
 *    Сценарий «память о проекте»:
 *    - Напиши: «Делаем iOS/Android-приложение для доставки еды, бюджет 500к»
 *    - Пиши ещё сообщения — детали, вопросы, уточнения
 *    - Нажми стрелку рядом с «Факты (N)», чтобы раскрыть панель фактов
 *    → LLM сам извлекает ключевые данные (цель, платформа, бюджет…)
 *      и помнит их даже когда история давно вышла из окна.
 *    - Спроси: «Напомни, какой у нас бюджет?» — ответит правильно.
 *
 * 3. BRANCHING («Ветки»)
 *    Сценарий «смена темы»:
 *    - Обсуждай одну тему несколько сообщений (например, архитектуру бэкенда)
 *    - Резко смени тему: «А давай поговорим про дизайн экрана авторизации»
 *    → Появится баннер «Обнаружена новая тема». Нажми «Создать» —
 *      появится новый таб «Ветка 1». Продолжай обсуждение дизайна здесь.
 *    - Переключись на таб «Главная» — контекст про бэкенд восстановится.
 *    - Каждая ветка отвечает только в рамках своего контекста.
 *    Строка Usage показывает токены последнего запроса.
 *
 * ───────────────────────────────────────────────────────────────────────────
 */
@Composable
fun ContextScreen(viewModel: ContextViewModel = viewModel { ContextViewModel() }) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val scrollToEnd = Int.MAX_VALUE / 2

    val isAtBottom by remember { derivedStateOf { !listState.canScrollForward } }

    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) {
            listState.scrollToItem(0, scrollToEnd)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                ContextContract.Effect.ScrollToBottom ->
                    listState.animateScrollToItem(0, scrollToEnd)
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {

        // ── Шапка ──────────────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "День 10 · Стратегии контекста",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            IconButton(
                onClick = { viewModel.onEvent(ContextContract.Event.ClearHistory) },
                enabled = !state.isLoading,
            ) {
                Icon(imageVector = CtxIconDelete, contentDescription = "Очистить")
            }
        }

        // ── Переключатель стратегий ─────────────────────────────────────────────
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 2.dp),
        ) {
            ContextContract.Strategy.entries.forEachIndexed { index, strategy ->
                SegmentedButton(
                    selected = state.strategy == strategy,
                    onClick = { viewModel.onEvent(ContextContract.Event.SwitchStrategy(strategy)) },
                    shape = SegmentedButtonDefaults.itemShape(
                        index = index,
                        count = ContextContract.Strategy.entries.size,
                    ),
                    label = { Text(strategy.label, fontSize = 13.sp) },
                )
            }
        }

        // ── Информационная строка (размер окна / ветка) ─────────────────────────
        val infoText = when (state.strategy) {
            ContextContract.Strategy.SlidingWindow ->
                "Последние ${state.windowSize} сообщений · всего: ${state.messages.size}"
            ContextContract.Strategy.Facts ->
                "Окно: ${state.windowSize} сообщений · фактов: ${state.facts.size}"
            ContextContract.Strategy.Branching ->
                "Веток: ${state.branches.size} · текущая: ${
                    state.branches.find { it.id == state.currentBranchId }?.name ?: "—"
                }"
        }
        Text(
            text = infoText,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
        )

        // ── Табы веток (только для Branching) ──────────────────────────────────
        if (state.strategy == ContextContract.Strategy.Branching && state.branches.isNotEmpty()) {
            val selectedBranchIndex = state.branches.indexOfFirst { it.id == state.currentBranchId }
                .coerceAtLeast(0)
            PrimaryScrollableTabRow(
                selectedTabIndex = selectedBranchIndex,
                edgePadding = 8.dp,
            ) {
                state.branches.forEach { branch ->
                    Tab(
                        selected = branch.id == state.currentBranchId,
                        onClick = {
                            viewModel.onEvent(ContextContract.Event.SwitchBranch(branch.id))
                        },
                        text = { Text(branch.name, fontSize = 13.sp) },
                    )
                }
            }
        }

        // ── Список сообщений ───────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(state.messages) { message ->
                    MessageBubble(message)
                }
                if (state.isLoading) {
                    item { LoadingBubble() }
                }
            }

            if (!isAtBottom && state.messages.isNotEmpty()) {
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
                        imageVector = CtxIconExpandMore,
                        contentDescription = "Вниз",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(8.dp),
                    )
                }
            }
        }

        // ── Баннер смены темы (только Branching) ────────────────────────────────
        AnimatedVisibility(
            visible = state.topicShiftSuggested && state.strategy == ContextContract.Strategy.Branching,
            enter = expandVertically(),
            exit = shrinkVertically(),
        ) {
            TopicShiftBanner(
                suggestedName = state.suggestedBranchName,
                onConfirm = { viewModel.onEvent(ContextContract.Event.ConfirmCreateBranch) },
                onDismiss = { viewModel.onEvent(ContextContract.Event.DismissTopicShift) },
            )
        }

        // ── Панель фактов (только Facts) ────────────────────────────────────────
        if (state.strategy == ContextContract.Strategy.Facts && state.facts.isNotEmpty()) {
            AnimatedVisibility(
                visible = state.showFacts,
                enter = expandVertically(),
                exit = shrinkVertically(),
            ) {
                FactsPanel(facts = state.facts)
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Факты (${state.facts.size})",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
                IconButton(onClick = { viewModel.onEvent(ContextContract.Event.ToggleFacts) }) {
                    Icon(
                        imageVector = if (state.showFacts) CtxIconExpandMore else CtxIconExpandLess,
                        contentDescription = if (state.showFacts) "Скрыть факты" else "Показать факты",
                    )
                }
            }
        }

        // ── Usage-строка ───────────────────────────────────────────────────────
        state.lastUsage?.let { usage ->
            Text(
                text = "Токены → ↑${usage.prompt} ↓${usage.completion} =${usage.total}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
            )
        }

        // ── Поле ввода ────────────────────────────────────────────────────────
        TextField(
            value = state.inputText,
            onValueChange = { viewModel.onEvent(ContextContract.Event.InputChanged(it)) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            placeholder = { Text("Введите сообщение…") },
            enabled = !state.isLoading,
            singleLine = true,
            trailingIcon = {
                IconButton(
                    onClick = { viewModel.onEvent(ContextContract.Event.SendMessage) },
                    enabled = !state.isLoading && state.inputText.isNotBlank(),
                ) {
                    Icon(imageVector = CtxIconSend, contentDescription = "Отправить")
                }
            },
        )
    }
}

@Composable
private fun MessageBubble(message: ContextContract.DisplayMessage) {
    val isUser = message.role == "user"
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Text(
            text = message.content,
            modifier = Modifier
                .widthIn(max = 280.dp)
                .clip(
                    RoundedCornerShape(
                        topStart = 12.dp, topEnd = 12.dp,
                        bottomStart = if (isUser) 12.dp else 2.dp,
                        bottomEnd = if (isUser) 2.dp else 12.dp,
                    ),
                )
                .background(
                    if (isUser) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.secondaryContainer,
                )
                .padding(horizontal = 12.dp, vertical = 8.dp),
            color = if (isUser) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSecondaryContainer
            },
            style = MaterialTheme.typography.bodyMedium,
        )
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
            .widthIn(max = 120.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "● ● ●",
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun TopicShiftBanner(suggestedName: String?, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.tertiaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Обнаружена новая тема",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
                Text(
                    text = if (suggestedName != null)
                        "Создать ветку «$suggestedName», чтобы не смешивать контексты?"
                    else
                        "Создать ветку, чтобы не смешивать контексты?",
                    style = MaterialTheme.typography.labelSmall,
                    fontStyle = FontStyle.Italic,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.tertiary,
                ),
            ) {
                Text("Создать", fontSize = 12.sp)
            }
            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = CtxIconClose,
                    contentDescription = "Игнорировать",
                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }
        }
    }
}

@Composable
private fun FactsPanel(facts: Map<String, String>) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
            HorizontalDivider()
            Spacer(Modifier.height(4.dp))
            facts.entries.forEach { (key, value) ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = "$key:",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.widthIn(max = 120.dp),
                    )
                    Text(
                        text = value,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}
