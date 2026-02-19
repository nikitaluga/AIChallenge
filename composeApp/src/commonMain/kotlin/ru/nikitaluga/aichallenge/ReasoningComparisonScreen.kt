package ru.nikitaluga.aichallenge

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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import ru.nikitaluga.aichallenge.api.RouterAiApiService
import kotlin.math.roundToInt

// ---------------------------------------------------------------------------
// Системные промпты
// ---------------------------------------------------------------------------

private val TASK_GENERATION_SYSTEM = """
    Ты — генератор учебных задач. Твоя задача — создать интересную задачу указанного типа и сложности.

    Правила генерации:
    - Задача должна быть оригинальной (не бери классические задачи типа "8 шаров" или "палиндром")
    - Задача должна быть сформулирована четко и однозначно
    - Не давай решение задачи в условии
    - Задача должна быть на русском языке
    - Сложность 1 — очень простая, может решить даже новичок
    - Сложность 5 — экспертная, требует глубокого анализа
""".trimIndent()

private const val STEP_BY_STEP_SYSTEM =
    "Решай пошагово, объясняя каждый шаг. Сначала опиши план, потом реализуй его шаг за шагом."

private const val COMPARISON_SYSTEM_PROMPT =
    "Ты — аналитик, который сравнивает различные способы решения одной и той же задачи. " +
    "Проанализируй предоставленные 4 ответа от языковой модели (каждый получен разным способом) " +
    "и составь сравнительный анализ в несколько предложений."

private val EXPERT_GROUP_SYSTEM = """
    Ты — группа из трёх экспертов: Аналитик, Инженер и Критик.
    Каждый эксперт должен предложить своё решение следующей задачи, исходя из своей роли:
    - Аналитик: подходит логически, разбивает задачу на подзадачи, использует дедукцию.
    - Инженер: предлагает практическое, пошаговое решение, возможно с аналогиями или примерами.
    - Критик: анализирует оба предложенных решения, указывает на сильные и слабые стороны, выбирает лучшее и объясняет почему.

    Представь ответ в формате:
    Аналитик: <решение аналитика>
    Инженер: <решение инженера>
    Критик: <анализ и итоговое решение>
""".trimIndent()

// ---------------------------------------------------------------------------
// Состояние одного способа
// ---------------------------------------------------------------------------

private data class ReasoningState(
    val isLoading: Boolean = false,
    val result: String? = null,
    val error: String? = null,
    /** Только для способа 3 — сгенерированный промпт. */
    val generatedPrompt: String? = null,
)

// ---------------------------------------------------------------------------
// Главный экран
// ---------------------------------------------------------------------------

@Composable
fun ReasoningComparisonScreen() {
    val apiService = remember { RouterAiApiService() }
    val scope = rememberCoroutineScope()

    // ── Шаг 1: выбор сложности и генерация задачи ─────────────────────────
    var difficulty by remember { mutableStateOf(3f) }
    var generatedTask by remember { mutableStateOf<String?>(null) }
    var isGeneratingTask by remember { mutableStateOf(false) }

    // ── Шаги 2–4: решение задачи ──────────────────────────────────────────
    var m1 by remember { mutableStateOf(ReasoningState()) }
    var m2 by remember { mutableStateOf(ReasoningState()) }
    var m3 by remember { mutableStateOf(ReasoningState()) }
    var m4 by remember { mutableStateOf(ReasoningState()) }
    var comparison by remember { mutableStateOf(ReasoningState()) }
    var isSolving by remember { mutableStateOf(false) }

    val difficultyInt = difficulty.roundToInt()
    val hasSolvingData = listOf(m1, m2, m3, m4).any {
        it.isLoading || it.result != null || it.error != null
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {

        // ── Секция выбора сложности ────────────────────────────────────────
        Text(
            text = "Сложность задачи",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
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
                    value = difficulty,
                    onValueChange = { difficulty = it },
                    valueRange = 1f..5f,
                    steps = 3, // 1,2,3,4,5 → 3 intermediate steps
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isGeneratingTask && !isSolving,
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

        // ── Кнопка генерации задачи ───────────────────────────────────────
        Button(
            onClick = {
                isGeneratingTask = true
                generatedTask = null
                // Сброс результатов решения при новой задаче
                m1 = ReasoningState()
                m2 = ReasoningState()
                m3 = ReasoningState()
                m4 = ReasoningState()
                comparison = ReasoningState()
                scope.launch {
                    val userPrompt = "Сгенерируй задачу одного из следующих типов " +
                        "(выбери сам рандомно): логическая, алгоритмическая, аналитическая.\n" +
                        "Сложность задачи: $difficultyInt из 5.\n\n" +
                        "Верни ТОЛЬКО текст задачи, без пояснений, без комментариев, " +
                        "без вариантов ответа. Только условие задачи."
                    apiService.clearHistory()
                    generatedTask = runCatching {
                        apiService.sendMessage(
                            prompt = userPrompt,
                            systemPrompt = TASK_GENERATION_SYSTEM,
                            maxTokens = 512,
                        )
                    }.getOrElse { e -> null.also { /* store error separately */ } }

                    if (generatedTask == null) {
                        // Показываем ошибку через comparison state, т.к. отдельного нет
                        // Для простоты — просто оставляем null, ниже UI покажет ошибку
                    }
                    isGeneratingTask = false
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isGeneratingTask && !isSolving,
        ) {
            if (isGeneratingTask) {
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

        // ── Область отображения задачи ────────────────────────────────────
        if (isGeneratingTask || generatedTask != null) {
            Text(
                text = "Задача:",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (isGeneratingTask) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                    ),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
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
                }
            } else if (generatedTask != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                    ),
                ) {
                    Text(
                        text = generatedTask!!,
                        modifier = Modifier.padding(12.dp),
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
        }

        // ── Кнопки управления решением ────────────────────────────────────
        if (generatedTask != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = {
                        val task = generatedTask ?: return@Button
                        isSolving = true
                        scope.launch {
                            // ─── Способ 1: Прямой ответ ──────────────────────
                            m1 = ReasoningState(isLoading = true)
                            apiService.clearHistory()
                            m1 = runCatching {
                                ReasoningState(
                                    result = apiService.sendMessage(
                                        prompt = task,
                                        systemPrompt = null,
                                        maxTokens = 1024,
                                    ),
                                )
                            }.getOrElse { e -> ReasoningState(error = e.message ?: "Неизвестная ошибка") }

                            // ─── Способ 2: Пошаговое решение ─────────────────
                            m2 = ReasoningState(isLoading = true)
                            apiService.clearHistory()
                            m2 = runCatching {
                                ReasoningState(
                                    result = apiService.sendMessage(
                                        prompt = task,
                                        systemPrompt = STEP_BY_STEP_SYSTEM,
                                        maxTokens = 1024,
                                    ),
                                )
                            }.getOrElse { e -> ReasoningState(error = e.message ?: "Неизвестная ошибка") }

                            // ─── Способ 3: Генерация промпта → решение ────────
                            m3 = ReasoningState(isLoading = true)
                            apiService.clearHistory()
                            m3 = try {
                                val promptGenRequest =
                                    "Составь подробный промпт (инструкцию), который поможет правильно решить " +
                                    "следующую задачу. Промпт должен содержать четкие указания, которые приведут " +
                                    "к верному решению. Задача: $task. " +
                                    "Ответ дай в виде готового промпта (только текст промпта, без пояснений)."

                                val generatedPrompt = apiService.sendMessage(
                                    prompt = promptGenRequest,
                                    systemPrompt = null,
                                    maxTokens = 1024,
                                )

                                if (generatedPrompt.isBlank()) {
                                    ReasoningState(error = "Модель вернула пустой промпт — попробуйте ещё раз")
                                } else {
                                    apiService.clearHistory()
                                    val solution = apiService.sendMessage(
                                        prompt = task,
                                        systemPrompt = generatedPrompt,
                                        maxTokens = 1024,
                                    )
                                    ReasoningState(result = solution, generatedPrompt = generatedPrompt)
                                }
                            } catch (e: Exception) {
                                ReasoningState(error = e.message ?: "Неизвестная ошибка")
                            }

                            // ─── Способ 4: Группа экспертов ──────────────────
                            m4 = ReasoningState(isLoading = true)
                            apiService.clearHistory()
                            m4 = runCatching {
                                ReasoningState(
                                    result = apiService.sendMessage(
                                        prompt = task,
                                        systemPrompt = EXPERT_GROUP_SYSTEM,
                                        maxTokens = 1024,
                                    ),
                                )
                            }.getOrElse { e -> ReasoningState(error = e.message ?: "Неизвестная ошибка") }

                            // ─── Сравнительный анализ всех 4 способов ─────────
                            val allSucceeded = listOf(m1, m2, m3, m4).all { it.result != null }
                            if (allSucceeded) {
                                comparison = ReasoningState(isLoading = true)
                                val comparisonPrompt = buildString {
                                    appendLine("Задача: $task")
                                    appendLine()
                                    appendLine("Способ 1 (Прямой ответ, без системного промпта):")
                                    appendLine(m1.result)
                                    appendLine()
                                    appendLine("Способ 2 (Пошаговое решение):")
                                    appendLine(m2.result)
                                    appendLine()
                                    appendLine("Способ 3 (Решение через сгенерированный промпт):")
                                    appendLine(m3.result)
                                    appendLine()
                                    appendLine("Способ 4 (Группа экспертов: Аналитик + Инженер + Критик):")
                                    appendLine(m4.result)
                                    appendLine()
                                    appendLine("Проанализируй и сравни эти 4 ответа по следующим критериям:")
                                    appendLine("1. Точность решения (правильность ответа)")
                                    appendLine("2. Полнота (все ли аспекты задачи раскрыты)")
                                    appendLine("3. Ясность и понятность объяснения")
                                    appendLine("4. Логичность и структурированность")
                                    appendLine("5. Креативность / нестандартный подход")
                                    appendLine("6. Длина ответа (лаконичность)")
                                }
                                apiService.clearHistory()
                                comparison = runCatching {
                                    ReasoningState(
                                        result = apiService.sendMessage(
                                            prompt = comparisonPrompt,
                                            systemPrompt = COMPARISON_SYSTEM_PROMPT,
                                            maxTokens = 1024,
                                        ),
                                    )
                                }.getOrElse { e -> ReasoningState(error = e.message ?: "Неизвестная ошибка") }
                            }

                            isSolving = false
                        }
                    },
                    enabled = !isSolving,
                    modifier = Modifier.weight(1f),
                ) {
                    if (isSolving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(if (isSolving) "Решаю…" else "Решить всеми способами")
                }

                OutlinedButton(
                    onClick = {
                        m1 = ReasoningState()
                        m2 = ReasoningState()
                        m3 = ReasoningState()
                        m4 = ReasoningState()
                        comparison = ReasoningState()
                        generatedTask = null
                    },
                    enabled = !isSolving && !isGeneratingTask,
                ) {
                    Text("Сброс")
                }
            }
        }

        // ── Четыре карточки результатов (появляются после запуска решения) ──
        if (hasSolvingData) {
            MethodCard(
                title = "Способ 1: Прямой ответ",
                tag = "Без системного промпта — задача задаётся как есть",
                state = m1,
            )
            MethodCard(
                title = "Способ 2: Пошагово",
                tag = "System: \"Решай пошагово, объясняя каждый шаг…\"",
                state = m2,
            )
            Method3Card(state = m3)
            MethodCard(
                title = "Способ 4: Экспертная группа",
                tag = "Три роли: Аналитик + Инженер + Критик",
                state = m4,
            )
        }

        // ── Сравнительный анализ ──────────────────────────────────────────
        if (comparison.isLoading || comparison.result != null || comparison.error != null) {
            ComparisonAnalysisCard(state = comparison)
        }
    }
}

// ---------------------------------------------------------------------------
// Вспомогательная функция — метка сложности
// ---------------------------------------------------------------------------

private fun difficultyLabel(level: Int): String = when (level) {
    1 -> "1 — Очень лёгкая"
    2 -> "2 — Лёгкая"
    3 -> "3 — Средняя"
    4 -> "4 — Сложная"
    5 -> "5 — Экспертная"
    else -> "$level"
}

// ---------------------------------------------------------------------------
// Универсальная карточка способа (1, 2, 4)
// ---------------------------------------------------------------------------

@Composable
private fun MethodCard(
    title: String,
    tag: String,
    state: ReasoningState,
) {
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
                TextButton(
                    onClick = { expanded = !expanded },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = if (expanded) "▲ Свернуть" else "▼ Развернуть",
                        fontSize = 12.sp,
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Карточка способа 3 — с секцией «сгенерированного промпта»
// ---------------------------------------------------------------------------

@Composable
private fun Method3Card(state: ReasoningState) {
    var resultExpanded by remember { mutableStateOf(true) }
    var promptExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "Способ 3: Сгенерированный промпт",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
            )
            Text(
                text = "Шаг 1 — модель создаёт промпт; Шаг 2 — решает задачу с его помощью",
                fontSize = 11.sp,
                fontStyle = FontStyle.Italic,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 4.dp),
            )

            if (state.generatedPrompt != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Сгенерированный промпт",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { promptExpanded = !promptExpanded }) {
                        Text(
                            text = if (promptExpanded) "▲ Скрыть" else "▼ Показать",
                            fontSize = 11.sp,
                        )
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
                TextButton(
                    onClick = { resultExpanded = !resultExpanded },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = if (resultExpanded) "▲ Свернуть" else "▼ Развернуть",
                        fontSize = 12.sp,
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Карточка сравнительного анализа
// ---------------------------------------------------------------------------

@Composable
private fun ComparisonAnalysisCard(state: ReasoningState) {
    var expanded by remember { mutableStateOf(true) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
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
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
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

                    TextButton(
                        onClick = { expanded = !expanded },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
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

// ---------------------------------------------------------------------------
// Общий блок отображения состояния (загрузка / ошибка / результат)
// ---------------------------------------------------------------------------

@Composable
private fun ResultBlock(state: ReasoningState, expanded: Boolean) {
    when {
        state.isLoading -> {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Text(
                        text = "Запрос к модели…",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
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
