package ru.nikitaluga.aichallenge.support

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import ru.nikitaluga.aichallenge.api.RouterAiApiService
import ru.nikitaluga.aichallenge.rag.RagChunk
import ru.nikitaluga.aichallenge.rag.RagIndexFile
import ru.nikitaluga.aichallenge.rag.StrategyStats
import java.io.File
import java.time.Instant
import kotlin.math.sqrt

private const val EMBEDDING_MODEL = "openai/text-embedding-3-small"
private const val MAX_CHUNK_WORDS = 400

// ── Index repository ──────────────────────────────────────────────────────────

class SupportIndexRepository(filePath: String = "support_index.json") {
    private val json = Json { prettyPrint = false; ignoreUnknownKeys = true; encodeDefaults = true }
    private val file = File(filePath)
    private val mutex = Mutex()

    @Volatile private var cached: RagIndexFile? = null

    suspend fun load(): RagIndexFile? = mutex.withLock {
        if (cached != null) return@withLock cached
        if (!file.exists()) return@withLock null
        runCatching {
            json.decodeFromString(RagIndexFile.serializer(), file.readText()).also { cached = it }
        }.getOrNull()
    }

    suspend fun save(index: RagIndexFile): Unit = mutex.withLock {
        file.writeText(json.encodeToString(RagIndexFile.serializer(), index))
        cached = index
    }
}

// ── Indexer ───────────────────────────────────────────────────────────────────

class SupportDocsIndexer(
    private val repository: SupportIndexRepository,
    private val apiService: RouterAiApiService,
) {

    suspend fun buildIndex() = withContext(Dispatchers.IO) {
        val faqText = loadFaqContent()
        if (faqText.isBlank()) return@withContext

        val chunks = chunkStructural(faqText, "faq.md", "faq")
        val embeddings = apiService.embed(chunks.map { it.text }, EMBEDDING_MODEL)
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
    }

    suspend fun search(query: String, k: Int = 5): List<SupportSearchResult> = withContext(Dispatchers.IO) {
        val index = repository.load() ?: return@withContext emptyList()
        val queryEmbedding = apiService.embed(listOf(query), EMBEDDING_MODEL).firstOrNull()
            ?: return@withContext emptyList()
        index.chunks
            .map { chunk -> chunk to cosineSimilarity(queryEmbedding, chunk.embedding) }
            .sortedByDescending { it.second }
            .take(k)
            .map { (chunk, score) ->
                SupportSearchResult(
                    chunkId = chunk.chunkId,
                    source = chunk.source,
                    section = chunk.section,
                    text = chunk.text,
                    score = score,
                )
            }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun loadFaqContent(): String =
        SupportDocsIndexer::class.java.getResourceAsStream("/support/faq.md")
            ?.bufferedReader()?.readText() ?: ""

    private fun chunkStructural(text: String, source: String, idBase: String): List<RagChunk> {
        val chunks = mutableListOf<RagChunk>()
        val lines = text.lines()
        var currentSection: String? = null
        var buffer = mutableListOf<String>()
        var chunkIdx = 0

        fun flush() {
            val body = buffer.joinToString("\n").trim()
            if (body.isBlank()) return
            val words = body.split(Regex("\\s+")).size
            if (words > MAX_CHUNK_WORDS) {
                val wordList = body.split(Regex("\\s+"))
                var start = 0
                while (start < wordList.size) {
                    val end = minOf(start + MAX_CHUNK_WORDS, wordList.size)
                    chunks.add(
                        RagChunk(
                            chunkId = "${idBase}_s${chunkIdx++}",
                            source = source,
                            section = currentSection,
                            strategy = "structural",
                            text = wordList.subList(start, end).joinToString(" "),
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

    private fun cosineSimilarity(a: List<Float>, b: List<Float>): Float {
        if (a.size != b.size || a.isEmpty()) return 0f
        var dot = 0f; var normA = 0f; var normB = 0f
        for (i in a.indices) { dot += a[i] * b[i]; normA += a[i] * a[i]; normB += b[i] * b[i] }
        val denom = sqrt(normA) * sqrt(normB)
        return if (denom == 0f) 0f else dot / denom
    }

    private fun String.wordCount() = trim().split(Regex("\\s+")).size
}

// ── Search result ─────────────────────────────────────────────────────────────

data class SupportSearchResult(
    val chunkId: String,
    val source: String,
    val section: String?,
    val text: String,
    val score: Float,
)
