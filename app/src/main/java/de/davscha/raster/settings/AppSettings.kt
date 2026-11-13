package de.davscha.raster.settings

import android.content.Context
import androidx.compose.runtime.mutableStateOf

/** Dauerhafte Einstellungen, gespeichert in SharedPreferences. */
class AppSettings(context: Context) {
    private val prefs = context.getSharedPreferences("raster", Context.MODE_PRIVATE)

    private val fingerDrawsState = mutableStateOf(prefs.getBoolean("fingerDraws", false))
    private val snapState = mutableStateOf(prefs.getBoolean("snap", true))

    /** Aus: nur der Stift zeichnet, Finger verschieben und zoomen die Ansicht. */
    var fingerDraws: Boolean
        get() = fingerDrawsState.value
        set(value) {
            fingerDrawsState.value = value
            prefs.edit().putBoolean("fingerDraws", value).apply()
        }

    /** Beim Verschieben und Skalieren an Screen und anderen Elementen ausrichten. */
    var snap: Boolean
        get() = snapState.value
        set(value) {
            snapState.value = value
            prefs.edit().putBoolean("snap", value).apply()
        }

    var lastFileUri: String?
        get() = prefs.getString("lastFileUri", null)
        set(value) {
            prefs.edit().putString("lastFileUri", value).apply()
        }

    var lastFileName: String?
        get() = prefs.getString("lastFileName", null)
        set(value) {
            prefs.edit().putString("lastFileName", value).apply()
        }
}
