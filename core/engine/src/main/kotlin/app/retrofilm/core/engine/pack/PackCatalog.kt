package app.retrofilm.core.engine.pack

import org.json.JSONObject

/**
 * Wpis `catalog-144.json` (assets APK). Pliki leżą w repo Jack4B/Retro-Film-LUTs jako `<cat>/<id>.png`.
 * [sha256], [bytes] i [unpackedBytes] wypełnia skrypt Pythona; placeholder ma puste `sha256`.
 */
data class PackEntry(
    val id: String,
    val displayName: String,
    val category: String,
    val file: String,
    val size: Int,
    val format: String,
    val sha256: String,
    val bytes: Long,
    val unpackedBytes: Long,
    val bundled: Boolean
) {
    /** Bez hasha nie wolno przyjąć pliku – takiego wpisu nie da się pobrać. */
    val downloadable: Boolean get() = !bundled && sha256.length == SHA256_HEX_LENGTH

    companion object {
        const val SHA256_HEX_LENGTH = 64
    }
}

object PackCatalog {
    const val ASSET = "luts/catalog-144.json"

    /** Tag przypięty na sztywno – nigdy `main`. */
    const val TAG = "luts-v1"
    const val HOST = "raw.githubusercontent.com"
    const val BASE_URL = "https://$HOST/Jack4B/Retro-Film-LUTs/$TAG/"

    fun urlFor(entry: PackEntry): String = BASE_URL + entry.file

    fun parse(json: String): List<PackEntry> {
        val root = JSONObject(json)
        val arr = root.getJSONArray("packs")
        return List(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            val id = o.getString("id")
            val category = o.getString("category")
            val size = o.optInt("size", root.optInt("size", HALD_GRID))
            val entry = PackEntry(
                id = id,
                displayName = o.getString("displayName"),
                category = category,
                file = o.optString("file", "$category/$id.png"),
                size = size,
                format = o.optString("format", HaldClutDecoder.FORMAT_PNG_HALD),
                sha256 = o.optString("sha256").lowercase(),
                bytes = o.optLong("bytes"),
                unpackedBytes = o.optLong("unpackedBytes", size.toLong() * size * size * CHANNELS),
                bundled = o.optBoolean("bundled", false)
            )
            require(SAFE_PATH.matches(entry.file)) { "Niebezpieczna ścieżka pliku: ${entry.file}" }
            entry
        }
    }

    private const val HALD_GRID = 144
    private const val CHANNELS = 3
    private val SAFE_PATH = Regex("[a-z0-9_-]+/[a-z0-9_.-]+\\.png")
}
