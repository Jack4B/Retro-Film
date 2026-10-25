package app.retrofilm.lut

import android.content.Context
import app.retrofilm.core.ui.lut.LutDownloadSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Ustawienie „tylko Wi‑Fi” (domyślnie włączone) w SharedPreferences. */
class PrefsLutDownloadSettings(context: Context) : LutDownloadSettings {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val state = MutableStateFlow(prefs.getBoolean(KEY_ONLY_WIFI, true))
    override val onlyWifi: StateFlow<Boolean> = state.asStateFlow()

    override fun setOnlyWifi(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ONLY_WIFI, enabled).apply()
        state.value = enabled
    }

    private companion object {
        const val PREFS = "lut_download"
        const val KEY_ONLY_WIFI = "only_wifi"
    }
}
