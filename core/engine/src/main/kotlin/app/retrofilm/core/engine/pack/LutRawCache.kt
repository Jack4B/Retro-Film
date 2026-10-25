package app.retrofilm.core.engine.pack

import app.retrofilm.core.engine.Lut
import java.io.File

/**
 * Cache rozpakowanych LUT-ów (surowe RGB8 `size^3*3`) z limitem rozmiaru i eviction LRU.
 * Rozpakowanie dopiero przy pierwszym użyciu filmu; po rozpakowaniu walidacja długości.
 */
class LutRawCache(
    private val dir: File,
    private val maxBytes: Long = DEFAULT_MAX_BYTES,
    private val onChanged: (Long) -> Unit = {}
) {
    /** Zwraca LUT z cache albo rozpakowuje go przez [unpack] i zapisuje. */
    @Synchronized
    fun get(id: String, size: Int, unpack: () -> Lut): Lut {
        val expected = Lut.expectedBytes(size, 1)
        val file = fileFor(id)
        if (file.length() == expected.toLong()) {
            file.setLastModified(nextStamp())
            return Lut(size, file.readBytes())
        }
        file.delete()
        val lut = unpack()
        require(lut.size == size && lut.data.size == expected) {
            "Rozpakowany LUT $id ma ${lut.data.size} B, oczekiwano $expected"
        }
        dir.mkdirs()
        val tmp = File(dir, "$id.tmp")
        tmp.writeBytes(lut.data)
        if (!tmp.renameTo(file)) tmp.delete()
        file.setLastModified(nextStamp())
        evict(keep = file)
        return lut
    }

    @Synchronized
    fun remove(id: String) {
        fileFor(id).delete()
        onChanged(sizeBytes())
    }

    @Synchronized
    fun clear() {
        files().forEach { it.delete() }
        onChanged(0)
    }

    fun sizeBytes(): Long = files().sumOf { it.length() }

    fun contains(id: String): Boolean = fileFor(id).exists()

    private fun evict(keep: File) {
        var total = sizeBytes()
        for (f in files().filter { it != keep }.sortedBy { it.lastModified() }) {
            if (total <= maxBytes) break
            total -= f.length()
            f.delete()
        }
        onChanged(total)
    }

    private fun files(): List<File> = dir.listFiles { f -> f.name.endsWith(SUFFIX) }?.toList().orEmpty()

    private fun fileFor(id: String): File {
        require(SAFE_ID.matches(id)) { "Niebezpieczne id: $id" }
        return File(dir, id + SUFFIX)
    }

    // Monotoniczny znacznik: mtime ma rozdzielczość ~1 s, a LRU potrzebuje kolejności.
    private var stamp = 0L

    private fun nextStamp(): Long {
        val now = System.currentTimeMillis()
        stamp = maxOf(now, stamp + STAMP_STEP_MS)
        return stamp
    }

    companion object {
        /** ~20 filmów po 144^3*3 ≈ 8,96 MB. */
        const val DEFAULT_MAX_BYTES = 180L * 1024 * 1024
        private const val SUFFIX = ".raw"
        private const val STAMP_STEP_MS = 1000L
        private val SAFE_ID = Regex("[a-z0-9_-]+")
    }
}
