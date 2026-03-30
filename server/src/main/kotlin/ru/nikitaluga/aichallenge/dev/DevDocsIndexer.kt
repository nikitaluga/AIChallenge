package ru.nikitaluga.aichallenge.dev

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.nikitaluga.aichallenge.api.RouterAiApiService
import ru.nikitaluga.aichallenge.rag.RagChunk
import ru.nikitaluga.aichallenge.rag.RagIndexFile
import ru.nikitaluga.aichallenge.rag.StrategyStats
import java.io.File
import java.time.Instant
import kotlin.math.sqrt

private const val EMBEDDING_MODEL = "openai/text-embedding-3-small"
private const val MAX_STRUCTURAL_CHUNK_WORDS = 600

// День 31 — DevDocs Indexer.
// Индексирует только документацию проекта: README.md, CLAUDE.md, docs/, .specs/
// Сохраняет результат в dev_docs_index.json.
class DevDocsIndexer(
    private val repository: DevDocsRepository,
    private val apiService: RouterAiApiService,
) {

    suspend fun buildIndex(): DevDocsIndexResponse = withContext(Dispatchers.IO) {
        val documents = collectDocuments()
        val chunks = mutableListOf<RagChunk>()

        documents.forEachIndexed { docIdx, (path, text) ->
            val idBase = "dev${docIdx.toString().padStart(3, '0')}"
            chunks += chunkStructural(text, path, idBase)
        }

        val texts = chunks.map { it.text }
        val embeddings = apiService.embed(texts, EMBEDDING_MODEL)

        val embeddedChunks = chunks.mapIndexed { i, chunk ->
            chunk.copy(embedding = embeddings.getOrElse(i) { emptyList() })
        }

        val index = RagIndexFile(
            model = EMBEDDING_MODEL,
            chunkSize = 0,
            overlap = 0,
            createdAt = Instant.now().toString(),
            strategies = mapOf(
                "structural" to StrategyStats(
                    chunkCount = embeddedChunks.size,
                    avgChunkSize = embeddedChunks.map { it.text.wordCount() }.average().toInt(),
                )
            ),
            chunks = embeddedChunks,
        )

        repository.save(index)

        DevDocsIndexResponse(
            success = true,
            totalChunks = embeddedChunks.size,
            docsIndexed = documents.size,
            message = "Проиндексировано ${documents.size} документов, ${embeddedChunks.size} чанков",
        )
    }

    suspend fun search(query: String, k: Int = 5): List<DevSearchResult> = withContext(Dispatchers.IO) {
        val index = repository.load() ?: return@withContext emptyList()
        val queryEmbedding = apiService.embed(listOf(query), EMBEDDING_MODEL).firstOrNull() ?: return@withContext emptyList()
        index.chunks
            .map { chunk -> chunk to cosineSimilarity(queryEmbedding, chunk.embedding) }
            .sortedByDescending { it.second }
            .take(k)
            .map { (chunk, score) ->
                DevSearchResult(
                    chunkId = chunk.chunkId,
                    source = chunk.source,
                    section = chunk.section,
                    text = chunk.text,
                    score = score,
                )
            }
    }

    // ── Document collection ───────────────────────────────────────────────────

    private fun collectDocuments(): List<Pair<String, String>> {
        val docs = mutableListOf<Pair<String, String>>()
        val root = detectProjectRoot()

        // README.md and CLAUDE.md at project root
        listOf("README.md", "CLAUDE.md").forEach { name ->
            val f = File(root, name)
            if (f.exists()) docs.add(name to f.readText())
        }

        // docs/ markdown files
        val docsDir = File(root, "docs")
        if (docsDir.exists()) {
            docsDir.walkTopDown()
                .filter { it.isFile && it.extension.equals("md", ignoreCase = true) }
                .sortedBy { it.path }
                .forEach { f ->
                    val rel = "docs/${f.relativeTo(docsDir).path}"
                    docs.add(rel to f.readText())
                }
        }

        // .specs/ markdown files
        val specsDir = File(root, ".specs")
        if (specsDir.exists()) {
            specsDir.walkTopDown()
                .filter { it.isFile && it.extension.equals("md", ignoreCase = true) }
                .sortedBy { it.path }
                .forEach { f ->
                    val rel = ".specs/${f.relativeTo(specsDir).path}"
                    docs.add(rel to f.readText())
                }
        }

        return docs
    }

    private fun detectProjectRoot(): File {
        val cwd = File(".").canonicalFile
        return when {
            File(cwd, ".specs").exists() -> cwd
            File(cwd.parentFile, ".specs").exists() -> cwd.parentFile
            else -> cwd
        }
    }

    // ── Structural chunking by markdown headers ───────────────────────────────

    private fun chunkStructural(text: String, source: String, idBase: String): List<RagChunk> {
        val chunks = mutableListOf<RagChunk>()
        val lines = text.lines()
        var currentSection: String? = null
        var buffer = mutableListOf<String>()
        var chunkIdx = 0

        fun flush() {
            val body = buffer.joinToString("\n").trim()
            if (body.isNotBlank()) {
                val words = body.split(Regex("\\s+")).size
                if (words > MAX_STRUCTURAL_CHUNK_WORDS) {
                    // Split large sections into sub-chunks
                    val wordList = body.split(Regex("\\s+"))
                    var start = 0
                    while (start < wordList.size) {
                        val end = minOf(start + MAX_STRUCTURAL_CHUNK_WORDS, wordList.size)
                        val sub = wordList.subList(start, end).joinToString(" ")
                        chunks.add(
                            RagChunk(
                                chunkId = "${idBase}_s${chunkIdx++}",
                                source = source,
                                section = currentSection,
                                strategy = "structural",
                                text = sub,
                            )
                        )
                        start = end
                    }
                } else {
                    chunks.add(
                        RagChunk(
                            chunkId = "${idBase}_s${chunkIdx++}",
                            source = source,
                            section = currentSection,
                            strategy = "structural",
                            text = body,
                        )
                    )
                }
            }
            buffer = mutableListOf()
        }

        for (line in lines) {
            val headerMatch = Regex("^(#{1,3})\\s+(.+)").find(line)
            if (headerMatch != null) {
                flush()
                currentSection = headerMatch.groupValues[2].trim()
                buffer.add(line)
            } else {
                buffer.add(line)
            }
        }
        flush()
        return chunks
    }

    // ── Cosine similarity ─────────────────────────────────────────────────────

    private fun cosineSimilarity(a: List<Float>, b: List<Float>): Float {
        if (a.size != b.size || a.isEmpty()) return 0f
        var dot = 0f; var normA = 0f; var normB = 0f
        for (i in a.indices) {
            dot += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }
        val denom = sqrt(normA) * sqrt(normB)
        return if (denom == 0f) 0f else dot / denom
    }

    private fun String.wordCount() = trim().split(Regex("\\s+")).size
}

// ── Models ────────────────────────────────────────────────────────────────────

data class DevDocsIndexResponse(
    val success: Boolean,
    val totalChunks: Int,
    val docsIndexed: Int,
    val message: String,
)

data class DevSearchResult(
    val chunkId: String,
    val source: String,
    val section: String?,
    val text: String,
    val score: Float,
)
