package me.arianb.usb_hid_client.macros.engine

class Lexer(private val source: String) {

    private val KEY_MODIFIERS = setOf(
        "CTRL", "CONTROL", "ALT", "SHIFT", "WIN", "WINDOWS", "GUI", "META", "SUPER", "CMD"
    )

    private val KEY_NAMES = setOf(
        "ENTER", "RETURN", "TAB", "ESC", "ESCAPE", "SPACE", "SPACEBAR",
        "BACKSPACE", "BKSP", "DELETE", "DEL", "UP", "DOWN", "LEFT", "RIGHT",
        "HOME", "END", "PAGEUP", "PGUP", "PAGEDOWN", "PGDN",
        "CAPSLOCK", "PRINTSCREEN", "INSERT", "PAUSE", "MENU", "APP",
        "F1", "F2", "F3", "F4", "F5", "F6", "F7", "F8", "F9", "F10", "F11", "F12"
    )

    fun tokenize(): List<Token> {
        val tokens = mutableListOf<Token>()
        val lines = source.lines()

        for ((lineIndex, rawLine) in lines.withIndex()) {
            val lineNumber = lineIndex + 1
            val trimmedLine = rawLine.trim()

            if (trimmedLine.isEmpty()) {
                tokens.add(Token(TokenType.NEWLINE, "\n", null, lineNumber, 1))
                continue
            }

            if (trimmedLine.startsWith("REM", ignoreCase = true) || trimmedLine.startsWith("//")) {
                tokens.add(Token(TokenType.REM, rawLine, rawLine, lineNumber, 1))
                tokens.add(Token(TokenType.NEWLINE, "\n", null, lineNumber, rawLine.length + 1))
                continue
            }

            val parts = trimmedLine.split(Regex("\\s+"), limit = 2)
            val firstWord = parts[0].uppercase()
            val remainder = if (parts.size > 1) parts[1] else ""

            when (firstWord) {
                "STRING" -> {
                    tokens.add(Token(TokenType.STRING, parts[0], null, lineNumber, 1))
                    if (remainder.isNotEmpty()) {
                        tokens.add(Token(TokenType.TEXT_LITERAL, remainder, remainder, lineNumber, parts[0].length + 2))
                    }
                }
                "STRINGLN" -> {
                    tokens.add(Token(TokenType.STRINGLN, parts[0], null, lineNumber, 1))
                    if (remainder.isNotEmpty()) {
                        tokens.add(Token(TokenType.TEXT_LITERAL, remainder, remainder, lineNumber, parts[0].length + 2))
                    }
                }
                "STRINGDELAY", "STRING_DELAY" -> {
                    tokens.add(Token(TokenType.STRINGDELAY, parts[0], null, lineNumber, 1))
                    tokenizeLineRemainder(remainder, lineNumber, parts[0].length + 2, tokens)
                }
                "DELAY" -> {
                    tokens.add(Token(TokenType.DELAY, parts[0], null, lineNumber, 1))
                    tokenizeLineRemainder(remainder, lineNumber, parts[0].length + 2, tokens)
                }
                "DEFAULT_DELAY", "DEFAULTDELAY" -> {
                    tokens.add(Token(TokenType.DEFAULT_DELAY, parts[0], null, lineNumber, 1))
                    tokenizeLineRemainder(remainder, lineNumber, parts[0].length + 2, tokens)
                }
                "REPEAT" -> {
                    tokens.add(Token(TokenType.REPEAT, parts[0], null, lineNumber, 1))
                    tokenizeLineRemainder(remainder, lineNumber, parts[0].length + 2, tokens)
                }
                "VAR", "DEFINE" -> {
                    tokens.add(Token(TokenType.VAR, parts[0], null, lineNumber, 1))
                    tokenizeLineRemainder(remainder, lineNumber, parts[0].length + 2, tokens)
                }
                "IF" -> {
                    tokens.add(Token(TokenType.IF, parts[0], null, lineNumber, 1))
                    tokenizeLineRemainder(remainder, lineNumber, parts[0].length + 2, tokens)
                }
                "ELSE" -> {
                    tokens.add(Token(TokenType.ELSE, parts[0], null, lineNumber, 1))
                    if (remainder.isNotEmpty()) {
                        tokenizeLineRemainder(remainder, lineNumber, parts[0].length + 2, tokens)
                    }
                }
                "END_IF", "ENDIF" -> {
                    tokens.add(Token(TokenType.END_IF, parts[0], null, lineNumber, 1))
                }
                "WHILE" -> {
                    tokens.add(Token(TokenType.WHILE, parts[0], null, lineNumber, 1))
                    tokenizeLineRemainder(remainder, lineNumber, parts[0].length + 2, tokens)
                }
                "END_WHILE", "ENDWHILE" -> {
                    tokens.add(Token(TokenType.END_WHILE, parts[0], null, lineNumber, 1))
                }
                "FOR" -> {
                    tokens.add(Token(TokenType.FOR, parts[0], null, lineNumber, 1))
                    tokenizeLineRemainder(remainder, lineNumber, parts[0].length + 2, tokens)
                }
                "END_FOR", "ENDFOR" -> {
                    tokens.add(Token(TokenType.END_FOR, parts[0], null, lineNumber, 1))
                }
                "FUNCTION" -> {
                    tokens.add(Token(TokenType.FUNCTION, parts[0], null, lineNumber, 1))
                    tokenizeLineRemainder(remainder, lineNumber, parts[0].length + 2, tokens)
                }
                "END_FUNCTION", "ENDFUNCTION" -> {
                    tokens.add(Token(TokenType.END_FUNCTION, parts[0], null, lineNumber, 1))
                }
                "HOLD" -> {
                    tokens.add(Token(TokenType.HOLD, parts[0], null, lineNumber, 1))
                    tokenizeLineRemainder(remainder, lineNumber, parts[0].length + 2, tokens)
                }
                "RELEASE" -> {
                    tokens.add(Token(TokenType.RELEASE, parts[0], null, lineNumber, 1))
                    tokenizeLineRemainder(remainder, lineNumber, parts[0].length + 2, tokens)
                }
                "ATTACKMODE" -> {
                    tokens.add(Token(TokenType.ATTACKMODE, parts[0], remainder, lineNumber, 1))
                }
                "EXFIL" -> {
                    tokens.add(Token(TokenType.EXFIL, parts[0], remainder, lineNumber, 1))
                }
                else -> {
                    tokenizeLineRemainder(trimmedLine, lineNumber, 1, tokens)
                }
            }

            tokens.add(Token(TokenType.NEWLINE, "\n", null, lineNumber, rawLine.length + 1))
        }

        tokens.add(Token(TokenType.EOF, "", null, lines.size + 1, 1))
        return tokens
    }

    private fun tokenizeLineRemainder(lineText: String, line: Int, startCol: Int, tokens: MutableList<Token>) {
        var idx = 0
        val length = lineText.length

        while (idx < length) {
            val ch = lineText[idx]

            if (ch.isWhitespace()) {
                idx++
                continue
            }

            val col = startCol + idx

            if (ch == '$') {
                val start = idx
                idx++
                while (idx < length && (lineText[idx].isLetterOrDigit() || lineText[idx] == '_')) {
                    idx++
                }
                val varName = lineText.substring(start, idx)
                tokens.add(Token(TokenType.IDENTIFIER, varName, varName, line, col))
                continue
            }

            if (ch.isDigit()) {
                val start = idx
                while (idx < length && lineText[idx].isDigit()) {
                    idx++
                }
                val numStr = lineText.substring(start, idx)
                tokens.add(Token(TokenType.NUMBER, numStr, numStr.toLongOrNull() ?: 0L, line, col))
                continue
            }

            if (ch.isLetter() || ch == '_') {
                val start = idx
                while (idx < length && (lineText[idx].isLetterOrDigit() || lineText[idx] == '_')) {
                    idx++
                }
                val word = lineText.substring(start, idx)
                val upper = word.uppercase()

                when {
                    KEY_MODIFIERS.contains(upper) -> tokens.add(Token(TokenType.KEY_MODIFIER, upper, upper, line, col))
                    KEY_NAMES.contains(upper) || upper.matches(Regex("F(1[0-2]|[1-9])")) -> tokens.add(Token(TokenType.KEY_NAME, upper, upper, line, col))
                    else -> tokens.add(Token(TokenType.IDENTIFIER, word, word, line, col))
                }
                continue
            }

            // Operators & Punctuation
            when (ch) {
                '=' -> {
                    if (idx + 1 < length && lineText[idx + 1] == '=') {
                        tokens.add(Token(TokenType.EQ_EQ, "==", null, line, col))
                        idx += 2
                    } else {
                        tokens.add(Token(TokenType.EQUALS, "=", null, line, col))
                        idx++
                    }
                }
                '!' -> {
                    if (idx + 1 < length && lineText[idx + 1] == '=') {
                        tokens.add(Token(TokenType.BANG_EQ, "!=", null, line, col))
                        idx += 2
                    } else {
                        tokens.add(Token(TokenType.UNKNOWN, "!", null, line, col))
                        idx++
                    }
                }
                '<' -> {
                    if (idx + 1 < length && lineText[idx + 1] == '=') {
                        tokens.add(Token(TokenType.LESS_EQ, "<=", null, line, col))
                        idx += 2
                    } else {
                        tokens.add(Token(TokenType.LESS, "<", null, line, col))
                        idx++
                    }
                }
                '>' -> {
                    if (idx + 1 < length && lineText[idx + 1] == '=') {
                        tokens.add(Token(TokenType.GREATER_EQ, ">=", null, line, col))
                        idx += 2
                    } else {
                        tokens.add(Token(TokenType.GREATER, ">", null, line, col))
                        idx++
                    }
                }
                '+' -> { tokens.add(Token(TokenType.PLUS, "+", null, line, col)); idx++ }
                '-' -> { tokens.add(Token(TokenType.MINUS, "-", null, line, col)); idx++ }
                '*' -> { tokens.add(Token(TokenType.STAR, "*", null, line, col)); idx++ }
                '/' -> { tokens.add(Token(TokenType.SLASH, "/", null, line, col)); idx++ }
                '%' -> { tokens.add(Token(TokenType.PERCENT, "%", null, line, col)); idx++ }
                '(' -> { tokens.add(Token(TokenType.LPAREN, "(", null, line, col)); idx++ }
                ')' -> { tokens.add(Token(TokenType.RPAREN, ")", null, line, col)); idx++ }
                else -> {
                    tokens.add(Token(TokenType.UNKNOWN, ch.toString(), null, line, col))
                    idx++
                }
            }
        }
    }
}
