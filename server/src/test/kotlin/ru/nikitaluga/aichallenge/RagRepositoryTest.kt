package ru.nikitaluga.aichallenge

import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import ru.nikitaluga.aichallenge.rag.RagChunk
import ru.nikitaluga.aichallenge.rag.RagIndexFile
import ru.nikitaluga.aichallenge.rag.RagRepository
import ru.nikitaluga.aichallenge.rag.StrategyStats
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RagRepositoryTest {

    private lateinit var tempFile: File
    private lateinit var repository: RagRepository

    @Before
    fun setUp() {
        tempFile = File.createTempFile("rag_test_", ".json")
        tempFile.delete()
        repository = RagRepository(tempFile.absolutePath)
    }

    @After
    fun tearDown() {
        tempFile.delete()
    }

    @Test
    fun `load returns null when file does not exist`() = runTest {
        assertNull(repository.load())
    }

    @Test
    fun `save and load roundtrip preserves data`() = runTest {
        val index = RagIndexFile(
            version = 1,
            model = "text-embedding-3-small",
            chunkSize = 300,
            overlap = 50,
            createdAt = "2026-04-17",
            strategies = mapOf(
                "fixed" to StrategyStats(chunkCount = 2, avgChunkSize = 250),
                "structural" to StrategyStats(chunkCount = 3, avgChunkSize = 320),
            ),
            chunks = listOf(
                RagChunk(
                    chunkId = "c1",
                    source = "CLAUDE.md",
                    section = "Architecture",
                    strategy = "fixed",
                    text = "MVI + Clean Architecture",
                    embedding = listOf(0.1f, 0.2f, 0.3f),
                ),
            ),
        )

        repository.save(index)
        val loaded = repository.load()

        assertNotNull(loaded)
        assertEquals(index.model, loaded.model)
        assertEquals(1, loaded.chunks.size)
        assertEquals("c1", loaded.chunks.first().chunkId)
        assertEquals(listOf(0.1f, 0.2f, 0.3f), loaded.chunks.first().embedding)
    }

    @Test
    fun `getSync returns cached value after save`() = runTest {
        assertNull(repository.getSync())

        val index = RagIndexFile(
            version = 1,
            model = "model",
            chunkSize = 100,
            overlap = 10,
            createdAt = "2026-04-17",
        )
        repository.save(index)

        assertNotNull(repository.getSync())
        assertEquals("model", repository.getSync()?.model)
    }

    @Test
    fun `clear removes file and invalidates cache`() = runTest {
        val index = RagIndexFile(
            version = 1,
            model = "model",
            chunkSize = 100,
            overlap = 10,
            createdAt = "2026-04-17",
        )
        repository.save(index)
        assertTrue(tempFile.exists())

        repository.clear()

        assertNull(repository.getSync())
        assertNull(repository.load())
    }

    @Test
    fun `save overwrites existing file`() = runTest {
        val first = RagIndexFile(
            version = 1, model = "v1", chunkSize = 100, overlap = 10, createdAt = "2026-04-17",
            chunks = listOf(RagChunk("c1", "src", null, "fixed", "text1")),
        )
        val second = RagIndexFile(
            version = 2, model = "v2", chunkSize = 200, overlap = 20, createdAt = "2026-04-17",
            chunks = listOf(
                RagChunk("c2", "src", null, "structural", "text2"),
                RagChunk("c3", "src", null, "structural", "text3"),
            ),
        )

        repository.save(first)
        repository.save(second)

        val loaded = repository.load()
        assertNotNull(loaded)
        assertEquals("v2", loaded.model)
        assertEquals(2, loaded.chunks.size)
    }
}
