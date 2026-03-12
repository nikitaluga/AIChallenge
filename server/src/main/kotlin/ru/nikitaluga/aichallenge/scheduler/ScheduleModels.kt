package ru.nikitaluga.aichallenge.scheduler

import kotlinx.serialization.Serializable

// ── Persistent schedule entry (stored in schedules.json) ─────────────────────

@Serializable
data class ScheduleEntry(
    val id: String,
    val city: String,
    val hour: Int,
    val minute: Int,
    val reports: List<WeatherReport> = emptyList(),
)

@Serializable
data class WeatherReport(
    val ts: Long,          // unix epoch seconds
    val summary: String,
)

// ── REST request / response models ───────────────────────────────────────────

@Serializable
data class CreateScheduleRequest(
    val city: String,
    val hour: Int,
    val minute: Int,
)

@Serializable
data class ScheduleListResponse(val schedules: List<ScheduleEntry>)

@Serializable
data class ScheduleReportsResponse(val reports: List<WeatherReport>)
