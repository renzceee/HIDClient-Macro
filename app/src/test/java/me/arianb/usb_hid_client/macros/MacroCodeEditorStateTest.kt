package me.arianb.usb_hid_client.macros

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MacroCodeEditorStateTest {

    @Test
    fun testUndoRedoStack() {
        val state = MacroCodeEditorState("STRING Hello")
        assertFalse(state.canUndo)
        assertFalse(state.canRedo)

        state.setText("STRING Hello World")
        assertTrue(state.canUndo)
        assertFalse(state.canRedo)
        assertEquals("STRING Hello World", state.textFieldValue.text)

        state.undo()
        assertEquals("STRING Hello", state.textFieldValue.text)
        assertFalse(state.canUndo)
        assertTrue(state.canRedo)

        state.redo()
        assertEquals("STRING Hello World", state.textFieldValue.text)
        assertTrue(state.canUndo)
        assertFalse(state.canRedo)
    }

    @Test
    fun testSearchAndReplace() {
        val state = MacroCodeEditorState("DELAY 100\nSTRING foo\nDELAY 200")
        state.toggleSearch()
        state.updateSearchQuery("DELAY")

        assertEquals(2, state.searchMatches.size)
        assertEquals(0, state.activeMatchIndex)

        state.nextMatch()
        assertEquals(1, state.activeMatchIndex)

        state.updateReplaceQuery("WAIT")
        state.replaceAllMatches()

        assertEquals("WAIT 100\nSTRING foo\nWAIT 200", state.textFieldValue.text)
    }

    @Test
    fun testPreviousMatchAndReplaceCurrent() {
        val state = MacroCodeEditorState("DELAY 100\nSTRING foo\nDELAY 200")
        state.toggleSearch()
        state.updateSearchQuery("DELAY")

        assertEquals(0, state.activeMatchIndex)
        state.previousMatch()
        assertEquals(1, state.activeMatchIndex)

        state.updateReplaceQuery("SLEEP")
        state.replaceCurrentMatch()
        assertEquals("DELAY 100\nSTRING foo\nSLEEP 200", state.textFieldValue.text)
    }

    @Test
    fun testSoftWrapToggle() {
        val state = MacroCodeEditorState("STRING Long Line")
        assertTrue(state.isSoftWrapEnabled)

        state.toggleSoftWrap()
        assertFalse(state.isSoftWrapEnabled)

        state.toggleSoftWrap()
        assertTrue(state.isSoftWrapEnabled)
    }

    @Test
    fun testPhysicalLineStartIndices() {
        val script = "LINE 1\nLINE 2\nLINE 3"
        val state = MacroCodeEditorState(script)

        val starts = state.getPhysicalLineStartIndices()
        assertEquals(listOf(0, 7, 14), starts)
        assertEquals(3, state.getTotalLines())
        assertEquals(script.length, state.getTotalCharacters())
    }

    @Test
    fun testFormatScript() {
        val raw = "string notepad\ndelay 100\n  rem test comment\ngui r"
        val formatted = MacroCodeEditorState.formatDuckyScript(raw)

        val expected = "STRING notepad\nDELAY 100\n  REM test comment\nGUI r"
        assertEquals(expected, formatted)
    }
}
