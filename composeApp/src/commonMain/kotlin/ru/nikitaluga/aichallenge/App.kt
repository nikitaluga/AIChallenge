package ru.nikitaluga.aichallenge

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import ru.nikitaluga.aichallenge.api.RouterAiApiService

data class ChatEntry(
    val role: String,
    val content: String,
)

@Composable
fun App() {
    MaterialTheme {
        val apiService = remember { RouterAiApiService() }
        var userInput by remember { mutableStateOf("") }
        var isLoading by remember { mutableStateOf(false) }
        val messages = remember { mutableStateListOf<ChatEntry>() }
        val coroutineScope = rememberCoroutineScope()
        val listState = rememberLazyListState()

        Column(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.background)
                .safeContentPadding()
                .fillMaxSize(),
        ) {
            // Messages list
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

            // Input row
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
                                        content = "Error: ${e.message ?: "Unknown error"}",
                                    ),
                                )
                            } finally {
                                isLoading = false
                            }
                            listState.animateScrollToItem(messages.size - 1)
                        }
                    },
                    enabled = !isLoading && userInput.isNotBlank(),
                ) {
                    Text("Send")
                }
            }
        }
    }
}
