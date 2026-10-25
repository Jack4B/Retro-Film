package app.retrofilm.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.appSettings: DataStore<Preferences> by preferencesDataStore(name = "app_settings")

/** Trwałe ustawienia aplikacji (DataStore Preferences). Na razie: dynamiczne kolory. */
class AppSettingsRepository(private val store: DataStore<Preferences>) {
    /** Dynamiczne kolory (Material You); domyślnie wyłączone. */
    val dynamicColor: Flow<Boolean> = store.data.map { it[DYNAMIC_COLOR] ?: false }

    suspend fun setDynamicColor(enabled: Boolean) {
        store.edit { it[DYNAMIC_COLOR] = enabled }
    }

    companion object {
        internal val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")

        fun create(context: Context): AppSettingsRepository =
            AppSettingsRepository(context.applicationContext.appSettings)
    }
}
