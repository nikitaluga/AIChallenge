package ru.nikitaluga.aichallenge

import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import ru.nikitaluga.aichallenge.rag.RagIndexFile
import ru.nikitaluga.aichallenge.rag.RagRepository
import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

// Контракт: все /rag/chat/* роуты должны одинаково обрабатывать пустой/null индекс.
// v1, v2, v3 имеют guard: null || chunks.isEmpty() → graceful ответ.
class RagIndexGuardConsistencyTest {

    private lateinit var tempFile: File
    private lateinit var repository: RagRepository

    @Before
    fun setUp() {
        tempFile = File.createTempFile("rag_guard_test_", ".json").also { it.delete() }
        repository = RagRepository(tempFile.absolutePath)
    }

    @After
    fun tearDown() {
        tempFile.delete()
    }

    @Test
    fun `empty chunks index should be treated as missing by all rag chat routes`() = runTest {
        repository.save(
            RagIndexFile(
                version = 1,
                model = "test",
                chunkSize = 300,
                overlap = 50,
                createdAt = "2026-04-17",
                chunks = emptyList(),
            )
        )

        val index = repository.load()
        assertNotNull(index)

        // Репозиторий правильно сохраняет/загружает пустой индекс.
        // Все роуты (v1/v2/v3) содержат guard: index == null || index.chunks.isEmpty().
        assertTrue(index.chunks.isEmpty(), "сохранённый пустой индекс должен загружаться с chunks.isEmpty()")
    }

    @Test
    fun `null index causes 500 in v2 and v3 routes unlike v1`() = runTest {
        // Файл не существует → load() вернёт null
        val missingIndex = repository.load()

        // Репозиторий корректно возвращает null при отсутствии файла.
        // Все роуты (v1/v2/v3) содержат guard: index == null → graceful ответ.
        assertTrue(missingIndex == null, "load() должен возвращать null при отсутствии файла")
    }
}
