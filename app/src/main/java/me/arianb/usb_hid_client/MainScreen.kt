package me.arianb.usb_hid_client

import android.app.Application
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Checkbox
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.currentOrThrow
import me.arianb.usb_hid_client.input_views.DirectInput
import me.arianb.usb_hid_client.input_views.DirectInputIconButton
import me.arianb.usb_hid_client.macros.MacroEditorScreen
import me.arianb.usb_hid_client.macros.MacrosViewModel
import me.arianb.usb_hid_client.settings.AppPreference
import me.arianb.usb_hid_client.settings.SettingsScreen
import me.arianb.usb_hid_client.settings.UserPreferencesRepository
import me.arianb.usb_hid_client.shell_utils.RootStateHolder
import me.arianb.usb_hid_client.troubleshooting.TroubleshootingScreen
import me.arianb.usb_hid_client.ui.standalone_screens.HelpScreen
import me.arianb.usb_hid_client.ui.standalone_screens.InfoScreen
import me.arianb.usb_hid_client.ui.theme.PaddingNormal
import me.arianb.usb_hid_client.ui.utils.BasicPage
import me.arianb.usb_hid_client.ui.utils.BasicTopBar
import me.arianb.usb_hid_client.ui.utils.DarkLightModePreviews
import timber.log.Timber

class MainScreen : Screen {
    @Composable
    override fun Content() {
        MainPage()
    }
}

@Composable
fun MainPage(
    mainViewModel: MainViewModel = viewModel(),
) {
    val navigator = LocalNavigator.currentOrThrow
    val rootStateHolder = RootStateHolder.getInstance()
    val rootState by rootStateHolder.uiState.collectAsState()
    val isPlaying by mainViewModel.isPlaying.collectAsState()

    // TODO: should i do this in VM constructor? but then I cant differentiate between
    //       missing char dev on startup or a weird issue of it missing AFTER startup.
    //       but should I even do that? should I just handle both situations the same way?
    val showMissingCharDeviceOnStartupAlert = remember { mutableStateOf(mainViewModel.anyCharacterDeviceMissing()) }

    val uiState by mainViewModel.uiState.collectAsState()
    Timber.d("in MainScreen, uiState is: %s", uiState.toString())

    val snackbarHostState = remember { SnackbarHostState() }

    val macrosViewModel: MacrosViewModel = viewModel()
    val macros by macrosViewModel.macrosFlow.collectAsState()
    var deleteId by remember { mutableStateOf<String?>(null) }
    val app = LocalContext.current.applicationContext as Application
    val prefsRepo = remember { UserPreferencesRepository.getInstance(app) }

    val padding = PaddingNormal
    BasicPage(
        snackbarHostState = snackbarHostState,
        topBar = { MainTopBar() },

        // The padding below the top app bar is pretty big, so omit top padding
        padding = PaddingValues(start = padding, end = padding, bottom = padding),

        horizontalAlignment = Alignment.CenterHorizontally,

        // I have to manually manage the spacing of elements here, because of the special case of having an invisible
        // View (Direct Input). Otherwise, there's gonna be an awkward spacing created by the invisible View.
        verticalArrangement = Arrangement.Top,
        floatingActionButton = {
            FloatingActionButton(onClick = { navigator.push(MacroEditorScreen()) }) {
                Icon(Icons.Default.Add, contentDescription = "Add Macro")
            }
        }
    ) {
        if (showMissingCharDeviceOnStartupAlert.value) {
            Timber.d("MISSING CHAR DEV ON START")
            CreateCharDevicesAlertDialog(showMissingCharDeviceOnStartupAlert)
        }

        // This has to be here, if I move it below Touchpad(), it never gets focused. I think it's because it ends up
        // out of the user's view, so Android just doesn't allow it to gain focus.
        DirectInput()

        // Macros list
        if (macros.isEmpty()) {
            Text("No macros yet. Tap + to create one.")
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(macros, key = { it.id }) { macro ->
                    ElevatedCard(
                        modifier = Modifier
                            .clickable(enabled = !isPlaying) { mainViewModel.executeMacro(macro.script) }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            androidx.compose.foundation.layout.Column(
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
                            if (!isPlaying) {
                                SplitActionButton(
                                    onPrimary = { mainViewModel.executeMacro(macro.script) },
                                    onEdit = { navigator.push(MacroEditorScreen(macro.id)) },
                                    onDelete = {
                                        val shouldConfirm = prefsRepo.getPreference(AppPreference.ConfirmMacroDeleteKey)
                                        if (shouldConfirm) deleteId = macro.id else macrosViewModel.deleteMacro(macro.id)
                                    }
                                )
                            } else {
                                Button(onClick = { mainViewModel.stopMacro() }) {
                                    Text("Stop")
                                }
                            }
                        }
                    }
                }
            }
        }

        if (deleteId != null) {
            var dontShowAgain by remember { mutableStateOf(false) }
            AlertDialog(
                onDismissRequest = { deleteId = null },
                title = { Text("Delete macro?") },
                text = {
                    androidx.compose.foundation.layout.Column {
                        Text("This action cannot be undone.")
                        androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(8.dp))
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
                // TODO: if this fails here, I need to make it incredibly clear that the app will not work.
                //       right now, you can still try to use it and it'll fail. It should just "lock" the inputs
                //       if this fails I think.
                snackbarHostState.showSnackbar(
                    message = "Missing root permissions",
                    duration = SnackbarDuration.Long
                )
            } else if (uiState.isDeviceUnplugged) {
                snackbarHostState.showSnackbar(
                    message = "ERROR: Your device seems to be disconnected. If not, try reseating the USB cable",
                    duration = SnackbarDuration.Long
                )
            } else if (!showMissingCharDeviceOnStartupAlert.value && uiState.missingCharacterDevice) {
                val result = snackbarHostState.showSnackbar(
                    message = "ERROR: Character device has disappeared since the app was started.",
                    actionLabel = "RECREATE",
                )
                when (result) {
                    SnackbarResult.ActionPerformed -> {
                        mainViewModel.createCharacterDevices()
                    }

                    SnackbarResult.Dismissed -> {}
                }
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
fun SplitActionButton(onPrimary: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    var showMenu by remember { mutableStateOf(false) }
    val rotation by animateFloatAsState(
        targetValue = if (showMenu) 180f else 0f,
        animationSpec = tween(durationMillis = 200),
        label = "splitArrowRotation"
    )

    Row(verticalAlignment = Alignment.CenterVertically) {
        val shapeStart = RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp, topEnd = 0.dp, bottomEnd = 0.dp)
        val shapeEnd = RoundedCornerShape(topStart = 0.dp, bottomStart = 0.dp, topEnd = 16.dp, bottomEnd = 16.dp)

        Button(
            onClick = onPrimary,
            shape = shapeStart,
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
        ) {
            Icon(Icons.Default.PlayArrow, contentDescription = null)
            Text(" Run")
        }

        // visual split between halves
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

private typealias MenuItem = Pair<Screen, String>

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainTopBar() {
    val navigator = LocalNavigator.currentOrThrow
    var showDropdownMenu by remember { mutableStateOf(false) }

    BasicTopBar(
        title = stringResource(R.string.app_name),
        actions = {
            DirectInputIconButton()
            IconButton(onClick = { showDropdownMenu = true }) {
                Icon(
                    imageVector = Icons.Outlined.MoreVert,
                    contentDescription = "Overflow Menu",
                )
                DropdownMenu(
                    expanded = showDropdownMenu,
                    onDismissRequest = { showDropdownMenu = false }
                ) {
                    val menuItems = arrayOf(
                        MenuItem(SettingsScreen(), stringResource(R.string.settings)),
                        MenuItem(TroubleshootingScreen(), stringResource(R.string.troubleshooting_title)),
                        MenuItem(HelpScreen(), stringResource(R.string.help)),
                        MenuItem(InfoScreen(), stringResource(R.string.info))
                    )
                    for (item in menuItems) {
                        DropdownMenuItem(
                            text = { Text(item.second) },
                            onClick = {
                                // Navigate to screen (safely)
                                //
                                // NOTE:
                                //  Extra code here is necessary because the user can spam click the DropdownMenuItem
                                //  before the navigation has completed. This would lead to it trying to navigate to the
                                //  same screen twice. As of right now, Voyager will crash if this happens without you
                                //  setting unique keys in every Screen. However, even after fixing that, being able
                                //  to navigate to the same screen multiple times is undesirable. For this reason, I have
                                //  added extra code that makes sure the given subclass of Screen isn't already present
                                //  in the navigation stack before we navigate.

                                val thisScreen = item.first

                                // Ensure that the Screen we're about to push isn't already in the navigation stack.
                                // Iterates in reverse because it's more likely for the duplicate item to be at the end.
                                for (screen in navigator.items.reversed()) {
                                    if (screen::class == thisScreen::class) {
                                        return@DropdownMenuItem
                                    }
                                }

                                // Navigate to screen
                                navigator.push(thisScreen)
                                showDropdownMenu = false
                            }
                        )
                    }
                }
            }
        }
    )
}

@Composable
private fun CreateCharDevicesAlertDialog(showAlert: MutableState<Boolean>, mainViewModel: MainViewModel = viewModel()) {
    AlertDialog(
        title = { Text("Character device(s) do not exist") },
        text = { Text("Add HID functions to the default USB gadget? This must be re-done after every reboot.\n\n**The app will not work if you decline**") },
        confirmButton = {
            TextButton(
                content = { Text("YES") },
                onClick = {
                    mainViewModel.createCharacterDevices()
                    showAlert.value = false
                }
            )
        },
        dismissButton = {
            TextButton(
                content = { Text("NO") },
                onClick = {
                    showAlert.value = false
                }
            )
        },
        onDismissRequest = {
            // Intentionally blocking dialog dismissal here since I want the user to make a conscious decision
        }
    )
}

@DarkLightModePreviews
@Composable
private fun MainScreenPreview() {
    Navigator(MainScreen())
}
