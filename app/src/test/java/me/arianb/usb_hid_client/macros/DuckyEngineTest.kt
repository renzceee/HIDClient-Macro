package me.arianb.usb_hid_client.macros

import kotlinx.coroutines.runBlocking
import me.arianb.usb_hid_client.macros.engine.*
import org.junit.Assert.*
import org.junit.Test

class DuckyEngineTest {

    private class TestCallbacks : DuckyExecutionCallbacks {
        val sentTexts = mutableListOf<String>()
        val sentChords = mutableListOf<List<String>>()
        val heldKeys = mutableListOf<List<String>>()
        val releasedKeys = mutableListOf<List<String>>()
        val infoLogs = mutableListOf<String>()
        val commandLogs = mutableListOf<String>()
        val typingLogs = mutableListOf<String>()
        val delayLogs = mutableListOf<String>()
        val warningLogs = mutableListOf<String>()
        val errorLogs = mutableListOf<String>()

        override suspend fun sendText(text: String) { sentTexts.add(text) }
        override suspend fun sendChord(keys: List<String>) { sentChords.add(keys) }
        override suspend fun holdKeys(keys: List<String>) { heldKeys.add(keys) }
        override suspend fun releaseKeys(keys: List<String>) { releasedKeys.add(keys) }
        override fun logInfo(message: String) { infoLogs.add(message) }
        override fun logCommand(message: String) { commandLogs.add(message) }
        override fun logTyping(message: String) { typingLogs.add(message) }
        override fun logDelay(message: String) { delayLogs.add(message) }
        override fun logWarning(message: String) { warningLogs.add(message) }
        override fun logError(message: String) { errorLogs.add(message) }
    }

    @Test
    fun testStringAndStringLnExecution() = runBlocking {
        val script = """
            STRING Hello World
            STRINGLN Second Line
        """.trimIndent()

        val lexer = Lexer(script)
        val tokens = lexer.tokenize()
        val parser = Parser(tokens)
        val parseResult = parser.parse()

        assertEquals(0, parseResult.diagnostics.size)
        assertEquals(2, parseResult.statements.size)

        val callbacks = TestCallbacks()
        val interpreter = DuckyInterpreter(callbacks)
        interpreter.execute(parseResult.statements)

        assertEquals(listOf("Hello World", "Second Line"), callbacks.sentTexts)
        assertEquals(listOf(listOf("ENTER")), callbacks.sentChords)
    }

    @Test
    fun testEscapedMultilineStringExecution() = runBlocking {
        val script = "STRING Line 1\\nLine 2\\tTabbed"
        val lexer = Lexer(script)
        val parser = Parser(lexer.tokenize())
        val parseResult = parser.parse()

        val callbacks = TestCallbacks()
        val interpreter = DuckyInterpreter(callbacks)
        interpreter.execute(parseResult.statements)

        assertEquals(listOf("Line 1", "Line 2\tTabbed"), callbacks.sentTexts)
        assertEquals(listOf(listOf("ENTER")), callbacks.sentChords)
    }

    @Test
    fun testVariablesAndMathExpression() = runBlocking {
        val script = """
            VAR ${'$'}A = 10
            VAR ${'$'}B = ${'$'}A * 2 + 5
            STRING ${'$'}B
        """.trimIndent()

        val lexer = Lexer(script)
        val parser = Parser(lexer.tokenize())
        val parseResult = parser.parse()

        assertEquals(0, parseResult.diagnostics.size)

        val callbacks = TestCallbacks()
        val env = Environment()
        val interpreter = DuckyInterpreter(callbacks)
        interpreter.execute(parseResult.statements, env)

        assertEquals(25L, env.getVar("${'$'}B"))
    }

    @Test
    fun testIfElseConditionals() = runBlocking {
        val script = """
            VAR ${'$'}X = 100
            IF ${'$'}X == 100
                STRING Matched 100
            ELSE
                STRING Not Matched
            END_IF
        """.trimIndent()

        val lexer = Lexer(script)
        val parser = Parser(lexer.tokenize())
        val parseResult = parser.parse()

        assertEquals(0, parseResult.diagnostics.size)

        val callbacks = TestCallbacks()
        val interpreter = DuckyInterpreter(callbacks)
        interpreter.execute(parseResult.statements)

        assertEquals(listOf("Matched 100"), callbacks.sentTexts)
    }

    @Test
    fun testWhileAndForLoops() = runBlocking {
        val script = """
            VAR ${'$'}COUNT = 0
            WHILE ${'$'}COUNT < 3
                VAR ${'$'}COUNT = ${'$'}COUNT + 1
            END_WHILE
            
            FOR ${'$'}I = 1 TO 2
                STRING Loop ${'$'}I
            END_FOR
        """.trimIndent()

        val lexer = Lexer(script)
        val parser = Parser(lexer.tokenize())
        val parseResult = parser.parse()

        assertEquals(0, parseResult.diagnostics.filter { it.isError }.size)

        val callbacks = TestCallbacks()
        val env = Environment()
        val interpreter = DuckyInterpreter(callbacks)
        interpreter.execute(parseResult.statements, env)

        assertEquals(3L, env.getVar("${'$'}COUNT"))
        assertEquals(listOf("Loop 1", "Loop 2"), callbacks.sentTexts)
    }

    @Test
    fun testFunctionDeclarationAndCall() = runBlocking {
        val script = """
            FUNCTION OPEN_RUN
                GUI r
                DELAY 10
            END_FUNCTION

            OPEN_RUN
        """.trimIndent()

        val lexer = Lexer(script)
        val parser = Parser(lexer.tokenize())
        val parseResult = parser.parse()

        assertEquals(0, parseResult.diagnostics.filter { it.isError }.size)

        val callbacks = TestCallbacks()
        val interpreter = DuckyInterpreter(callbacks)
        interpreter.execute(parseResult.statements)

        assertEquals(listOf(listOf("GUI", "r")), callbacks.sentChords)
    }

    @Test
    fun testHoldAndRelease() = runBlocking {
        val script = """
            HOLD SHIFT
            STRING a
            RELEASE SHIFT
        """.trimIndent()

        val lexer = Lexer(script)
        val parser = Parser(lexer.tokenize())
        val parseResult = parser.parse()

        val callbacks = TestCallbacks()
        val interpreter = DuckyInterpreter(callbacks)
        interpreter.execute(parseResult.statements)

        assertEquals(listOf(listOf("SHIFT")), callbacks.heldKeys)
        assertEquals(listOf(listOf("SHIFT")), callbacks.releasedKeys)
    }

    @Test
    fun testHardwareWarningDiagnostic() = runBlocking {
        val script = "ATTACKMODE HID STORAGE"
        val lexer = Lexer(script)
        val parser = Parser(lexer.tokenize())
        val parseResult = parser.parse()

        val warnings = parseResult.diagnostics.filter { !it.isError }
        assertTrue(warnings.any { it.message.contains("Hardware command 'ATTACKMODE'") })
    }

    @Test
    fun testGuiAndModifierChords() = runBlocking {
        val script = """
            GUI r
            WINDOWS d
            CTRL ALT DEL
        """.trimIndent()

        val lexer = Lexer(script)
        val parser = Parser(lexer.tokenize())
        val parseResult = parser.parse()

        assertEquals(0, parseResult.diagnostics.filter { it.isError }.size)

        val callbacks = TestCallbacks()
        val interpreter = DuckyInterpreter(callbacks)
        interpreter.execute(parseResult.statements)

        assertEquals(3, callbacks.sentChords.size)
        assertEquals(listOf("GUI", "r"), callbacks.sentChords[0])
        assertEquals(listOf("WINDOWS", "d"), callbacks.sentChords[1])
        assertEquals(listOf("CTRL", "ALT", "DEL"), callbacks.sentChords[2])
    }
}
