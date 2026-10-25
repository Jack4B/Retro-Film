package app.retrofilm.core.engine.pack

import org.json.JSONObject

/**
 * Wpis `catalog-144.json` – kopia 1:1 z repo Jack4B/Retro-Film-LUTs (tag `luts-v1`).
 *
 * [id] ma postać `<kategoria>/<nazwa>`, [file] to ścieżka w repo (`luts/<kategoria>/<nazwa>.png`).
 * [grid] to rozmiar siatki (144, wyjątkowo 256), [unpackedLength] = grid³·3 bajtów RGB8.
 */
data class PackEntry(
    val id: String,
    val displayName: String,
    val category: String,
    val file: String,
    val grid: Int,
    val format: String,
    val sha256: String,
    val bytes: Long,
    val unpackedLength: Long,
    val bundled: Boolean = false
) {
    /** Bez poprawnego hasha nie wolno przyjąć pliku – takiego wpisu nie da się pobrać. */
    val downloadable: Boolean get() = !bundled && SHA256_HEX.matches(sha256)

    /** Nazwa bezpieczna dla systemu plików (bez `/`). */
    val localName: String get() = id.replace('/', SEPARATOR)

    companion object {
        const val SEPARATOR = '~'
        private val SHA256_HEX = Regex("[0-9a-f]{64}")
    }
}

object PackCatalog {
    const val ASSET = "luts/catalog-144.json"

    /** Tag przypięty na sztywno – nigdy `main`. */
    const val TAG = "luts-v1"
    const val HOST = "raw.githubusercontent.com"
    const val BASE_URL = "https://$HOST/Jack4B/Retro-Film-LUTs/$TAG/"
    private const val CHANNELS = 3L

    fun urlFor(entry: PackEntry): String = BASE_URL + entry.file

    fun parse(json: String): List<PackEntry> {
        val root = JSONObject(json)
        val arr = root.getJSONArray("luts")
        val defaultGrid = root.optInt("grid")
        return List(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            val grid = o.optInt("grid", defaultGrid)
            val entry = PackEntry(
                id = o.getString("id"),
                displayName = o.optString("displayName", o.optString("name")),
                category = o.optString("category"),
                file = o.getString("file"),
                grid = grid,
                format = o.optString("format"),
                sha256 = o.optString("sha256").lowercase(),
                bytes = o.getLong("bytes"),
                unpackedLength = o.getLong("unpackedLength"),
                bundled = o.optBoolean("bundled", false)
            )
            require(SAFE_ID.matches(entry.id)) { "Niebezpieczne id: ${entry.id}" }
            require(SAFE_PATH.matches(entry.file)) { "Niebezpieczna ścieżka pliku: ${entry.file}" }
            require(entry.unpackedLength == grid * grid * grid * CHANNELS) {
                "${entry.id}: unpackedLength ${entry.unpackedLength} ≠ $grid³·3"
            }
            entry
        }
    }

    private val SAFE_ID = Regex("[a-z0-9_]+/[a-z0-9_.-]+")
    private val SAFE_PATH = Regex("luts/[a-z0-9_]+/[a-z0-9_.-]+\\.png")
}
