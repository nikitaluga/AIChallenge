package ru.nikitaluga.aichallenge.scheduler

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

private const val MAX_REPORTS_PER_SCHEDULE = 7

class ScheduleRepository(filePath: String = "schedules.json") {

    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }
    private val file = File(filePath)
    private val mutex = Mutex()
    private val schedules = mutableListOf<ScheduleEntry>()

    suspend fun load(): List<ScheduleEntry> = mutex.withLock {
        if (file.exists()) {
            runCatching {
                json.decodeFromString<SchedulesFile>(file.readText()).schedules
            }.getOrDefault(emptyList()).also { loaded ->
                schedules.clear()
                schedules.addAll(loaded)
            }
        } else {
            emptyList()
        }
    }

    suspend fun addSchedule(entry: ScheduleEntry): Unit = mutex.withLock {
        schedules.add(entry)
        persist()
    }

    suspend fun removeSchedule(id: String): Unit = mutex.withLock {
        schedules.removeAll { it.id == id }
        persist()
    }

    suspend fun addReport(id: String, report: WeatherReport): Unit = mutex.withLock {
        val idx = schedules.indexOfFirst { it.id == id }
        if (idx >= 0) {
            val updated = schedules[idx].let { entry ->
                entry.copy(reports = (entry.reports + report).takeLast(MAX_REPORTS_PER_SCHEDULE))
            }
            schedules[idx] = updated
            persist()
        }
    }

    fun getAll(): List<ScheduleEntry> = schedules.toList()

    fun getById(id: String): ScheduleEntry? = schedules.find { it.id == id }

    private fun persist() {
        file.writeText(json.encodeToString(SchedulesFile(schedules.toList())))
    }
}

@Serializable
private data class SchedulesFile(val schedules: List<ScheduleEntry>)
