package ru.nikitaluga.aichallenge.data.storage

import platform.Foundation.NSUserDefaults

actual object PlatformStorage {
    private val defaults = NSUserDefaults.standardUserDefaults

    actual fun save(key: String, value: String) {
        defaults.setObject(value, forKey = key)
    }

    actual fun load(key: String): String? = defaults.stringForKey(key)

    actual fun remove(key: String) {
        defaults.removeObjectForKey(key)
    }
}
