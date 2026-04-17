package ru.nikitaluga.aichallenge

import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import ru.nikitaluga.aichallenge.support.SupportRepository
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SupportRepositoryTest {

    private lateinit var usersFile: File
    private lateinit var ticketsFile: File
    private lateinit var repository: SupportRepository

    @Before
    fun setUp() {
        usersFile = File.createTempFile("support_users_test_", ".json")
        ticketsFile = File.createTempFile("support_tickets_test_", ".json")
        usersFile.delete()
        ticketsFile.delete()
        repository = SupportRepository(usersFile.absolutePath, ticketsFile.absolutePath)
    }

    @After
    fun tearDown() {
        usersFile.delete()
        ticketsFile.delete()
    }

    @Test
    fun `seed data initialises 3 users and 4 tickets`() {
        val users = repository.getAllUsers()
        assertEquals(3, users.size)

        val alice = repository.getUser("u1")
        assertNotNull(alice)
        assertEquals("Alice", alice.name)
        assertEquals("pro", alice.plan)
    }

    @Test
    fun `getTicketsForUser filters by userId`() {
        val aliceTickets = repository.getTicketsForUser("u1")
        assertEquals(2, aliceTickets.size)
        assertTrue(aliceTickets.all { it.userId == "u1" })

        val bobTickets = repository.getTicketsForUser("u2")
        assertEquals(1, bobTickets.size)
        assertEquals("T-003", bobTickets.first().id)
    }

    @Test
    fun `getTicket returns correct ticket by id`() {
        val ticket = repository.getTicket("T-004")
        assertNotNull(ticket)
        assertEquals("u3", ticket.userId)
        assertEquals("open", ticket.status)
    }

    @Test
    fun `getTicket returns null for unknown id`() {
        assertNull(repository.getTicket("UNKNOWN"))
    }

    @Test
    fun `updateTicketStatus changes status and persists`() = runTest {
        val updated = repository.updateTicketStatus("T-001", "resolved")
        assertTrue(updated)

        val ticket = repository.getTicket("T-001")
        assertNotNull(ticket)
        assertEquals("resolved", ticket.status)

        // Verify persistence: create new repo instance from same files
        val repo2 = SupportRepository(usersFile.absolutePath, ticketsFile.absolutePath)
        val persistedTicket = repo2.getTicket("T-001")
        assertNotNull(persistedTicket)
        assertEquals("resolved", persistedTicket.status)
    }

    @Test
    fun `updateTicketStatus returns false for unknown ticket`() = runTest {
        val updated = repository.updateTicketStatus("UNKNOWN", "resolved")
        assertTrue(!updated)
    }

    @Test
    fun `getUser returns null for unknown userId`() {
        assertNull(repository.getUser("UNKNOWN"))
    }
}
