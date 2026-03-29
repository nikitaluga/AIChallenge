package ru.nikitaluga.aichallenge.rag

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import java.io.File

class LocalRagRepository(filePath: String = "local_rag_index.json") {

    private val json = Json {
        prettyPrint = false
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    private val file = File(filePath)
    private val mutex = Mutex()

    @Volatile
    private var cachedIndex: LocalRagIndexFile? = null

    suspend fun load(): LocalRagIndexFile? = mutex.withLock {
        if (cachedIndex != null) return@withLock cachedIndex
        if (!file.exists()) return@withLock null
        runCatching {
            json.decodeFromString<LocalRagIndexFile>(file.readText()).also { cachedIndex = it }
        }.getOrNull()
    }

    suspend fun save(index: LocalRagIndexFile): Unit = mutex.withLock {
        file.writeText(json.encodeToString(LocalRagIndexFile.serializer(), index))
        cachedIndex = index
    }

    fun getSync(): LocalRagIndexFile? = cachedIndex
}
