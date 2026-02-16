package ru.nikitaluga.aichallenge

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform