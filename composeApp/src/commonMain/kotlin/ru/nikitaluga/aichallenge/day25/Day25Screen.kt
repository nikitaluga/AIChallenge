package ru.nikitaluga.aichallenge.day25

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ru.nikitaluga.aichallenge.domain.model.RagCitation
import ru.nikitaluga.aichallenge.domain.model.RagSource
import ru.nikitaluga.aichallenge.domain.model.TaskMemory

/**
 * День 25 — RAG-чат с историей диалога и памятью задачи (production-like).
 *
 * - Каждый вопрос ищет контекст в RAG-индексе (POST /rag/chat/v3).
 * - История диалога передаётся на сервер с каждым запросом.
 * - LLM автоматически извлекает и обновляет TaskMemory (цель, термины, ограничения).
 * - Каждый ответ содержит источники (sources) и цитаты (citations) из базы знаний.
 * - При score ниже threshold ответ помечается ⚠ «ниже порога».
 */
@Composable
fun Day25Screen(viewModel: Day25ViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is Day25Contract.Effect.ScrollToBottom ->
                    if (state.messages.isNotEmpty()) listState.animateScrollToItem(state.messages.lastIndex)
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // TaskMemory panel
        TaskMemoryPanel(
            taskMemory = state.taskMemory,
            isIndexing = state.isIndexing,
            onClear = { viewModel.onEvent(Day25Contract.Event.ClearHistory) },
            onBuildIndex = { viewModel.onEvent(Day25Contract.Event.BuildIndex) },
        )

        // Error banner
        AnimatedVisibility(visible = state.errorMessage != null) {
            val msg = state.errorMessage ?: ""
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = msg,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(
                        onClick = { viewModel.onEvent(Day25Contract.Event.DismissError) },
                        modifier = Modifier.size(24.dp),
                    ) {
                        Icon(
                            imageVector = ru.nikitaluga.aichallenge.context.CtxIconClose,
                            contentDescription = "Закрыть",
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
        }

        // Chat messages
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { Spacer(modifier = Modifier.height(4.dp)) }
            items(state.messages) { message ->
                Day25MessageBubble(message = message)
            }
            if (state.isLoading) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.Start,
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Поиск и генерация...", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            item { Spacer(modifier = Modifier.height(4.dp)) }
        }

        // Question carousel
        QuestionCarousel(
            questions = Day25Contract.SAMPLE_QUESTIONS,
            isLoading = state.isLoading,
            onSelect = { viewModel.onEvent(Day25Contract.Event.SelectQuestion(it)) },
        )

        // Input bar
        Day25InputBar(
            text = state.inputText,
            isLoading = state.isLoading,
            onTextChange = { viewModel.onEvent(Day25Contract.Event.InputChanged(it)) },
            onSend = { viewModel.onEvent(Day25Contract.Event.SendMessage) },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TaskMemoryPanel(
    taskMemory: TaskMemory,
    isIndexing: Boolean,
    onClear: () -> Unit,
    onBuildIndex: () -> Unit,
) {
    val hasContent = taskMemory.goal.isNotEmpty() ||
        taskMemory.terms.isNotEmpty() ||
        taskMemory.constraints.isNotEmpty()

    var expanded by remember { mutableStateOf(true) }

    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Память задачи",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(
                        imageVector = if (expanded) ru.nikitaluga.aichallenge.context.CtxIconExpandMore
                                      else ru.nikitaluga.aichallenge.context.CtxIconExpandLess,
                        contentDescription = if (expanded) "Свернуть" else "Развернуть",
                    )
                }
                if (isIndexing) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(4.dp))
                }
                TextButton(onClick = onBuildIndex, enabled = !isIndexing) {
                    Text("Индексировать", style = MaterialTheme.typography.labelSmall)
                }
                TextButton(onClick = onClear) {
                    Text("Сбросить", style = MaterialTheme.typography.labelSmall)
                }
            }

            AnimatedVisibility(visible = expanded && hasContent) {
                Column {
                    if (taskMemory.goal.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Цель: ${taskMemory.goal}",
                            style = MaterialTheme.typography.bodySmall,
                            fontStyle = FontStyle.Italic,
                        )
                    }
                    if (taskMemory.terms.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Термины:",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                        )
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            taskMemory.terms.forEach { term ->
                                SuggestionChip(
                                    onClick = {},
                                    label = { Text(term, fontSize = 11.sp) },
                                )
                            }
                        }
                    }
                    if (taskMemory.constraints.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Ограничения:",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                        )
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            taskMemory.constraints.forEach { constraint ->
                                SuggestionChip(
                                    onClick = {},
                                    label = { Text(constraint, fontSize = 11.sp) },
                                )
                            }
                        }
                    }
                }
            }

            if (!hasContent) {
                Text(
                    text = "Начните диалог — цель и термины появятся здесь",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.6f),
                )
            }
        }
    }
}

@Composable
private fun Day25MessageBubble(message: Day25Contract.Message) {
    val isUser = message.role == "user"

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        Column(
            modifier = Modifier.widthIn(max = 320.dp),
        ) {
            Box(
                modifier = Modifier
                    .clip(
                        RoundedCornerShape(
                            topStart = 16.dp,
                            topEnd = 16.dp,
                            bottomStart = if (isUser) 16.dp else 4.dp,
                            bottomEnd = if (isUser) 4.dp else 16.dp,
                        )
                    )
                    .background(
                        if (isUser) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
                    .padding(horizontal = 4.dp, vertical = 8.dp),
            ) {
                Text(
                    text = message.content,
                    color = if (isUser) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            if (!isUser) {
                if (message.belowThreshold) {
                    Text(
                        text = "⚠ Контекст ниже порога — ответ без RAG",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.padding(top = 4.dp, start = 4.dp),
                    )
                }
                if (message.sources.isNotEmpty()) {
                    SourcesSection(sources = message.sources)
                }
                if (message.citations.isNotEmpty()) {
                    CitationsSection(citations = message.citations)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SourcesSection(sources: List<RagSource>) {
    Column(modifier = Modifier.padding(top = 4.dp, start = 4.dp)) {
        Text(
            text = "Источники:",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.primary,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            sources.forEach { src ->
                val label = buildString {
                    append("📄 ")
                    append(src.source.substringAfterLast('/').take(20))
                    if (!src.section.isNullOrBlank()) append(" · ${src.section!!.take(15)}")
                }
                SuggestionChip(
                    onClick = {},
                    label = { Text(label, fontSize = 10.sp) },
                )
            }
        }
    }
}

@Composable
private fun CitationsSection(citations: List<RagCitation>) {
    Column(modifier = Modifier.padding(top = 4.dp, start = 4.dp)) {
        Text(
            text = "Цитаты из базы:",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.primary,
        )
        citations.take(2).forEach { citation ->
            Card(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                ),
            ) {
                Text(
                    text = "\u201c${citation.text}\u201d",
                    style = MaterialTheme.typography.bodySmall,
                    fontStyle = FontStyle.Italic,
                    modifier = Modifier.padding(8.dp),
                )
            }
        }
    }
}

@Composable
private fun QuestionCarousel(
    questions: List<String>,
    isLoading: Boolean,
    onSelect: (String) -> Unit,
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp),
    ) {
        items(questions) { question ->
            SuggestionChip(
                onClick = { if (!isLoading) onSelect(question) },
                enabled = !isLoading,
                label = {
                    Text(
                        text = question,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 200.dp),
                    )
                },
            )
        }
    }
}

@Composable
private fun Day25InputBar(
    text: String,
    isLoading: Boolean,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
) {
    HorizontalDivider()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = onTextChange,
            modifier = Modifier.weight(1f),
            placeholder = { Text("Спросите что-нибудь...") },
            maxLines = 4,
            shape = RoundedCornerShape(16.dp),
        )
        FilledIconButton(
            onClick = onSend,
            enabled = text.isNotBlank() && !isLoading,
        ) {
            Icon(
                imageVector = ru.nikitaluga.aichallenge.context.CtxIconSend,
                contentDescription = "Отправить",
            )
        }
    }
}
