package ru.nikitaluga.aichallenge.rag

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import java.io.File

class RagRepository(filePath: String = "rag_index.json") {

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

    fun getSync(): RagIndexFile? = cachedIndex
}
