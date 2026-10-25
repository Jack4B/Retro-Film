package app.retrofilm.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.appSettings: DataStore<Preferences> by preferencesDataStore(name = "app_settings")

/** Trwałe ustawienia aplikacji (DataStore Preferences): dynamiczne kolory i ulubione filmy. */
class AppSettingsRepository(private val store: DataStore<Preferences>) {
    /** Dynamiczne kolory (Material You); domyślnie wyłączone. */
    val dynamicColor: Flow<Boolean> = store.data.map { it[DYNAMIC_COLOR] ?: false }

    suspend fun setDynamicColor(enabled: Boolean) {
        store.edit { it[DYNAMIC_COLOR] = enabled }
    }

    /** Id ulubionych filmów (katalog LUT). Tylko lokalnie, bez synchronizacji. */
    val favoriteFilms: Flow<Set<String>> = store.data.map { it[FAVORITE_FILMS] ?: emptySet() }

    suspend fun setFavorite(id: String, favorite: Boolean) {
        store.edit { prefs ->
            val current = prefs[FAVORITE_FILMS] ?: emptySet()
            prefs[FAVORITE_FILMS] = if (favorite) current + id else current - id
        }
    }

    companion object {
        internal val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        internal val FAVORITE_FILMS = stringSetPreferencesKey("favorite_films")

        fun create(context: Context): AppSettingsRepository =
            AppSettingsRepository(context.applicationContext.appSettings)
    }
}
