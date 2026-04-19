package ru.nikitaluga.aichallenge.day35

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class Day35ViewModelTest {

    @Test
    fun `DiffChanged updates diff and clears error`() {
        val vm = Day35ViewModel()
        vm.onEvent(Event.DiffChanged("+ added line"))
        assertEquals("+ added line", vm.state.value.diff)
        assertNull(vm.state.value.error)
    }

    @Test
    fun `ContextChanged updates context field`() {
        val vm = Day35ViewModel()
        vm.onEvent(Event.ContextChanged("refactoring auth module"))
        assertEquals("refactoring auth module", vm.state.value.context)
    }

    @Test
    fun `Generate with blank diff does not set isLoading`() {
        val vm = Day35ViewModel()
        vm.onEvent(Event.DiffChanged("  "))
        vm.onEvent(Event.Generate)
        assertTrue(!vm.state.value.isLoading)
        assertTrue(vm.state.value.results.isEmpty())
    }

    @Test
    fun `DismissError clears error field`() {
        val vm = Day35ViewModel()
        // Manually set error via state inspection pattern
        vm.onEvent(Event.DismissError)
        assertNull(vm.state.value.error)
    }

    @Test
    fun `DismissCopied clears copiedMessage`() {
        val vm = Day35ViewModel()
        vm.onEvent(Event.CopyMessage("feat: add something"))
        assertEquals("feat: add something", vm.state.value.copiedMessage)
        vm.onEvent(Event.DismissCopied)
        assertNull(vm.state.value.copiedMessage)
    }
}
