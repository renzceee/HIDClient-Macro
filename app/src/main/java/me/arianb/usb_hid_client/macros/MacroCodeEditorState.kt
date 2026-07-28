package me.arianb.usb_hid_client.macros

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import me.arianb.usb_hid_client.macros.engine.Diagnostic
import me.arianb.usb_hid_client.macros.engine.Lexer
import me.arianb.usb_hid_client.macros.engine.Parser

class MacroCodeEditorState(initialText: String = "") {
    var textFieldValue by mutableStateOf(TextFieldValue(initialText))
        private set

    var diagnostics by mutableStateOf<List<Diagnostic>>(emptyList())
        private set

    init {
        recomputeDiagnostics()
    }

    private fun recomputeDiagnostics() {
        val lexer = Lexer(textFieldValue.text)
        val tokens = lexer.tokenize()
        val parser = Parser(tokens)
        diagnostics = parser.parse().diagnostics
    }

    private val undoStack = ArrayDeque<TextFieldValue>()
    private val redoStack = ArrayDeque<TextFieldValue>()

    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()

    var isSoftWrapEnabled by mutableStateOf(true)
        private set

    fun toggleSoftWrap() {
        isSoftWrapEnabled = !isSoftWrapEnabled
    }

    var isSearchOpen by mutableStateOf(false)
        private set

    var searchQuery by mutableStateOf("")
        private set

    var replaceQuery by mutableStateOf("")
        private set

    val searchMatches = mutableStateListOf<TextRange>()
    var activeMatchIndex by mutableStateOf(0)
        private set

    fun updateText(newValue: TextFieldValue, recordHistory: Boolean = true) {
        val textChanged = newValue.text != textFieldValue.text
        if (recordHistory && textChanged) {
            undoStack.addLast(textFieldValue)
            redoStack.clear()
        }
        textFieldValue = newValue
        if (textChanged) {
            recomputeDiagnostics()
        }
        updateSearchMatches()
    }

    fun setText(newText: String) {
        val newFieldValue = TextFieldValue(newText, selection = TextRange(newText.length))
        updateText(newFieldValue)
    }

    fun formatCurrentScript() {
        val formatted = formatDuckyScript(textFieldValue.text)
        if (formatted != textFieldValue.text) {
            setText(formatted)
        }
    }

    fun undo() {
        if (canUndo) {
            redoStack.addLast(textFieldValue)
            val previous = undoStack.removeLast()
            textFieldValue = previous
            updateSearchMatches()
        }
    }

    fun redo() {
        if (canRedo) {
            undoStack.addLast(textFieldValue)
            val next = redoStack.removeLast()
            textFieldValue = next
            updateSearchMatches()
        }
    }

    fun insertToken(token: String) {
        val text = textFieldValue.text
        val sel = textFieldValue.selection
        val start = sel.start.coerceIn(0, text.length)
        val end = sel.end.coerceIn(0, text.length)
        val minSel = minOf(start, end)
        val maxSel = maxOf(start, end)

        val newText = buildString(text.length + token.length) {
            append(text.substring(0, minSel))
            append(token)
            append(text.substring(maxSel))
        }
        val newCursor = minSel + token.length
        updateText(TextFieldValue(newText, selection = TextRange(newCursor)))
    }

    fun toggleSearch() {
        isSearchOpen = !isSearchOpen
        if (!isSearchOpen) {
            searchQuery = ""
            searchMatches.clear()
            activeMatchIndex = 0
        }
    }

    fun updateSearchQuery(query: String) {
        searchQuery = query
        updateSearchMatches()
    }

    fun updateReplaceQuery(query: String) {
        replaceQuery = query
    }

    private fun updateSearchMatches() {
        searchMatches.clear()
        if (searchQuery.isEmpty()) {
            activeMatchIndex = 0
            return
        }

        val text = textFieldValue.text
        var startIndex = 0
        while (startIndex < text.length) {
            val index = text.indexOf(searchQuery, startIndex, ignoreCase = true)
            if (index == -1) break
            searchMatches.add(TextRange(index, index + searchQuery.length))
            startIndex = index + searchQuery.length.coerceAtLeast(1)
        }
        if (activeMatchIndex >= searchMatches.size) {
            activeMatchIndex = 0
        }
    }

    fun nextMatch() {
        if (searchMatches.isNotEmpty()) {
            activeMatchIndex = (activeMatchIndex + 1) % searchMatches.size
        }
    }

    fun previousMatch() {
        if (searchMatches.isNotEmpty()) {
            activeMatchIndex = if (activeMatchIndex - 1 < 0) searchMatches.size - 1 else activeMatchIndex - 1
        }
    }

    fun replaceCurrentMatch() {
        if (searchMatches.isEmpty() || activeMatchIndex !in searchMatches.indices) return
        val match = searchMatches[activeMatchIndex]
        val text = textFieldValue.text
        val newText = text.substring(0, match.start) + replaceQuery + text.substring(match.end)
        updateText(TextFieldValue(newText, selection = TextRange(match.start + replaceQuery.length)))
    }

    fun replaceAllMatches() {
        if (searchQuery.isEmpty()) return
        val newText = textFieldValue.text.replace(searchQuery, replaceQuery, ignoreCase = true)
        setText(newText)
    }

    // Returns character offset in text where each physical line starts (0-indexed line array)
    fun getPhysicalLineStartIndices(): List<Int> {
        val text = textFieldValue.text
        if (text.isEmpty()) return listOf(0)
        val list = mutableListOf<Int>()
        list.add(0)
        for (i in text.indices) {
            if (text[i] == '\n' && i + 1 <= text.length) {
                list.add(i + 1)
            }
        }
        return list
    }

    // Line and Cursor Statistics for Editor Gutter & Highlighting
    fun getLineAndColumn(): Pair<Int, Int> {
        val text = textFieldValue.text
        val selStart = textFieldValue.selection.start.coerceIn(0, text.length)
        var line = 1
        var col = 1
        for (i in 0 until selStart) {
            if (text[i] == '\n') {
                line++
                col = 1
            } else {
                col++
            }
        }
        return Pair(line, col)
    }

    fun getTotalLines(): Int {
        val text = textFieldValue.text
        if (text.isEmpty()) return 1
        return text.count { it == '\n' } + 1
    }

    fun getTotalCharacters(): Int {
        return textFieldValue.text.length
    }

    companion object {
        fun formatDuckyScript(raw: String): String {
            val keywords = setOf(
                "REM", "STRING", "DELAY", "GUI", "WINDOWS", "APP", "MENU",
                "SHIFT", "ALT", "CTRL", "CONTROL", "ENTER", "REPEAT",
                "DOWN", "LEFT", "RIGHT", "UP", "SPACE", "TAB", "CAPSLOCK", "DELETE"
            )
            return raw.lineSequence().joinToString("\n") { line ->
                val trimmed = line.trimStart()
                val leadingSpaces = line.take(line.length - trimmed.length)
                val firstWord = trimmed.substringBefore(' ')
                val firstWordUpper = firstWord.uppercase()

                if (keywords.contains(firstWordUpper)) {
                    val rest = trimmed.substring(firstWord.length)
                    "$leadingSpaces$firstWordUpper$rest"
                } else {
                    line
                }
            }
        }
    }
}
