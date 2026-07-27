package me.arianb.usb_hid_client.hid_utils

import android.app.Application
import android.content.Intent
import com.topjohnwu.superuser.Shell
import com.topjohnwu.superuser.ipc.RootService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import me.arianb.usb_hid_client.settings.GadgetUserPreferences
import me.arianb.usb_hid_client.shell_utils.RootStateHolder
import timber.log.Timber
import java.io.File
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

class CharacterDeviceManager private constructor(private val application: Application) {
    private val rootStateHolder = RootStateHolder.getInstance()

    private val mConnection = UsbGadgetServiceConnection()

    @Throws(TimeoutCancellationException::class)
    private suspend fun ensureServiceIsBound(
        timeout: Duration = 5000.milliseconds,
        pollInterval: Duration = 500.milliseconds
    ) {
        if (!mConnection.isBound) {
            Intent(application, UsbGadgetService::class.java).also { intent ->
                RootService.bind(intent, mConnection)
            }
        }

        withTimeout(timeout) {
            // wait until the service is bound before trying to use it
            while (!mConnection.isBound) {
                Timber.d("not bound yet, sleeping for a bit before trying again...")
                delay(pollInterval)
            }
            Timber.d("service is bound now!!!")
        }
    }

    private suspend fun useService(block: suspend (UsbGadgetServiceConnection) -> Unit) {
        try {
            ensureServiceIsBound()
        } catch (e: TimeoutCancellationException) {
            Timber.e("Failed to bind service within timeout duration: $e")
            return
        }

        block(mConnection)

        if (mConnection.isBound) {
            RootService.unbind(mConnection)
        }
    }

    suspend fun createCharacterDevices(gadgetUserPreferences: GadgetUserPreferences) {
        useService {
            it.createGadget(gadgetUserPreferences)
        }

        withContext(Dispatchers.IO) {
            fixSelinuxPermissions()

            for (devicePath in DevicePaths.all) {
                try {
                    withTimeout(3000) {
                        // wait until the device file exists before trying to fix its permissions
                        while (!devicePath.exists()) {
                            Timber.d("$devicePath doesn't exist yet, sleeping for a bit before trying again...")
                            delay(50)
                        }
                        Timber.d("$devicePath exists now!!!")
                    }
                    fixCharacterDevicePermissions(devicePath)
                } catch (e: TimeoutCancellationException) {
                    // FIXME: show this error to the user
                    Timber.e("Timed out while waiting for character device '$devicePath' to be created.")
                }
            }
        }
    }

    private fun fixSelinuxPermissions() {
        val cmdPrefix = rootStateHolder.sepolicyCommand ?: return
        val policies = listOf(
            "allow appdomain device chr_file { getattr open read write ioctl lock map }",
            "allow untrusted_app device chr_file { getattr open read write ioctl lock map }",
            "allow untrusted_app_all device chr_file { getattr open read write ioctl lock map }",
            "allow appdomain uhid_device chr_file { getattr open read write ioctl lock map }",
            "allow untrusted_app uhid_device chr_file { getattr open read write ioctl lock map }",
            "allow untrusted_app_all uhid_device chr_file { getattr open read write ioctl lock map }"
        )
        for (policy in policies) {
            Shell.cmd("$cmdPrefix \"$policy\"").exec()
        }
    }

    fun fixCharacterDevicePermissions(device: DevicePath) = fixCharacterDevicePermissions(device.path)

    fun fixCharacterDevicePermissions(device: String) {
        val appUID: Int = application.applicationInfo.uid

        // Set Linux permissions -> allow r/w to the char device
        val chownCommand = "chown '${appUID}:${appUID}' $device"
        val chmodCommand = "chmod 666 $device"
        Shell.cmd(chownCommand).exec()
        Shell.cmd(chmodCommand).exec()

        // Set SELinux permissions
        Shell.cmd("chcon 'u:object_r:uhid_device:s0' $device").exec()
        val chconCommand = "chcon 'u:object_r:device:s0:${getSelinuxCategories()}' $device"
        Shell.cmd(chconCommand).exec()

        fixSelinuxPermissions()
        return
    }

    private fun getSelinuxCategories(): String {
        val appDataDirPath: String = application.applicationInfo.dataDir

        // Get selinux context for app
        val commandResult = Shell.cmd("stat -c %C $appDataDirPath").exec()

        val selinuxContextString = commandResult.out.joinToString(separator = "\n").trim()

        // Get the part of the context that I need (categories) by grabbing everything after the last ':'
        val categories = selinuxContextString.substringAfterLast(':')

        // If it hasn't changed, then the previous piece of code failed to get the substring
        if (categories == selinuxContextString) {
            Timber.wtf("Failed to get app's selinux context")
        }

        Timber.d("context (before,after): (%s,%s)", selinuxContextString, categories)

        return categories
    }

    suspend fun deleteCharacterDevices(gadgetUserPreferences: GadgetUserPreferences) {
        useService {
            it.deleteGadget(gadgetUserPreferences)
        }

        withContext(Dispatchers.IO) {
            for (devicePath in DevicePaths.all) {
                try {
                    withTimeout(3000) {
                        while (devicePath.exists()) {
                            Timber.d("$devicePath still exists, sleeping for a bit before trying again...")
                            delay(50)
                        }
                        Timber.d("$devicePath deleted successfully!")
                    }
                } catch (e: TimeoutCancellationException) {
                    Timber.e("Timed out while waiting for character device '$devicePath' to be deleted.")
                }
            }
        }
    }

    @ModifiesStateDirectly
    fun characterDeviceMissing(charDevicePath: DevicePath): Boolean {
        val isCharDevMissing = if (!DevicePaths.all.contains(charDevicePath)) {
            true
        } else !charDevicePath.exists()

        return isCharDevMissing
    }

    @ModifiesStateDirectly
    fun anyCharacterDeviceMissing(): Boolean {
        for (charDevicePath in DevicePaths.all) {
            if (!charDevicePath.exists()) {
                return true
            }
        }

        return false
    }

    companion object {
        object DevicePaths {
            val DEFAULT_KEYBOARD_DEVICE_PATH = KeyboardDevicePath("/dev/hidg0")
            private val _keyboard = MutableStateFlow(DEFAULT_KEYBOARD_DEVICE_PATH)

            val keyboard: StateFlow<KeyboardDevicePath> = _keyboard

            val all: List<DevicePath>
                get() = listOf(keyboard.value)
        }

        // SELinux stuff
        private const val SELINUX_DOMAIN = "appdomain"
        private const val SELINUX_POLICY = "allow $SELINUX_DOMAIN device chr_file { getattr open read write }"

        @Volatile
        private var INSTANCE: CharacterDeviceManager? = null
        fun getInstance(application: Application): CharacterDeviceManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE?.let {
                    return it
                }

                val instance = CharacterDeviceManager(application)
                INSTANCE = instance
                instance
            }
        }
    }
}

private fun logShellCommandResult(label: String, commandResult: Shell.Result) {
    with(commandResult) {
        Timber.i("${label}: \nexit code=%d\nstdout=%s\nstderr=%s", code, out, err)
    }
}

// This annotation is to help me ensure that I don't accidentally bypass my ViewModel wrappers when calling certain
// methods from inside ViewModel. For example, by calling CharacterDeviceManager.anyCharacterDeviceMissing directly without
// updating the state at the call site.
@Retention(value = AnnotationRetention.BINARY)
@RequiresOptIn(
    level = RequiresOptIn.Level.ERROR,
    message = "Ensure to update state when calling this method. You will usually want to be calling the ViewModel wrappers instead"
)
annotation class ModifiesStateDirectly

interface DevicePath {
    val path: String

    fun exists(): Boolean = File(path).exists()
}

@JvmInline
value class KeyboardDevicePath(override val path: String) : DevicePath

@JvmInline
value class TouchpadDevicePath(override val path: String) : DevicePath
