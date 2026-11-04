package app.retrofilm.backup

import app.retrofilm.ui.Preset
import app.retrofilm.ui.PresetCodec
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * Kopia zapasowa: wszystkie ustawienia (wartości DataStore, w tym ulubione filmy – filmy wskazywane po id),
 * presety użytkownika. Plik JSON z [BackupCodec.SCHEMA_VERSION] i wersją aplikacji; wartości to Boolean, Int, Long,
 * Float, String albo Set<String>.
 */
data class Backup(
    val appVersion: String,
    val prefs: Map<String, Any> = emptyMap(),
    val presets: List<Preset> = emptyList(),
    val schemaVersion: Int = BackupCodec.SCHEMA_VERSION
) {
    fun summary() = BackupSummary(
        settings = prefs.size,
        presets = presets.size,
        favorites = (prefs[BackupCodec.FAVORITES_KEY] as? Set<*>)?.size ?: 0
    )
}

data class BackupSummary(val settings: Int, val presets: Int, val favorites: Int)

enum class BackupError { NotJson, NotRetroFilm, NewerSchema, Corrupt }

sealed interface BackupResult {
    data class Ok(val backup: Backup) : BackupResult

    data class Invalid(val error: BackupError) : BackupResult
}

enum class RestoreMode { Merge, Replace }

object BackupCodec {
    const val SCHEMA_VERSION = 1
    const val APP_ID = "RetroFilm"
    const val FAVORITES_KEY = "favorite_films"
    const val MIME = "application/json"

    fun encode(b: Backup): String = JSONObject().apply {
        put("app", APP_ID)
        put("schemaVersion", b.schemaVersion)
        put("appVersion", b.appVersion)
        put(
            "prefs",
            JSONObject().also { o ->
                b.prefs.toSortedMap().forEach { (k, v) -> typed(v)?.let { o.put(k, it) } }
            }
        )
        put("presets", JSONArray(PresetCodec.encode(b.presets)))
    }.toString()

    fun decode(text: String): BackupResult {
        val trimmed = text.trim()
        return try {
            if (trimmed.startsWith("[")) {
                // starszy format: sama tablica presetów (presets.json)
                BackupResult.Ok(Backup(appVersion = "", presets = PresetCodec.decode(JSONArray(trimmed).toString())))
            } else {
                decodeObject(JSONObject(trimmed))
            }
        } catch (_: JSONException) {
            BackupResult.Invalid(BackupError.NotJson)
        }
    }

    private fun decodeObject(o: JSONObject): BackupResult {
        val version = o.optInt("schemaVersion", -1)
        val error = when {
            o.optString("app") != APP_ID -> BackupError.NotRetroFilm
            version > SCHEMA_VERSION -> BackupError.NewerSchema
            version < 1 -> BackupError.Corrupt
            o.has("prefs") && o.optJSONObject("prefs") == null -> BackupError.Corrupt
            o.has("presets") && o.optJSONArray("presets") == null -> BackupError.Corrupt
            else -> null
        }
        if (error != null) return BackupResult.Invalid(error)
        val prefsJson = o.optJSONObject("prefs") ?: JSONObject()
        val prefs = prefsJson.keys().asSequence().mapNotNull { k ->
            untyped(prefsJson.optJSONObject(k))?.let { k to it }
        }
        val presets = o.optJSONArray("presets")?.let { PresetCodec.decode(it.toString()) }.orEmpty()
        return BackupResult.Ok(Backup(o.optString("appVersion"), prefs.toMap(), presets, version))
    }

    private fun typed(v: Any): JSONObject? {
        val (t, value) = when (v) {
            is Boolean -> "b" to v
            is Int -> "i" to v
            is Long -> "l" to v
            is Float -> "f" to v.toDouble()
            is String -> "s" to v
            is Set<*> -> "ss" to JSONArray(v.filterIsInstance<String>().sorted())
            else -> return null
        }
        return JSONObject().put("t", t).put("v", value)
    }

    private fun untyped(o: JSONObject?): Any? {
        if (o == null || !o.has("v")) return null
        val v = o.get("v")
        return when (o.optString("t")) {
            "b" -> v as? Boolean
            "i" -> (v as? Number)?.toInt()
            "l" -> (v as? Number)?.toLong()
            "f" -> (v as? Number)?.toFloat()
            "s" -> v as? String
            "ss" -> (v as? JSONArray)?.let { a -> (0 until a.length()).mapNotNull { a.opt(it) as? String }.toSet() }
            else -> null
        }
    }
}

/**
 * Scalanie (kopia wygrywa przy konflikcie; ulubione sumowane; presety po nazwie bez wielkości liter)
 * albo zastąpienie.
 */
object BackupMerge {
    fun apply(current: Backup, incoming: Backup, mode: RestoreMode): Backup = when (mode) {
        RestoreMode.Replace -> current.copy(prefs = incoming.prefs, presets = incoming.presets)

        RestoreMode.Merge -> {
            val prefs = current.prefs + incoming.prefs
            val favorites = setOfNotNull(
                current.prefs[BackupCodec.FAVORITES_KEY],
                incoming.prefs[BackupCodec.FAVORITES_KEY]
            )
                .flatMap { (it as? Set<*>).orEmpty() }.filterIsInstance<String>().toSet()
            val withFav = if (favorites.isEmpty()) prefs else prefs + (BackupCodec.FAVORITES_KEY to favorites)
            val names = incoming.presets.map { it.name.lowercase() }.toSet()
            current.copy(
                prefs = withFav,
                presets = current.presets.filterNot { it.name.lowercase() in names } + incoming.presets
            )
        }
    }
}
