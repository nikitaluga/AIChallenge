package ru.nikitaluga.aichallenge.dev

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import ru.nikitaluga.aichallenge.rag.RagIndexFile
import java.io.File

class DevDocsRepository(filePath: String = "dev_docs_index.json") {

    private val json = Json {
        prettyPrint = false
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    private val file = File(filePath)
    private val mutex = Mutex()

    @Volatile
    private var cachedIndex: RagIndexFile? = null

    suspend fun load(): RagIndexFile? = mutex.withLock {
        if (cachedIndex != null) return@withLock cachedIndex
        if (!file.exists()) return@withLock null
        runCatching {
            json.decodeFromString<RagIndexFile>(file.readText()).also { cachedIndex = it }
        }.getOrNull()
    }

    suspend fun save(index: RagIndexFile): Unit = mutex.withLock {
        file.writeText(json.encodeToString(RagIndexFile.serializer(), index))
        cachedIndex = index
    }

    suspend fun clear(): Unit = mutex.withLock {
        cachedIndex = null
        if (file.exists()) file.delete()
    }
}
