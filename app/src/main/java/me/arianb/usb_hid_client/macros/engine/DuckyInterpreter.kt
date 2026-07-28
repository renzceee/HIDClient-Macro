package me.arianb.usb_hid_client.macros.engine

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

interface DuckyExecutionCallbacks {
    suspend fun sendText(text: String)
    suspend fun sendChord(keys: List<String>)
    suspend fun holdKeys(keys: List<String>)
    suspend fun releaseKeys(keys: List<String>)
    fun logInfo(message: String)
    fun logCommand(message: String)
    fun logTyping(message: String)
    fun logDelay(message: String)
    fun logWarning(message: String)
    fun logError(message: String)
}

class DuckyInterpreter(
    private val callbacks: DuckyExecutionCallbacks
) {
    private var defaultDelayMs = 0L
    private var stringDelayMs = 0L
    private var lastStatement: Stmt? = null

    suspend fun execute(statements: List<Stmt>, env: Environment = Environment()) {
        for (stmt in statements) {
            if (!currentCoroutineContext().isActive) break
            executeStatement(stmt, env)
        }
    }

    private suspend fun executeStatement(stmt: Stmt, env: Environment) {
        if (!currentCoroutineContext().isActive) return

        when (stmt) {
            is CommentStmt -> {
                callbacks.logInfo("Comment: ${stmt.text}")
            }
            is StringStmt -> {
                var textToType = stmt.text
                // Replace escaped characters like \n, \t, \\
                textToType = textToType.replace("\\n", "\n")
                    .replace("\\t", "\t")
                    .replace("\\\\", "\\")

                val varRegex = Regex("\\\$[A-Za-z0-9_]+")
                textToType = varRegex.replace(textToType) { matchResult ->
                    val varName = matchResult.value
                    env.getVar(varName)?.toString() ?: varName
                }

                val delayEach = stringDelayMs
                callbacks.logTyping("Typing string: \"$textToType\"")

                val linesToType = textToType.split('\n')
                for ((lineIdx, lineText) in linesToType.withIndex()) {
                    if (!currentCoroutineContext().isActive) break

                    if (delayEach > 0) {
                        for (ch in lineText) {
                            if (!currentCoroutineContext().isActive) break
                            callbacks.sendText(ch.toString())
                            delay(delayEach)
                        }
                    } else {
                        if (lineText.isNotEmpty()) {
                            callbacks.sendText(lineText)
                        }
                    }

                    if (lineIdx < linesToType.size - 1) {
                        callbacks.sendChord(listOf("ENTER"))
                        if (delayEach > 0) delay(delayEach)
                    }
                }

                if (stmt.isLn) {
                    callbacks.sendChord(listOf("ENTER"))
                }
                lastStatement = stmt
                applyDefaultDelay()
            }
            is StringDelayStmt -> {
                val ms = evaluateExpr(stmt.delayExpr, env) as? Long ?: 0L
                stringDelayMs = ms.coerceAtLeast(0L)
                callbacks.logCommand("Set STRINGDELAY to ${stringDelayMs}ms")
            }
            is DelayStmt -> {
                val ms = evaluateExpr(stmt.durationExpr, env) as? Long ?: 0L
                if (ms > 0) {
                    callbacks.logDelay("Delaying ${ms}ms...")
                    delay(ms)
                }
            }
            is DefaultDelayStmt -> {
                val ms = evaluateExpr(stmt.durationExpr, env) as? Long ?: 0L
                defaultDelayMs = ms.coerceAtLeast(0L)
                callbacks.logCommand("Set DEFAULT_DELAY to ${defaultDelayMs}ms")
            }
            is KeyComboStmt -> {
                callbacks.logCommand("Key press/chord: ${stmt.keys.joinToString(" + ")}")
                callbacks.sendChord(stmt.keys)
                lastStatement = stmt
                applyDefaultDelay()
            }
            is HoldStmt -> {
                callbacks.logCommand("Hold keys: ${stmt.keys.joinToString(" + ")}")
                callbacks.holdKeys(stmt.keys)
                lastStatement = stmt
                applyDefaultDelay()
            }
            is ReleaseStmt -> {
                callbacks.logCommand("Release keys: ${stmt.keys.joinToString(" + ")}")
                callbacks.releaseKeys(stmt.keys)
                lastStatement = stmt
                applyDefaultDelay()
            }
            is VarDeclStmt -> {
                val valObj = evaluateExpr(stmt.initializer, env)
                env.setVar(stmt.varName, valObj)
                callbacks.logCommand("Var ${stmt.varName} = $valObj")
            }
            is IfStmt -> {
                val condVal = evaluateExpr(stmt.condition, env)
                val isTrue = isTruthy(condVal)
                if (isTrue) {
                    execute(stmt.thenBranch, env)
                } else if (stmt.elseBranch != null) {
                    execute(stmt.elseBranch, env)
                }
            }
            is WhileStmt -> {
                var iterations = 0
                while (currentCoroutineContext().isActive && isTruthy(evaluateExpr(stmt.condition, env))) {
                    execute(stmt.body, env)
                    iterations++
                    if (iterations > 100000) {
                        callbacks.logError("Safety stop: WHILE loop exceeded 100,000 iterations")
                        break
                    }
                }
            }
            is ForStmt -> {
                val start = (evaluateExpr(stmt.startExpr, env) as? Long) ?: 0L
                val end = (evaluateExpr(stmt.endExpr, env) as? Long) ?: 0L
                for (i in start..end) {
                    if (!currentCoroutineContext().isActive) break
                    env.setVar(stmt.varName, i)
                    execute(stmt.body, env)
                }
            }
            is FunctionDeclStmt -> {
                env.defineFunction(stmt.name, stmt.body)
                callbacks.logInfo("Defined function ${stmt.name}")
            }
            is CallStmt -> {
                val funcBody = env.getFunction(stmt.name)
                if (funcBody != null) {
                    callbacks.logInfo("Calling function ${stmt.name}")
                    execute(funcBody, Environment(env))
                } else {
                    callbacks.logError("Undefined function: ${stmt.name}")
                }
            }
            is RepeatStmt -> {
                val times = (evaluateExpr(stmt.countExpr, env) as? Long ?: 1L).toInt()
                val targetStmt = lastStatement
                if (targetStmt != null && times > 0) {
                    callbacks.logCommand("REPEAT previous statement ($times times)")
                    repeat(times) {
                        if (!currentCoroutineContext().isActive) return@repeat
                        executeStatement(targetStmt, env)
                    }
                }
            }
            is HardwareWarningStmt -> {
                callbacks.logWarning("[WARNING] ${stmt.command}: ${stmt.message}")
            }
        }
    }

    private suspend fun applyDefaultDelay() {
        if (defaultDelayMs > 0 && currentCoroutineContext().isActive) {
            delay(defaultDelayMs)
        }
    }

    private fun evaluateExpr(expr: Expr, env: Environment): Any {
        return when (expr) {
            is NumberLiteralExpr -> expr.value
            is StringLiteralExpr -> expr.value
            is VariableExpr -> env.getVar(expr.name) ?: 0L
            is UnaryExpr -> {
                val valObj = evaluateExpr(expr.right, env) as? Long ?: 0L
                if (expr.operator == TokenType.MINUS) -valObj else valObj
            }
            is BinaryExpr -> {
                val left = evaluateExpr(expr.left, env)
                val right = evaluateExpr(expr.right, env)

                val lNum = left as? Long ?: (left.toString().toLongOrNull() ?: 0L)
                val rNum = right as? Long ?: (right.toString().toLongOrNull() ?: 0L)

                when (expr.operator) {
                    TokenType.PLUS -> lNum + rNum
                    TokenType.MINUS -> lNum - rNum
                    TokenType.STAR -> lNum * rNum
                    TokenType.SLASH -> if (rNum != 0L) lNum / rNum else 0L
                    TokenType.PERCENT -> if (rNum != 0L) lNum % rNum else 0L
                    TokenType.EQ_EQ -> if (lNum == rNum) 1L else 0L
                    TokenType.BANG_EQ -> if (lNum != rNum) 1L else 0L
                    TokenType.LESS -> if (lNum < rNum) 1L else 0L
                    TokenType.GREATER -> if (lNum > rNum) 1L else 0L
                    TokenType.LESS_EQ -> if (lNum <= rNum) 1L else 0L
                    TokenType.GREATER_EQ -> if (lNum >= rNum) 1L else 0L
                    else -> 0L
                }
            }
        }
    }

    private fun isTruthy(value: Any?): Boolean {
        return when (value) {
            is Boolean -> value
            is Number -> value.toLong() != 0L
            is String -> value.isNotEmpty() && value != "0"
            else -> false
        }
    }
}
