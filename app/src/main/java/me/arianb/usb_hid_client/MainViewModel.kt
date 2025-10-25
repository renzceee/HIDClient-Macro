package me.arianb.usb_hid_client

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import me.arianb.usb_hid_client.hid_utils.CharacterDeviceManager
import me.arianb.usb_hid_client.hid_utils.DevicePath
import me.arianb.usb_hid_client.hid_utils.ModifiesStateDirectly
import me.arianb.usb_hid_client.hid_utils.KeyCodeTranslation
import me.arianb.usb_hid_client.report_senders.KeySender
import me.arianb.usb_hid_client.settings.GadgetUserPreferences
import me.arianb.usb_hid_client.settings.UserPreferencesRepository
import me.arianb.usb_hid_client.shell_utils.RootStateHolder
import timber.log.Timber
import java.io.FileNotFoundException
import java.io.IOException

/**
 * Data class that represents the UI state
 */
data class MyUiState(
    // Character Device Stuff
    val missingCharacterDevice: Boolean = false,
    val isCharacterDevicePermissionsBroken: String? = null,

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

    init {
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

    private fun handleException(e: IOException, devicePath: DevicePath) {
        val exceptionString = e.message ?: Log.getStackTraceString(e)
        val lowercaseExceptionString = exceptionString.lowercase()

        if (lowercaseExceptionString.contains("errno 108")) {
            Timber.i("device might be unplugged")
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

        viewModelScope.launch {
            val gadgetUserPreferences = GadgetUserPreferences.fromUserPreferences(userPreferencesStateFlow.value)
            characterDeviceManager.createCharacterDevices(gadgetUserPreferences)

            // Re-evaluate state
            anyCharacterDeviceMissing()
        }
    }

    fun deleteCharacterDevices() {
        if (!rootStateHolder.hasRootPermissions()) {
            Timber.w("Can't delete character devices, missing root permissions")
            return
        }

        viewModelScope.launch {
            val gadgetUserPreferences = GadgetUserPreferences.fromUserPreferences(userPreferencesStateFlow.value)
            characterDeviceManager.deleteCharacterDevices(gadgetUserPreferences)

            // Re-evaluate state
            anyCharacterDeviceMissing()
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

    // Macro executor
    fun executeMacro(script: String) {
        if (_isPlaying.value) return
        currentMacroJob = viewModelScope.launch {
            _isPlaying.value = true
            try {
                val lines = script.lines()
                var defaultDelayMs = 0L
                var lastAction: (suspend () -> Unit)? = null

                fun normalizeToken(t: String): String = when (t.lowercase()) {
                    "windows", "win", "gui", "meta", "cmd", "super" -> "win"
                    "control", "ctrl" -> "ctrl"
                    "escape", "esc" -> "esc"
                    "enter", "return" -> "enter"
                    "tab" -> "tab"
                    "space", "spacebar" -> "space"
                    "backspace", "bksp" -> "backspace"
                    "delete", "del" -> "delete"
                    "up" -> "up"
                    "down" -> "down"
                    "left" -> "left"
                    "right" -> "right"
                    "pageup", "pgup" -> "pageup"
                    "pagedown", "pgdn" -> "pagedown"
                    "home" -> "home"
                    "end" -> "end"
                    else -> t.lowercase()
                }

                suspend fun runActionAndMaybeDelay(action: suspend () -> Unit) {
                    if (!isActive) return
                    action()
                    if (defaultDelayMs > 0 && isActive) delay(defaultDelayMs)
                }

                for (rawLine in lines) {
                    if (!isActive) break
                    val line = rawLine.trim()
                    if (line.isEmpty()) continue
                    if (line.startsWith("REM", ignoreCase = true)) continue

                    val parts = line.split("\u0020+".toRegex(), limit = 2)
                    val cmd = parts[0].uppercase()
                    val arg = if (parts.size > 1) parts[1] else ""

                    when (cmd) {
                        "DEFAULT_DELAY", "DEFAULTDELAY" -> {
                            defaultDelayMs = arg.trim().toLongOrNull() ?: defaultDelayMs
                        }
                        "DELAY" -> {
                            val ms = arg.trim().toLongOrNull() ?: 0L
                            if (ms > 0) delay(ms)
                        }
                        "STRING" -> {
                            val action: suspend () -> Unit = { sendText(arg) }
                            lastAction = action
                            runActionAndMaybeDelay(action)
                        }
                        "ENTER" -> {
                            val action: suspend () -> Unit = { sendChord(listOf("enter")) }
                            lastAction = action
                            runActionAndMaybeDelay(action)
                        }
                        "TAB" -> {
                            val action: suspend () -> Unit = { sendChord(listOf("tab")) }
                            lastAction = action
                            runActionAndMaybeDelay(action)
                        }
                        "ESC", "ESCAPE" -> {
                            val action: suspend () -> Unit = { sendChord(listOf("esc")) }
                            lastAction = action
                            runActionAndMaybeDelay(action)
                        }
                        "SPACE" -> {
                            val action: suspend () -> Unit = { sendChord(listOf("space")) }
                            lastAction = action
                            runActionAndMaybeDelay(action)
                        }
                        "BACKSPACE", "BKSP" -> {
                            val action: suspend () -> Unit = { sendChord(listOf("backspace")) }
                            lastAction = action
                            runActionAndMaybeDelay(action)
                        }
                        "DELETE", "DEL" -> {
                            val action: suspend () -> Unit = { sendChord(listOf("delete")) }
                            lastAction = action
                            runActionAndMaybeDelay(action)
                        }
                        "LEFT" -> {
                            val action: suspend () -> Unit = { sendChord(listOf("left")) }
                            lastAction = action
                            runActionAndMaybeDelay(action)
                        }
                        "RIGHT" -> {
                            val action: suspend () -> Unit = { sendChord(listOf("right")) }
                            lastAction = action
                            runActionAndMaybeDelay(action)
                        }
                        "UP" -> {
                            val action: suspend () -> Unit = { sendChord(listOf("up")) }
                            lastAction = action
                            runActionAndMaybeDelay(action)
                        }
                        "DOWN" -> {
                            val action: suspend () -> Unit = { sendChord(listOf("down")) }
                            lastAction = action
                            runActionAndMaybeDelay(action)
                        }
                        "HOME" -> {
                            val action: suspend () -> Unit = { sendChord(listOf("home")) }
                            lastAction = action
                            runActionAndMaybeDelay(action)
                        }
                        "END" -> {
                            val action: suspend () -> Unit = { sendChord(listOf("end")) }
                            lastAction = action
                            runActionAndMaybeDelay(action)
                        }
                        "PAGEUP", "PGUP" -> {
                            val action: suspend () -> Unit = { sendChord(listOf("pageup")) }
                            lastAction = action
                            runActionAndMaybeDelay(action)
                        }
                        "PAGEDOWN", "PGDN" -> {
                            val action: suspend () -> Unit = { sendChord(listOf("pagedown")) }
                            lastAction = action
                            runActionAndMaybeDelay(action)
                        }
                        "REPEAT" -> {
                            val times = arg.trim().toIntOrNull() ?: 0
                            repeat(times) {
                                if (!isActive) return@repeat
                                val a = lastAction
                                if (a != null) runActionAndMaybeDelay(a)
                            }
                        }
                        "GUI", "WINDOWS", "WIN", "CMD", "META", "SUPER",
                        "CTRL", "CONTROL", "ALT", "SHIFT" -> {
                            // Parse a chord: modifiers + optional key
                            val words = line.split("\u0020+".toRegex()).map { normalizeToken(it) }
                            val tokens = words
                            val action: suspend () -> Unit = { sendChord(tokens) }
                            lastAction = action
                            runActionAndMaybeDelay(action)
                        }
                        else -> {
                            // Fallback: allow lines like CTRL ALT DEL or plain text via STRING
                            val words = line.split("\u0020+".toRegex()).map { normalizeToken(it) }
                            val maybeHasModifier = words.any { it in listOf("ctrl", "alt", "shift", "win") }
                            if (maybeHasModifier) {
                                val action: suspend () -> Unit = { sendChord(words) }
                                lastAction = action
                                runActionAndMaybeDelay(action)
                            } else {
                                // If it's a single known key token (e.g., left, f5), send as key press
                                val single = words.singleOrNull()
                                val isFunctionKey = single?.matches(Regex("f(1[0-2]|[1-9])", RegexOption.IGNORE_CASE)) == true
                                val knownKeys = setOf(
                                    "enter","esc","tab","space","backspace","delete",
                                    "up","down","left","right","home","end","pageup","pagedown",
                                    "capslock","printscreen"
                                )
                                if (single != null && (single in knownKeys || isFunctionKey)) {
                                    val action: suspend () -> Unit = { sendChord(listOf(single)) }
                                    lastAction = action
                                    runActionAndMaybeDelay(action)
                                } else {
                                    val action: suspend () -> Unit = { sendText(line) }
                                    lastAction = action
                                    runActionAndMaybeDelay(action)
                                }
                            }
                        }
                    }
                }
            } finally {
                _isPlaying.value = false
                currentMacroJob = null
            }
        }
    }

    fun stopMacro() {
        currentMacroJob?.cancel()
    }

    private fun sendChord(tokens: List<String>) {
        // Map tokens to modifier bits or key codes
        var modifier: Byte = 0
        val keys = mutableListOf<Byte>()

        fun modBit(token: String): Byte = when (token) {
            "ctrl", "control" -> 0x01
            "shift" -> 0x02
            "alt" -> 0x04
            "win", "meta", "cmd", "super" -> 0x08
            "rctrl" -> 0x10
            "rshift" -> 0x20
            "ralt" -> 0x40
            "rmeta", "rcmd", "rwin" -> 0x80.toByte()
            else -> 0
        }

        fun keyCode(token: String): Byte? = when (token) {
            "enter" -> 0x28
            "esc", "escape" -> 0x29
            "tab" -> 0x2B
            "space" -> 0x2C
            "backspace" -> 0x2A
            "delete" -> 0x4C
            "up" -> 0x52
            "down" -> 0x51
            "left" -> 0x50
            "right" -> 0x4F
            "home" -> 0x4A
            "end" -> 0x4D
            "pageup" -> 0x4B
            "pagedown" -> 0x4E
            "capslock" -> 0x39
            "printscreen" -> 0x46
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
            addStandardKey(scanCodes.first, scanCodes.second)
        }
    }
}
