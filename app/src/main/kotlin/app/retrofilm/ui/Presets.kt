package app.retrofilm.ui

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.retrofilm.pipeline.Adjustments
import app.retrofilm.pipeline.HslAdjust
import app.retrofilm.pipeline.SharpenMode
import app.retrofilm.pipeline.SharpenParams
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** Preset użytkownika: film, moc i korekty. Nazwa jest identyfikatorem (ta sama nazwa zastępuje zapis). */
data class Preset(val name: String, val film: String, val intensity: Float, val adjustments: Adjustments) {
    companion object {
        const val MAX_NAME = 30
    }
}

/** Zapis presetów do JSON (plik w pamięci aplikacji); odporny na braki pól i uszkodzony plik. */
object PresetCodec {
    fun encode(presets: List<Preset>): String = JSONArray().also { arr ->
        presets.forEach { arr.put(toJson(it)) }
    }.toString()

    fun decode(text: String): List<Preset> = try {
        val arr = JSONArray(text)
        (0 until arr.length()).mapNotNull { fromJson(arr.optJSONObject(it)) }
    } catch (_: JSONException) {
        emptyList()
    }

    private fun toJson(p: Preset): JSONObject = JSONObject().apply {
        put("name", p.name)
        put("film", p.film)
        put("intensity", p.intensity.toDouble())
        put("adjustments", adjustmentsToJson(p.adjustments))
    }

    private fun adjustmentsToJson(a: Adjustments): JSONObject = JSONObject().apply {
        mapOf(
            "exposure" to a.exposure, "contrast" to a.contrast, "saturation" to a.saturation,
            "temperature" to a.temperature, "tint" to a.tint, "highlights" to a.highlights,
            "shadows" to a.shadows, "fade" to a.fade, "grain" to a.grain, "vignette" to a.vignette,
            "sharpness" to a.sharpness, "sharpRadius" to a.sharpRadius, "sharpDetail" to a.sharpDetail,
            "sharpMasking" to a.sharpMasking, "clarity" to a.clarity, "dehaze" to a.dehaze, "denoise" to a.denoise,
            "shadowHue" to a.shadowHue, "shadowAmount" to a.shadowAmount,
            "highlightHue" to a.highlightHue, "highlightAmount" to a.highlightAmount,
            "toneBalance" to a.toneBalance
        ).forEach { (k, v) -> put(k, v.toDouble()) }
        put("hsl", JSONArray(a.hsl.values.map { it.toDouble() }))
        put("sharpMode", a.sharpMode.name)
    }

    private fun fromJson(o: JSONObject?): Preset? {
        val name = o?.optString("name").orEmpty()
        if (o == null || name.isBlank()) return null
        return Preset(
            name = name.take(Preset.MAX_NAME),
            film = o.optString("film", IDENTITY_ID),
            intensity = o.optDouble("intensity", 1.0).toFloat().coerceIn(0f, 1f),
            adjustments = adjustmentsFromJson(o.optJSONObject("adjustments"))
        )
    }

    private fun adjustmentsFromJson(o: JSONObject?): Adjustments {
        if (o == null) return Adjustments.None
        fun f(key: String, default: Float = 0f) = o.optDouble(key, default.toDouble()).toFloat()
        val hsl = o.optJSONArray("hsl")?.let { arr ->
            if (arr.length() ==
                HslAdjust.SIZE
            ) {
                HslAdjust(List(HslAdjust.SIZE) { arr.optDouble(it, 0.0).toFloat() })
            } else {
                null
            }
        } ?: HslAdjust()
        return Adjustments(
            exposure = f("exposure"), contrast = f("contrast"), saturation = f("saturation"),
            temperature = f("temperature"), tint = f("tint"), highlights = f("highlights"),
            shadows = f("shadows"), fade = f("fade"), grain = f("grain"), vignette = f("vignette"),
            sharpness = f("sharpness"), sharpRadius = f("sharpRadius", SharpenParams.DEFAULT_RADIUS),
            sharpDetail = f("sharpDetail", SharpenParams.DEFAULT_DETAIL), sharpMasking = f("sharpMasking"),
            sharpMode = SharpenMode.entries.firstOrNull { it.name == o.optString("sharpMode") } ?: SharpenMode.Standard,
            clarity = f("clarity"), dehaze = f("dehaze"), denoise = f("denoise"),
            hsl = hsl, shadowHue = f("shadowHue"), shadowAmount = f("shadowAmount"),
            highlightHue = f("highlightHue"), highlightAmount = f("highlightAmount"), toneBalance = f("toneBalance")
        )
    }
}

/** Lista presetów użytkownika z zapisem w `presets.json` w pamięci aplikacji. */
class PresetBook {
    var items by mutableStateOf<List<Preset>>(emptyList())
        private set

    private fun file(context: Context) = File(context.filesDir, FILE_NAME)

    suspend fun load(context: Context) {
        items = withContext(Dispatchers.IO) {
            val f = file(context)
            if (f.exists()) PresetCodec.decode(f.readText()) else emptyList()
        }
    }

    suspend fun save(context: Context, preset: Preset) {
        val clean = preset.copy(name = preset.name.trim().take(Preset.MAX_NAME))
        if (clean.name.isEmpty()) return
        items = items.filterNot { it.name.equals(clean.name, ignoreCase = true) } + clean
        persist(context)
    }

    suspend fun delete(context: Context, name: String) {
        items = items.filterNot { it.name == name }
        persist(context)
    }

    private suspend fun persist(context: Context) {
        val text = PresetCodec.encode(items)
        withContext(Dispatchers.IO) { file(context).writeText(text) }
    }

    private companion object {
        const val FILE_NAME = "presets.json"
    }
}

/** Zastosowanie presetu: film (jeśli jest dostępny), moc i korekty. */
fun MainState.applyPreset(p: Preset) {
    if (p.film == IDENTITY_ID || library.films.any { it.id == p.film }) film = p.film
    intensity = p.intensity
    adjustments = p.adjustments
}

/** Czy bieżące ustawienia są identyczne z presetem (do zaznaczenia kafelka). */
fun MainState.matches(p: Preset): Boolean = film == p.film && intensity == p.intensity && adjustments == p.adjustments
