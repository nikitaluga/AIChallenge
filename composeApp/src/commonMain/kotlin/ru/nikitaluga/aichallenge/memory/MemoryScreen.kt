package ru.nikitaluga.aichallenge.memory

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import ru.nikitaluga.aichallenge.context.CtxIconDelete
import ru.nikitaluga.aichallenge.context.CtxIconExpandMore
import ru.nikitaluga.aichallenge.context.CtxIconSend
import ru.nikitaluga.aichallenge.domain.model.PendingFact

/**
 * День 11 — Модель памяти ассистента.
 *
 * После каждого ответа LLM предлагает факты → пользователь подтверждает или отклоняет.
 * Только подтверждённые факты попадают в память.
 *
 * ── Как тестировать ────────────────────────────────────────────────────────
 * 1. «Привет, меня зовут Никита, я Kotlin-разработчик»
 *    → Появится панель с предложенными фактами (имя → Профиль, стек → Профиль)
 *    → Подтверди всё → факты появятся во вкладке Профиль
 * 2. «Хочу сделать трекер привычек на Android, срок — месяц»
 *    → LLM предложит цель и срок в слой «Задача»
 *    → Подтверди часть, откажись от части — увидишь разницу
 * 3. «Новая задача» → диалог + задача сброшены, Профиль остался
 * 4. «Привет» → агент обратится по имени (из подтверждённого Профиля)
 * ───────────────────────────────────────────────────────────────────────────
 */
@Composable
fun MemoryScreen(viewModel: MemoryViewModel = viewModel { MemoryViewModel() }) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val scrollToEnd = Int.MAX_VALUE / 2

    val isAtBottom by remember { derivedStateOf { !listState.canScrollForward } }

    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty() && state.activeTab == MemoryContract.MemoryTab.Dialog) {
            listState.scrollToItem(0, scrollToEnd)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                MemoryContract.Effect.ScrollToBottom ->
                    listState.animateScrollToItem(0, scrollToEnd)
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {

        // ── Шапка ──────────────────────────────────────────────────────────────
        Text(
            text = "День 11 · Модель памяти",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )

        // ── Вкладки ────────────────────────────────────────────────────────────
        val tabIndex = MemoryContract.MemoryTab.entries.indexOf(state.activeTab)
        PrimaryTabRow(selectedTabIndex = tabIndex) {
            MemoryContract.MemoryTab.entries.forEachIndexed { index, tab ->
                val count = when (tab) {
                    MemoryContract.MemoryTab.Dialog -> state.messages.size
                    MemoryContract.MemoryTab.Task -> state.taskFacts.size
                    MemoryContract.MemoryTab.Profile -> state.profileFacts.size
                }
                Tab(
                    selected = tabIndex == index,
                    onClick = { viewModel.onEvent(MemoryContract.Event.SwitchTab(tab)) },
                    text = {
                        Text(
                            text = if (count > 0) "${tab.label} ($count)" else tab.label,
                            fontSize = 13.sp,
                        )
                    },
                )
            }
        }

        // ── Основное содержимое вкладки ───────────────────────────────────────
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when (state.activeTab) {
                MemoryContract.MemoryTab.Dialog ->
                    DialogTab(state, listState, isAtBottom, coroutineScope, scrollToEnd)
                MemoryContract.MemoryTab.Task ->
                    MemoryFactsTab(
                        title = "Рабочая память",
                        subtitle = "Контекст текущей задачи. Сбрасывается кнопкой «Новая задача».",
                        facts = state.taskFacts,
                        emptyHint = "Фактов о задаче пока нет.\nРасскажи, что хочешь сделать.",
                        actionLabel = "Новая задача",
                        onAction = { viewModel.onEvent(MemoryContract.Event.StartNewTask) },
                        actionDestructive = true,
                    )
                MemoryContract.MemoryTab.Profile ->
                    MemoryFactsTab(
                        title = "Долговременная память",
                        subtitle = "Профиль пользователя. Не сбрасывается при смене задачи.",
                        facts = state.profileFacts,
                        emptyHint = "Профиль пуст.\nРасскажи о себе: имя, стек, цели.",
                        actionLabel = "Очистить профиль",
                        onAction = { viewModel.onEvent(MemoryContract.Event.ClearProfile) },
                        actionDestructive = false,
                    )
            }
        }

        // ── Панель ожидающих фактов ───────────────────────────────────────────
        AnimatedVisibility(
            visible = state.pendingFacts.isNotEmpty(),
            enter = expandVertically(),
            exit = shrinkVertically(),
        ) {
            PendingFactsPanel(
                facts = state.pendingFacts,
                onConfirm = { viewModel.onEvent(MemoryContract.Event.ConfirmFact(it)) },
                onReject = { viewModel.onEvent(MemoryContract.Event.RejectFact(it)) },
                onConfirmAll = { viewModel.onEvent(MemoryContract.Event.ConfirmAllFacts) },
                onRejectAll = { viewModel.onEvent(MemoryContract.Event.RejectAllFacts) },
            )
        }

        // ── Usage строка ──────────────────────────────────────────────────────
        if (state.showUsage) {
            Text(
                text = "Токены → ↑${state.lastUsagePrompt} ↓${state.lastUsageCompletion} =${state.lastUsageTotal}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
            )
        }

        // ── Поле ввода ────────────────────────────────────────────────────────
        TextField(
            value = state.inputText,
            onValueChange = { viewModel.onEvent(MemoryContract.Event.InputChanged(it)) },
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            placeholder = { Text("Напишите что-нибудь…") },
            enabled = !state.isLoading,
            singleLine = true,
            trailingIcon = {
                IconButton(
                    onClick = { viewModel.onEvent(MemoryContract.Event.SendMessage) },
                    enabled = !state.isLoading && state.inputText.isNotBlank(),
                ) {
                    Icon(imageVector = CtxIconSend, contentDescription = "Отправить")
                }
            },
        )
    }
}

// ── Панель предложенных фактов ────────────────────────────────────────────────

@Composable
private fun PendingFactsPanel(
    facts: List<PendingFact>,
    onConfirm: (PendingFact) -> Unit,
    onReject: (PendingFact) -> Unit,
    onConfirmAll: () -> Unit,
    onRejectAll: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shadowElevation = 4.dp,
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            // Заголовок + кнопки "всё сразу"
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "LLM нашёл факты (${facts.size})",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row {
                    TextButton(onClick = onRejectAll) {
                        Text("Отклонить все", fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
                    }
                    TextButton(onClick = onConfirmAll) {
                        Text("Сохранить все", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            // Каждый факт — отдельная строка с подтверждением
            facts.forEach { fact ->
                PendingFactRow(fact = fact, onConfirm = onConfirm, onReject = onReject)
            }
        }
    }
}

@Composable
private fun PendingFactRow(
    fact: PendingFact,
    onConfirm: (PendingFact) -> Unit,
    onReject: (PendingFact) -> Unit,
) {
    val layerColor = if (fact.layer == "profile")
        MaterialTheme.colorScheme.primary
    else
        MaterialTheme.colorScheme.secondary

    val layerLabel = if (fact.layer == "profile") "👤 Профиль" else "📋 Задача"

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // Бейдж слоя
        Surface(
            shape = RoundedCornerShape(4.dp),
            color = layerColor.copy(alpha = 0.15f),
        ) {
            Text(
                text = layerLabel,
                style = MaterialTheme.typography.labelSmall,
                color = layerColor,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }

        // Содержимое факта
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = fact.key,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = fact.value,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        // Кнопки подтверждения
        IconButton(
            onClick = { onReject(fact) },
            modifier = Modifier.size(32.dp),
        ) {
            Icon(
                imageVector = MemIconClose,
                contentDescription = "Отклонить",
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(18.dp),
            )
        }
        IconButton(
            onClick = { onConfirm(fact) },
            modifier = Modifier.size(32.dp),
        ) {
            Icon(
                imageVector = MemIconCheck,
                contentDescription = "Сохранить",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

// ── Вкладка «Диалог» ──────────────────────────────────────────────────────────

@Composable
private fun DialogTab(
    state: MemoryContract.State,
    listState: androidx.compose.foundation.lazy.LazyListState,
    isAtBottom: Boolean,
    coroutineScope: kotlinx.coroutines.CoroutineScope,
    scrollToEnd: Int,
) {
    Box(modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp)) {
        Column(modifier = Modifier.fillMaxSize()) {
            Text(
                text = "Краткосрочная · в памяти: ${state.messages.size} из ${state.windowSize}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(vertical = 4.dp, horizontal = 4.dp),
            )
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(state.messages) { MessageBubble(it) }
                if (state.isLoading) { item { LoadingBubble() } }
            }
        }
        if (!isAtBottom && state.messages.isNotEmpty()) {
            Surface(
                onClick = { coroutineScope.launch { listState.animateScrollToItem(0, scrollToEnd) } },
                modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp),
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
}

// ── Вкладка с фактами (Задача / Профиль) ─────────────────────────────────────

@Composable
private fun MemoryFactsTab(
    title: String,
    subtitle: String,
    facts: Map<String, String>,
    emptyHint: String,
    actionLabel: String,
    onAction: () -> Unit,
    actionDestructive: Boolean,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(text = title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline, fontStyle = FontStyle.Italic)
        HorizontalDivider()

        if (facts.isEmpty()) {
            Text(text = emptyHint, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline, fontStyle = FontStyle.Italic)
        } else {
            facts.forEach { (key, value) ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                    Text(text = "$key:", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.secondary, modifier = Modifier.widthIn(min = 80.dp, max = 130.dp))
                    Text(text = value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                }
            }
        }

        Spacer(Modifier.weight(1f))

        if (actionDestructive) {
            Button(
                onClick = onAction,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer),
            ) {
                Icon(imageVector = CtxIconDelete, contentDescription = null)
                Text(" $actionLabel", modifier = Modifier.padding(start = 4.dp))
            }
        } else {
            OutlinedButton(onClick = onAction, modifier = Modifier.fillMaxWidth(), enabled = facts.isNotEmpty()) {
                Icon(imageVector = CtxIconDelete, contentDescription = null)
                Text(" $actionLabel", modifier = Modifier.padding(start = 4.dp))
            }
        }
    }
}

// ── Общие компоненты ──────────────────────────────────────────────────────────

@Composable
private fun MessageBubble(message: MemoryContract.DisplayMessage) {
    val isUser = message.role == "user"
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart) {
        Text(
            text = message.content,
            modifier = Modifier
                .widthIn(max = 280.dp)
                .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp, bottomStart = if (isUser) 12.dp else 2.dp, bottomEnd = if (isUser) 2.dp else 12.dp))
                .background(if (isUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            color = if (isUser) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSecondaryContainer,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun LoadingBubble() {
    val infiniteTransition = rememberInfiniteTransition()
    val alpha by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = 0.2f,
        animationSpec = infiniteRepeatable(animation = keyframes { durationMillis = 800; 1f at 0; 0.2f at 400; 1f at 800 }),
    )
    Box(
        modifier = Modifier.widthIn(max = 120.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = "● ● ●", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha), style = MaterialTheme.typography.bodyMedium)
    }
}
