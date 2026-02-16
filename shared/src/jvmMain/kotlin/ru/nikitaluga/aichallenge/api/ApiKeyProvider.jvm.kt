package ru.nikitaluga.aichallenge.api

actual fun getApiKey(): String {
    return System.getenv("ROUTERAI_API_KEY")
        ?: error("ROUTERAI_API_KEY environment variable is not set")
}
