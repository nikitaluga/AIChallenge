package ru.nikitaluga.aichallenge

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import ru.nikitaluga.aichallenge.agent.AgentScreen
import ru.nikitaluga.aichallenge.chat.ChatScreen
import ru.nikitaluga.aichallenge.comparison.ComparisonScreen
import ru.nikitaluga.aichallenge.compression.CompressionScreen
import ru.nikitaluga.aichallenge.context.ContextScreen
import ru.nikitaluga.aichallenge.memory.MemoryScreen
import ru.nikitaluga.aichallenge.personalization.PersonalizationScreen
import ru.nikitaluga.aichallenge.taskstate.TaskStateScreen
import ru.nikitaluga.aichallenge.invariants.InvariantsScreen
import ru.nikitaluga.aichallenge.mcp.McpScreen
import ru.nikitaluga.aichallenge.orchestrator.OrchestratorScreen
import ru.nikitaluga.aichallenge.rag.RagScreen
import ru.nikitaluga.aichallenge.day25.Day25Screen
import ru.nikitaluga.aichallenge.day26.Day26Screen
import ru.nikitaluga.aichallenge.day27.Day27Screen
import ru.nikitaluga.aichallenge.day28.Day28Screen
import ru.nikitaluga.aichallenge.day29.Day29Screen
import ru.nikitaluga.aichallenge.day30.Day30Screen
import ru.nikitaluga.aichallenge.day31.Day31Screen
import ru.nikitaluga.aichallenge.day32.Day32Screen
import ru.nikitaluga.aichallenge.day33.Day33Screen
import ru.nikitaluga.aichallenge.day34.Day34Screen
import ru.nikitaluga.aichallenge.day35.Day35Screen
import ru.nikitaluga.aichallenge.pipeline.PipelineScreen
import ru.nikitaluga.aichallenge.scheduler.SchedulerScreen
import ru.nikitaluga.aichallenge.taskprofile.TaskProfileScreen
import ru.nikitaluga.aichallenge.reasoning.ReasoningScreen
import ru.nikitaluga.aichallenge.modelscomparison.ModelsComparisonScreen
import ru.nikitaluga.aichallenge.temperature.TemperatureScreen
import ru.nikitaluga.aichallenge.token.TokenScreen

@Composable
fun App() {
    MaterialTheme {
        val tabs = listOf("Чат", "День 2", "День 3", "День 4", "День 5", "День 6", "День 8", "День 9", "День 10", "День 11", "День 12", "День 13", "День 14", "День 15", "День 17", "День 18", "День 19", "День 20", "День 21-22", "День 25", "День 26", "День 27", "День 28", "День 29", "День 30", "День 31", "День 32", "День 33", "День 34", "День 35")
        var selectedTab by remember { mutableStateOf(0) }

        LaunchedEffect(Unit) {
            delay(300)
            selectedTab = tabs.lastIndex
        }

        Column(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.background)
                .safeContentPadding()
                .fillMaxSize(),
        ) {
            PrimaryScrollableTabRow(selectedTabIndex = selectedTab, edgePadding = 0.dp) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title) },
                    )
                }
            }

            when (selectedTab) {
                0 -> ChatScreen()
                1 -> ComparisonScreen()
                2 -> ReasoningScreen()
                3 -> TemperatureScreen()
                4 -> ModelsComparisonScreen()
                5 -> AgentScreen()         // День 6 – ChatAgent со стримингом
                6 -> TokenScreen()          // День 8 – токен-аналитика
                7 -> CompressionScreen()    // День 9 – сжатие контекста
                8 -> ContextScreen()        // День 10 – стратегии контекста
                9 -> MemoryScreen()         // День 11 – модель памяти
                10 -> PersonalizationScreen()    // День 12 – персонализация
                11 -> TaskStateScreen()          // День 13 – конечный автомат задачи
                12 -> InvariantsScreen()         // День 14 – инварианты
                13 -> TaskProfileScreen()          // День 15 – контролируемые переходы
                14 -> McpScreen()                 // День 17 – MCP Weather Tool
                15 -> SchedulerScreen()           // День 18 – Планировщик
                16 -> PipelineScreen()            // День 19 – Pipeline
                17 -> OrchestratorScreen()          // День 20 – Orchestration MCP
                18 -> RagScreen()                   // День 21-22 – RAG Indexing
                19 -> Day25Screen()                 // День 25 – RAG + Memory Chat
                20 -> Day26Screen()                 // День 26 – Локальная LLM (Ollama)
                21 -> Day27Screen()                 // День 27 – Стриминг + выбор модели
                22 -> Day28Screen()                 // День 28 – Local LLM + RAG
                23 -> Day29Screen()                 // День 29 – Оптимизация LLM
                24 -> Day30Screen()                 // День 30 – Локальная LLM как сервис
                25 -> Day31Screen()                 // День 31 – Ассистент разработчика
                26 -> Day32Screen()                 // День 32 – AI Code Review
                27 -> Day33Screen()                 // День 33 – Ассистент поддержки
                28 -> Day34Screen()                 // День 34 – Файловый ассистент
                else -> Day35Screen()               // День 35 – Git Commit Generator
            }
        }
    }
}
