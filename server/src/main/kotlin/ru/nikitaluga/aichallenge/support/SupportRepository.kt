package ru.nikitaluga.aichallenge.support

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

class SupportRepository(
    usersFilePath: String = "support_users.json",
    ticketsFilePath: String = "support_tickets.json",
) {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true; encodeDefaults = true }
    private val usersFile = File(usersFilePath)
    private val ticketsFile = File(ticketsFilePath)
    private val mutex = Mutex()

    private val users = mutableListOf<SupportUser>()
    private val tickets = mutableListOf<SupportTicket>()

    init {
        if (usersFile.exists()) {
            runCatching { json.decodeFromString(UsersFile.serializer(), usersFile.readText()).users }
                .getOrDefault(emptyList()).also { users.addAll(it) }
        } else {
            users.addAll(seedUsers())
            usersFile.writeText(json.encodeToString(UsersFile.serializer(), UsersFile(users.toList())))
        }

        if (ticketsFile.exists()) {
            runCatching { json.decodeFromString(TicketsFile.serializer(), ticketsFile.readText()).tickets }
                .getOrDefault(emptyList()).also { tickets.addAll(it) }
        } else {
            tickets.addAll(seedTickets())
            ticketsFile.writeText(json.encodeToString(TicketsFile.serializer(), TicketsFile(tickets.toList())))
        }
    }

    fun getAllUsers(): List<SupportUser> = users.toList()

    fun getUser(userId: String): SupportUser? = users.find { it.id == userId }

    fun getTicket(ticketId: String): SupportTicket? = tickets.find { it.id == ticketId }

    fun getTicketsForUser(userId: String): List<SupportTicket> = tickets.filter { it.userId == userId }

    suspend fun updateTicketStatus(ticketId: String, status: String): Boolean = mutex.withLock {
        val idx = tickets.indexOfFirst { it.id == ticketId }
        if (idx < 0) return@withLock false
        tickets[idx] = tickets[idx].copy(status = status)
        persistTickets()
        true
    }

    private fun persistTickets() {
        ticketsFile.writeText(json.encodeToString(TicketsFile.serializer(), TicketsFile(tickets.toList())))
    }

    // ── Seed data ─────────────────────────────────────────────────────────────

    private fun seedUsers() = listOf(
        SupportUser("u1", "Alice", "alice@example.com", "pro", "2025-01-15"),
        SupportUser("u2", "Bob", "bob@example.com", "free", "2025-03-01"),
        SupportUser("u3", "Carol", "carol@example.com", "enterprise", "2024-11-20"),
    )

    private fun seedTickets() = listOf(
        SupportTicket(
            id = "T-001",
            userId = "u1",
            subject = "Не могу войти в аккаунт",
            description = "Нажимаю кнопку «Войти», но страница перезагружается и ничего не происходит. Пробовал несколько браузеров.",
            status = "open",
            createdAt = "2025-12-30",
        ),
        SupportTicket(
            id = "T-002",
            userId = "u1",
            subject = "Ошибка при оплате",
            description = "При попытке обновить подписку до Pro получаю ошибку «Payment declined». Карта рабочая.",
            status = "resolved",
            createdAt = "2025-12-15",
        ),
        SupportTicket(
            id = "T-003",
            userId = "u2",
            subject = "Приложение падает на Android",
            description = "После обновления до версии 2.1.0 приложение падает при открытии вкладки с чатом. Android 14, Pixel 7.",
            status = "in_progress",
            createdAt = "2025-12-28",
        ),
        SupportTicket(
            id = "T-004",
            userId = "u3",
            subject = "Как экспортировать данные?",
            description = "Нам нужно выгрузить историю взаимодействий для аудита. Есть ли API или кнопка экспорта?",
            status = "open",
            createdAt = "2025-12-29",
        ),
    )
}

@Serializable
private data class UsersFile(val users: List<SupportUser>)

@Serializable
private data class TicketsFile(val tickets: List<SupportTicket>)
