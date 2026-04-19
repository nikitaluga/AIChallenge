package ru.nikitaluga.aichallenge.util

import kotlinx.serialization.json.Json

val CommonJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    explicitNulls = false
}
