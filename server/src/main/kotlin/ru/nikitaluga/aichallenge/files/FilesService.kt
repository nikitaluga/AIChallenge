package ru.nikitaluga.aichallenge.files

import ru.nikitaluga.aichallenge.api.ChatMessage
import ru.nikitaluga.aichallenge.api.RouterAiApiService
import ru.nikitaluga.aichallenge.api.ToolDefinition
import ru.nikitaluga.aichallenge.api.ToolFunction
import ru.nikitaluga.aichallenge.api.ToolParameters
import ru.nikitaluga.aichallenge.api.ToolProperty
import kotlinx.serialization.json.Json
import java.io.File

private const val CHAT_MODEL = "openai/gpt-4o-mini"
private const val MAX_TOOL_ITERATIONS = 5
private const val MAX_FILE_SIZE_BYTES = 50 * 1024

private val WRITE_TRIGGER_WORDS = listOf("сохрани", "запиши", "примени")

internal val json = Json { ignoreUnknownKeys = true; explicitNulls = false; encodeDefaults = true }

internal suspend fun runFilesToolLoop(
    request: FilesChatRequest,
    apiService: RouterAiApiService,
): FilesChatResponse {
    val systemPrompt = """
        Ты — файловый ассистент проекта AIChallenge (KMP/Ktor/Compose Multiplatform).
        Используй инструменты для работы с файлами: read_file, search_in_files, write_file, generate_diff.
        ВАЖНО: при изменении файлов — сначала показывай diff, не записывай без явного подтверждения пользователя.
        Отвечай на русском языке. Ссылайся на конкретные файлы и строки.
    """.trimIndent()

    val messages = mutableListOf<ChatMessage>()
    messages.add(ChatMessage(role = "system", content = systemPrompt))
    request.history.takeLast(10).forEach { msg ->
        messages.add(ChatMessage(role = msg.role, content = msg.content))
    }
    messages.add(ChatMessage(role = "user", content = request.query))

    val tools = buildFileToolDefinitions()
    val toolsUsed = mutableListOf<String>()
    val diffs = mutableListOf<FileDiff>()

    val userWantsWrite = WRITE_TRIGGER_WORDS.any { request.query.contains(it, ignoreCase = true) }

    var iteration = 0
    while (iteration < MAX_TOOL_ITERATIONS) {
        iteration++
        val result = apiService.sendMessagesWithTools(messages = messages, tools = tools, model = CHAT_MODEL)

        val currentToolCalls = result.toolCalls
        if (result.finishReason == "tool_calls" && !currentToolCalls.isNullOrEmpty()) {
            messages.add(ChatMessage(role = "assistant", content = result.content, toolCalls = currentToolCalls))

            currentToolCalls.forEach { toolCall ->
                val toolName = toolCall.function.name
                toolsUsed.add(toolName)
                val inputMap = runCatching {
                    json.decodeFromString<Map<String, String>>(toolCall.function.arguments)
                }.getOrDefault(emptyMap())

                val toolResult = executeFileTool(toolName, inputMap, request.useDryRun, userWantsWrite, diffs)
                messages.add(
                    ChatMessage(
                        role = "tool",
                        content = toolResult,
                        toolCallId = toolCall.id,
                        name = toolCall.function.name,
                    )
                )
            }
        } else {
            return FilesChatResponse(
                answer = result.content ?: "",
                toolsUsed = toolsUsed,
                diffs = diffs,
            )
        }
    }

    val finalResult = apiService.sendMessages(messages = messages, model = CHAT_MODEL)
    return FilesChatResponse(
        answer = finalResult.content ?: "",
        toolsUsed = toolsUsed,
        diffs = diffs,
    )
}

private fun executeFileTool(
    name: String,
    input: Map<String, String>,
    useDryRun: Boolean,
    userWantsWrite: Boolean,
    diffs: MutableList<FileDiff>,
): String = when (name) {
    "read_file" -> input["path"]
        ?.let { readFileSandboxed(it) }
        ?: "Ошибка: параметр 'path' обязателен"
    "search_in_files" -> input["pattern"]
        ?.let { searchInFiles(it, input["glob"] ?: "**/*.kt", input["max_results"]?.toIntOrNull() ?: 50) }
        ?: "Ошибка: параметр 'pattern' обязателен"
    "write_file" -> {
        val path = input["path"]
        val content = input["content"]
        when {
            path == null -> "Ошибка: параметр 'path' обязателен"
            content == null -> "Ошибка: параметр 'content' обязателен"
            else -> writeFileTool(path, content, useDryRun, userWantsWrite, diffs)
        }
    }
    "generate_diff" -> {
        val path = input["path"]
        val newContent = input["new_content"]
        when {
            path == null -> "Ошибка: параметр 'path' обязателен"
            newContent == null -> "Ошибка: параметр 'new_content' обязателен"
            else -> generateDiff(path, newContent, diffs)
        }
    }
    else -> "Инструмент '$name' не найден"
}

private fun readFileSandboxed(path: String): String {
    val root = detectProjectRoot()
    val target = runCatching { File(root, path).canonicalFile }.getOrElse {
        return "Ошибка: некорректный путь '$path'"
    }
    if (!target.canonicalPath.startsWith(root.canonicalPath)) {
        return "Ошибка: доступ за пределы проекта запрещён"
    }
    if (!target.exists()) return "Файл не найден: $path"
    if (!target.isFile) return "Указанный путь не является файлом: $path"
    if (target.length() > MAX_FILE_SIZE_BYTES) {
        return "Файл слишком большой (${target.length()} байт, лимит ${MAX_FILE_SIZE_BYTES} байт): $path"
    }
    return runCatching { target.readText() }.getOrElse { "Ошибка чтения файла: ${it.message}" }
}

private fun searchInFiles(pattern: String, glob: String, maxResults: Int): String {
    val root = detectProjectRoot()
    val regex = runCatching { Regex(pattern) }.getOrElse {
        return "Ошибка: невалидное regex-выражение '$pattern': ${it.message}"
    }
    val extension = glob.substringAfterLast("*.").let { if (it == glob) null else ".$it" }
    val matches = mutableListOf<String>()

    root.walkTopDown()
        .filter { it.isFile }
        .filter { extension == null || it.name.endsWith(extension) }
        .forEach { file ->
            if (matches.size >= maxResults) return@forEach
            runCatching {
                file.readLines().forEachIndexed { idx, line ->
                    if (matches.size < maxResults && regex.containsMatchIn(line)) {
                        val relativePath = file.relativeTo(root).path
                        matches.add("$relativePath:${idx + 1}: $line")
                    }
                }
            }
        }

    return if (matches.isEmpty()) "(совпадений не найдено)"
    else matches.take(100).joinToString("\n")
}

private fun writeFileTool(
    path: String,
    content: String,
    useDryRun: Boolean,
    userWantsWrite: Boolean,
    diffs: MutableList<FileDiff>,
): String {
    val shouldWrite = !useDryRun || userWantsWrite
    return if (shouldWrite) {
        val root = detectProjectRoot()
        val target = runCatching { File(root, path).canonicalFile }.getOrElse {
            return "Ошибка: некорректный путь '$path'"
        }
        if (!target.canonicalPath.startsWith(root.canonicalPath)) {
            return "Ошибка: запись за пределы проекта запрещёна"
        }
        runCatching {
            target.parentFile?.mkdirs()
            target.writeText(content)
            "Файл успешно записан: $path"
        }.getOrElse { "Ошибка записи файла: ${it.message}" }
    } else {
        generateDiff(path, content, diffs)
    }
}

private fun generateDiff(path: String, newContent: String, diffs: MutableList<FileDiff>): String {
    val root = detectProjectRoot()
    val target = runCatching { File(root, path).canonicalFile }.getOrElse {
        return "Ошибка: некорректный путь '$path'"
    }
    val oldContent = if (target.exists() && target.isFile) target.readText() else ""
    val diff = buildUnifiedDiff(path, oldContent, newContent)
    if (diff.isNotBlank()) {
        diffs.removeAll { it.path == path }
        diffs.add(FileDiff(path = path, diff = diff))
    }
    return diff.ifBlank { "(файл не изменился)" }
}

private fun buildUnifiedDiff(path: String, oldText: String, newText: String): String {
    if (oldText == newText) return ""
    val oldLines = oldText.lines()
    val newLines = newText.lines()
    val sb = StringBuilder()
    sb.appendLine("--- a/$path")
    sb.appendLine("+++ b/$path")

    val hunks = computeHunks(oldLines, newLines)
    for (hunk in hunks) {
        sb.append(hunk)
    }
    return sb.toString().trimEnd()
}

private fun computeHunks(oldLines: List<String>, newLines: List<String>): List<String> {
    // Simple LCS-based diff
    val m = oldLines.size
    val n = newLines.size
    val lcs = Array(m + 1) { IntArray(n + 1) }
    for (i in 1..m) {
        for (j in 1..n) {
            lcs[i][j] = if (oldLines[i - 1] == newLines[j - 1]) lcs[i - 1][j - 1] + 1
            else maxOf(lcs[i - 1][j], lcs[i][j - 1])
        }
    }

    // Backtrack to get edit script
    data class Edit(val kind: Char, val line: String, val oldIdx: Int, val newIdx: Int)
    val edits = mutableListOf<Edit>()
    var i = m; var j = n
    while (i > 0 || j > 0) {
        when {
            i > 0 && j > 0 && oldLines[i - 1] == newLines[j - 1] -> {
                edits.add(Edit(' ', oldLines[i - 1], i - 1, j - 1))
                i--; j--
            }
            j > 0 && (i == 0 || lcs[i][j - 1] >= lcs[i - 1][j]) -> {
                edits.add(Edit('+', newLines[j - 1], i - 1, j - 1))
                j--
            }
            else -> {
                edits.add(Edit('-', oldLines[i - 1], i - 1, j - 1))
                i--
            }
        }
    }
    edits.reverse()

    // Group into hunks (context = 3)
    val CONTEXT = 3
    data class HunkRange(val startEdit: Int, val endEdit: Int)
    val hunks = mutableListOf<HunkRange>()
    var hunkStart = -1
    var lastChange = -1
    edits.forEachIndexed { idx, edit ->
        if (edit.kind != ' ') {
            if (hunkStart < 0) hunkStart = maxOf(0, idx - CONTEXT)
            lastChange = idx
        }
    }
    // Simple approach: one hunk per changed range
    var start = -1
    var end = -1
    for (idx in edits.indices) {
        if (edits[idx].kind != ' ') {
            if (start < 0) start = maxOf(0, idx - CONTEXT)
            end = minOf(edits.size - 1, idx + CONTEXT)
        }
    }
    if (start >= 0) hunks.add(HunkRange(start, end))

    return hunks.map { range ->
        val hunkEdits = edits.subList(range.startEdit, range.endEdit + 1)
        val oldStart = hunkEdits.firstOrNull { it.kind != '+' }?.oldIdx?.plus(1) ?: 1
        val newStart = hunkEdits.firstOrNull { it.kind != '-' }?.newIdx?.plus(1) ?: 1
        val oldCount = hunkEdits.count { it.kind != '+' }
        val newCount = hunkEdits.count { it.kind != '-' }
        val sb = StringBuilder()
        sb.appendLine("@@ -$oldStart,$oldCount +$newStart,$newCount @@")
        hunkEdits.forEach { edit -> sb.appendLine("${edit.kind}${edit.line}") }
        sb.toString()
    }
}

internal fun detectProjectRoot(): File {
    val cwd = File(".").canonicalFile
    return when {
        File(cwd, ".specs").exists() -> cwd
        File(cwd.parentFile, ".specs").exists() -> cwd.parentFile
        else -> cwd
    }
}

private fun buildFileToolDefinitions(): List<ToolDefinition> = listOf(
    ToolDefinition(
        function = ToolFunction(
            name = "read_file",
            description = "Прочитать содержимое файла проекта (максимум 50 KB). Только файлы внутри корня проекта.",
            parameters = ToolParameters(
                properties = mapOf(
                    "path" to ToolProperty(
                        type = "string",
                        description = "Относительный путь от корня проекта, например 'server/src/main/kotlin/Application.kt'",
                    )
                ),
                required = listOf("path"),
            ),
        )
    ),
    ToolDefinition(
        function = ToolFunction(
            name = "search_in_files",
            description = "Поиск по файлам проекта с помощью regex. Возвращает совпадения в формате 'файл:строка: текст'.",
            parameters = ToolParameters(
                properties = mapOf(
                    "pattern" to ToolProperty(
                        type = "string",
                        description = "Regex-паттерн для поиска",
                    ),
                    "glob" to ToolProperty(
                        type = "string",
                        description = "Glob-фильтр по расширению файлов, например '**/*.kt' или '**/*.kts'. По умолчанию '**/*.kt'",
                    ),
                    "max_results" to ToolProperty(
                        type = "string",
                        description = "Максимальное количество результатов (число, по умолчанию 50)",
                    ),
                ),
                required = listOf("pattern"),
            ),
        )
    ),
    ToolDefinition(
        function = ToolFunction(
            name = "write_file",
            description = "Записать содержимое файла. Если useDryRun=true и пользователь не написал 'сохрани/запиши/примени' — покажет diff вместо записи.",
            parameters = ToolParameters(
                properties = mapOf(
                    "path" to ToolProperty(
                        type = "string",
                        description = "Относительный путь от корня проекта",
                    ),
                    "content" to ToolProperty(
                        type = "string",
                        description = "Новое содержимое файла",
                    ),
                ),
                required = listOf("path", "content"),
            ),
        )
    ),
    ToolDefinition(
        function = ToolFunction(
            name = "generate_diff",
            description = "Сгенерировать unified diff для предлагаемых изменений файла без реальной записи.",
            parameters = ToolParameters(
                properties = mapOf(
                    "path" to ToolProperty(
                        type = "string",
                        description = "Относительный путь от корня проекта",
                    ),
                    "new_content" to ToolProperty(
                        type = "string",
                        description = "Новое содержимое файла",
                    ),
                ),
                required = listOf("path", "new_content"),
            ),
        )
    ),
)
