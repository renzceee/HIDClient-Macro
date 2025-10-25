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

    fun addOrUpdateMacro(macro: Macro) = repo.addOrUpdateMacro(macro)
    fun deleteMacro(id: String) = repo.deleteMacro(id)
    fun getMacroById(id: String): Macro? = repo.getMacros().find { it.id == id }
}
