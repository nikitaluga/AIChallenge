package ru.nikitaluga.aichallenge.day34

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class Day34ViewModelTest {

    @Test
    fun `InputChanged updates inputText and clears error`() {
        val vm = Day34ViewModel()
        vm.onEvent(Event.InputChanged("test query"))
        assertEquals("test query", vm.state.value.inputText)
        assertNull(vm.state.value.error)
    }

    @Test
    fun `SendMessage with blank input does not add message`() {
        val vm = Day34ViewModel()
        vm.onEvent(Event.InputChanged("   "))
        val initialCount = vm.state.value.messages.size
        vm.onEvent(Event.SendMessage)
        assertEquals(initialCount, vm.state.value.messages.size)
    }

    @Test
    fun `ClearChat resets messages and inputText`() {
        val vm = Day34ViewModel()
        vm.onEvent(Event.InputChanged("something"))
        vm.onEvent(Event.ClearChat)
        assertTrue(vm.state.value.messages.isEmpty())
        assertEquals("", vm.state.value.inputText)
    }

    @Test
    fun `DismissError clears error field`() {
        val vm = Day34ViewModel()
        vm.onEvent(Event.InputChanged("a".repeat(2001)))
        vm.onEvent(Event.SendMessage)
        assertEquals("Слишком длинный запрос (максимум 2000 символов)", vm.state.value.error)
        vm.onEvent(Event.DismissError)
        assertNull(vm.state.value.error)
    }

    @Test
    fun `SendMessage with over 2000 chars sets length error`() {
        val vm = Day34ViewModel()
        vm.onEvent(Event.InputChanged("x".repeat(2001)))
        vm.onEvent(Event.SendMessage)
        assertEquals("Слишком длинный запрос (максимум 2000 символов)", vm.state.value.error)
    }
}
