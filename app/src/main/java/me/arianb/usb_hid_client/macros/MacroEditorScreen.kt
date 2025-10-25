package me.arianb.usb_hid_client.macros

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import me.arianb.usb_hid_client.settings.Macro
import me.arianb.usb_hid_client.ui.theme.PaddingNormal
import me.arianb.usb_hid_client.ui.utils.BasicPage
import me.arianb.usb_hid_client.ui.utils.BasicTopBar

class MacroEditorScreen(private val macroId: String? = null) : Screen {
    @Composable
    @OptIn(ExperimentalMaterial3Api::class)
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val macrosViewModel: MacrosViewModel = viewModel()

        var name by remember { mutableStateOf("") }
        var scriptValue by remember { mutableStateOf(TextFieldValue("")) }

        LaunchedEffect(macroId) {
            macroId?.let { id ->
                macrosViewModel.getMacroById(id)?.let { macro ->
                    name = macro.name
                    scriptValue = TextFieldValue(macro.script)
                }
            }
        }

        fun saveAndExit() {
            val existingId = macroId
            val macro = if (existingId == null) {
                Macro(name = name.ifBlank { "New Macro" }, script = scriptValue.text)
            } else {
                Macro(id = existingId, name = name.ifBlank { "Macro" }, script = scriptValue.text)
            }
            macrosViewModel.addOrUpdateMacro(macro)
            navigator.pop()
        }

        val padding = PaddingNormal
        val clipboard = LocalClipboardManager.current
        BasicPage(
            topBar = {
                BasicTopBar(
                    title = if (macroId == null) "New Macro" else "Edit Macro",
                    actions = {
                        IconButton(onClick = { clipboard.setText(AnnotatedString(scriptValue.text)) }) {
                            Icon(painter = androidx.compose.ui.res.painterResource(id = me.arianb.usb_hid_client.R.drawable.ic_copy), contentDescription = "Copy script")
                        }
                    }
                )
            },
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(padding, Alignment.Top),
            // Remove extra top padding under the top app bar
            padding = androidx.compose.foundation.layout.PaddingValues(start = PaddingNormal, end = PaddingNormal, bottom = PaddingNormal),
            floatingActionButton = {
                FloatingActionButton(onClick = { saveAndExit() }) {
                    Icon(Icons.Filled.Check, contentDescription = "Save")
                }
            }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = scriptValue,
                    onValueChange = { scriptValue = it },
                    label = { Text("Script") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 8,
                    textStyle = androidx.compose.material3.MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                    visualTransformation = run {
                        // Capture colors in composable scope (MaterialTheme) and use them inside the transformer
                        val cmdColor = androidx.compose.material3.MaterialTheme.colorScheme.primary
                        val keyColor = androidx.compose.material3.MaterialTheme.colorScheme.tertiary
                        val numColor = androidx.compose.material3.MaterialTheme.colorScheme.secondary
                        val commentColor = androidx.compose.material3.MaterialTheme.colorScheme.outline
                        VisualTransformation { text ->
                            val src = text.text
                            val commandRegex = Regex("^[\\t ]*([A-Za-z0-9_]+)")
                            val keyRegex = Regex("\\b(CTRL|CONTROL|ALT|SHIFT|GUI|WINDOWS|WIN|CMD|META|SUPER|ENTER|TAB|ESC|ESCAPE|SPACE|BACKSPACE|BKSP|DELETE|DEL|UP|DOWN|LEFT|RIGHT|HOME|END|PAGEUP|PGUP|PAGEDOWN|PGDN|F(?:1[0-2]?|[2-9]))\\b", RegexOption.IGNORE_CASE)
                            val numRegex = Regex("\\b\\d+\\b")
                            val lines = src.split('\n')
                            val annotated = buildAnnotatedString {
                                append(src)
                                var pos = 0
                                for (line in lines) {
                                    val lineStart = pos
                                    val lineEnd = pos + line.length
                                    val trimmed = line.trimStart()
                                    if (trimmed.startsWith("REM", ignoreCase = true)) {
                                        addStyle(SpanStyle(color = commentColor), lineStart, lineEnd)
                                    } else {
                                        val m = commandRegex.find(line)
                                        if (m != null) {
                                            val s = lineStart + m.groups[1]!!.range.first
                                            val e = lineStart + m.groups[1]!!.range.last + 1
                                            addStyle(SpanStyle(color = cmdColor, fontWeight = FontWeight.SemiBold), s, e)
                                        }
                                        keyRegex.findAll(line).forEach { mm ->
                                            val s = lineStart + mm.range.first
                                            val e = lineStart + mm.range.last + 1
                                            addStyle(SpanStyle(color = keyColor), s, e)
                                        }
                                        numRegex.findAll(line).forEach { mm ->
                                            val s = lineStart + mm.range.first
                                            val e = lineStart + mm.range.last + 1
                                            addStyle(SpanStyle(color = numColor), s, e)
                                        }
                                    }
                                    pos = lineEnd + 1
                                }
                            }
                            TransformedText(annotated, OffsetMapping.Identity)
                        }
                    }
                )

                // Preset buttons
                Row {
                    fun insertToken(token: String) {
                        val text = scriptValue.text
                        val sel = scriptValue.selection
                        val start = sel.start.coerceIn(0, text.length)
                        val end = sel.end.coerceIn(0, text.length)
                        val newText = buildString(text.length + token.length) {
                            append(text.substring(0, minOf(start, end)))
                            append(token)
                            append(text.substring(maxOf(start, end)))
                        }
                        val newCursor = minOf(start, end) + token.length
                        scriptValue = TextFieldValue(newText, selection = TextRange(newCursor))
                    }
                    val buttons = listOf(
                        "STRING" to "STRING ",
                        "ENTER" to "ENTER\n",
                        "CTRL ALT DEL" to "CTRL ALT DEL\n",
                        "GUI r" to "GUI r\n",
                        "DELAY 100" to "DELAY 100\n",
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(buttons) { (label, token) ->
                            TextButton(onClick = { insertToken(token) }) { Text(label) }
                        }
                        item {
                            var showDelayDialog by remember { mutableStateOf(false) }
                            TextButton(onClick = { showDelayDialog = true }) { Text("ADD DELAY") }

                            if (showDelayDialog) {
                                var delayText by remember { mutableStateOf("") }
                                AlertDialog(
                                    onDismissRequest = { showDelayDialog = false },
                                    title = { Text("Add delay (ms)") },
                                    text = {
                                        OutlinedTextField(
                                            value = delayText,
                                            onValueChange = { delayText = it.filter { ch -> ch.isDigit() } },
                                            label = { Text("Milliseconds") },
                                            singleLine = true,
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    },
                                    confirmButton = {
                                        TextButton(onClick = {
                                            val ms = delayText.toLongOrNull()
                                            if (ms != null && ms >= 0) insertToken("DELAY $ms\n")
                                            showDelayDialog = false
                                        }) { Text("Add") }
                                    },
                                    dismissButton = {
                                        TextButton(onClick = { showDelayDialog = false }) { Text("Cancel") }
                                    }
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.size(8.dp))
                Text(
                    "Tips: Ducky-style. Examples: GUI r, STRING notepad, ENTER, DELAY 250, DEFAULT_DELAY 100, CTRL ALT DEL, REPEAT 3.",
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
