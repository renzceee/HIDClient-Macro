package me.arianb.usb_hid_client.macros.engine

class Parser(private val tokens: List<Token>) {
    private var current = 0
    private val diagnostics = mutableListOf<Diagnostic>()

    fun parse(): ParseResult {
        val statements = mutableListOf<Stmt>()
        while (!isAtEnd()) {
            val stmt = parseStatement()
            if (stmt != null) {
                statements.add(stmt)
            }
        }
        return ParseResult(statements, diagnostics)
    }

    private fun parseStatement(): Stmt? {
        if (isAtEnd()) return null

        if (check(TokenType.NEWLINE)) {
            advance()
            return null
        }

        if (check(TokenType.REM)) {
            val tok = advance()
            return CommentStmt(tok.value as? String ?: tok.lexeme, tok.line)
        }

        if (check(TokenType.STRING)) {
            val tok = advance()
            val textTok = if (check(TokenType.TEXT_LITERAL)) advance() else null
            return StringStmt(textTok?.value as? String ?: "", isLn = false, line = tok.line)
        }

        if (check(TokenType.STRINGLN)) {
            val tok = advance()
            val textTok = if (check(TokenType.TEXT_LITERAL)) advance() else null
            return StringStmt(textTok?.value as? String ?: "", isLn = true, line = tok.line)
        }

        if (check(TokenType.STRINGDELAY)) {
            val tok = advance()
            val expr = parseExpression() ?: NumberLiteralExpr(0L, tok.line)
            consumeNewlineOrEof()
            return StringDelayStmt(expr, tok.line)
        }

        if (check(TokenType.DELAY)) {
            val tok = advance()
            val expr = parseExpression() ?: NumberLiteralExpr(0L, tok.line)
            consumeNewlineOrEof()
            return DelayStmt(expr, tok.line)
        }

        if (check(TokenType.DEFAULT_DELAY) || check(TokenType.DEFAULTDELAY)) {
            val tok = advance()
            val expr = parseExpression() ?: NumberLiteralExpr(0L, tok.line)
            consumeNewlineOrEof()
            return DefaultDelayStmt(expr, tok.line)
        }

        if (check(TokenType.REPEAT)) {
            val tok = advance()
            val expr = parseExpression() ?: NumberLiteralExpr(1L, tok.line)
            consumeNewlineOrEof()
            return RepeatStmt(expr, tok.line)
        }

        if (check(TokenType.VAR)) {
            val tok = advance()
            val varTok = consume(TokenType.IDENTIFIER, "Expected variable name starting with $")
            val name = varTok?.lexeme ?: "\$_UNKNOWN"
            if (check(TokenType.EQUALS)) {
                advance()
            }
            val initExpr = parseExpression() ?: NumberLiteralExpr(0L, tok.line)
            consumeNewlineOrEof()
            return VarDeclStmt(name, initExpr, tok.line)
        }

        if (check(TokenType.HOLD)) {
            val tok = advance()
            val keys = parseKeyList()
            consumeNewlineOrEof()
            return HoldStmt(keys, tok.line)
        }

        if (check(TokenType.RELEASE)) {
            val tok = advance()
            val keys = parseKeyList()
            consumeNewlineOrEof()
            return ReleaseStmt(keys, tok.line)
        }

        if (check(TokenType.IF)) {
            return parseIfStatement()
        }

        if (check(TokenType.WHILE)) {
            return parseWhileStatement()
        }

        if (check(TokenType.FOR)) {
            return parseForStatement()
        }

        if (check(TokenType.FUNCTION)) {
            return parseFunctionDecl()
        }

        if (check(TokenType.ATTACKMODE) || check(TokenType.EXFIL)) {
            val tok = advance()
            consumeNewlineOrEof()
            diagnostics.add(Diagnostic(tok.line, tok.column, "Hardware command '${tok.lexeme}' is not applicable in HID Keyboard client mode.", isError = false))
            return HardwareWarningStmt(tok.lexeme, "Hardware command not supported", tok.line)
        }

        // Variable assignment without VAR keyword (e.g. $FOO = 10)
        if (check(TokenType.IDENTIFIER) && peekNext()?.type == TokenType.EQUALS) {
            val varTok = advance()
            advance() // consume =
            val expr = parseExpression() ?: NumberLiteralExpr(0L, varTok.line)
            consumeNewlineOrEof()
            return VarDeclStmt(varTok.lexeme, expr, varTok.line)
        }

        // Function call (e.g. MY_FUNCTION)
        if (check(TokenType.IDENTIFIER) && (peekNext()?.type == TokenType.NEWLINE || peekNext()?.type == TokenType.EOF)) {
            val tok = advance()
            consumeNewlineOrEof()
            return CallStmt(tok.lexeme, tok.line)
        }

        // Key combos or modifiers (e.g. CTRL ALT DEL, ENTER, GUI r)
        val line = peek().line
        val keys = parseKeyList()
        if (keys.isNotEmpty()) {
            consumeNewlineOrEof()
            return KeyComboStmt(keys, line)
        }

        // Fallback for unparsed single tokens - ALWAYS advance to guarantee loop termination
        val tok = advance()
        diagnostics.add(Diagnostic(tok.line, tok.column, "Unrecognized token: ${tok.lexeme}"))
        consumeNewlineOrEof()
        return null
    }

    private fun parseKeyList(): List<String> {
        val keys = mutableListOf<String>()
        while (!isAtEnd() && peek().type != TokenType.NEWLINE && peek().type != TokenType.EOF) {
            val tok = peek()
            if (tok.type == TokenType.KEY_MODIFIER || tok.type == TokenType.KEY_NAME || tok.type == TokenType.IDENTIFIER || tok.type == TokenType.NUMBER) {
                keys.add(tok.lexeme)
                advance()
            } else {
                break
            }
        }
        return keys
    }

    private fun parseIfStatement(): Stmt {
        val ifTok = advance() // consume IF
        val condition = parseExpression() ?: NumberLiteralExpr(0L, ifTok.line)
        consumeNewlineOrEof()

        val thenBranch = mutableListOf<Stmt>()
        val elseBranch = mutableListOf<Stmt>()
        var inElse = false

        while (!isAtEnd() && !check(TokenType.END_IF)) {
            if (check(TokenType.ELSE)) {
                advance()
                consumeNewlineOrEof()
                inElse = true
                continue
            }
            val stmt = parseStatement()
            if (stmt != null) {
                if (inElse) elseBranch.add(stmt) else thenBranch.add(stmt)
            }
        }

        if (check(TokenType.END_IF)) {
            advance()
            consumeNewlineOrEof()
        } else {
            diagnostics.add(Diagnostic(ifTok.line, ifTok.column, "Unclosed IF block. Missing END_IF."))
        }

        return IfStmt(condition, thenBranch, if (inElse) elseBranch else null, ifTok.line)
    }

    private fun parseWhileStatement(): Stmt {
        val whileTok = advance() // consume WHILE
        val condition = parseExpression() ?: NumberLiteralExpr(0L, whileTok.line)
        consumeNewlineOrEof()

        val body = mutableListOf<Stmt>()
        while (!isAtEnd() && !check(TokenType.END_WHILE)) {
            val stmt = parseStatement()
            if (stmt != null) body.add(stmt)
        }

        if (check(TokenType.END_WHILE)) {
            advance()
            consumeNewlineOrEof()
        } else {
            diagnostics.add(Diagnostic(whileTok.line, whileTok.column, "Unclosed WHILE block. Missing END_WHILE."))
        }

        return WhileStmt(condition, body, whileTok.line)
    }

    private fun parseForStatement(): Stmt {
        val forTok = advance() // consume FOR
        val varTok = consume(TokenType.IDENTIFIER, "Expected loop variable in FOR")
        val varName = varTok?.lexeme ?: "\$_I"
        if (check(TokenType.EQUALS)) advance()

        val startExpr = parseExpression() ?: NumberLiteralExpr(0L, forTok.line)
        if (check(TokenType.IDENTIFIER) && peek().lexeme.equals("TO", ignoreCase = true)) {
            advance()
        }
        val endExpr = parseExpression() ?: NumberLiteralExpr(10L, forTok.line)
        consumeNewlineOrEof()

        val body = mutableListOf<Stmt>()
        while (!isAtEnd() && !check(TokenType.END_FOR)) {
            val stmt = parseStatement()
            if (stmt != null) body.add(stmt)
        }

        if (check(TokenType.END_FOR)) {
            advance()
            consumeNewlineOrEof()
        } else {
            diagnostics.add(Diagnostic(forTok.line, forTok.column, "Unclosed FOR block. Missing END_FOR."))
        }

        return ForStmt(varName, startExpr, endExpr, body, forTok.line)
    }

    private fun parseFunctionDecl(): Stmt {
        val funcTok = advance() // consume FUNCTION
        val nameTok = consume(TokenType.IDENTIFIER, "Expected function name")
        val name = nameTok?.lexeme ?: "ANONYMOUS"
        consumeNewlineOrEof()

        val body = mutableListOf<Stmt>()
        while (!isAtEnd() && !check(TokenType.END_FUNCTION)) {
            val stmt = parseStatement()
            if (stmt != null) body.add(stmt)
        }

        if (check(TokenType.END_FUNCTION)) {
            advance()
            consumeNewlineOrEof()
        } else {
            diagnostics.add(Diagnostic(funcTok.line, funcTok.column, "Unclosed FUNCTION block. Missing END_FUNCTION."))
        }

        return FunctionDeclStmt(name, body, funcTok.line)
    }

    // Expression parser: Equality -> Relational -> Additive -> Multiplicative -> Primary
    private fun parseExpression(): Expr? {
        return parseEquality()
    }

    private fun parseEquality(): Expr? {
        var expr = parseRelational() ?: return null

        while (match(TokenType.EQ_EQ, TokenType.BANG_EQ)) {
            val opTok = previous()
            val right = parseRelational() ?: break
            expr = BinaryExpr(expr, opTok.type, right, opTok.line)
        }
        return expr
    }

    private fun parseRelational(): Expr? {
        var expr = parseAdditive() ?: return null

        while (match(TokenType.LESS, TokenType.LESS_EQ, TokenType.GREATER, TokenType.GREATER_EQ)) {
            val opTok = previous()
            val right = parseAdditive() ?: break
            expr = BinaryExpr(expr, opTok.type, right, opTok.line)
        }
        return expr
    }

    private fun parseAdditive(): Expr? {
        var expr = parseMultiplicative() ?: return null

        while (match(TokenType.PLUS, TokenType.MINUS)) {
            val opTok = previous()
            val right = parseMultiplicative() ?: break
            expr = BinaryExpr(expr, opTok.type, right, opTok.line)
        }
        return expr
    }

    private fun parseMultiplicative(): Expr? {
        var expr = parsePrimary() ?: return null

        while (match(TokenType.STAR, TokenType.SLASH, TokenType.PERCENT)) {
            val opTok = previous()
            val right = parsePrimary() ?: break
            expr = BinaryExpr(expr, opTok.type, right, opTok.line)
        }
        return expr
    }

    private fun parsePrimary(): Expr? {
        val tok = peek()
        return when (tok.type) {
            TokenType.NUMBER -> {
                advance()
                NumberLiteralExpr(tok.value as? Long ?: 0L, tok.line)
            }
            TokenType.IDENTIFIER -> {
                advance()
                VariableExpr(tok.lexeme, tok.line)
            }
            TokenType.TEXT_LITERAL -> {
                advance()
                StringLiteralExpr(tok.value as? String ?: "", tok.line)
            }
            TokenType.LPAREN -> {
                advance()
                val expr = parseExpression()
                consume(TokenType.RPAREN, "Expected ')' after expression")
                expr
            }
            else -> null
        }
    }

    private fun match(vararg types: TokenType): Boolean {
        for (type in types) {
            if (check(type)) {
                advance()
                return true
            }
        }
        return false
    }

    private fun check(type: TokenType): Boolean {
        if (isAtEnd()) return false
        return peek().type == type
    }

    private fun advance(): Token {
        if (!isAtEnd()) current++
        return previous()
    }

    private fun isAtEnd(): Boolean = peek().type == TokenType.EOF || current >= tokens.size

    private fun peek(): Token = if (current < tokens.size) tokens[current] else Token(TokenType.EOF, "", null, 0, 0)

    private fun peekNext(): Token? = if (current + 1 < tokens.size) tokens[current + 1] else null

    private fun previous(): Token = if (current > 0) tokens[current - 1] else Token(TokenType.EOF, "", null, 0, 0)

    private fun consume(type: TokenType, message: String): Token? {
        if (check(type)) return advance()
        val tok = peek()
        diagnostics.add(Diagnostic(tok.line, tok.column, message))
        return null
    }

    private fun consumeNewlineOrEof() {
        while (!isAtEnd() && (check(TokenType.NEWLINE))) {
            advance()
        }
    }
}
