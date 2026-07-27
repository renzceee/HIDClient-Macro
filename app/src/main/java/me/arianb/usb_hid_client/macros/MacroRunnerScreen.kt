package me.arianb.usb_hid_client.macros

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import me.arianb.usb_hid_client.MacroLogEntry
import me.arianb.usb_hid_client.MacroLogType
import me.arianb.usb_hid_client.MainViewModel
import me.arianb.usb_hid_client.ui.theme.PaddingNormal
import me.arianb.usb_hid_client.ui.utils.BasicPage

class MacroRunnerScreen(private val macroId: String) : Screen {

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val macrosViewModel: MacrosViewModel = viewModel()
        val mainViewModel: MainViewModel = viewModel()

        val macros by macrosViewModel.macrosFlow.collectAsState()
        val macro = remember(macroId, macros) { macrosViewModel.getMacroById(macroId) }

        val isPlaying by mainViewModel.isPlaying.collectAsState()
        val runningMacroId by mainViewModel.runningMacroId.collectAsState()
        val macroLogs by mainViewModel.macroLogs.collectAsState()
        val uiState by mainViewModel.uiState.collectAsState()

        val isThisMacroRunning = isPlaying && runningMacroId == macroId
        val listState = rememberLazyListState()

        LaunchedEffect(macroLogs.size) {
            if (macroLogs.isNotEmpty()) {
                listState.animateScrollToItem(macroLogs.size - 1)
            }
        }

        BasicPage(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = macro?.name ?: "Macro Realtime Logs",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { navigator.pop() }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            },
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.Top,
            padding = androidx.compose.foundation.layout.PaddingValues(
                start = PaddingNormal,
                end = PaddingNormal,
                bottom = PaddingNormal
            )
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Header Info Card with Controls
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = macro?.name ?: "Unknown Macro",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${macro?.script?.lines()?.count() ?: 0} lines in script",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            // Status Chip
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isThisMacroRunning) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else if (isPlaying) {
                                    MaterialTheme.colorScheme.surfaceVariant
                                } else {
                                    MaterialTheme.colorScheme.secondaryContainer
                                }
                            ) {
                                Text(
                                    text = if (isThisMacroRunning) "RUNNING" else if (isPlaying) "BUSY" else "IDLE",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isThisMacroRunning) {
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.onSecondaryContainer
                                    },
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }

                        // Action Buttons Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (isPlaying) {
                                Button(
                                    onClick = { mainViewModel.stopMacro() },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.error,
                                        contentColor = MaterialTheme.colorScheme.onError
                                    ),
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = "Stop")
                                    Spacer(Modifier.width(6.dp))
                                    Text("Stop")
                                }
                            } else {
                                val canRun = !uiState.missingCharacterDevice && uiState.isCharacterDevicePermissionsBroken == null
                                Button(
                                    onClick = {
                                        macro?.let { mainViewModel.executeMacro(it.id, it.script) }
                                    },
                                    enabled = canRun,
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = "Run")
                                    Spacer(Modifier.width(6.dp))
                                    Text("Run Macro")
                                }
                            }

                            OutlinedButton(
                                onClick = { navigator.push(MacroEditorScreen(macroId)) },
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = "Edit", modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Edit")
                            }
                        }
                    }
                }

                // Log Console Surface
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
                ) {
                    if (macroLogs.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 24.dp, vertical = 16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Press 'Run Macro' to view logs",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    } else {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(macroLogs) { logEntry ->
                                LogLineView(logEntry)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LogLineView(logEntry: MacroLogEntry) {
    val timestampColor = MaterialTheme.colorScheme.outline
    val messageColor = when (logEntry.type) {
        MacroLogType.COMMAND -> MaterialTheme.colorScheme.primary
        MacroLogType.TYPING -> MaterialTheme.colorScheme.secondary
        MacroLogType.DELAY -> MaterialTheme.colorScheme.tertiary
        MacroLogType.SUCCESS -> MaterialTheme.colorScheme.primary
        MacroLogType.ERROR -> MaterialTheme.colorScheme.error
        MacroLogType.INFO -> MaterialTheme.colorScheme.onSurface
    }

    val annotatedString = buildAnnotatedString {
        // Timestamp format [00:00:00]
        withStyle(SpanStyle(color = timestampColor, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)) {
            append(logEntry.timestamp)
        }
        append(" ")
        withStyle(SpanStyle(color = messageColor, fontFamily = FontFamily.Monospace)) {
            append(logEntry.message)
        }
    }

    Text(
        text = annotatedString,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        modifier = Modifier.fillMaxWidth()
    )
}
