package ru.nikitaluga.aichallenge.personalization

import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ru.nikitaluga.aichallenge.domain.model.UserProfileConfig

/**
 * День 12 — Персонализация ассистента.
 *
 * Три демо-профиля загружаются при первом старте:
 *   • Senior Developer — краткий технический стиль
 *   • Junior / Студент — подробные объяснения с примерами
 *   • Менеджер / PM    — bullet points, деловой язык
 *
 * ── Как тестировать ─────────────────────────────────────────────────────────
 * 1. Выбери «Senior Developer» → спроси «Объясни что такое Mutex»
 *    → короткий технический ответ без лирики
 * 2. Переключи на «Junior / Студент» → «Что такое Mutex?»
 *    → подробное объяснение с аналогиями
 * 3. Нажми «+ Новый» → создай свой профиль с любыми полями
 * ────────────────────────────────────────────────────────────────────────────
 */
@Composable
fun PersonalizationScreen(viewModel: PersonalizationViewModel = viewModel { PersonalizationViewModel() }) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val scrollToEnd = Int.MAX_VALUE / 2

    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) {
            listState.scrollToItem(0, scrollToEnd)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                PersonalizationContract.Effect.ScrollToBottom ->
                    listState.animateScrollToItem(0, scrollToEnd)
            }
        }
    }

    // ── Диалог создания / редактирования профиля ─────────────────────────────
    if (state.showDialog) {
        ProfileEditDialog(
            profile = state.editingProfile,
            onSave = { viewModel.onEvent(PersonalizationContract.Event.SaveProfile(it)) },
            onDismiss = { viewModel.onEvent(PersonalizationContract.Event.DismissDialog) },
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {

        // ── Заголовок ─────────────────────────────────────────────────────────
        Text(
            text = "День 12 · Персонализация",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )

        // ── Выбор профиля ─────────────────────────────────────────────────────
        ProfileSelector(
            profiles = state.profiles,
            activeId = state.activeProfileId,
            onSelect = { viewModel.onEvent(PersonalizationContract.Event.SelectProfile(it)) },
            onCreate = { viewModel.onEvent(PersonalizationContract.Event.CreateProfile) },
        )

        // ── Карточка активного профиля ───────────────────────────────────────
        state.activeProfile?.let { profile ->
            ActiveProfileCard(
                profile = profile,
                canDelete = state.profiles.size > 1,
                onEdit = { viewModel.onEvent(PersonalizationContract.Event.EditProfile(profile.id)) },
                onDelete = { viewModel.onEvent(PersonalizationContract.Event.DeleteProfile(profile.id)) },
            )
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

        // ── Список сообщений ──────────────────────────────────────────────────
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (state.messages.isEmpty() && !state.isLoading) {
                item {
                    Text(
                        text = "Выбери профиль и начни диалог.\nАссистент адаптирует стиль автоматически.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
            items(state.messages) { MessageBubble(it) }
            if (state.isLoading) { item { LoadingBubble() } }
        }

        // ── Usage ─────────────────────────────────────────────────────────────
        if (state.showUsage) {
            Text(
                text = "Токены → ↑${state.lastUsagePrompt} ↓${state.lastUsageCompletion}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
            )
        }

        // ── Ввод ─────────────────────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            IconButton(
                onClick = { viewModel.onEvent(PersonalizationContract.Event.ClearHistory) },
                enabled = state.messages.isNotEmpty() && !state.isLoading,
            ) {
                Icon(
                    imageVector = PersIconDelete,
                    contentDescription = "Сброс диалога",
                    tint = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(20.dp),
                )
            }
            TextField(
                value = state.inputText,
                onValueChange = { viewModel.onEvent(PersonalizationContract.Event.InputChanged(it)) },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Написать…") },
                enabled = !state.isLoading,
                singleLine = true,
                trailingIcon = {
                    IconButton(
                        onClick = { viewModel.onEvent(PersonalizationContract.Event.SendMessage) },
                        enabled = !state.isLoading && state.inputText.isNotBlank(),
                    ) {
                        Icon(imageVector = PersIconSend, contentDescription = "Отправить")
                    }
                },
            )
        }
    }
}

// ── Выбор профиля ─────────────────────────────────────────────────────────────

@Composable
private fun ProfileSelector(
    profiles: List<UserProfileConfig>,
    activeId: String?,
    onSelect: (String) -> Unit,
    onCreate: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        profiles.forEach { profile ->
            FilterChip(
                selected = profile.id == activeId,
                onClick = { onSelect(profile.id) },
                label = { Text(profile.name, fontSize = 13.sp) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            )
        }
        // Кнопка создания нового профиля
        Surface(
            onClick = onCreate,
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.secondaryContainer,
            modifier = Modifier.padding(2.dp),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(
                    imageVector = PersIconAdd,
                    contentDescription = "Создать профиль",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Text(
                    text = "Новый",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
    }
}

// ── Карточка активного профиля ────────────────────────────────────────────────

@Composable
private fun ActiveProfileCard(
    profile: UserProfileConfig,
    canDelete: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = profile.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Row {
                    IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                        Icon(
                            imageVector = PersIconEdit,
                            contentDescription = "Редактировать",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.secondary,
                        )
                    }
                    IconButton(
                        onClick = onDelete,
                        enabled = canDelete,
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            imageVector = PersIconDelete,
                            contentDescription = "Удалить",
                            modifier = Modifier.size(18.dp),
                            tint = if (canDelete) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.outline,
                        )
                    }
                }
            }
            // Превью первых 3 полей
            profile.fields.entries.take(3).forEach { (k, v) ->
                Text(
                    text = "$k: $v",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            if (profile.fields.size > 3) {
                Text(
                    text = "…ещё ${profile.fields.size - 3} поля",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        }
    }
}

// ── Диалог создания / редактирования профиля ──────────────────────────────────

@Composable
private fun ProfileEditDialog(
    profile: UserProfileConfig?,
    onSave: (UserProfileConfig) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember(profile?.id) { mutableStateOf(profile?.name ?: "") }
    var fields by remember(profile?.id) { mutableStateOf<Map<String, String>>(profile?.fields ?: emptyMap()) }
    var newKey by remember { mutableStateOf("") }
    var newValue by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (profile == null) "Новый профиль" else "Редактировать профиль") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Имя профиля") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )

                if (fields.isNotEmpty()) {
                    HorizontalDivider()
                    Text(
                        text = "Поля профиля",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.outline,
                    )
                    fields.entries.toList().forEach { (k, v) ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = k,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.secondary,
                                )
                                Text(
                                    text = v,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                            IconButton(
                                onClick = { fields = fields - k },
                                modifier = Modifier.size(28.dp),
                            ) {
                                Icon(
                                    imageVector = PersIconClose,
                                    contentDescription = "Удалить поле",
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    }
                }

                HorizontalDivider()
                Text(
                    text = "Добавить поле",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.outline,
                )
                OutlinedTextField(
                    value = newKey,
                    onValueChange = { newKey = it },
                    label = { Text("Ключ (напр. «стиль ответа»)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = newValue,
                    onValueChange = { newValue = it },
                    label = { Text("Значение") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Button(
                    onClick = {
                        if (newKey.isNotBlank() && newValue.isNotBlank()) {
                            fields = fields + mapOf(newKey.trim() to newValue.trim())
                            newKey = ""
                            newValue = ""
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = newKey.isNotBlank() && newValue.isNotBlank(),
                ) {
                    Icon(imageVector = PersIconAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Добавить поле")
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        onSave(
                            UserProfileConfig(
                                id = profile?.id ?: "custom_${(100000..999999).random()}",
                                name = name.trim(),
                                fields = fields,
                            )
                        )
                    }
                },
                enabled = name.isNotBlank(),
            ) {
                Text("Сохранить")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        },
    )
}

// ── Пузыри сообщений ──────────────────────────────────────────────────────────

@Composable
private fun MessageBubble(message: PersonalizationContract.DisplayMessage) {
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
                    )
                )
                .background(
                    if (isUser) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.secondaryContainer
                )
                .padding(horizontal = 12.dp, vertical = 8.dp),
            color = if (isUser) MaterialTheme.colorScheme.onPrimaryContainer
            else MaterialTheme.colorScheme.onSecondaryContainer,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun LoadingBubble() {
    val infiniteTransition = rememberInfiniteTransition()
    val alpha by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = 0.2f,
        animationSpec = infiniteRepeatable(
            animation = keyframes { durationMillis = 800; 1f at 0; 0.2f at 400; 1f at 800 }
        ),
    )
    Box(
        modifier = Modifier
            .widthIn(max = 100.dp)
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
