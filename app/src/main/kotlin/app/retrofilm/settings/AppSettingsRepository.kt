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
import kotlin.reflect.KClass
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.appSettings: DataStore<Preferences> by preferencesDataStore(name = "app_settings")

/** Trwałe ustawienia aplikacji (DataStore Preferences): dynamiczne kolory i ulubione filmy. */
@Suppress("TooManyFunctions") // para flow+setter na każde ustawienie
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

    /** „HDR ze zdjęcia” dla nowo otwieranych zdjęć (ekran Ustawienia); domyślnie wył. – szybsze otwieranie i zapis. */
    val defaultHdrFromPhoto: Flow<Boolean> = store.data.map { it[DEF_HDR] ?: false }

    suspend fun setDefaultHdrFromPhoto(on: Boolean) {
        store.edit { it[DEF_HDR] = on }
    }

    /** „Logi czasów” (lokalny bufor pomiarów do eksportu); domyślnie wył. */
    val perfLogEnabled: Flow<Boolean> = store.data.map { it[PERF_LOG] ?: false }

    suspend fun setPerfLogEnabled(on: Boolean) {
        store.edit { it[PERF_LOG] = on }
    }

    /** K: tryb ramki – true „Ramka na zewnątrz” (domyślnie), false „Oryginalny rozmiar pliku”. */
    val frameOutward: Flow<Boolean> = store.data.map { it[FRAME_OUTWARD] ?: true }

    suspend fun setFrameOutward(on: Boolean) {
        store.edit { it[FRAME_OUTWARD] = on }
    }

    /** K: Diagnostyka „Zapis na GPU (eksperymentalny)” – domyślnie włączony, z automatycznym powrotem na CPU. */
    val gpuSave: Flow<Boolean> = store.data.map { it[GPU_SAVE] ?: false }

    suspend fun setGpuSave(on: Boolean) {
        store.edit { it[GPU_SAVE] = on }
    }

    /** O: AGSL odrzucone (format 22) – nie próbuj przy kolejnych startach. */
    val gpuSkipAgsl: Flow<Boolean> = store.data.map { it[GPU_SKIP_AGSL] ?: false }

    suspend fun setGpuSkipAgsl(on: Boolean) {
        store.edit { it[GPU_SKIP_AGSL] = on }
    }

    /** Wszystkie znane ustawienia (do kopii zapasowej): nazwa klucza → wartość. */
    suspend fun snapshot(): Map<String, Any> {
        val p = store.data.first()
        return KNOWN.keys.mapNotNull { k -> p[k]?.let { k.name to it } }.toMap()
    }

    /**
     * Przywraca [values] z kopii: tylko znane klucze o zgodnym typie (reszta pomijana). [replace] = najpierw
     * usuwa wszystkie znane ustawienia (powrót do fabrycznych dla brakujących w kopii).
     */
    @Suppress("UNCHECKED_CAST") // typ wartości sprawdzony względem KNOWN przed zapisem
    suspend fun restore(values: Map<String, Any>, replace: Boolean) {
        store.edit { p ->
            if (replace) KNOWN.keys.forEach { p.remove(it) }
            KNOWN.forEach { (key, type) ->
                val v = values[key.name]?.takeIf { type.isInstance(it) } ?: return@forEach
                val clean = if (v is Set<*>) v.filterIsInstance<String>().toSet() else v
                p[key as Preferences.Key<Any>] = clean
            }
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
                displayP3 = p[DEF_P3] ?: false,
                hdrFromPhoto = p[DEF_HDR] ?: false,
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
            p[DEF_P3] = v.displayP3
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
        internal val DEF_P3 = booleanPreferencesKey("default_display_p3")
        internal val DEF_HDR = booleanPreferencesKey("default_hdr_from_photo")
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
            DEF_P3,
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

        internal val PERF_LOG = booleanPreferencesKey("perf_log_enabled")
        internal val FRAME_OUTWARD = booleanPreferencesKey("frame_outward")
        internal val GPU_SAVE = booleanPreferencesKey("gpu_save")
        internal val GPU_SKIP_AGSL = booleanPreferencesKey("gpu_skip_agsl")

        private val KNOWN: Map<Preferences.Key<*>, KClass<*>> = mapOf(
            DYNAMIC_COLOR to Boolean::class,
            THEME_MODE to String::class,
            FAVORITE_FILMS to Set::class,
            PERF_LOG to Boolean::class,
            FRAME_OUTWARD to Boolean::class,
            GPU_SAVE to Boolean::class,
            GPU_SKIP_AGSL to Boolean::class,
            DEF_HDR to Boolean::class,
            DEF_QUALITY to Int::class,
            DEF_MAX_SIDE to Int::class,
            DEF_CHROMA_444 to Boolean::class,
            DEF_KEEP_EXIF to Boolean::class,
            DEF_P3 to Boolean::class,
            DEF_FRAME to Boolean::class,
            DEF_ASPECT to String::class,
            DEF_MARGIN to Float::class,
            DEF_SIGNATURE_ON to Boolean::class,
            DEF_SIGNATURE to String::class,
            DEF_FONT to String::class
        )

        fun create(context: Context): AppSettingsRepository =
            AppSettingsRepository(context.applicationContext.appSettings)
    }
}
