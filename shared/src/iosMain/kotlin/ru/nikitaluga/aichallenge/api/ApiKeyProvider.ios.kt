package ru.nikitaluga.aichallenge.api

actual fun getApiKey(): String {
    return platform.Foundation.NSBundle.mainBundle
        .objectForInfoDictionaryKey("ROUTERAI_API_KEY") as? String
        ?: error("ROUTERAI_API_KEY not found in Info.plist")
}
