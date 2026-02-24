package ru.nikitaluga.aichallenge.data.storage

import android.content.Context.MODE_PRIVATE
import ru.nikitaluga.aichallenge.AppContextHolder

// SharedPreferences выбран по трём причинам:
// 1. Нет новых зависимостей — встроен в Android SDK.
// 2. История хранится как одна JSON-строка (key-value), что идеально для SharedPreferences.
// 3. Прямой аналог NSUserDefaults на iOS — единая концепция на обеих платформах.
//
// Когда стоит перейти на Room/SQLDelight:
// - История > 100–200 сообщений (большая JSON-строка)
// - Нужен поиск по истории или поддержка нескольких сессий
actual object PlatformStorage {
    private val prefs
        get() = AppContextHolder.context.getSharedPreferences("ai_challenge_storage", MODE_PRIVATE)

    actual fun save(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }

    actual fun load(key: String): String? = prefs.getString(key, null)

    actual fun remove(key: String) {
        prefs.edit().remove(key).apply()
    }
}
