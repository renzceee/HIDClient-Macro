package me.arianb.usb_hid_client.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.Navigator
import kotlinx.coroutines.launch
import me.arianb.usb_hid_client.MainViewModel
import me.arianb.usb_hid_client.R
import me.arianb.usb_hid_client.macros.MacrosViewModel
import me.arianb.usb_hid_client.settings.AppSettings.AppThemePreference
import me.arianb.usb_hid_client.settings.AppSettings.ConfirmMacroDeleteToggle
import me.arianb.usb_hid_client.settings.AppSettings.DynamicColors
import me.arianb.usb_hid_client.settings.AppSettings.ExperimentalMode
import me.arianb.usb_hid_client.settings.AppSettings.FullyDisableGadgetDuringConfiguration
import me.arianb.usb_hid_client.settings.AppSettings.KeyboardCharacterDevicePath
import me.arianb.usb_hid_client.settings.AppSettings.PreferenceCategory
import me.arianb.usb_hid_client.settings.AppSettings.UsbGadgetPath
import me.arianb.usb_hid_client.troubleshooting.DebuggingInfoList
import me.arianb.usb_hid_client.troubleshooting.ExportLogsPreferenceButton
import me.arianb.usb_hid_client.troubleshooting.GadgetActionButtons
import me.arianb.usb_hid_client.ui.theme.PaddingNormal
import me.arianb.usb_hid_client.ui.theme.isDynamicColorAvailable
import me.arianb.usb_hid_client.ui.utils.BasicPage
import me.arianb.usb_hid_client.ui.utils.DarkLightModePreviews
import me.arianb.usb_hid_client.ui.utils.Experimental
import me.arianb.usb_hid_client.ui.utils.SimpleNavTopBar
import me.arianb.usb_hid_client.ui.utils.isExperimentalModeEnabled
import timber.log.Timber

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.unit.dp

class SettingsScreen : Screen {
    @Composable
    override fun Content() {
        SettingsPage()
    }
}

@Composable
fun SettingsPage(
    macrosViewModel: MacrosViewModel = viewModel(),
    mainViewModel: MainViewModel = viewModel(),
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    isStandalone: Boolean = true
) {
    if (isStandalone) {
        BasicPage(
            topBar = { SettingsTopBar() },
            snackbarHostState = snackbarHostState,
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(PaddingNormal, Alignment.Top),
            scrollable = true,
            padding = PaddingValues(start = PaddingNormal, end = PaddingNormal, bottom = 0.dp)
        ) {
            SettingsContent(
                macrosViewModel = macrosViewModel,
                mainViewModel = mainViewModel,
                snackbarHostState = snackbarHostState
            )
        }
    } else {
        SettingsContent(
            macrosViewModel = macrosViewModel,
            mainViewModel = mainViewModel,
            snackbarHostState = snackbarHostState
        )
    }
}

@Composable
fun SettingsContent(
    macrosViewModel: MacrosViewModel = viewModel(),
    mainViewModel: MainViewModel = viewModel(),
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() }
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        mainViewModel.anyCharacterDeviceMissing()
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                val content = context.contentResolver.openInputStream(uri)?.use { it.bufferedReader().readText() }
                if (content != null) {
                    val result = macrosViewModel.importMacros(content)
                    result.onSuccess { count ->
                        val msg = context.getString(R.string.import_success, count)
                        scope.launch { snackbarHostState.showSnackbar(msg) }
                    }.onFailure { err ->
                        val msg = context.getString(R.string.import_failed, err.localizedMessage ?: "Unknown error")
                        scope.launch { snackbarHostState.showSnackbar(msg) }
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to import macros")
                val msg = context.getString(R.string.import_failed, e.localizedMessage ?: "Unknown error")
                scope.launch { snackbarHostState.showSnackbar(msg) }
            }
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            try {
                val jsonString = macrosViewModel.exportSelectedMacros(emptySet())
                context.contentResolver.openOutputStream(uri)?.use { stream ->
                    stream.write(jsonString.toByteArray())
                }
                val count = macrosViewModel.macrosFlow.value.size
                val msg = context.getString(R.string.export_success, count)
                scope.launch { snackbarHostState.showSnackbar(msg) }
            } catch (e: Exception) {
                Timber.e(e, "Failed to export macros")
                val msg = context.getString(R.string.export_failed)
                scope.launch { snackbarHostState.showSnackbar(msg) }
            }
        }
    }

    PreferenceCategory(
        title = stringResource(R.string.theme_header),
    ) {
        AppThemePreference()

        if (isDynamicColorAvailable()) {
            DynamicColors()
        }
    }

    PreferenceCategory(
        title = stringResource(R.string.macros_header),
    ) {
        OnClickPreference(
            title = stringResource(R.string.import_macros),
            summary = stringResource(R.string.import_macros_summary),
            onClick = { importLauncher.launch(arrayOf("application/json", "*/*")) }
        )
        OnClickPreference(
            title = stringResource(R.string.export_macros),
            summary = stringResource(R.string.export_macros_summary),
            onClick = {
                if (macrosViewModel.macrosFlow.value.isEmpty()) {
                    scope.launch { snackbarHostState.showSnackbar(context.getString(R.string.no_macros_to_export)) }
                } else {
                    exportLauncher.launch("macros_config.json")
                }
            }
        )
    }

    PreferenceCategory(
        title = stringResource(R.string.hid_troubleshooting_header),
    ) {
        GadgetActionButtons(mainViewModel)
        DebuggingInfoList(mainViewModel)
        ExportLogsPreferenceButton()
    }

    PreferenceCategory(
        title = stringResource(R.string.misc_header),
        showDivider = isExperimentalModeEnabled()
    ) {
        ExperimentalMode()
        ConfirmMacroDeleteToggle()
    }

    Experimental {
        PreferenceCategory(
            title = stringResource(R.string.device_specific_quirks_header),
            showDivider = false
        ) {
            FullyDisableGadgetDuringConfiguration()
            UsbGadgetPath()
            KeyboardCharacterDevicePath()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsTopBar() {
    SimpleNavTopBar(
        title = stringResource(R.string.settings)
    )
}

// Specializations of the generic preference composable "helper" functions
private object AppSettings {
    @Composable
    fun PreferenceCategory(
        title: String,
        showDivider: Boolean = true,
        preferences: @Composable (() -> Unit)
    ) {
        PreferenceCategory(
            title = title,
            modifier = Modifier.fillMaxWidth(),
            showDivider = showDivider,
            preferences = preferences,
        )
    }

    @Composable
    fun AppThemePreference(
        enabled: Boolean = true,
        settingsViewModel: SettingsViewModel = viewModel()
    ) {
        val preferencesState by settingsViewModel.userPreferencesFlow.collectAsState()

        val selectedTheme = preferencesState.appTheme
        val options = AppTheme.values
        BasicListPreference(
            title = stringResource(R.string.app_theme_title),
            options = options,
            enabled = enabled,
            selected = selectedTheme,
            onPreferenceClicked = { thisAppTheme ->
                settingsViewModel.setPreference(AppPreference.AppThemeKey, thisAppTheme)
            }
        )
    }

    @Composable
    fun DynamicColors() {
        SwitchPreference(
            title = stringResource(R.string.dynamic_colors_title),
            preference = AppPreference.DynamicColorKey
        )
    }


    @Composable
    fun ExperimentalMode() {
        SwitchPreference(
            title = stringResource(R.string.experimental_mode_title),
            summary = stringResource(R.string.experimental_mode_summary),
            preference = AppPreference.ExperimentalMode
        )
    }

    @Composable
    fun ConfirmMacroDeleteToggle() {
        SwitchPreference(
            title = "Confirm before deleting macros",
            summary = "Show confirmation dialog when deleting a macro",
            preference = AppPreference.ConfirmMacroDeleteKey
        )
    }

    @Composable
    fun UsbGadgetPath() {
        TextDialogPreference(
            title = stringResource(R.string.usb_gadget_path_title),
            preference = AppPreference.UsbGadgetPathPref,
            property = UserPreferences::usbGadgetPath
        )
    }

    @Composable
    fun KeyboardCharacterDevicePath() {
        TextDialogPreference(
            title = stringResource(R.string.keyboard_character_device_path),
            preference = AppPreference.KeyboardCharacterDevicePath,
            property = UserPreferences::keyboardCharacterDevicePath
        )
    }


    @Composable
    fun FullyDisableGadgetDuringConfiguration() {
        SwitchPreference(
            title = stringResource(R.string.disable_gadget_functions_during_config),
            summary = stringResource(R.string.disable_gadget_functions_during_config_summary),
            preference = AppPreference.DisableGadgetFunctionsDuringConfiguration
        )
    }
}

@DarkLightModePreviews
@Composable
private fun SettingsScreenPreview() {
    Navigator(SettingsScreen())
}
