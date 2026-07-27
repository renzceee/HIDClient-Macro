package me.arianb.usb_hid_client

import android.app.Application
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.currentOrThrow
import kotlinx.coroutines.launch
import me.arianb.usb_hid_client.input_views.DirectInput
import me.arianb.usb_hid_client.input_views.DirectInputIconButton
import me.arianb.usb_hid_client.macros.MacroEditorScreen
import me.arianb.usb_hid_client.macros.MacroRunnerScreen
import me.arianb.usb_hid_client.macros.MacrosViewModel
import me.arianb.usb_hid_client.settings.AppPreference
import me.arianb.usb_hid_client.settings.SettingsContent
import me.arianb.usb_hid_client.settings.UserPreferencesRepository
import me.arianb.usb_hid_client.shell_utils.RootStateHolder
import me.arianb.usb_hid_client.ui.components.KsuStatusCard
import androidx.compose.ui.text.font.FontWeight
import me.arianb.usb_hid_client.ui.theme.PaddingNormal
import me.arianb.usb_hid_client.ui.utils.BasicPage
import me.arianb.usb_hid_client.ui.utils.BasicTopBar
import me.arianb.usb_hid_client.ui.utils.DarkLightModePreviews
import timber.log.Timber

class MainScreen(
    val initialTab: Int = 0
) : Screen {
    @Composable
    override fun Content() {
        MainPage(initialTab = initialTab)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainPage(
    initialTab: Int = 0,
    mainViewModel: MainViewModel = viewModel(),
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(initialTab) }
    val navigator = LocalNavigator.currentOrThrow
    val rootStateHolder = RootStateHolder.getInstance()
    val rootState by rootStateHolder.uiState.collectAsState()
    val isPlaying by mainViewModel.isPlaying.collectAsState()
    val runningMacroId by mainViewModel.runningMacroId.collectAsState()
    val autoRunStatus by mainViewModel.autoRunStatus.collectAsState()

    val uiState by mainViewModel.uiState.collectAsState()
    Timber.d("in MainScreen, uiState is: %s", uiState.toString())

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mainViewModel.setAppForegroundState(true)
                Lifecycle.Event.ON_STOP -> mainViewModel.setAppForegroundState(false)
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val macrosViewModel: MacrosViewModel = viewModel()
    val macros by macrosViewModel.macrosFlow.collectAsState()
    var deleteId by remember { mutableStateOf<String?>(null) }

    val app = LocalContext.current.applicationContext as Application
    val prefsRepo = remember { UserPreferencesRepository.getInstance(app) }

    val padding = PaddingNormal
    BasicPage(
        snackbarHostState = snackbarHostState,
        topBar = {
            if (selectedTab == 0) {
                MainTopBar()
            } else {
                BasicTopBar(title = stringResource(R.string.settings))
            }
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                tonalElevation = 3.dp
            ) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.Home, contentDescription = stringResource(R.string.home)) },
                    label = { Text(stringResource(R.string.home), fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal) }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.settings)) },
                    label = { Text(stringResource(R.string.settings), fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal) }
                )
            }
        },

        padding = PaddingValues(start = padding, end = padding, bottom = if (selectedTab == 1) 0.dp else padding),

        horizontalAlignment = if (selectedTab == 0) Alignment.CenterHorizontally else Alignment.Start,

        verticalArrangement = Arrangement.Top,
        scrollable = (selectedTab == 1),
        floatingActionButton = {
            if (selectedTab == 0) {
                FloatingActionButton(
                    onClick = { navigator.push(MacroEditorScreen()) },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Macro")
                }
            }
        }
    ) {
        if (selectedTab == 0) {
            // KernelSU Hero Status Card
            val isWorking = !rootState.missingRootPrivileges && !uiState.missingCharacterDevice && uiState.isCharacterDevicePermissionsBroken == null
            KsuStatusCard(
                title = "USB HID Client",
                statusText = if (isWorking) "ACTIVE" else "ERROR",
                detailText = if (rootState.missingRootPrivileges) {
                    "Root access missing or denied"
                } else if (uiState.missingCharacterDevice) {
                    "Character devices disabled in Settings"
                } else if (uiState.isCharacterDevicePermissionsBroken != null) {
                    "Character device permission issue: ${uiState.isCharacterDevicePermissionsBroken}"
                } else {
                    "HID Kernel gadget mode active"
                },
                isWorking = isWorking,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            DirectInput()

            if (autoRunStatus != null) {
                ElevatedCard(
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Auto-running ${autoRunStatus!!.currentIndex} of ${autoRunStatus!!.totalCount}",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                            Text(
                                text = autoRunStatus!!.currentMacroName,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                        Button(
                            onClick = { mainViewModel.stopMacro() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Text("Stop")
                        }
                    }
                }
            }

            if (macros.isEmpty()) {
                Text("No macros yet. Tap + to create one.")
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(macros, key = { it.id }) { macro ->
                        val cardShape = RoundedCornerShape(20.dp)
                        Card(
                            onClick = { navigator.push(MacroRunnerScreen(macro.id)) },
                            enabled = !isPlaying,
                            shape = cardShape,
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(end = 12.dp)
                                ) {
                                    Switch(
                                        checked = macro.autoRunEnabled,
                                        onCheckedChange = { macrosViewModel.toggleAutoRun(macro.id) },
                                        enabled = !isPlaying && !uiState.missingCharacterDevice
                                    )
                                    if (macro.autoRunEnabled && macro.autoRunOrder > 0) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = MaterialTheme.colorScheme.primary,
                                        ) {
                                            Text(
                                                text = "#${macro.autoRunOrder}",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.onPrimary,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                                Column(
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        text = macro.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = macro.script.lineSequence().firstOrNull()?.take(80) ?: "",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                val isThisMacroRunning = isPlaying && runningMacroId == macro.id
                                if (isThisMacroRunning) {
                                    Button(
                                        onClick = { mainViewModel.stopMacro() },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.error,
                                            contentColor = MaterialTheme.colorScheme.onError
                                        )
                                    ) {
                                        Text("Stop")
                                    }
                                } else {
                                    val canRun = !isPlaying && !uiState.missingCharacterDevice && uiState.isCharacterDevicePermissionsBroken == null
                                    SplitActionButton(
                                        onPrimary = { mainViewModel.executeMacro(macro.id, macro.script) },
                                        onEdit = { navigator.push(MacroEditorScreen(macro.id)) },
                                        onDelete = {
                                            val shouldConfirm = prefsRepo.getPreference(AppPreference.ConfirmMacroDeleteKey)
                                            if (shouldConfirm) deleteId = macro.id else macrosViewModel.deleteMacro(macro.id)
                                        },
                                        enabled = canRun
                                    )
                                }
                            }
                        }
                    }
                }
            }
        } else {
            SettingsContent(
                macrosViewModel = macrosViewModel,
                mainViewModel = mainViewModel,
                snackbarHostState = snackbarHostState
            )
        }

        if (deleteId != null) {
            var dontShowAgain by remember { mutableStateOf(false) }
            AlertDialog(
                onDismissRequest = { deleteId = null },
                title = { Text("Delete macro?") },
                text = {
                    Column {
                        Text("This action cannot be undone.")
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = dontShowAgain, onCheckedChange = { dontShowAgain = it })
                            Text("Don't show again")
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        if (dontShowAgain) {
                            prefsRepo.setPreference(AppPreference.ConfirmMacroDeleteKey, false)
                        }
                        macrosViewModel.deleteMacro(deleteId!!)
                        deleteId = null
                    }) { Text("Delete") }
                },
                dismissButton = {
                    TextButton(onClick = { deleteId = null }) { Text("Cancel") }
                }
            )
        }

        LaunchedEffect(uiState) {
            Timber.d("LAUNCHED EFFECT RUNNING WITH UI STATE = %s", uiState.toString())
            if (rootState.missingRootPrivileges) {
                snackbarHostState.showSnackbar(
                    message = "Missing root permissions",
                    duration = SnackbarDuration.Long
                )
            } else if (uiState.isCharacterDevicePermissionsBroken != null) {
                val characterDevicePath = uiState.isCharacterDevicePermissionsBroken!!
                val result = snackbarHostState.showSnackbar(
                    message = "ERROR: Character device permissions seem incorrect.",
                    actionLabel = "FIX",
                )
                when (result) {
                    SnackbarResult.ActionPerformed -> {
                        mainViewModel.fixCharacterDevicePermissions(characterDevicePath)
                    }

                    SnackbarResult.Dismissed -> {}
                }
            }
        }
    }
}

@Composable
fun SplitActionButton(onPrimary: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit, enabled: Boolean = true) {
    var showMenu by remember { mutableStateOf(false) }
    val rotation by animateFloatAsState(
        targetValue = if (showMenu) 180f else 0f,
        animationSpec = tween(durationMillis = 200),
        label = "splitArrowRotation"
    )

    Row(verticalAlignment = Alignment.CenterVertically) {
        val shapeStart = RoundedCornerShape(topStart = 50.dp, bottomStart = 50.dp, topEnd = 0.dp, bottomEnd = 0.dp)
        val shapeEnd = RoundedCornerShape(topStart = 0.dp, bottomStart = 0.dp, topEnd = 50.dp, bottomEnd = 50.dp)

        Button(
            onClick = onPrimary,
            enabled = enabled,
            shape = shapeStart,
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
        ) {
            Icon(Icons.Default.PlayArrow, contentDescription = null)
            Text(" Run")
        }

        Box(
            modifier = Modifier
                .height(40.dp)
                .width(1.dp)
                .background(MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.15f))
        )

        Button(
            onClick = { showMenu = true },
            shape = shapeEnd,
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
            colors = ButtonDefaults.buttonColors()
        ) {
            Icon(Icons.Filled.ArrowDropDown, contentDescription = null, modifier = Modifier.rotate(rotation))
        }

        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
            DropdownMenuItem(
                text = { Text("Edit") },
                onClick = { showMenu = false; onEdit() },
                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) }
            )
            DropdownMenuItem(
                text = { Text("Delete") },
                onClick = {
                    showMenu = false
                    onDelete()
                },
                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainTopBar() {
    BasicTopBar(
        title = stringResource(R.string.app_name),
        actions = {
            DirectInputIconButton()
        }
    )
}

@DarkLightModePreviews
@Composable
private fun MainScreenPreview() {
    Navigator(MainScreen())
}

