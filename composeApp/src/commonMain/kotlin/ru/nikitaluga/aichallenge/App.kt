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
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
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
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import ru.nikitaluga.aichallenge.api.RouterAiApiService
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily

// ---------------------------------------------------------------------------
// Shared data types
// ---------------------------------------------------------------------------

data class ChatEntry(
    val role: String,
    val content: String,
)

// Case descriptor — paramsInfo computed from actual field values
private data class ComparisonCase(
    val label: String,
    val systemPrompt: String?,
    val maxTokens: Int = 1024,
    val stop: List<String>?,
    val prompt: String = FIXED_QUERY,
    val validateJson: Boolean = false,
) {
    val paramsInfo: String = run {
        val sysText = systemPrompt
            ?.let {
                val cut = it.indexOf('.').takeIf { idx -> idx in 0..79 }
                    ?.let { idx -> it.take(idx + 1) }
                    ?: it.take(80)
                "\"$cut${if (cut.length < it.length) "…" else ""}\""
            }
            ?: "—"
        val stopText = stop
            ?.joinToString(", ", "[", "]") { "\"$it\"" }
            ?: "—"
        val promptLine = if (prompt != FIXED_QUERY) "\nprompt: \"${prompt.take(40)}\"" else ""
        "system: $sysText\nmax_tokens: $maxTokens  •  stop: $stopText$promptLine"
    }
}

// ---------------------------------------------------------------------------
// JSON demo constants
// ---------------------------------------------------------------------------

private val prettyJson = Json { prettyPrint = true }

private const val JSON_DEMO_PROMPT = "Перечисли 3 преимущества бега"

private val JSON_SYSTEM_PROMPT = """
    Ты отвечаешь ТОЛЬКО на русском языке и ТОЛЬКО в формате JSON.

    Строгая схема ответа:
    {
      "benefits": [
        {
          "title": "короткий заголовок преимущества",
          "description": "развернутое описание на 2-3 предложения"
        }
      ],
      "total_count": 3
    }

    Правила:
    1. НЕ добавляй НИКАКОГО текста до или после JSON
    2. НЕ используй markdown (```json)
    3. НЕ пиши пояснения вроде "Вот ваш JSON:"
    4. Только чистый JSON объект
    5. Все ключи и строки в двойных кавычках
    6. total_count должен соответствовать количеству элементов в benefits""".trimIndent()

private const val FIXED_QUERY = "Напиши краткое руководство по бегу для начинающих"

private val COMPARISON_CASES = listOf(
    ComparisonCase(
        label = "Без ограничений",
        systemPrompt = null,
        stop = null,
    ),
    ComparisonCase(
        label = "JSON ответ (со strict system prompt)",
        systemPrompt = JSON_SYSTEM_PROMPT,
        prompt = JSON_DEMO_PROMPT,
        stop = null,
        validateJson = true,
    ),
    ComparisonCase(
        label = "С ограничением длины",
        systemPrompt = "Ответь кратко.",
        maxTokens = 30,
        stop = null,
    ),
    ComparisonCase(
        label = "С условием завершения",
        systemPrompt = "Перечисли 5 преимуществ бега.",
        stop = listOf("3."),
    ),
)

// ---------------------------------------------------------------------------
// Root composable — tab navigation
// ---------------------------------------------------------------------------

@Composable
fun App() {
    MaterialTheme {
        var selectedTab by remember { mutableStateOf(0) }

        Column(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.background)
                .safeContentPadding()
                .fillMaxSize(),
        ) {
            PrimaryTabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Чат") },
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Сравнение") },
                )
            }

            when (selectedTab) {
                0 -> ChatScreen()
                1 -> ComparisonScreen()
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Chat screen
// ---------------------------------------------------------------------------

@Composable
private fun ChatScreen() {
    val apiService = remember { RouterAiApiService() }
    var userInput by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    val messages = remember { mutableStateListOf<ChatEntry>() }
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    Column(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(messages) { entry ->
                val isUser = entry.role == "user"
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart,
                ) {
                    Text(
                        text = entry.content,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (isUser) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.secondaryContainer,
                            )
                            .padding(12.dp),
                        color = if (isUser) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }

            if (isLoading) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TextField(
                value = userInput,
                onValueChange = { userInput = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Type a message...") },
                enabled = !isLoading,
                singleLine = true,
            )
            Button(
                onClick = {
                    val prompt = userInput.trim()
                    if (prompt.isEmpty()) return@Button
                    userInput = ""
                    messages.add(ChatEntry(role = "user", content = prompt))
                    isLoading = true
                    coroutineScope.launch {
                        try {
                            val response = apiService.sendMessage(prompt)
                            messages.add(ChatEntry(role = "assistant", content = response))
                        } catch (e: Exception) {
                            messages.add(
                                ChatEntry(
                                    role = "assistant",
                                    content = "Ошибка: ${e.message ?: "Unknown error"}",
                                ),
                            )
                        } finally {
                            isLoading = false
                        }
                        if (messages.isNotEmpty()) {
                            listState.animateScrollToItem(messages.size - 1)
                        }
                    }
                },
                enabled = !isLoading && userInput.isNotBlank(),
            ) {
                Text("Send")
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Comparison screen — Lesson 2 demo
// ---------------------------------------------------------------------------

@Composable
private fun ComparisonScreen() {
    val apiService = remember { RouterAiApiService() }
    val coroutineScope = rememberCoroutineScope()

    val loadingStates = remember { mutableStateListOf(false, false, false, false) }
    val results = remember { mutableStateListOf<String?>(null, null, null, null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "Запрос для всех вариантов:",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
            ),
        ) {
            Text(
                text = FIXED_QUERY,
                modifier = Modifier.padding(12.dp),
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        COMPARISON_CASES.forEachIndexed { index, case ->
            ComparisonCard(
                case = case,
                isLoading = loadingStates[index],
                result = results[index],
                onSend = {
                    coroutineScope.launch {
                        loadingStates[index] = true
                        results[index] = null
                        apiService.clearHistory()
                        results[index] = runCatching {
                            apiService.sendMessage(
                                prompt = case.prompt,
                                systemPrompt = case.systemPrompt,
                                maxTokens = case.maxTokens,
                                stopSequences = case.stop,
                            )
                        }.getOrElse { e -> "Ошибка: ${e.message}" }
                        loadingStates[index] = false
                    }
                },
            )
        }
    }
}

@Composable
private fun ComparisonCard(
    case: ComparisonCase,
    isLoading: Boolean,
    result: String?,
    onSend: () -> Unit,
) {
    var expanded by remember { mutableStateOf(true) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {

            // ── Title row ─────────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = case.label,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    modifier = Modifier.weight(1f),
                )

                Button(
                    onClick = onSend,
                    enabled = !isLoading,
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    } else {
                        Text("Отправить")
                    }
                }
            }

            // ── Params info — always visible ───────────────────────────────
            Text(
                text = case.paramsInfo,
                fontSize = 11.sp,
                fontStyle = FontStyle.Italic,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
            )

            // ── Result area ────────────────────────────────────────────────
            when {
                isLoading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(28.dp))
                    }
                }

                result != null -> {
                    if (case.validateJson) {
                        val parseResult = runCatching {
                            val element: JsonElement = Json.parseToJsonElement(result.trim())
                            prettyJson.encodeToString(element)
                        }
                        Text(
                            text = if (parseResult.isSuccess) "✓ JSON валиден" else "✗ JSON невалиден",
                            fontWeight = FontWeight.Bold,
                            color = if (parseResult.isSuccess) Color(0xFF2E7D32) else Color(0xFFC62828),
                            fontSize = 13.sp,
                            modifier = Modifier.padding(top = 4.dp, bottom = 2.dp),
                        )
                        Text(
                            text = parseResult.getOrElse { result },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(10.dp),
                            fontSize = 13.sp,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = if (expanded) Int.MAX_VALUE else 3,
                            overflow = if (expanded) TextOverflow.Clip else TextOverflow.Ellipsis,
                        )
                    } else {
                        Text(
                            text = result,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(10.dp),
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = if (expanded) Int.MAX_VALUE else 3,
                            overflow = if (expanded) TextOverflow.Clip else TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            // ── Expand/collapse toggle — full width, below text ────────────
            if (result != null || isLoading) {
                IconButton(
                    onClick = { expanded = !expanded },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                ) {
                    Text(
                        text = if (expanded) "▲" else "▼",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}
