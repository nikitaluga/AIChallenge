package ru.nikitaluga.aichallenge.rag

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import ru.nikitaluga.aichallenge.api.RouterAiApiService
import java.io.File
import java.time.Instant
import kotlin.math.min
import kotlin.math.sqrt

private const val EMBEDDING_MODEL = "openai/text-embedding-3-small"
private const val MAX_STRUCTURAL_CHUNK_WORDS = 600

class RagIndexer(
    private val repository: RagRepository,
    private val apiService: RouterAiApiService,
) {

    // ── Public API ────────────────────────────────────────────────────────────

    suspend fun buildIndex(chunkSize: Int = 300, overlap: Int = 50): RagIndexResponse =
        withContext(Dispatchers.IO) {
            val documents = collectDocuments()
            val fixedChunks = mutableListOf<RagChunk>()
            val structuralChunks = mutableListOf<RagChunk>()

            documents.forEachIndexed { docIdx, (path, text, ext) ->
                val idBase = "doc${docIdx.toString().padStart(3, '0')}"
                fixedChunks += chunkFixed(text, path, chunkSize, overlap, "${idBase}_f")
                structuralChunks += chunkStructural(text, path, "${idBase}_s", ext)
            }

            val allChunks = fixedChunks + structuralChunks
            val texts = allChunks.map { it.text }

            // Generate embeddings in batches via routerai.ru
            val embeddings = apiService.embed(texts, EMBEDDING_MODEL)

            val embeddedChunks = allChunks.mapIndexed { i, chunk ->
                chunk.copy(embedding = embeddings.getOrElse(i) { emptyList() })
            }

            val fixedEmbedded = embeddedChunks.filter { it.strategy == "fixed" }
            val structuralEmbedded = embeddedChunks.filter { it.strategy == "structural" }

            val index = RagIndexFile(
                model = EMBEDDING_MODEL,
                chunkSize = chunkSize,
                overlap = overlap,
                createdAt = Instant.now().toString(),
                strategies = mapOf(
                    "fixed" to StrategyStats(
                        chunkCount = fixedEmbedded.size,
                        avgChunkSize = fixedEmbedded.map { it.text.wordCount() }.average().toInt(),
                    ),
                    "structural" to StrategyStats(
                        chunkCount = structuralEmbedded.size,
                        avgChunkSize = structuralEmbedded.map { it.text.wordCount() }.average().toInt(),
                    ),
                ),
                chunks = embeddedChunks,
            )

            repository.save(index)

            RagIndexResponse(
                success = true,
                totalChunks = embeddedChunks.size,
                fixedChunks = fixedEmbedded.size,
                structuralChunks = structuralEmbedded.size,
                message = "Проиндексировано ${documents.size} документов, ${embeddedChunks.size} чанков",
            )
        }

    suspend fun search(query: String, k: Int, strategy: String): List<RagSearchResult> {
        val index = repository.load() ?: return emptyList()
        val queryEmbedding = apiService.embed(listOf(query), EMBEDDING_MODEL).firstOrNull() ?: return emptyList()
        val candidates = if (strategy == "all") index.chunks else index.chunks.filter { it.strategy == strategy }
        return candidates
            .map { chunk -> chunk to cosineSimilarity(queryEmbedding, chunk.embedding) }
            .sortedByDescending { it.second }
            .take(k)
            .map { (chunk, score) ->
                RagSearchResult(
                    chunkId = chunk.chunkId,
                    source = chunk.source,
                    section = chunk.section,
                    strategy = chunk.strategy,
                    text = chunk.text,
                    score = score,
                )
            }
    }

    suspend fun buildContextAndAnswer(
        query: String,
        k: Int,
        strategy: String,
        model: String = "deepseek/deepseek-v3.2",
    ): RagChatResponse {
        val chunks = search(query, k, strategy)
        if (chunks.isEmpty()) {
            val answer = apiService.sendMessages(
                messages = listOf(
                    ru.nikitaluga.aichallenge.api.ChatMessage(role = "user", content = query)
                ),
                model = model,
            ).content
            return RagChatResponse(answer = answer, usedChunks = emptyList())
        }

        val context = chunks.joinToString("\n\n---\n\n") { chunk ->
            buildString {
                append("Источник: ${chunk.source}")
                if (chunk.section != null) append(" / ${chunk.section}")
                append("\n")
                append(chunk.text)
            }
        }

        val systemPrompt = """Ты помощник по документации проекта.
Используй только предоставленный контекст для ответа.
Если ответа нет в контексте — скажи об этом честно.

КОНТЕКСТ:
$context"""

        val answer = apiService.sendMessages(
            messages = listOf(
                ru.nikitaluga.aichallenge.api.ChatMessage(role = "system", content = systemPrompt),
                ru.nikitaluga.aichallenge.api.ChatMessage(role = "user", content = query),
            ),
            model = model,
        ).content

        return RagChatResponse(answer = answer, usedChunks = chunks)
    }

    suspend fun compare(
        query: String,
        k: Int,
        strategy: String,
        model: String = "deepseek/deepseek-v3.2",
    ): RagCompareResponse {
        // RAG answer: search + context + LLM
        val ragResult = buildContextAndAnswer(query, k, strategy, model)

        // No-RAG answer: plain LLM, no context
        val noRagAnswer = apiService.sendMessages(
            messages = listOf(
                ru.nikitaluga.aichallenge.api.ChatMessage(role = "user", content = query)
            ),
            model = model,
        ).content

        return RagCompareResponse(
            ragAnswer = ragResult.answer,
            noRagAnswer = noRagAnswer,
            usedChunks = ragResult.usedChunks,
        )
    }

    suspend fun compareEnhanced(
        query: String,
        k: Int,
        strategy: String,
        threshold: Float,
        topKBefore: Int,
        doRewriteQuery: Boolean,
        model: String = "deepseek/deepseek-v3.2",
    ): RagEnhancedCompareResponse {
        // 1. No-RAG answer
        val noRagAnswer = apiService.sendMessages(
            messages = listOf(ru.nikitaluga.aichallenge.api.ChatMessage(role = "user", content = query)),
            model = model,
        ).content

        // 2. RAG baseline (original pipeline: top-K, no filter, no rewrite)
        val baselineResult = buildContextAndAnswer(query, k, strategy, model)

        // 3. RAG enhanced: optional rewrite → top-KBefore → threshold filter → top-K
        val rewrittenQuery = if (doRewriteQuery) rewriteQuery(query, model) else null
        val searchQuery = rewrittenQuery ?: query

        val index = repository.load()
        val enhancedChunks: List<RagSearchResult>
        val filterStats: FilterStats
        if (index == null) {
            enhancedChunks = emptyList()
            filterStats = FilterStats(0, 0, threshold, rewrittenQuery)
        } else {
            val queryEmbedding = apiService.embed(listOf(searchQuery), EMBEDDING_MODEL).firstOrNull() ?: emptyList()
            val candidates = if (strategy == "all") index.chunks else index.chunks.filter { it.strategy == strategy }
            val scored = candidates
                .map { chunk -> chunk to cosineSimilarity(queryEmbedding, chunk.embedding) }
                .sortedByDescending { it.second }
                .take(topKBefore)

            val filtered = scored.filter { it.second >= threshold }.take(k)
            // Fallback: if nothing passed threshold, use top-K unfiltered
            val finalCandidates = if (filtered.isEmpty()) scored.take(k) else filtered

            enhancedChunks = finalCandidates.map { (chunk, score) ->
                RagSearchResult(
                    chunkId = chunk.chunkId,
                    source = chunk.source,
                    section = chunk.section,
                    strategy = chunk.strategy,
                    text = chunk.text,
                    score = score,
                )
            }
            filterStats = FilterStats(
                candidatesBefore = scored.size,
                candidatesAfter = filtered.size,
                threshold = threshold,
                rewrittenQuery = rewrittenQuery,
            )
        }

        val enhancedAnswer = if (enhancedChunks.isEmpty()) {
            apiService.sendMessages(
                messages = listOf(ru.nikitaluga.aichallenge.api.ChatMessage(role = "user", content = query)),
                model = model,
            ).content
        } else {
            val context = enhancedChunks.joinToString("\n\n---\n\n") { chunk ->
                buildString {
                    append("Источник: ${chunk.source}")
                    if (chunk.section != null) append(" / ${chunk.section}")
                    append("\n")
                    append(chunk.text)
                }
            }
            val systemPrompt = """Ты помощник по документации проекта.
Используй только предоставленный контекст для ответа.
Если ответа нет в контексте — скажи об этом честно.

КОНТЕКСТ:
$context"""
            apiService.sendMessages(
                messages = listOf(
                    ru.nikitaluga.aichallenge.api.ChatMessage(role = "system", content = systemPrompt),
                    ru.nikitaluga.aichallenge.api.ChatMessage(role = "user", content = query),
                ),
                model = model,
            ).content
        }

        return RagEnhancedCompareResponse(
            noRagAnswer = noRagAnswer,
            ragBaselineAnswer = baselineResult.answer,
            ragEnhancedAnswer = enhancedAnswer,
            baselineChunks = baselineResult.usedChunks,
            enhancedChunks = enhancedChunks,
            filterStats = filterStats,
        )
    }

    suspend fun buildContextAndAnswerV2(
        query: String,
        k: Int,
        strategy: String,
        threshold: Float = 0.35f,
        model: String = "deepseek/deepseek-v3.2",
    ): RagChatV2Response {
        val chunks = search(query, k, strategy)

        val maxScore = chunks.maxOfOrNull { it.score } ?: 0f
        if (chunks.isEmpty() || maxScore < threshold) {
            return RagChatV2Response(
                answer = "Я не знаю. Пожалуйста, уточните вопрос или расширьте базу знаний.",
                usedChunks = emptyList(),
                sources = emptyList(),
                citations = emptyList(),
                belowThreshold = true,
            )
        }

        val context = chunks.joinToString("\n\n---\n\n") { chunk ->
            buildString {
                append("[chunkId: ${chunk.chunkId}] Источник: ${chunk.source}")
                if (chunk.section != null) append(" / ${chunk.section}")
                append("\n")
                append(chunk.text)
            }
        }

        val systemPrompt = """Ты — ассистент с доступом к базе знаний. Тебе предоставлены фрагменты документации.
Отвечай ТОЛЬКО на основе этих фрагментов.

ПРАВИЛА:
1. Верни ответ строго в формате JSON (без markdown-блоков, без пояснений вне JSON):
{"answer":"...","sources":[{"chunkId":"...","source":"...","section":"..."}],"citations":[{"text":"...","chunkId":"..."}]}
2. В "sources" перечисли все chunkId, которые использовал для ответа.
3. В "citations" приведи дословные короткие фрагменты (1-2 предложения) из chunks, подтверждающие ответ. Укажи chunkId цитируемого chunk.
4. Если фрагменты не содержат ответа — answer="Я не знаю. Пожалуйста, уточните вопрос.", sources=[], citations=[].
5. НЕ придумывай информацию, которой нет в chunks.

КОНТЕКСТ:
$context"""

        val rawAnswer = apiService.sendMessages(
            messages = listOf(
                ru.nikitaluga.aichallenge.api.ChatMessage(role = "system", content = systemPrompt),
                ru.nikitaluga.aichallenge.api.ChatMessage(role = "user", content = query),
            ),
            model = model,
        ).content

        return parseV2Response(rawAnswer, chunks)
    }

    suspend fun buildContextAndAnswerV3(
        query: String,
        history: List<RagHistoryMessageV3>,
        taskMemory: TaskMemoryV3,
        k: Int,
        strategy: String,
        threshold: Float = 0.35f,
        model: String = "deepseek/deepseek-v3.2",
    ): RagChatV3Response {
        val chunks = search(query, k, strategy)
        val maxScore = chunks.maxOfOrNull { it.score } ?: 0f
        val belowThreshold = chunks.isEmpty() || maxScore < threshold

        // Ограничиваем историю: последние 6 сообщений, каждое не длиннее 300 символов
        val historyText = history.takeLast(6).joinToString("\n") {
            val prefix = if (it.role == "user") "Пользователь" else "Ассистент"
            val text = it.content.take(300).let { s -> if (it.content.length > 300) "$s…" else s }
            "$prefix: $text"
        }

        val taskMemoryText = buildString {
            append("Цель: ${if (taskMemory.goal.isNotEmpty()) taskMemory.goal else "не определена"}\n")
            append("Термины: ${if (taskMemory.terms.isNotEmpty()) taskMemory.terms.joinToString(", ") else "нет"}\n")
            append("Ограничения: ${if (taskMemory.constraints.isNotEmpty()) taskMemory.constraints.joinToString(", ") else "нет"}")
        }

        // Ограничиваем каждый чанк до 600 символов, чтобы уложиться в лимит токенов
        val contextSection = if (!belowThreshold) {
            val context = chunks.joinToString("\n\n---\n\n") { chunk ->
                buildString {
                    append("[chunkId: ${chunk.chunkId}] Источник: ${chunk.source}")
                    if (chunk.section != null) append(" / ${chunk.section}")
                    append("\n")
                    append(chunk.text.take(600))
                }
            }
            "\n\nБАЗА ЗНАНИЙ (релевантные фрагменты):\n$context"
        } else ""

        val noKnowledgeRule = if (belowThreshold) {
            "6. База знаний не содержит релевантного ответа — answer=\"Я не знаю. Пожалуйста, уточните вопрос или расширьте базу знаний.\", sources=[], citations=[]."
        } else {
            "6. Если фрагменты не содержат ответа — answer=\"Я не знаю. Пожалуйста, уточните вопрос.\", sources=[], citations=[]."
        }

        val systemPrompt = """Ты — ассистент с памятью задачи и доступом к базе знаний.

ТЕКУЩАЯ ПАМЯТЬ ЗАДАЧИ:
$taskMemoryText

ИСТОРИЯ ДИАЛОГА:
$historyText$contextSection

ПРАВИЛА:
1. Отвечай на основе базы знаний и истории диалога.
2. Верни строго JSON без markdown-блоков:
{"answer":"...","sources":[{"chunkId":"...","source":"...","section":"..."}],"citations":[{"text":"...","chunkId":"..."}],"taskMemory":{"goal":"...","terms":["..."],"constraints":["..."]}}
3. В taskMemory.goal — одно предложение, описывающее цель пользователя в этом диалоге.
4. В taskMemory.terms — список ключевых терминов и понятий из диалога.
5. В taskMemory.constraints — явные ограничения или требования пользователя.
$noKnowledgeRule
7. НЕ придумывай информацию, которой нет в базе знаний."""

        val rawAnswer = apiService.sendMessages(
            messages = listOf(
                ru.nikitaluga.aichallenge.api.ChatMessage(role = "system", content = systemPrompt),
                ru.nikitaluga.aichallenge.api.ChatMessage(role = "user", content = query),
            ),
            model = model,
            maxTokens = 2048,
        ).content

        return parseV3Response(rawAnswer, chunks, belowThreshold, taskMemory)
    }

    private fun parseV3Response(
        rawAnswer: String,
        usedChunks: List<RagSearchResult>,
        belowThreshold: Boolean,
        fallbackMemory: TaskMemoryV3,
    ): RagChatV3Response {
        val jsonStr = extractJsonObject(rawAnswer)
        return runCatching {
            val jsonParser = Json { ignoreUnknownKeys = true }
            val obj = jsonParser.parseToJsonElement(jsonStr).jsonObject
            val answer = obj["answer"]?.jsonPrimitive?.contentOrNull ?: rawAnswer
            val sources = obj["sources"]?.jsonArray?.mapNotNull { src ->
                runCatching {
                    val s = src.jsonObject
                    RagSourceV2(
                        chunkId = s["chunkId"]?.jsonPrimitive?.contentOrNull ?: "",
                        source = s["source"]?.jsonPrimitive?.contentOrNull ?: "",
                        section = s["section"]?.jsonPrimitive?.contentOrNull,
                    )
                }.getOrNull()
            } ?: emptyList()
            val citations = obj["citations"]?.jsonArray?.mapNotNull { cit ->
                runCatching {
                    val c = cit.jsonObject
                    RagCitationV2(
                        text = c["text"]?.jsonPrimitive?.contentOrNull ?: "",
                        chunkId = c["chunkId"]?.jsonPrimitive?.contentOrNull ?: "",
                    )
                }.getOrNull()
            } ?: emptyList()
            val memObj = obj["taskMemory"]?.jsonObject
            val parsedMemory = if (memObj != null) {
                TaskMemoryV3(
                    goal = memObj["goal"]?.jsonPrimitive?.contentOrNull ?: fallbackMemory.goal,
                    terms = memObj["terms"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: fallbackMemory.terms,
                    constraints = memObj["constraints"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: fallbackMemory.constraints,
                )
            } else fallbackMemory
            RagChatV3Response(
                answer = answer,
                usedChunks = usedChunks,
                sources = sources,
                citations = citations,
                belowThreshold = belowThreshold,
                taskMemory = parsedMemory,
            )
        }.getOrElse {
            RagChatV3Response(
                answer = rawAnswer,
                usedChunks = usedChunks,
                sources = if (!belowThreshold) usedChunks.map { RagSourceV2(it.chunkId, it.source, it.section) } else emptyList(),
                citations = emptyList(),
                belowThreshold = belowThreshold,
                taskMemory = fallbackMemory,
            )
        }
    }

    private fun parseV2Response(rawAnswer: String, usedChunks: List<RagSearchResult>): RagChatV2Response {
        val jsonStr = extractJsonObject(rawAnswer)
        return runCatching {
            val jsonParser = Json { ignoreUnknownKeys = true }
            val obj = jsonParser.parseToJsonElement(jsonStr).jsonObject
            val answer = obj["answer"]?.jsonPrimitive?.contentOrNull ?: rawAnswer
            val sources = obj["sources"]?.jsonArray?.mapNotNull { src ->
                runCatching {
                    val s = src.jsonObject
                    RagSourceV2(
                        chunkId = s["chunkId"]?.jsonPrimitive?.contentOrNull ?: "",
                        source = s["source"]?.jsonPrimitive?.contentOrNull ?: "",
                        section = s["section"]?.jsonPrimitive?.contentOrNull,
                    )
                }.getOrNull()
            } ?: emptyList()
            val citations = obj["citations"]?.jsonArray?.mapNotNull { cit ->
                runCatching {
                    val c = cit.jsonObject
                    RagCitationV2(
                        text = c["text"]?.jsonPrimitive?.contentOrNull ?: "",
                        chunkId = c["chunkId"]?.jsonPrimitive?.contentOrNull ?: "",
                    )
                }.getOrNull()
            } ?: emptyList()
            RagChatV2Response(
                answer = answer,
                usedChunks = usedChunks,
                sources = sources,
                citations = citations,
                belowThreshold = false,
            )
        }.getOrElse {
            RagChatV2Response(
                answer = rawAnswer,
                usedChunks = usedChunks,
                sources = usedChunks.map { RagSourceV2(it.chunkId, it.source, it.section) },
                citations = emptyList(),
                belowThreshold = false,
            )
        }
    }

    private fun extractJsonObject(text: String): String {
        val stripped = text.trim()
            .removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        val start = stripped.indexOf('{')
        val end = stripped.lastIndexOf('}')
        return if (start >= 0 && end > start) stripped.substring(start, end + 1) else stripped
    }

    private suspend fun rewriteQuery(query: String, model: String): String {
        val systemPrompt = "Ты помощник для семантического поиска по технической документации. " +
            "Переформулируй запрос пользователя для точного семантического поиска. " +
            "Сделай запрос более конкретным и техническим. Верни только переформулированный запрос без объяснений."
        return runCatching {
            apiService.sendMessages(
                messages = listOf(
                    ru.nikitaluga.aichallenge.api.ChatMessage(role = "system", content = systemPrompt),
                    ru.nikitaluga.aichallenge.api.ChatMessage(role = "user", content = query),
                ),
                model = model,
            ).content.trim()
        }.getOrDefault(query)
    }

    /** Возвращает чанки без эмбеддингов — для переиспользования в LocalRagIndexer. */
    internal suspend fun buildRawChunks(chunkSize: Int = 300, overlap: Int = 50): List<RagChunk> =
        withContext(Dispatchers.IO) {
            val documents = collectDocuments()
            val fixedChunks = mutableListOf<RagChunk>()
            val structuralChunks = mutableListOf<RagChunk>()
            documents.forEachIndexed { docIdx, (path, text, ext) ->
                val idBase = "doc${docIdx.toString().padStart(3, '0')}"
                fixedChunks += chunkFixed(text, path, chunkSize, overlap, "${idBase}_f")
                structuralChunks += chunkStructural(text, path, "${idBase}_s", ext)
            }
            fixedChunks + structuralChunks
        }

    // ── Document collection ────────────────────────────────────────────────────

    private fun collectDocuments(): List<Triple<String, String, String>> {
        val docs = mutableListOf<Triple<String, String, String>>() // (path, text, ext)
        val projectRoot = detectProjectRoot()

        // .specs/ markdown files
        File(projectRoot, ".specs").listSortedFiles("md") { true }.forEach { file ->
            file.readTextSafe()?.let { docs.add(Triple(".specs/${file.name}", it, "md")) }
        }

        // CLAUDE.md and README.md
        listOf("CLAUDE.md", "README.md").forEach { name ->
            File(projectRoot, name).readTextSafe()?.let { docs.add(Triple(name, it, "md")) }
        }

        // Kotlin shared source files
        File(projectRoot, "shared/src/commonMain").walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .sortedBy { it.path }
            .forEach { file ->
                file.readTextSafe()?.let {
                    val relPath = file.relativeTo(projectRoot).path
                    docs.add(Triple(relPath, it, "kt"))
                }
            }

        // User-added docs/ folder (md, txt, pdf)
        val docsDir = File("docs").also { it.mkdirs() }
        docsDir.listSortedFiles("md", "txt", "pdf") { it.length() < 5_000_000 }.forEach { file ->
            val text = when (file.extension.lowercase()) {
                "pdf" -> extractPdfText(file)
                else -> file.readTextSafe()
            }
            text?.let { docs.add(Triple("docs/${file.name}", it, file.extension)) }
        }

        return docs
    }

    private fun detectProjectRoot(): File {
        // When running via `gradle :server:run`, CWD = server module dir
        // When running the assembled jar, CWD can vary
        // .specs/ is at the project root, try parent first
        val cwd = File(".").canonicalFile
        return when {
            File(cwd, ".specs").exists() -> cwd            // CWD == project root
            File(cwd.parentFile, ".specs").exists() -> cwd.parentFile  // CWD == server/
            else -> cwd                                    // fallback
        }
    }

    private fun extractPdfText(file: File): String? = runCatching {
        // PDFBox extraction — import lazily to avoid hard dependency failure
        val pdDocumentClass = Class.forName("org.apache.pdfbox.pdmodel.PDDocument")
        val stripper = Class.forName("org.apache.pdfbox.text.PDFTextStripper").getDeclaredConstructor().newInstance()
        val load = pdDocumentClass.getMethod("load", File::class.java)
        val getText = stripper.javaClass.getMethod("getText", pdDocumentClass)
        val close = pdDocumentClass.getMethod("close")
        val doc = load.invoke(null, file)
        val text = getText.invoke(stripper, doc) as String
        close.invoke(doc)
        text
    }.getOrNull()

    // ── Chunking strategies ────────────────────────────────────────────────────

    private fun chunkFixed(
        text: String,
        source: String,
        chunkSize: Int,
        overlap: Int,
        idPrefix: String,
    ): List<RagChunk> {
        val words = text.split(Regex("\\s+")).filter { it.isNotBlank() }
        if (words.isEmpty()) return emptyList()
        val chunks = mutableListOf<RagChunk>()
        var idx = 0
        var chunkNum = 0
        val step = (chunkSize - overlap).coerceAtLeast(1)
        while (idx < words.size) {
            val end = min(idx + chunkSize, words.size)
            val chunkText = words.subList(idx, end).joinToString(" ")
            chunks.add(
                RagChunk(
                    chunkId = "${idPrefix}_${chunkNum.toString().padStart(4, '0')}",
                    source = source,
                    section = null,
                    strategy = "fixed",
                    text = chunkText,
                )
            )
            chunkNum++
            idx += step
        }
        return chunks
    }

    private fun chunkStructural(
        text: String,
        source: String,
        idPrefix: String,
        ext: String,
    ): List<RagChunk> = when (ext.lowercase()) {
        "md" -> chunkMarkdown(text, source, idPrefix)
        "kt" -> chunkKotlin(text, source, idPrefix)
        else -> listOf(
            RagChunk(
                chunkId = "${idPrefix}_0000",
                source = source,
                section = null,
                strategy = "structural",
                text = text.take(3000),
            )
        )
    }

    private fun chunkMarkdown(text: String, source: String, idPrefix: String): List<RagChunk> {
        // Split on H1/H2/H3 headings
        val sections = mutableListOf<Pair<String?, StringBuilder>>()
        var currentHeader: String? = null
        var currentContent = StringBuilder()

        for (line in text.lines()) {
            val headerMatch = Regex("^(#{1,3})\\s+(.+)").matchEntire(line)
            if (headerMatch != null) {
                if (currentContent.isNotBlank()) {
                    sections.add(currentHeader to currentContent)
                }
                currentHeader = headerMatch.groupValues[2].trim()
                currentContent = StringBuilder()
                currentContent.appendLine(line)
            } else {
                currentContent.appendLine(line)
            }
        }
        if (currentContent.isNotBlank()) {
            sections.add(currentHeader to currentContent)
        }

        // Split oversized sections further
        val result = mutableListOf<RagChunk>()
        var chunkNum = 0
        sections.forEach { (header, content) ->
            val words = content.toString().split(Regex("\\s+")).filter { it.isNotBlank() }
            if (words.size <= MAX_STRUCTURAL_CHUNK_WORDS) {
                result.add(
                    RagChunk(
                        chunkId = "${idPrefix}_${chunkNum.toString().padStart(4, '0')}",
                        source = source,
                        section = header,
                        strategy = "structural",
                        text = content.toString().trim(),
                    )
                )
                chunkNum++
            } else {
                // Sub-chunk large sections by paragraph
                content.toString().split(Regex("\n{2,}")).filter { it.isNotBlank() }.forEach { para ->
                    result.add(
                        RagChunk(
                            chunkId = "${idPrefix}_${chunkNum.toString().padStart(4, '0')}",
                            source = source,
                            section = header,
                            strategy = "structural",
                            text = para.trim(),
                        )
                    )
                    chunkNum++
                }
            }
        }
        return result
    }

    private fun chunkKotlin(text: String, source: String, idPrefix: String): List<RagChunk> {
        // Split by top-level declarations
        val declarationPattern = Regex(
            "^((?:private |public |internal |protected |open |abstract |sealed |data |inline |suspend |override |companion )*" +
            "(?:fun |class |object |interface |val |var |typealias ))",
            setOf(RegexOption.MULTILINE)
        )

        val matches = declarationPattern.findAll(text).toList()
        if (matches.isEmpty()) {
            return listOf(
                RagChunk(
                    chunkId = "${idPrefix}_0000",
                    source = source,
                    section = null,
                    strategy = "structural",
                    text = text.take(2000),
                )
            )
        }

        val chunks = mutableListOf<RagChunk>()
        // First chunk = package + imports + everything before first declaration
        val preamble = text.substring(0, matches.first().range.first).trim()
        if (preamble.isNotBlank()) {
            chunks.add(
                RagChunk(
                    chunkId = "${idPrefix}_0000",
                    source = source,
                    section = "imports",
                    strategy = "structural",
                    text = preamble,
                )
            )
        }

        matches.forEachIndexed { idx, match ->
            val start = match.range.first
            val end = if (idx + 1 < matches.size) matches[idx + 1].range.first else text.length
            val chunkText = text.substring(start, end).trim()
            // Extract declaration name
            val nameMatch = Regex("(?:fun |class |object |interface |val |var )(\\w+)").find(chunkText)
            val section = nameMatch?.groupValues?.getOrNull(1)

            if (chunkText.isNotBlank()) {
                chunks.add(
                    RagChunk(
                        chunkId = "${idPrefix}_${(idx + 1).toString().padStart(4, '0')}",
                        source = source,
                        section = section,
                        strategy = "structural",
                        text = chunkText.take(2000),
                    )
                )
            }
        }
        return chunks
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun cosineSimilarity(a: List<Float>, b: List<Float>): Float {
        if (a.isEmpty() || b.isEmpty() || a.size != b.size) return 0f
        var dot = 0.0
        var normA = 0.0
        var normB = 0.0
        for (i in a.indices) {
            dot += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }
        val denom = sqrt(normA) * sqrt(normB)
        return if (denom == 0.0) 0f else (dot / denom).toFloat()
    }

    private fun String.wordCount() = split(Regex("\\s+")).count { it.isNotBlank() }

    private fun File.readTextSafe(): String? = runCatching { readText() }.getOrNull()

    private fun File.listSortedFiles(vararg extensions: String, filter: (File) -> Boolean): List<File> =
        listFiles()
            ?.filter { it.isFile && (extensions.isEmpty() || it.extension.lowercase() in extensions) && filter(it) }
            ?.sortedBy { it.name }
            ?: emptyList()
}
