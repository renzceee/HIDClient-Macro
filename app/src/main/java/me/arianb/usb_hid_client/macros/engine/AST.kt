package me.arianb.usb_hid_client.macros.engine

sealed interface Expr {
    val line: Int
}

data class NumberLiteralExpr(val value: Long, override val line: Int) : Expr
data class StringLiteralExpr(val value: String, override val line: Int) : Expr
data class VariableExpr(val name: String, override val line: Int) : Expr
data class BinaryExpr(val left: Expr, val operator: TokenType, val right: Expr, override val line: Int) : Expr
data class UnaryExpr(val operator: TokenType, val right: Expr, override val line: Int) : Expr

sealed interface Stmt {
    val line: Int
}

data class CommentStmt(val text: String, override val line: Int) : Stmt
data class StringStmt(val text: String, val isLn: Boolean, override val line: Int) : Stmt
data class DelayStmt(val durationExpr: Expr, override val line: Int) : Stmt
data class DefaultDelayStmt(val durationExpr: Expr, override val line: Int) : Stmt
data class StringDelayStmt(val delayExpr: Expr, override val line: Int) : Stmt
data class KeyComboStmt(val keys: List<String>, override val line: Int) : Stmt
data class HoldStmt(val keys: List<String>, override val line: Int) : Stmt
data class ReleaseStmt(val keys: List<String>, override val line: Int) : Stmt
data class VarDeclStmt(val varName: String, val initializer: Expr, override val line: Int) : Stmt
data class IfStmt(val condition: Expr, val thenBranch: List<Stmt>, val elseBranch: List<Stmt>?, override val line: Int) : Stmt
data class WhileStmt(val condition: Expr, val body: List<Stmt>, override val line: Int) : Stmt
data class ForStmt(val varName: String, val startExpr: Expr, val endExpr: Expr, val body: List<Stmt>, override val line: Int) : Stmt
data class FunctionDeclStmt(val name: String, val body: List<Stmt>, override val line: Int) : Stmt
data class CallStmt(val name: String, override val line: Int) : Stmt
data class RepeatStmt(val countExpr: Expr, override val line: Int) : Stmt
data class HardwareWarningStmt(val command: String, val message: String, override val line: Int) : Stmt

data class Diagnostic(
    val line: Int,
    val column: Int,
    val message: String,
    val isError: Boolean = true
)

data class ParseResult(
    val statements: List<Stmt>,
    val diagnostics: List<Diagnostic>
)
