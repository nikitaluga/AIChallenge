package ru.nikitaluga.aichallenge.mcp

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private const val OWM_BASE = "https://api.openweathermap.org/data/2.5/weather"

class WeatherService {

    private val apiKey: String = System.getProperty("owm.api.key")
        ?: error("owm.api.key system property is not set. Add it to local.properties.")

    private val json = Json { ignoreUnknownKeys = true }

    private val client = HttpClient(CIO) {
        install(ContentNegotiation) { json(json) }
    }

    suspend fun getWeather(city: String): String {
        val url = "$OWM_BASE?q=${city.trim()}&appid=$apiKey&units=metric&lang=ru"
        val data = client.get(url).body<WeatherResponse>()
        return buildString {
            appendLine("Город: ${data.name}")
            appendLine("Температура: ${data.main.temp}°C")
            appendLine("Ощущается как: ${data.main.feelsLike}°C")
            appendLine("Описание: ${data.weather.firstOrNull()?.description ?: "неизвестно"}")
            appendLine("Влажность: ${data.main.humidity}%")
            append("Ветер: ${data.wind.speed} м/с")
        }
    }
}

// ── OpenWeatherMap response models ────────────────────────────────────────────

@Serializable
private data class WeatherResponse(
    val name: String,
    val main: MainWeather,
    val weather: List<WeatherDesc>,
    val wind: Wind,
)

@Serializable
private data class MainWeather(
    val temp: Double,
    @SerialName("feels_like") val feelsLike: Double,
    val humidity: Int,
)

@Serializable
private data class WeatherDesc(val description: String)

@Serializable
private data class Wind(val speed: Double)
