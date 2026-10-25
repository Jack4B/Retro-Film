package app.retrofilm.core.engine

import android.content.res.AssetManager
import app.retrofilm.core.engine.pack.HaldClutDecoder
import app.retrofilm.core.engine.pack.LutRawCache
import app.retrofilm.core.engine.pack.PackEntry
import java.io.FileNotFoundException
import java.io.InputStream
import org.json.JSONObject

/**
 * Wpis katalogu LUT-ów (`assets/luts/catalog.json`).
 *
 * [format] `rgb8`/`rgb16` = surowa siatka ([Lut.fromBinary]); `png144`/`png-hald` = HaldCLUT PNG
 * dekodowany przez [HaldClutDecoder] (D9: filmy startowe w natywnej siatce 144³).
 */
data class LutEntry(
    val id: String,
    val name: String,
    val category: String,
    val file: String,
    val size: Int = Lut.DEFAULT_SIZE,
    val format: String = Lut.FORMAT_RGB8,
    val unpackedLength: Long = Lut.expectedBytes(size, Lut.bytesPerChannelOrOne(format)).toLong()
) {
    val isHald: Boolean get() = isHaldFormat(format)

    companion object {
        const val FORMAT_PNG144 = "png144"

        fun isHaldFormat(format: String): Boolean =
            format.lowercase().let { it == FORMAT_PNG144 || it == HaldClutDecoder.FORMAT_PNG_HALD }
    }
}

/**
 * Katalog LUT-ów z assetów; brak katalogu (np. przed dołączeniem LUT-ów) = pusta lista.
 *
 * Wpisy PNG (Hald) są dekodowane przez [HaldClutDecoder]; z [cache] rozpakowana siatka trafia
 * do wspólnego [LutRawCache] (LRU, te same klucze co pakiety pobierane – identyczne bajty).
 */
class LutCatalog(private val assets: AssetManager, private val cache: LutRawCache? = null) {
    val entries: List<LutEntry> by lazy { load() }

    fun open(entry: LutEntry): Lut = open(entry, cache) { assets.open(entry.file) }

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

        /** Wczytuje LUT wpisu z [source]; Hald PNG przez [HaldClutDecoder] i opcjonalnie [cache]. */
        fun open(entry: LutEntry, cache: LutRawCache?, source: () -> InputStream): Lut {
            if (!entry.isHald) return source().use { Lut.fromBinary(it.readBytes(), entry.size, entry.format) }
            val decode = { source().use { HaldClutDecoder.decode(it, entry.size) } }
            return cache?.get(cacheKey(entry.id), entry.size, entry.unpackedLength, decode) ?: decode()
        }

        /** Klucz w [LutRawCache] – ten sam co [PackEntry.localName] pakietu pobieranego. */
        fun cacheKey(id: String): String = id.replace('/', PackEntry.SEPARATOR)

        fun parse(json: String): List<LutEntry> {
            val root = JSONObject(json)
            val arr = root.getJSONArray("luts")
            val rootSize = root.optInt("grid", root.optInt("size", Lut.DEFAULT_SIZE))
            return List(arr.length()) { i ->
                val o = arr.getJSONObject(i)
                val format = o.optString("format", Lut.FORMAT_RGB8)
                val size = o.optInt("grid", o.optInt("size", rootSize))
                val expected = Lut.expectedBytes(size, Lut.bytesPerChannelOrOne(format)).toLong()
                val unpacked = o.optLong("unpackedLength", expected)
                require(unpacked == expected) { "${o.optString("id")}: unpackedLength $unpacked ≠ $expected" }
                LutEntry(
                    id = o.getString("id"),
                    name = o.getString("name"),
                    category = o.optString("category"),
                    file = o.getString("file"),
                    size = size,
                    format = format,
                    unpackedLength = unpacked
                )
            }
        }
    }
}
