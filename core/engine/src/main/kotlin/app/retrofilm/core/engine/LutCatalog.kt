package app.retrofilm.core.engine

import android.content.res.AssetManager
import java.io.FileNotFoundException
import org.json.JSONObject

/** Wpis katalogu LUT-ów (`assets/luts/catalog.json`). */
data class LutEntry(val id: String, val name: String, val category: String, val file: String)

/** Katalog LUT-ów z assetów; brak katalogu (np. przed dołączeniem LUT-ów) = pusta lista. */
class LutCatalog(private val assets: AssetManager) {
    val entries: List<LutEntry> by lazy { load() }

    fun open(entry: LutEntry): Lut = assets.open(entry.file).use { Lut.fromBinary(it.readBytes()) }

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
            val arr = JSONObject(json).getJSONArray("luts")
            return List(arr.length()) { i ->
                val o = arr.getJSONObject(i)
                LutEntry(o.getString("id"), o.getString("name"), o.optString("category"), o.getString("file"))
            }
        }
    }
}
