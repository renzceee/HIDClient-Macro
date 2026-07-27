package me.arianb.usb_hid_client.macros

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import me.arianb.usb_hid_client.settings.Macro
import me.arianb.usb_hid_client.settings.UserPreferencesRepository

class MacrosViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = UserPreferencesRepository.getInstance(application)

    val macrosFlow: StateFlow<List<Macro>> = repo.userPreferencesFlow
        .map { it.macros }
        .stateIn(viewModelScope, SharingStarted.Eagerly, repo.getMacros())

    fun toggleAutoRun(id: String) = repo.toggleMacroAutoRun(id)
    fun addOrUpdateMacro(macro: Macro) = repo.addOrUpdateMacro(macro)
    fun deleteMacro(id: String) = repo.deleteMacro(id)
    fun deleteMacros(ids: Set<String>) = repo.deleteMacros(ids)
    fun getMacroById(id: String): Macro? = repo.getMacros().find { it.id == id }

    fun exportSelectedMacros(selectedIds: Set<String>): String = repo.exportMacrosJson(selectedIds)

    fun importMacros(jsonContent: String): Result<Int> {
        return try {
            val count = repo.importMacros(jsonContent)
            Result.success(count)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
