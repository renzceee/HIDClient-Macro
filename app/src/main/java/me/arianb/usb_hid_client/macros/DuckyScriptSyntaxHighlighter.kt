package me.arianb.usb_hid_client.macros

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration

data class SyntaxColors(
    val commandColor: Color,
    val keyColor: Color,
    val stringColor: Color,
    val numberColor: Color,
    val commentColor: Color,
    val warningColor: Color,
    val highlightBgColor: Color
)

object DuckyScriptSyntaxHighlighter {

    private val KEYWORD_COMMANDS = setOf(
        "STRING", "STRINGLN", "DELAY", "DEFAULT_DELAY", "DEFAULTDELAY", "STRINGDELAY", "STRING_DELAY", "REPEAT",
        "VAR", "DEFINE", "IF", "ELSE", "END_IF", "ENDIF", "WHILE", "END_WHILE", "ENDWHILE",
        "FOR", "END_FOR", "ENDFOR", "FUNCTION", "END_FUNCTION", "ENDFUNCTION", "HOLD", "RELEASE",
        "ENTER", "SPACE", "WINDOWS", "GUI", "MENU", "APP"
    )

    private val HARDWARE_WARNING_COMMANDS = setOf(
        "ATTACKMODE", "EXFIL"
    )

    private val KEY_MODIFIERS = setOf(
        "CTRL", "CONTROL", "ALT", "SHIFT", "SUPER", "WIN", "CMD", "META",
        "ESC", "ESCAPE", "TAB", "BACKSPACE", "BKSP", "DELETE", "DEL",
        "UP", "DOWN", "LEFT", "RIGHT", "HOME", "END",
        "PAGEUP", "PGUP", "PAGEDOWN", "PGDN",
        "F1", "F2", "F3", "F4", "F5", "F6", "F7", "F8", "F9", "F10", "F11", "F12",
        "CAPSLOCK", "PRINTSCREEN", "INSERT", "PAUSE", "MENU", "APP"
    )

    private val COMMAND_REGEX = Regex("^[\\t ]*([A-Za-z0-9_]+)")
    private val KEY_REGEX = Regex("\\b(" + KEY_MODIFIERS.joinToString("|") + ")\\b", RegexOption.IGNORE_CASE)
    private val VAR_REGEX = Regex("\\$[A-Za-z0-9_]+")
    private val NUM_REGEX = Regex("\\b\\d+\\b")

    fun highlight(text: String, colors: SyntaxColors, searchHighlightRanges: List<IntRange> = emptyList()): AnnotatedString {
        return buildAnnotatedString {
            append(text)
            var pos = 0
            val lines = text.split('\n')

            for (line in lines) {
                val lineStart = pos
                val lineEnd = pos + line.length
                val trimmed = line.trimStart()

                if (trimmed.startsWith("REM", ignoreCase = true) || trimmed.startsWith("//")) {
                    // Full line comment
                    addStyle(
                        SpanStyle(color = colors.commentColor, fontStyle = FontStyle.Italic),
                        lineStart,
                        lineEnd
                    )
                } else if (trimmed.isNotEmpty()) {
                    val cmdMatch = COMMAND_REGEX.find(line)
                    if (cmdMatch != null) {
                        val firstWordGroup = cmdMatch.groups[1]!!
                        val firstWord = firstWordGroup.value
                        val s = lineStart + firstWordGroup.range.first
                        val e = lineStart + firstWordGroup.range.last + 1

                        val upperWord = firstWord.uppercase()
                        if (KEYWORD_COMMANDS.contains(upperWord)) {
                            addStyle(
                                SpanStyle(color = colors.commandColor, fontWeight = FontWeight.Bold),
                                s, e
                            )

                            // Special handling for STRING / STRINGLN payload
                            if (upperWord == "STRING" || upperWord == "STRINGLN") {
                                val stringStart = e
                                if (stringStart < lineEnd) {
                                    addStyle(
                                        SpanStyle(color = colors.stringColor),
                                        stringStart, lineEnd
                                    )
                                }
                            }
                        } else if (KEY_MODIFIERS.contains(upperWord)) {
                            addStyle(
                                SpanStyle(color = colors.keyColor, fontWeight = FontWeight.SemiBold),
                                s, e
                            )
                        } else {
                            // Unrecognized first command word -> Highlight warning/error
                            addStyle(
                                SpanStyle(
                                    color = colors.warningColor,
                                    textDecoration = TextDecoration.Underline
                                ),
                                s, e
                            )
                        }
                    }

                    // Highlight key modifiers throughout the line (except for STRING text handled above)
                    val isStringLine = trimmed.startsWith("STRING", ignoreCase = true) ||
                            trimmed.startsWith("STRINGLN", ignoreCase = true)
                    if (!isStringLine) {
                        KEY_REGEX.findAll(line).forEach { match ->
                            val s = lineStart + match.range.first
                            val e = lineStart + match.range.last + 1
                            addStyle(
                                SpanStyle(color = colors.keyColor, fontWeight = FontWeight.SemiBold),
                                s, e
                            )
                        }

                        NUM_REGEX.findAll(line).forEach { match ->
                            val s = lineStart + match.range.first
                            val e = lineStart + match.range.last + 1
                            addStyle(
                                SpanStyle(color = colors.numberColor, fontWeight = FontWeight.Medium),
                                s, e
                            )
                        }
                    }
                }

                pos = lineEnd + 1
            }

            // Apply search match highlights
            for (range in searchHighlightRanges) {
                val s = range.first.coerceIn(0, text.length)
                val e = (range.last + 1).coerceIn(0, text.length)
                if (s < e) {
                    addStyle(
                        SpanStyle(background = colors.highlightBgColor),
                        s, e
                    )
                }
            }
        }
    }

    fun createVisualTransformation(colors: SyntaxColors, searchHighlightRanges: List<IntRange> = emptyList()): VisualTransformation {
        return VisualTransformation { text ->
            val annotated = highlight(text.text, colors, searchHighlightRanges)
            TransformedText(annotated, OffsetMapping.Identity)
        }
    }
}
