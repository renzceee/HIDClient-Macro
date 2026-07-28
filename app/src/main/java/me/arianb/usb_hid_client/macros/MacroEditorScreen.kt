package me.arianb.usb_hid_client.macros

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import me.arianb.usb_hid_client.settings.Macro
import me.arianb.usb_hid_client.ui.theme.PaddingNormal
import me.arianb.usb_hid_client.ui.utils.BasicPage
import me.arianb.usb_hid_client.ui.utils.SimpleNavTopBar

class MacroEditorScreen(private val macroId: String? = null) : Screen {

    @Composable
    @OptIn(ExperimentalMaterial3Api::class)
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val macrosViewModel: MacrosViewModel = viewModel()

        var name by remember { mutableStateOf("") }
        val editorState = remember { MacroCodeEditorState("") }
        var existingMacro by remember { mutableStateOf<Macro?>(null) }

        LaunchedEffect(macroId) {
            macroId?.let { id ->
                macrosViewModel.getMacroById(id)?.let { macro ->
                    existingMacro = macro
                    name = macro.name
                    editorState.setText(macro.script)
                }
            }
        }

        fun saveAndExit() {
            val currentScript = editorState.textFieldValue.text
            val currentMacro = existingMacro
            val macro = if (currentMacro == null) {
                Macro(name = name.ifBlank { "New Macro" }, script = currentScript)
            } else {
                currentMacro.copy(
                    name = name.ifBlank { "Macro" },
                    script = currentScript
                )
            }
            macrosViewModel.addOrUpdateMacro(macro)
            navigator.pop()
        }

        // Color theme palette for DuckyScript syntax
        val syntaxColors = SyntaxColors(
            commandColor = MaterialTheme.colorScheme.primary,
            keyColor = MaterialTheme.colorScheme.tertiary,
            stringColor = MaterialTheme.colorScheme.secondary,
            numberColor = MaterialTheme.colorScheme.error.copy(alpha = 0.85f),
            commentColor = MaterialTheme.colorScheme.outline,
            warningColor = MaterialTheme.colorScheme.error,
            highlightBgColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
        )

        BasicPage(
            topBar = {
                SimpleNavTopBar(
                    title = if (macroId == null) "New Macro" else "Edit Macro",
                    actions = {
                        IconButton(onClick = { editorState.toggleSoftWrap() }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.List,
                                contentDescription = if (editorState.isSoftWrapEnabled) "Disable Soft Wrap" else "Enable Soft Wrap",
                                tint = if (editorState.isSoftWrapEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                )
            },
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.Top,
            padding = androidx.compose.foundation.layout.PaddingValues(
                start = PaddingNormal,
                end = PaddingNormal,
                bottom = PaddingNormal
            ),
            floatingActionButton = {
                FloatingActionButton(
                    onClick = { saveAndExit() },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Icon(Icons.Filled.Check, contentDescription = "Save")
                }
            }
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Macro Name Field
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Macro Name") },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                // Quick Insert Keyboard Toolbar (Premade Text Presets)
                var showDelayDialog by remember { mutableStateOf(false) }
                val insertChips = listOf(
                    "STRING" to "STRING ",
                    "STRINGLN" to "STRINGLN ",
                    "ENTER" to "ENTER\n",
                    "CTRL ALT DEL" to "CTRL ALT DEL\n",
                    "GUI r" to "GUI r\n",
                    "DELAY 100" to "DELAY 100\n",
                    "REPEAT 3" to "REPEAT 3\n",
                    "REM" to "REM ",
                    "TAB" to "TAB\n",
                    "ESC" to "ESC\n",
                    "SPACE" to "SPACE\n"
                )

                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    items(insertChips) { (label, token) ->
                        TextButton(
                            onClick = { editorState.insertToken(token) },
                            modifier = Modifier.height(36.dp),
                            shape = RoundedCornerShape(18.dp)
                        ) {
                            Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    item {
                        TextButton(
                            onClick = { showDelayDialog = true },
                            modifier = Modifier.height(36.dp),
                            shape = RoundedCornerShape(18.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("DELAY", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                if (showDelayDialog) {
                    var delayText by remember { mutableStateOf("") }
                    AlertDialog(
                        onDismissRequest = { showDelayDialog = false },
                        title = { Text("Insert Delay (ms)") },
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
                                if (ms != null && ms >= 0) editorState.insertToken("DELAY $ms\n")
                                showDelayDialog = false
                            }) { Text("Insert") }
                        },
                        dismissButton = {
                            TextButton(onClick = { showDelayDialog = false }) { Text("Cancel") }
                        }
                    )
                }

                // Main Code Editor Area (Precise Gutter Y-Positioning for Wrapped Lines)
                val verticalScrollState = rememberScrollState()
                val horizontalScrollState = rememberScrollState()
                val lineCol = editorState.getLineAndColumn()
                val activeLine = lineCol.first
                val totalLines = editorState.getTotalLines()
                val physicalLineStarts = remember(editorState.textFieldValue.text) {
                    editorState.getPhysicalLineStartIndices()
                }

                val lineCountDigits = totalLines.toString().length.coerceAtLeast(2)
                val gutterWidth = (lineCountDigits * 10 + 22).dp

                val editorTextStyle = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    lineHeight = 20.sp,
                    platformStyle = PlatformTextStyle(includeFontPadding = false),
                    lineHeightStyle = LineHeightStyle(
                        alignment = LineHeightStyle.Alignment.Center,
                        trim = LineHeightStyle.Trim.None
                    )
                )

                val primaryColor = MaterialTheme.colorScheme.primary
                val outlineColor = MaterialTheme.colorScheme.outline
                val density = LocalDensity.current

                var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outlineVariant,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .clip(RoundedCornerShape(8.dp)),
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(verticalScrollState)
                            .padding(bottom = 120.dp)
                    ) {
                        // Line Number Gutter (Precise Y-Offset Positioned based on physical lines)
                        Box(
                            modifier = Modifier
                                .width(gutterWidth)
                                .fillMaxHeight()
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                .padding(vertical = 12.dp, horizontal = 4.dp),
                            contentAlignment = Alignment.TopEnd
                        ) {
                            val layout = textLayoutResult
                            if (layout != null) {
                                for (i in 0 until totalLines) {
                                    val lineNum = i + 1
                                    val startChar = if (i < physicalLineStarts.size) physicalLineStarts[i] else 0
                                    val visualLine = layout.getLineForOffset(startChar)
                                    val topPx = layout.getLineTop(visualLine)
                                    val topDp = with(density) { topPx.toDp() }
                                    val isActive = lineNum == activeLine

                                    Text(
                                        text = lineNum.toString(),
                                        style = editorTextStyle.copy(
                                            color = if (isActive) primaryColor else outlineColor,
                                            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                                            textAlign = TextAlign.End
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .offset(y = topDp)
                                    )
                                }
                            } else {
                                // Fallback before initial layout measurement
                                val fallbackAnnotatedString: AnnotatedString = remember(totalLines, activeLine, primaryColor, outlineColor) {
                                    buildAnnotatedString {
                                        for (i in 1..totalLines) {
                                            if (i > 1) append("\n")
                                            val start = length
                                            append(i.toString())
                                            val end = length
                                            val isActive = i == activeLine
                                            addStyle(
                                                SpanStyle(
                                                    color = if (isActive) primaryColor else outlineColor,
                                                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal
                                                ),
                                                start,
                                                end
                                            )
                                        }
                                    }
                                }
                                Text(
                                    text = fallbackAnnotatedString,
                                    style = editorTextStyle.copy(textAlign = TextAlign.End),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }

                        Spacer(
                            modifier = Modifier
                                .width(1.dp)
                                .fillMaxHeight()
                                .background(MaterialTheme.colorScheme.outlineVariant)
                        )

                        // Code Editor Field Container
                        val codeEditorBoxModifier = if (editorState.isSoftWrapEnabled) {
                            Modifier
                                .weight(1f)
                                .fillMaxSize()
                                .padding(vertical = 12.dp, horizontal = 8.dp)
                        } else {
                            Modifier
                                .weight(1f)
                                .fillMaxSize()
                                .horizontalScroll(horizontalScrollState)
                                .padding(vertical = 12.dp, horizontal = 8.dp)
                        }

                        Box(modifier = codeEditorBoxModifier) {
                            // Active Line Background Highlight (Calculated precisely from Layout)
                            val layout = textLayoutResult
                            if (layout != null && activeLine <= physicalLineStarts.size) {
                                val startChar = physicalLineStarts[activeLine - 1]
                                val endChar = if (activeLine < physicalLineStarts.size) physicalLineStarts[activeLine] - 1 else editorState.getTotalCharacters()
                                val startVLine = layout.getLineForOffset(startChar)
                                val endVLine = layout.getLineForOffset(endChar.coerceAtLeast(startChar))
                                val topPx = layout.getLineTop(startVLine)
                                val bottomPx = layout.getLineBottom(endVLine)
                                val topDp = with(density) { topPx.toDp() }
                                val heightDp = with(density) { (bottomPx - topPx).toDp() }

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(heightDp)
                                        .offset(y = topDp)
                                        .background(
                                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
                                        )
                                )
                            }

                            val visualTransformation = remember(syntaxColors) {
                                DuckyScriptSyntaxHighlighter.createVisualTransformation(syntaxColors)
                            }

                            val textFieldModifier = if (editorState.isSoftWrapEnabled) {
                                Modifier.fillMaxWidth()
                            } else {
                                Modifier.defaultMinSize(minWidth = 1000.dp)
                            }

                            BasicTextField(
                                value = editorState.textFieldValue,
                                onValueChange = { editorState.updateText(it) },
                                modifier = textFieldModifier,
                                textStyle = editorTextStyle.copy(color = MaterialTheme.colorScheme.onSurface),
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                visualTransformation = visualTransformation,
                                onTextLayout = { result ->
                                    textLayoutResult = result
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
