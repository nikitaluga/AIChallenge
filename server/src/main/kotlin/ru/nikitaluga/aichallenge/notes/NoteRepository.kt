package ru.nikitaluga.aichallenge.notes

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class Note(val title: String, val content: String)

class NoteRepository {
    private val mutex = Mutex()
    private val file = File("notes.json")
    private val json = Json { prettyPrint = true }

    suspend fun save(note: Note) = mutex.withLock {
        val notes = load().toMutableList()
        val idx = notes.indexOfFirst { it.title == note.title }
        if (idx >= 0) notes[idx] = note else notes.add(note)
        file.writeText(json.encodeToString<List<Note>>(notes))
    }

    suspend fun list(): List<Note> = mutex.withLock { load() }

    suspend fun read(title: String): Note? = mutex.withLock {
        load().firstOrNull { it.title == title }
    }

    private fun load(): List<Note> = runCatching {
        if (!file.exists()) return emptyList()
        json.decodeFromString<List<Note>>(file.readText())
    }.getOrDefault(emptyList())
}
