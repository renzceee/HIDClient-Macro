package me.arianb.usb_hid_client

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.Dispatchers
import me.arianb.usb_hid_client.hid_utils.CharacterDeviceManager
import me.arianb.usb_hid_client.hid_utils.DevicePath
import me.arianb.usb_hid_client.hid_utils.ModifiesStateDirectly
import me.arianb.usb_hid_client.hid_utils.KeyCodeTranslation
import me.arianb.usb_hid_client.macros.engine.DuckyExecutionCallbacks
import me.arianb.usb_hid_client.macros.engine.DuckyInterpreter
import me.arianb.usb_hid_client.macros.engine.Lexer
import me.arianb.usb_hid_client.macros.engine.Parser
import me.arianb.usb_hid_client.report_senders.KeySender
import me.arianb.usb_hid_client.settings.GadgetUserPreferences
import me.arianb.usb_hid_client.settings.Macro
import me.arianb.usb_hid_client.settings.UserPreferencesRepository
import me.arianb.usb_hid_client.shell_utils.RootStateHolder
import timber.log.Timber
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import java.io.FileNotFoundException
import java.io.IOException

enum class MacroLogType {
    INFO,
    COMMAND,
    TYPING,
    DELAY,
    SUCCESS,
    ERROR
}

data class MacroLogEntry(
    val timestamp: String,
    val message: String,
    val type: MacroLogType = MacroLogType.INFO
)

/**
 * Data class that represents the UI state
 */
data class MyUiState(
    // Character Device Stuff
    val missingCharacterDevice: Boolean = false,
    val isCharacterDevicePermissionsBroken: String? = null,
    val isCharacterDeviceUpdating: Boolean = false,

    // Other Stuff
    val isDeviceUnplugged: Boolean = false
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val _uiState = MutableStateFlow(MyUiState())
    val uiState: StateFlow<MyUiState> = _uiState

    private val characterDeviceManager = CharacterDeviceManager.getInstance(application)
    private val rootStateHolder = RootStateHolder.getInstance()
    private val userPreferencesStateFlow = UserPreferencesRepository.getInstance(application).userPreferencesFlow

    val keySender: StateFlow<KeySender> = userPreferencesStateFlow
        .mapState {
            KeySender(it.keyboardCharacterDevicePath)
        }

    private val senderFlowList = listOf(keySender)

    // Macro playback state
    private var currentMacroJob: Job? = null
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying

    private val _runningMacroId = MutableStateFlow<String?>(null)
    val runningMacroId: StateFlow<String?> = _runningMacroId

    data class AutoRunStatus(
        val currentIndex: Int,
        val totalCount: Int,
        val currentMacroName: String
    )

    private val _autoRunStatus = MutableStateFlow<AutoRunStatus?>(null)
    val autoRunStatus: StateFlow<AutoRunStatus?> = _autoRunStatus

    private val _isAppInForeground = MutableStateFlow(true)
    val isAppInForeground: StateFlow<Boolean> = _isAppInForeground

    private val _macroLogs = MutableStateFlow<List<MacroLogEntry>>(emptyList())
    val macroLogs: StateFlow<List<MacroLogEntry>> = _macroLogs.asStateFlow()

    fun clearLogs() {
        _macroLogs.value = emptyList()
    }

    fun appendLog(message: String, type: MacroLogType = MacroLogType.INFO) {
        val timeStr = java.text.SimpleDateFormat("[HH:mm:ss]", java.util.Locale.getDefault()).format(java.util.Date())
        val newEntry = MacroLogEntry(timestamp = timeStr, message = message, type = type)
        _macroLogs.update { it + newEntry }
    }

    private var hasAutoRunExecutedForCurrentConnection = true
    private var isUsbConnectedState = false

    fun setAppForegroundState(isForeground: Boolean) {
        _isAppInForeground.value = isForeground
        if (!isForeground) {
            stopMacro()
        } else {
            checkAndTriggerAutoRunOnConnect()
        }
    }

    private fun onUsbConnected() {
        _uiState.update { it.copy(isDeviceUnplugged = false) }
        if (!isUsbConnectedState) {
            isUsbConnectedState = true
            hasAutoRunExecutedForCurrentConnection = false
            viewModelScope.launch {
                delay(1000L)
                checkAndTriggerAutoRunOnConnect()
            }
        }
    }

    private fun onUsbDisconnected() {
        isUsbConnectedState = false
        hasAutoRunExecutedForCurrentConnection = false
        _uiState.update { it.copy(isDeviceUnplugged = true) }
    }

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val action = intent?.action ?: return
            Timber.d("UsbBroadcastReceiver received action: %s", action)
            when (action) {
                "android.hardware.usb.action.USB_STATE" -> {
                    val connected = intent.getBooleanExtra("connected", false)
                    val configured = intent.getBooleanExtra("configured", false)
                    Timber.d("USB_STATE: connected=%b, configured=%b", connected, configured)
                    if (connected || configured) {
                        onUsbConnected()
                    } else {
                        onUsbDisconnected()
                    }
                }
                Intent.ACTION_POWER_CONNECTED -> {
                    Timber.d("ACTION_POWER_CONNECTED received")
                    onUsbConnected()
                }
                Intent.ACTION_POWER_DISCONNECTED -> {
                    Timber.d("ACTION_POWER_DISCONNECTED received")
                    onUsbDisconnected()
                }
            }
        }
    }

    init {
        val filter = IntentFilter().apply {
            addAction("android.hardware.usb.action.USB_STATE")
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
        }
        val stickyIntent = ContextCompat.registerReceiver(
            application,
            usbReceiver,
            filter,
            ContextCompat.RECEIVER_EXPORTED
        )
        if (stickyIntent != null && stickyIntent.action == "android.hardware.usb.action.USB_STATE") {
            val connected = stickyIntent.getBooleanExtra("connected", false)
            val configured = stickyIntent.getBooleanExtra("configured", false)
            if (connected || configured) {
                _uiState.update { it.copy(isDeviceUnplugged = false) }
                isUsbConnectedState = true
                hasAutoRunExecutedForCurrentConnection = true
            }
        }

        senderFlowList.forEach { senderFlow ->
            viewModelScope.launch {
                senderFlow.collectLatest { sender ->
                    sender.start(
                        onSuccess = {
                            // This is called when no exception was thrown, meaning everything is good :)
                            // so let's set the UI state back to default (no errors)
                            _uiState.update { MyUiState() }
                        },
                        onException = { e ->
                            val characterDevicePath = sender.characterDevicePath
                            if (e is FileNotFoundException && characterDeviceMissing(characterDevicePath)) {
                                Timber.i("Character device '$characterDevicePath' doesn't exist. The user probably skipped the character device creation prompt.")
                            } else {
                                handleException(e, sender.characterDevicePath)
                            }
                        }
                    )
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        try {
            getApplication<Application>().unregisterReceiver(usbReceiver)
        } catch (e: Exception) {
            Timber.e(e)
        }
    }



    private fun handleException(e: IOException, devicePath: DevicePath) {
        val exceptionString = e.message ?: Log.getStackTraceString(e)
        val lowercaseExceptionString = exceptionString.lowercase()

        if (lowercaseExceptionString.contains("errno 108")) {
            Timber.i("device might be unplugged")
            hasAutoRunExecutedForCurrentConnection = false
            _uiState.update { it.copy(isDeviceUnplugged = true) }
        } else if (lowercaseExceptionString.contains("permission denied")) {
            Timber.i("char dev perms are wrong")
            _uiState.update { it.copy(isCharacterDevicePermissionsBroken = devicePath.path) }
        } else if (lowercaseExceptionString.contains("enxio")) {
            Timber.i("somehow the HID gadget is disabled but the character devices are still present")
        } else {
            Timber.e(e)
            Timber.e("unknown error has occurred while trying to write to character device")
//            showSnackbar("ERROR: Failed to send mouse report.", Snackbar.LENGTH_SHORT)
        }

        Timber.d("in MainViewModel, new state is: %s", uiState.value.toString())
    }

    // Character Device Manager
    fun createCharacterDevices() {
        if (!rootStateHolder.hasRootPermissions()) {
            Timber.w("Can't create character devices, missing root permissions")
            return
        }

        _uiState.update { it.copy(missingCharacterDevice = false, isCharacterDeviceUpdating = true) }

        viewModelScope.launch {
            try {
                val gadgetUserPreferences = GadgetUserPreferences.fromUserPreferences(userPreferencesStateFlow.value)
                characterDeviceManager.createCharacterDevices(gadgetUserPreferences)
            } finally {
                val missing = anyCharacterDeviceMissing()
                _uiState.update { it.copy(missingCharacterDevice = missing, isCharacterDeviceUpdating = false) }
            }
        }
    }

    fun deleteCharacterDevices() {
        if (!rootStateHolder.hasRootPermissions()) {
            Timber.w("Can't delete character devices, missing root permissions")
            return
        }

        _uiState.update { it.copy(missingCharacterDevice = true, isCharacterDeviceUpdating = true) }

        viewModelScope.launch {
            try {
                val gadgetUserPreferences = GadgetUserPreferences.fromUserPreferences(userPreferencesStateFlow.value)
                characterDeviceManager.deleteCharacterDevices(gadgetUserPreferences)
            } finally {
                val missing = anyCharacterDeviceMissing()
                _uiState.update { it.copy(missingCharacterDevice = missing, isCharacterDeviceUpdating = false) }
            }
        }
    }

    fun fixCharacterDevicePermissions(device: String) {
        if (!rootStateHolder.hasRootPermissions()) {
            Timber.w("Can't fix character device permissions, missing root permissions")
            return
        }

        characterDeviceManager.fixCharacterDevicePermissions(device)
    }

    @OptIn(ModifiesStateDirectly::class)
    fun characterDeviceMissing(charDevicePath: DevicePath): Boolean {
        val result = characterDeviceManager.characterDeviceMissing(charDevicePath)

        _uiState.update { it.copy(missingCharacterDevice = result) }

        return result
    }

    @OptIn(ModifiesStateDirectly::class)
    fun anyCharacterDeviceMissing(): Boolean {
        val result = characterDeviceManager.anyCharacterDeviceMissing()

        _uiState.update { it.copy(missingCharacterDevice = result) }

        return result
    }

    // Keyboard
    fun addStandardKey(modifier: Byte, key: Byte) =
        keySender.value.addStandardKey(modifier, key)

    fun addMediaKey(key: Byte) =
        keySender.value.addMediaKey(key)

    private inline fun <T, R> StateFlow<T>.mapState(
        crossinline transform: (value: T) -> R
    ) = mapState(viewModelScope, transform)



    fun checkAndTriggerAutoRunOnConnect() {
        if (_isAppInForeground.value && isUsbConnectedState && !hasAutoRunExecutedForCurrentConnection && !_isPlaying.value && !_uiState.value.missingCharacterDevice) {
            val enabledMacros = userPreferencesStateFlow.value.macros
                .filter { it.autoRunEnabled }
                .sortedBy { it.autoRunOrder }
            if (enabledMacros.isNotEmpty()) {
                hasAutoRunExecutedForCurrentConnection = true
                executeAutoRunSequence(enabledMacros)
            }
        }
    }

    fun executeAutoRunSequence(enabledMacros: List<Macro>) {
        if (_isPlaying.value || _uiState.value.missingCharacterDevice || enabledMacros.isEmpty()) return
        val sorted = enabledMacros.sortedBy { it.autoRunOrder }
        currentMacroJob = viewModelScope.launch(Dispatchers.Default) {
            _isPlaying.value = true
            try {
                for ((index, macro) in sorted.withIndex()) {
                    if (!isActive) break
                    _autoRunStatus.value = AutoRunStatus(
                        currentIndex = index + 1,
                        totalCount = sorted.size,
                        currentMacroName = macro.name
                    )
                    _runningMacroId.value = macro.id
                    runScriptInternal(macro.script)
                    if (index < sorted.size - 1 && isActive) {
                        delay(1000L)
                    }
                }
            } finally {
                _isPlaying.value = false
                _autoRunStatus.value = null
                _runningMacroId.value = null
                currentMacroJob = null
            }
        }
    }

    fun executeMacro(macro: Macro) = executeMacro(macro.id, macro.script)

    fun executeMacro(script: String) = executeMacro(null, script)

    fun executeMacro(macroId: String?, script: String) {
        if (_isPlaying.value || _uiState.value.missingCharacterDevice) return
        currentMacroJob = viewModelScope.launch(Dispatchers.Default) {
            _isPlaying.value = true
            _runningMacroId.value = macroId
            clearLogs()
            appendLog("Starting macro execution...", MacroLogType.INFO)
            try {
                runScriptInternal(script)
                if (isActive) {
                    appendLog("Macro execution finished successfully.", MacroLogType.SUCCESS)
                } else {
                    appendLog("Macro execution cancelled.", MacroLogType.ERROR)
                }
            } catch (e: Exception) {
                appendLog("Macro execution failed: ${e.message}", MacroLogType.ERROR)
            } finally {
                _isPlaying.value = false
                _runningMacroId.value = null
                currentMacroJob = null
            }
        }
    }

    private suspend fun runScriptInternal(script: String) {
        val lexer = Lexer(script)
        val tokens = lexer.tokenize()
        val parser = Parser(tokens)
        val parseResult = parser.parse()

        for (diag in parseResult.diagnostics) {
            if (diag.isError) {
                appendLog("[ERROR L${diag.line}:${diag.column}] ${diag.message}", MacroLogType.ERROR)
            } else {
                appendLog("[WARN L${diag.line}:${diag.column}] ${diag.message}", MacroLogType.INFO)
            }
        }

        if (parseResult.diagnostics.any { it.isError }) {
            appendLog("Aborting macro execution due to syntax errors.", MacroLogType.ERROR)
            return
        }

        val callbacks = object : DuckyExecutionCallbacks {
            override suspend fun sendText(text: String) {
                this@MainViewModel.sendText(text)
            }

            override suspend fun sendChord(keys: List<String>) {
                this@MainViewModel.sendChord(keys)
            }

            override suspend fun holdKeys(keys: List<String>) {
                this@MainViewModel.holdKeys(keys)
            }

            override suspend fun releaseKeys(keys: List<String>) {
                this@MainViewModel.releaseKeys(keys)
            }

            override fun logInfo(message: String) {
                appendLog(message, MacroLogType.INFO)
            }

            override fun logCommand(message: String) {
                appendLog(message, MacroLogType.COMMAND)
            }

            override fun logTyping(message: String) {
                appendLog(message, MacroLogType.TYPING)
            }

            override fun logDelay(message: String) {
                appendLog(message, MacroLogType.DELAY)
            }

            override fun logWarning(message: String) {
                appendLog(message, MacroLogType.INFO)
            }

            override fun logError(message: String) {
                appendLog(message, MacroLogType.ERROR)
            }
        }

        try {
            val interpreter = DuckyInterpreter(callbacks)
            interpreter.execute(parseResult.statements)
        } finally {
            releaseKeys(emptyList())
        }
    }

    fun stopMacro() {
        currentMacroJob?.cancel()
        releaseKeys(emptyList())
    }

    private var heldModifier: Byte = 0

    private fun holdKeys(keys: List<String>) {
        for (k in keys) {
            val bit = modBit(k)
            if (bit.toInt() != 0) {
                heldModifier = (heldModifier.toInt() or bit.toInt()).toByte()
            }
        }
        sendChord(keys)
    }

    private fun releaseKeys(keys: List<String>) {
        if (keys.isEmpty()) {
            heldModifier = 0
        } else {
            for (k in keys) {
                val bit = modBit(k)
                if (bit.toInt() != 0) {
                    heldModifier = (heldModifier.toInt() and bit.toInt().inv()).toByte()
                }
            }
        }
        addStandardKey(heldModifier, 0x00)
    }

    private fun modBit(token: String): Byte = when (token.lowercase()) {
        "ctrl", "control" -> 0x01
        "shift" -> 0x02
        "alt" -> 0x04
        "gui", "windows", "win", "meta", "cmd", "super" -> 0x08
        "rctrl" -> 0x10
        "rshift" -> 0x20
        "ralt" -> 0x40
        "rgui", "rwindows", "rwin", "rmeta", "rcmd", "rsuper" -> 0x80.toByte()
        else -> 0
    }

    private fun keyCode(token: String): Byte? = when (token.lowercase()) {
        "enter", "return" -> 0x28
        "esc", "escape" -> 0x29
        "tab" -> 0x2B
        "space", "spacebar" -> 0x2C
        "backspace", "bksp" -> 0x2A
        "delete", "del" -> 0x4C
        "up", "uparrow" -> 0x52
        "down", "downarrow" -> 0x51
        "left", "leftarrow" -> 0x50
        "right", "rightarrow" -> 0x4F
        "home" -> 0x4A
        "end" -> 0x4D
        "pageup", "pgup" -> 0x4B
        "pagedown", "pgdn" -> 0x4E
        "capslock" -> 0x39
        "printscreen" -> 0x46
        "insert" -> 0x49
        "pause", "break" -> 0x48
        "menu", "app" -> 0x65.toByte()
        "f1" -> 0x3A
        "f2" -> 0x3B
        "f3" -> 0x3C
        "f4" -> 0x3D
        "f5" -> 0x3E
        "f6" -> 0x3F
        "f7" -> 0x40
        "f8" -> 0x41
        "f9" -> 0x42
        "f10" -> 0x43
        "f11" -> 0x44
        "f12" -> 0x45
        else -> {
            if (token.length == 1) {
                val ch = token[0]
                val pair = KeyCodeTranslation.keyCharToScanCodes(ch)
                pair?.second
            } else null
        }
    }

    private fun sendChord(tokens: List<String>) {
        var modifier: Byte = heldModifier
        val keys = mutableListOf<Byte>()

        for (t in tokens) {
            val bit = modBit(t)
            if (bit.toInt() != 0) {
                modifier = (modifier.toInt() or bit.toInt()).toByte()
            } else {
                keyCode(t)?.let { keys.add(it) }
            }
        }

        if (keys.isEmpty()) {
            // Only modifiers: send and release
            addStandardKey(modifier, 0x00)
        } else {
            // Send each key with the same modifier
            keys.forEach { k -> addStandardKey(modifier, k) }
        }
    }

    private fun sendText(text: String) {
        for (c in text) {
            val scanCodes = KeyCodeTranslation.keyCharToScanCodes(c) ?: continue
            val mod = (scanCodes.first.toInt() or heldModifier.toInt()).toByte()
            addStandardKey(mod, scanCodes.second)
        }
    }
}
