package ru.nikitaluga.aichallenge.day32

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun Day32Screen(vm: Day32ViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    LaunchedEffect(vm.effects) {
        vm.effects.collect { effect ->
            when (effect) {
                Effect.ScrollToResult -> listState.animateScrollToItem(listState.layoutInfo.totalItemsCount - 1)
            }
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Spacer(Modifier.height(8.dp)) }

        // Header
        item {
            Text(
                "День 32 — AI Code Review",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "Вставьте git diff для автоматического анализа",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // PR Title input
        item {
            OutlinedTextField(
                value = state.prTitle,
                onValueChange = { vm.onEvent(Event.TitleChanged(it)) },
                label = { Text("Заголовок PR (необязательно)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
        }

        // Diff input + sample button
        item {
            OutlinedTextField(
                value = state.diffInput,
                onValueChange = { vm.onEvent(Event.DiffChanged(it)) },
                label = { Text("Git diff (patch)") },
                placeholder = { Text("Вставьте вывод git diff здесь...", fontFamily = FontFamily.Monospace) },
                modifier = Modifier.fillMaxWidth().height(200.dp),
                maxLines = 20,
                textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            )
        }

        // Buttons row
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TextButton(onClick = { vm.onEvent(Event.InsertSampleDiff) }) {
                    Text("Вставить пример")
                }
                Spacer(Modifier.weight(1f))
                if (state.review != null) {
                    TextButton(onClick = { vm.onEvent(Event.ClearResult) }) {
                        Text("Сбросить")
                    }
                }
                Button(
                    onClick = { vm.onEvent(Event.SubmitReview) },
                    enabled = !state.isLoading && state.diffInput.isNotBlank(),
                ) {
                    Text(if (state.isLoading) "Анализ..." else "Анализировать")
                }
            }
        }

        // Loading indicator
        if (state.isLoading) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
        }

        // Error
        state.error?.let { error ->
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        error,
                        modifier = Modifier.padding(12.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }
        }

        // Review result
        state.review?.let { review ->
            // Summary
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            "Резюме",
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            review.summary.ifBlank { "Анализ завершён" },
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        Text(
                            "Проанализировано: ${review.diffLength} символов",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                        )
                    }
                }
            }

            // Bugs
            if (review.bugs.isNotEmpty()) {
                item {
                    ReviewSection(
                        title = "🐛 Потенциальные баги",
                        items = review.bugs,
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }

            // Architecture
            if (review.architecture.isNotEmpty()) {
                item {
                    ReviewSection(
                        title = "🏗 Архитектурные проблемы",
                        items = review.architecture,
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                }
            }

            // Recommendations
            if (review.recommendations.isNotEmpty()) {
                item {
                    ReviewSection(
                        title = "💡 Рекомендации",
                        items = review.recommendations,
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }

            // All clear
            if (review.bugs.isEmpty() && review.architecture.isEmpty() && review.recommendations.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                    ) {
                        Text(
                            "✅ Проблем не обнаружено",
                            modifier = Modifier.padding(12.dp),
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                }
            }
        }

        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun ReviewSection(
    title: String,
    items: List<String>,
    containerColor: androidx.compose.ui.graphics.Color,
    contentColor: androidx.compose.ui.graphics.Color,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold, color = contentColor, fontSize = 15.sp)
            Spacer(Modifier.height(6.dp))
            items.forEach { item ->
                Text("• $item", color = contentColor, modifier = Modifier.padding(vertical = 2.dp))
            }
        }
    }
}
