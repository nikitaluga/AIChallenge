package ru.nikitaluga.aichallenge.scheduler

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ru.nikitaluga.aichallenge.api.ChatMessage
import ru.nikitaluga.aichallenge.api.RouterAiApiService
import ru.nikitaluga.aichallenge.mcp.WeatherService
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.concurrent.ConcurrentHashMap

class WeatherSchedulerService(
    private val repo: ScheduleRepository,
    private val weatherService: WeatherService,
    private val apiService: RouterAiApiService,
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val jobs = ConcurrentHashMap<String, Job>()

    fun startAll(schedules: List<ScheduleEntry>) {
        schedules.forEach { startSchedule(it) }
    }

    fun startSchedule(entry: ScheduleEntry) {
        cancelSchedule(entry.id)
        jobs[entry.id] = scope.launch {
            while (true) {
                delay(msUntilNext(entry.hour, entry.minute))
                runReport(entry)
            }
        }
    }

    fun cancelSchedule(id: String) {
        jobs.remove(id)?.cancel()
    }

    private suspend fun runReport(entry: ScheduleEntry) {
        val rawWeather = runCatching {
            weatherService.getWeather(entry.city)
        }.getOrElse { "Ошибка получения погоды: ${it.message}" }

        val summary = runCatching {
            apiService.sendMessages(
                messages = listOf(
                    ChatMessage(
                        role = "system",
                        content = "Напиши краткую сводку погоды (1–2 предложения). Отвечай на русском языке.",
                    ),
                    ChatMessage(role = "user", content = rawWeather),
                ),
                maxTokens = 150,
            ).content
        }.getOrElse { rawWeather }

        val report = WeatherReport(
            ts = System.currentTimeMillis() / 1000L,
            summary = summary,
        )
        repo.addReport(entry.id, report)
    }

    private fun msUntilNext(hour: Int, minute: Int): Long {
        val now = LocalDateTime.now()
        val todayFire = now.toLocalDate().atTime(hour, minute)
        val nextFire = if (now.isBefore(todayFire)) todayFire else todayFire.plusDays(1)
        return ChronoUnit.MILLIS.between(LocalDateTime.now(), nextFire).coerceAtLeast(1_000L)
    }
}
