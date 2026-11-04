package app.retrofilm.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.retrofilm.core.ui.ThemeMode
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

    /** Motyw: Ciemny (domyślnie, D3), Jasny albo Systemowy. Nieznana wartość = Ciemny. */
    val themeMode: Flow<ThemeMode> = store.data.map { prefs ->
        ThemeMode.entries.firstOrNull { it.name == prefs[THEME_MODE] } ?: ThemeMode.Dark
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        store.edit { it[THEME_MODE] = mode.name }
    }

    /** Id ulubionych filmów (katalog LUT). Tylko lokalnie, bez synchronizacji. */
    val favoriteFilms: Flow<Set<String>> = store.data.map { it[FAVORITE_FILMS] ?: emptySet() }

    suspend fun setFavorite(id: String, favorite: Boolean) {
        store.edit { prefs ->
            val current = prefs[FAVORITE_FILMS] ?: emptySet()
            prefs[FAVORITE_FILMS] = if (favorite) current + id else current - id
        }
    }

    /** Domyślne ustawienia nowej edycji (jakość, rozmiar, ramka z podpisem); brak zapisu = fabryczne. */
    val editDefaults: Flow<EditDefaults> = store.data.map { p ->
        EditDefaults.sanitized(
            EditDefaults(
                quality = p[DEF_QUALITY] ?: EditDefaults.FACTORY_QUALITY,
                maxSide = p[DEF_MAX_SIDE] ?: 0,
                chroma444 = p[DEF_CHROMA_444] ?: true,
                keepExif = p[DEF_KEEP_EXIF] ?: true,
                frameEnabled = p[DEF_FRAME] ?: false,
                frameAspect = p[DEF_ASPECT],
                frameMargin = p[DEF_MARGIN],
                signatureEnabled = p[DEF_SIGNATURE_ON] ?: false,
                signature = p[DEF_SIGNATURE] ?: "",
                signatureFont = p[DEF_FONT]
            )
        )
    }

    suspend fun setEditDefaults(d: EditDefaults) {
        val v = EditDefaults.sanitized(d)
        store.edit { p ->
            p[DEF_QUALITY] = v.quality
            p[DEF_MAX_SIDE] = v.maxSide
            p[DEF_CHROMA_444] = v.chroma444
            p[DEF_KEEP_EXIF] = v.keepExif
            p[DEF_FRAME] = v.frameEnabled
            v.frameAspect?.let { p[DEF_ASPECT] = it } ?: p.remove(DEF_ASPECT)
            v.frameMargin?.let { p[DEF_MARGIN] = it } ?: p.remove(DEF_MARGIN)
            p[DEF_SIGNATURE_ON] = v.signatureEnabled
            p[DEF_SIGNATURE] = v.signature
            v.signatureFont?.let { p[DEF_FONT] = it } ?: p.remove(DEF_FONT)
        }
    }

    /** „Przywróć fabryczne”: usuwa zapisane domyślne. */
    suspend fun clearEditDefaults() {
        store.edit { p -> DEFAULT_KEYS.forEach { p.remove(it) } }
    }

    companion object {
        internal val DEF_QUALITY = intPreferencesKey("default_quality")
        internal val DEF_MAX_SIDE = intPreferencesKey("default_max_side")
        internal val DEF_CHROMA_444 = booleanPreferencesKey("default_chroma_444")
        internal val DEF_KEEP_EXIF = booleanPreferencesKey("default_keep_exif")
        internal val DEF_FRAME = booleanPreferencesKey("default_frame")
        internal val DEF_ASPECT = stringPreferencesKey("default_frame_aspect")
        internal val DEF_MARGIN = floatPreferencesKey("default_frame_margin")
        internal val DEF_SIGNATURE_ON = booleanPreferencesKey("default_signature_on")
        internal val DEF_SIGNATURE = stringPreferencesKey("default_signature")
        internal val DEF_FONT = stringPreferencesKey("default_signature_font")
        private val DEFAULT_KEYS = listOf(
            DEF_QUALITY,
            DEF_MAX_SIDE,
            DEF_CHROMA_444,
            DEF_KEEP_EXIF,
            DEF_FRAME,
            DEF_ASPECT,
            DEF_MARGIN,
            DEF_SIGNATURE_ON,
            DEF_SIGNATURE,
            DEF_FONT
        )

        internal val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        internal val THEME_MODE = stringPreferencesKey("theme_mode")
        internal val FAVORITE_FILMS = stringSetPreferencesKey("favorite_films")

        fun create(context: Context): AppSettingsRepository =
            AppSettingsRepository(context.applicationContext.appSettings)
    }
}
