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
import ru.nikitaluga.aichallenge.reasoning.ReasoningScreen
import ru.nikitaluga.aichallenge.modelscomparison.ModelsComparisonScreen
import ru.nikitaluga.aichallenge.temperature.TemperatureScreen
import ru.nikitaluga.aichallenge.token.TokenScreen

@Composable
fun App() {
    MaterialTheme {
        val tabs = listOf("Чат", "День 2", "День 3", "День 4", "День 5", "День 6", "День 8", "День 9", "День 10")
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
                else -> ContextScreen()     // День 10 – стратегии контекста
            }
        }
    }
}
