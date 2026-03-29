package ru.nikitaluga.aichallenge.local

data class OllamaConfig(
    val baseUrl: String = System.getenv("OLLAMA_BASE_URL") ?: "http://localhost:11434",
    val maxConcurrent: Int = (System.getenv("OLLAMA_CONCURRENCY") ?: "2").toInt(),
    val maxCtx: Int = (System.getenv("OLLAMA_MAX_CTX") ?: "4096").toInt(),
    val maxHistoryMessages: Int = 20,
)
