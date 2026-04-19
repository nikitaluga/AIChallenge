package ru.nikitaluga.aichallenge.day33

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class Day33ViewModelTest {

    @Test
    fun `InputChanged updates inputText and clears error`() {
        val vm = Day33ViewModel()
        vm.onEvent(Event.InputChanged("как сбросить пароль?"))
        assertEquals("как сбросить пароль?", vm.state.value.inputText)
        assertNull(vm.state.value.error)
    }

    @Test
    fun `ClearChat resets messages and inputText`() {
        val vm = Day33ViewModel()
        vm.onEvent(Event.InputChanged("test"))
        vm.onEvent(Event.ClearChat)
        assertTrue(vm.state.value.messages.isEmpty())
        assertEquals("", vm.state.value.inputText)
    }

    @Test
    fun `DismissError clears error field`() {
        val vm = Day33ViewModel()
        vm.onEvent(Event.InputChanged("x".repeat(2001)))
        vm.onEvent(Event.SendMessage)
        assertEquals("Слишком длинный запрос (максимум 2000 символов)", vm.state.value.error)
        vm.onEvent(Event.DismissError)
        assertNull(vm.state.value.error)
    }

    @Test
    fun `DismissTicketDialog clears selectedTicket`() {
        val vm = Day33ViewModel()
        val ticket = TicketItem("T-001", "Login issue", "open")
        vm.onEvent(Event.TicketClicked(ticket))
        assertEquals(ticket, vm.state.value.selectedTicket)
        vm.onEvent(Event.DismissTicketDialog)
        assertNull(vm.state.value.selectedTicket)
    }

    @Test
    fun `SendMessage with 2001 chars sets length error without sending`() {
        val vm = Day33ViewModel()
        vm.onEvent(Event.InputChanged("y".repeat(2001)))
        val initialCount = vm.state.value.messages.size
        vm.onEvent(Event.SendMessage)
        assertEquals("Слишком длинный запрос (максимум 2000 символов)", vm.state.value.error)
        assertEquals(initialCount, vm.state.value.messages.size)
    }
}
