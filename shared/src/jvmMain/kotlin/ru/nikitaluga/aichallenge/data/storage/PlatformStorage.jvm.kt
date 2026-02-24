package ru.nikitaluga.aichallenge.data.storage

import java.io.File
import java.util.Properties

actual object PlatformStorage {
    private val file = File(System.getProperty("java.io.tmpdir"), "ai_challenge_storage.properties")
    private val props = Properties().also { p ->
        if (file.exists()) file.inputStream().use { p.load(it) }
    }

    actual fun save(key: String, value: String) {
        props[key] = value
        file.outputStream().use { props.store(it, null) }
    }

    actual fun load(key: String): String? = props.getProperty(key)

    actual fun remove(key: String) {
        props.remove(key)
        file.outputStream().use { props.store(it, null) }
    }
}
