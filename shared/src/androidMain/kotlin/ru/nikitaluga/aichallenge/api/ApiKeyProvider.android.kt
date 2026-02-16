package ru.nikitaluga.aichallenge.api

actual fun getApiKey(): String {
    return ru.nikitaluga.aichallenge.shared.BuildConfig.ROUTERAI_API_KEY
}
