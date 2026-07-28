package me.arianb.usb_hid_client.macros.engine

enum class TokenType {
    // Keywords
    STRING,
    STRINGLN,
    STRINGDELAY,
    DELAY,
    DEFAULT_DELAY,
    DEFAULTDELAY,
    REPEAT,
    VAR,
    IF,
    ELSE,
    END_IF,
    WHILE,
    END_WHILE,
    FOR,
    END_FOR,
    FUNCTION,
    END_FUNCTION,
    HOLD,
    RELEASE,
    REM,

    // Hardware warnings
    ATTACKMODE,
    EXFIL,

    // Key modifiers & keys
    KEY_MODIFIER, // CTRL, ALT, SHIFT, WIN, GUI, META, SUPER, CMD
    KEY_NAME,     // ENTER, TAB, ESC, SPACE, BACKSPACE, DELETE, ARROWS, F1-F12, etc.

    // Expressions & Operators
    IDENTIFIER,   // Variable names like $FOO or function names
    NUMBER,       // Integer numeric literals
    TEXT_LITERAL, // String literal payload
    PLUS,         // +
    MINUS,        // -
    STAR,         // *
    SLASH,        // /
    PERCENT,      // %
    EQUALS,       // =
    EQ_EQ,        // ==
    BANG_EQ,      // !=
    LESS,         // <
    GREATER,      // >
    LESS_EQ,      // <=
    GREATER_EQ,   // >=
    LPAREN,       // (
    RPAREN,       // )

    NEWLINE,
    EOF,
    UNKNOWN
}

data class Token(
    val type: TokenType,
    val lexeme: String,
    val value: Any? = null,
    val line: Int,
    val column: Int
)
