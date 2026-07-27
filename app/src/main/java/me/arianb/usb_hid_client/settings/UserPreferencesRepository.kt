package me.arianb.usb_hid_client.settings

import android.app.Application
import android.content.SharedPreferences
import android.os.Parcelable
import androidx.annotation.StringRes
import androidx.preference.PreferenceManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.parcelize.Parcelize
import me.arianb.usb_hid_client.R
import me.arianb.usb_hid_client.hid_utils.CharacterDeviceManager
import me.arianb.usb_hid_client.hid_utils.KeyboardDevicePath
import me.arianb.usb_hid_client.hid_utils.UsbGadgetPath
import org.json.JSONArray
import org.json.JSONObject

sealed class AppPreference(val preference: PreferenceKey<*>) {
    data object OnboardingDoneKey : BooleanPreferenceKey("onboarding_done", false)
    data object AppThemeKey : ObjectPreferenceKey<AppTheme>(
        "app_theme", AppTheme.System,
        fromStringPreference = {
            val defaultValue = AppTheme.System

            when (it) {
                AppTheme.System.key -> AppTheme.System
                AppTheme.DarkMode.key -> AppTheme.DarkMode
                AppTheme.LightMode.key -> AppTheme.LightMode
                else -> defaultValue
            }
        },
        toStringPreference = { it.key }
    )

    data object DynamicColorKey : BooleanPreferenceKey("dynamic_color", false)
    data object ExperimentalMode : BooleanPreferenceKey("experimental_mode", false)
    data object UsbGadgetPathPref : ObjectPreferenceKey<UsbGadgetPath>(
        "usb_gadget_path", UsbGadgetPath("/config/usb_gadget/g1"),
        fromStringPreference = { UsbGadgetPath(it) },
        toStringPreference = { it.path }
    )

    data object KeyboardCharacterDevicePath : ObjectPreferenceKey<KeyboardDevicePath>(
        "keyboard_character_device_path", CharacterDeviceManager.Companion.DevicePaths.DEFAULT_KEYBOARD_DEVICE_PATH,
        fromStringPreference = { KeyboardDevicePath(it) },
        toStringPreference = { it.path }
    )

    data object CreateNewGadgetForFunctions : BooleanPreferenceKey("create_new_gadget_for_functions", false)

    data object DisableGadgetFunctionsDuringConfiguration :
        BooleanPreferenceKey("disable_gadget_functions_during_config", false)

    data object MacrosJson : StringPreferenceKey("macros_json", "[]")

    // UI confirmations
    data object ConfirmMacroDeleteKey : BooleanPreferenceKey("confirm_macro_delete", true)
}

sealed class SealedString(val key: String, @StringRes val id: Int)

sealed class AppTheme(key: String, @StringRes id: Int) : SealedString(key, id) {
    data object System : AppTheme("system", R.string.app_theme_system)
    data object LightMode : AppTheme("light", R.string.app_theme_light_mode)
    data object DarkMode : AppTheme("dark", R.string.app_theme_dark_mode)

    companion object {
        val values: List<AppTheme>
            get() = listOf(
                System,
                LightMode,
                DarkMode
            )
    }
}

data class UserPreferences(
    val isOnboardingDone: Boolean,
    val appTheme: AppTheme,
    val isDynamicColorEnabled: Boolean,
    val isExperimentalModeEnabled: Boolean,
    val usbGadgetPath: UsbGadgetPath,
    val keyboardCharacterDevicePath: KeyboardDevicePath,
    val createNewGadgetForFunctions: Boolean,
    val disableGadgetFunctionsDuringConfiguration: Boolean,
    val macros: List<Macro>,
)

@Parcelize
data class GadgetUserPreferences(
    val usbGadgetPath: UsbGadgetPath,
    val createNewGadgetForFunctions: Boolean,
    val disableGadgetFunctionsDuringConfiguration: Boolean,
) : Parcelable {
    companion object {
        fun fromUserPreferences(userPreferences: UserPreferences): GadgetUserPreferences {
            return GadgetUserPreferences(
                usbGadgetPath = userPreferences.usbGadgetPath,
                createNewGadgetForFunctions = userPreferences.createNewGadgetForFunctions,
                disableGadgetFunctionsDuringConfiguration = userPreferences.disableGadgetFunctionsDuringConfiguration,
            )
        }
    }
}

class UserPreferencesRepository private constructor(application: Application) {
    private val sharedPreferences: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(application)

    private val _userPreferencesFlow = MutableStateFlow(userPreferences)
    val userPreferencesFlow: StateFlow<UserPreferences> = _userPreferencesFlow

    private fun <T> PreferenceKey<T>.getValue() = this.getValue(sharedPreferences)
    private fun <T> PreferenceKey<T>.setValue(value: T) = this.setValue(sharedPreferences, value)
    private fun <T> PreferenceKey<T>.resetToDefault() = this.resetToDefault(sharedPreferences)

    private val userPreferences: UserPreferences
        get() {
            return UserPreferences(
                isOnboardingDone = AppPreference.OnboardingDoneKey.getValue(),
                appTheme = AppPreference.AppThemeKey.getValue(),
                isDynamicColorEnabled = AppPreference.DynamicColorKey.getValue(),
                isExperimentalModeEnabled = AppPreference.ExperimentalMode.getValue(),
                usbGadgetPath = AppPreference.UsbGadgetPathPref.getValue(),
                keyboardCharacterDevicePath = AppPreference.KeyboardCharacterDevicePath.getValue(),
                createNewGadgetForFunctions = AppPreference.CreateNewGadgetForFunctions.getValue(),
                disableGadgetFunctionsDuringConfiguration = AppPreference.DisableGadgetFunctionsDuringConfiguration.getValue(),
                macros = getMacrosInternal(),
            )
        }

    fun <T> getPreference(key: PreferenceKey<T>): T =
        key.getValue()

    fun <T> setPreference(key: PreferenceKey<T>, value: T) {
        key.setValue(value)
        _userPreferencesFlow.update { userPreferences }
    }

    private fun resequenceAutoRunOrders(list: List<Macro>): List<Macro> {
        val enabledSorted = list.filter { it.autoRunEnabled }.sortedBy { it.autoRunOrder }
        val orderMap = enabledSorted.mapIndexed { index, macro -> macro.id to (index + 1) }.toMap()
        return list.map { macro ->
            if (macro.autoRunEnabled) {
                macro.copy(autoRunOrder = orderMap[macro.id] ?: 0)
            } else {
                macro.copy(autoRunOrder = 0)
            }
        }
    }

    private fun getMacrosInternal(): List<Macro> {
        val json = AppPreference.MacrosJson.getValue(sharedPreferences)
        val array = JSONArray(json)
        val list = ArrayList<Macro>(array.length())
        for (i in 0 until array.length()) {
            val obj: JSONObject = array.getJSONObject(i)
            list.add(
                Macro(
                    id = obj.optString("id"),
                    name = obj.optString("name"),
                    script = obj.optString("script"),
                    autoRunEnabled = obj.optBoolean("autoRunEnabled", false),
                    autoRunOrder = obj.optInt("autoRunOrder", 0),
                )
            )
        }
        return resequenceAutoRunOrders(list)
    }

    fun getMacros(): List<Macro> = getMacrosInternal()

    fun toggleMacroAutoRun(id: String) {
        val current = getMacrosInternal().toMutableList()
        val idx = current.indexOfFirst { it.id == id }
        if (idx >= 0) {
            val target = current[idx]
            val newEnabled = !target.autoRunEnabled
            val maxOrder = current.filter { it.autoRunEnabled }.maxOfOrNull { it.autoRunOrder } ?: 0
            val newOrder = if (newEnabled) maxOrder + 1 else 0
            current[idx] = target.copy(autoRunEnabled = newEnabled, autoRunOrder = newOrder)
            saveMacros(current)
        }
    }

    fun addOrUpdateMacro(macro: Macro) {
        val current = getMacrosInternal().toMutableList()
        val idx = current.indexOfFirst { it.id == macro.id }
        if (idx >= 0) {
            current[idx] = macro
        } else {
            current.add(macro)
        }
        saveMacros(current)
    }

    fun deleteMacro(id: String) {
        val current = getMacrosInternal().filterNot { it.id == id }
        saveMacros(current)
    }

    fun deleteMacros(ids: Set<String>) {
        val current = getMacrosInternal().filterNot { it.id in ids }
        saveMacros(current)
    }

    fun exportMacrosJson(selectedIds: Set<String> = emptySet()): String {
        val all = getMacrosInternal()
        val toExport = if (selectedIds.isEmpty()) all else all.filter { it.id in selectedIds }
        val array = JSONArray()
        toExport.forEach {
            val obj = JSONObject()
            obj.put("id", it.id)
            obj.put("name", it.name)
            obj.put("script", it.script)
            obj.put("autoRunEnabled", it.autoRunEnabled)
            obj.put("autoRunOrder", it.autoRunOrder)
            array.put(obj)
        }
        return array.toString(2)
    }

    fun importMacros(jsonContent: String): Int {
        val trimmed = jsonContent.trim()
        if (trimmed.isEmpty()) return 0

        val newMacros = mutableListOf<Macro>()
        val array = if (trimmed.startsWith("[")) {
            JSONArray(trimmed)
        } else if (trimmed.startsWith("{")) {
            val obj = JSONObject(trimmed)
            obj.optJSONArray("macros") ?: JSONArray()
        } else {
            throw IllegalArgumentException("Invalid JSON format")
        }

        for (i in 0 until array.length()) {
            val item = array.getJSONObject(i)
            val name = item.optString("name", "").ifBlank { "Imported Macro ${i + 1}" }
            val script = item.optString("script", "")
            val autoRunEnabled = item.optBoolean("autoRunEnabled", false)
            val autoRunOrder = item.optInt("autoRunOrder", 0)
            newMacros.add(
                Macro(
                    id = java.util.UUID.randomUUID().toString(),
                    name = name,
                    script = script,
                    autoRunEnabled = autoRunEnabled,
                    autoRunOrder = autoRunOrder
                )
            )
        }

        if (newMacros.isNotEmpty()) {
            val current = getMacrosInternal().toMutableList()
            current.addAll(newMacros)
            saveMacros(current)
        }

        return newMacros.size
    }

    private fun saveMacros(list: List<Macro>) {
        val resequenced = resequenceAutoRunOrders(list)
        val array = JSONArray()
        resequenced.forEach {
            val obj = JSONObject()
            obj.put("id", it.id)
            obj.put("name", it.name)
            obj.put("script", it.script)
            obj.put("autoRunEnabled", it.autoRunEnabled)
            obj.put("autoRunOrder", it.autoRunOrder)
            array.put(obj)
        }
        AppPreference.MacrosJson.setValue(array.toString())
        _userPreferencesFlow.update { userPreferences }
    }

    fun <T> resetPreferenceToDefault(key: PreferenceKey<T>) {
        key.resetToDefault()
        _userPreferencesFlow.update { userPreferences }
    }

    companion object {
        @Volatile
        private var INSTANCE: UserPreferencesRepository? = null

        fun getInstance(application: Application): UserPreferencesRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE?.let {
                    return it
                }

                val instance = UserPreferencesRepository(application)
                INSTANCE = instance
                instance
            }
        }
    }
}
