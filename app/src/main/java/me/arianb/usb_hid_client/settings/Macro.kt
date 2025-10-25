package me.arianb.usb_hid_client.settings

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.util.UUID

@Parcelize
data class Macro(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val script: String,
) : Parcelable
