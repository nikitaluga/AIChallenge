package ru.nikitaluga.aichallenge

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import ru.nikitaluga.aichallenge.chat.ChatScreen
import ru.nikitaluga.aichallenge.comparison.ComparisonScreen
import ru.nikitaluga.aichallenge.reasoning.ReasoningScreen
import ru.nikitaluga.aichallenge.modelscomparison.ModelsComparisonScreen
import ru.nikitaluga.aichallenge.temperature.TemperatureScreen

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
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Чат") })
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("День 2") })
                Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 }, text = { Text("День 3") })
                Tab(selected = selectedTab == 3, onClick = { selectedTab = 3 }, text = { Text("День 4") })
                Tab(selected = selectedTab == 4, onClick = { selectedTab = 4 }, text = { Text("День 5") })
            }

            when (selectedTab) {
                0 -> ChatScreen()
                1 -> ComparisonScreen()
                2 -> ReasoningScreen()
                3 -> TemperatureScreen()
                4 -> ModelsComparisonScreen()
            }
        }
    }
}
