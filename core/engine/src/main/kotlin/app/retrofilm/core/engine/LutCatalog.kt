package app.retrofilm.core.engine

import android.content.res.AssetManager
import java.io.FileNotFoundException
import org.json.JSONObject

/** Wpis katalogu LUT-ów (`assets/luts/catalog.json`). */
data class LutEntry(
    val id: String,
    val name: String,
    val category: String,
    val file: String,
    val size: Int = Lut.DEFAULT_SIZE,
    val format: String = Lut.FORMAT_RGB8
)

/** Katalog LUT-ów z assetów; brak katalogu (np. przed dołączeniem LUT-ów) = pusta lista. */
class LutCatalog(private val assets: AssetManager) {
    val entries: List<LutEntry> by lazy { load() }

    fun open(entry: LutEntry): Lut = assets.open(entry.file).use {
        Lut.fromBinary(it.readBytes(), entry.size, entry.format)
    }

    private fun load(): List<LutEntry> {
        val text = try {
            assets.open(CATALOG).bufferedReader().use { it.readText() }
        } catch (_: FileNotFoundException) {
            return emptyList()
        }
        return parse(text)
    }

    companion object {
        const val CATALOG = "luts/catalog.json"

        fun parse(json: String): List<LutEntry> {
            val root = JSONObject(json)
            val arr = root.getJSONArray("luts")
            return List(arr.length()) { i ->
                val o = arr.getJSONObject(i)
                LutEntry(
                    id = o.getString("id"),
                    name = o.getString("name"),
                    category = o.optString("category"),
                    file = o.getString("file"),
                    size = o.optInt("size", root.optInt("size", Lut.DEFAULT_SIZE)),
                    format = o.optString("format", Lut.FORMAT_RGB8)
                )
            }
        }
    }
}
