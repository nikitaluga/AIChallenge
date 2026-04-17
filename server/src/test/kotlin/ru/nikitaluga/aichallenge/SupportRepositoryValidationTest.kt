package ru.nikitaluga.aichallenge

import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import ru.nikitaluga.aichallenge.support.SupportRepository
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * Контракт: updateTicketStatus должен отклонять невалидные статусы.
 * Допустимые значения: "open", "in_progress", "resolved".
 *
 * ОЖИДАЕМЫЙ РЕЗУЛЬТАТ: тест ПАДАЕТ — валидации в SupportRepository нет.
 * Это намеренный failure для демонстрации цикла обнаружения багов.
 */
class SupportRepositoryValidationTest {

    private lateinit var usersFile: File
    private lateinit var ticketsFile: File
    private lateinit var repository: SupportRepository

    @Before
    fun setUp() {
        usersFile = File.createTempFile("support_users_val_", ".json").also { it.delete() }
        ticketsFile = File.createTempFile("support_tickets_val_", ".json").also { it.delete() }
        repository = SupportRepository(usersFile.absolutePath, ticketsFile.absolutePath)
    }

    @After
    fun tearDown() {
        usersFile.delete()
        ticketsFile.delete()
    }

    @Test
    fun `updateTicketStatus rejects invalid status string`() = runTest {
        // FAIL: SupportRepository не валидирует статус, вернёт true вместо false
        val result = repository.updateTicketStatus("T-001", "INVALID_STATUS")

        assertFalse(result, "Невалидный статус должен быть отклонён, но был принят")
    }

    @Test
    fun `updateTicketStatus rejects empty status`() = runTest {
        // FAIL: пустая строка тоже должна отклоняться
        val result = repository.updateTicketStatus("T-001", "")

        assertFalse(result, "Пустой статус должен быть отклонён, но был принят")
    }

    @Test
    fun `ticket status remains unchanged after invalid update attempt`() = runTest {
        val originalStatus = repository.getTicket("T-001")?.status

        repository.updateTicketStatus("T-001", "HACKED")

        // FAIL: статус изменился на "HACKED" вместо того чтобы остаться "open"
        val currentStatus = repository.getTicket("T-001")?.status
        assertEquals(originalStatus, currentStatus, "Статус изменился на невалидное значение: $currentStatus")
    }
}
