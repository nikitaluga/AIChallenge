package ru.nikitaluga.aichallenge.api

actual fun getApiKey(): String {
    return System.getenv("ROUTERAI_API_KEY")
        ?: System.getProperty("routerai.api.key")
        ?: error("ROUTERAI_API_KEY env var or routerai.api.key system property is not set")
}
