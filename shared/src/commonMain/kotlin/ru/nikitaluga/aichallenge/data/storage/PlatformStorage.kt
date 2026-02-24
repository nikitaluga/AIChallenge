package ru.nikitaluga.aichallenge.data.storage

expect object PlatformStorage {
    fun save(key: String, value: String)
    fun load(key: String): String?
    fun remove(key: String)
}
