package app.retrofilm.core.engine

/**
 * Ograniczony (bajtowo) cache LRU zdekodowanych [Lut]-ów w pamięci RAM.
 *
 * Film 144³ RGB8 to ~9 MB; domyślny limit [DEFAULT_MAX_BYTES] mieści 3 takie LUT-y (GPU LRU ma 2,
 * więc aktywny film + poprzedni + jeden zapas). Przy przekroczeniu limitu usuwany jest najdawniej
 * używany wpis; ostatnio wstawiony zostaje zawsze (nawet gdy sam przekracza limit, np. Ektar 256³).
 * Trwała kopia rozpakowanych siatek jest w `LutRawCache` (dysk, 180 MB) – tu tylko gorące wpisy.
 */
class LutMemoryCache(private val maxBytes: Long = DEFAULT_MAX_BYTES) {
    private val map = LinkedHashMap<String, Lut>(INITIAL_CAPACITY, LOAD_FACTOR, true)
    private var bytes = 0L

    @Synchronized
    fun get(id: String): Lut? = map[id]

    private val loading = HashMap<String, Any>()

    /**
     * Zwraca LUT z cache albo ładuje go przez [load] i wstawia; `null` z [load] nie jest cache'owane.
     * Ładowanie odbywa się poza główną blokadą, ale z blokadą na identyfikator: równoległe wywołania
     * dla tego samego [id] dekodują LUT raz (kolejne czekają i biorą wynik z cache), a różne id nie
     * blokują się nawzajem.
     */
    fun getOrPut(id: String, load: () -> Lut?): Lut? {
        get(id)?.let { return it }
        val lock = synchronized(this) { loading.getOrPut(id) { Any() } }
        return synchronized(lock) {
            get(id) ?: load()?.let { put(id, it) }
        }.also { synchronized(this) { loading.remove(id) } }
    }

    @Synchronized
    fun put(id: String, lut: Lut): Lut {
        map[id]?.let { return it }
        map[id] = lut
        bytes += lut.data.size
        trim(keep = id)
        return lut
    }

    @Synchronized
    fun contains(id: String): Boolean = map.containsKey(id)

    @Synchronized
    fun keys(): List<String> = map.keys.toList()

    @Synchronized
    fun sizeBytes(): Long = bytes

    @Synchronized
    fun clear() {
        map.clear()
        bytes = 0
    }

    private fun trim(keep: String) {
        val it = map.entries.iterator()
        while (bytes > maxBytes && it.hasNext()) {
            val e = it.next()
            if (e.key == keep) continue
            bytes -= e.value.data.size
            it.remove()
        }
    }

    companion object {
        private const val GRID_144 = 144L
        private const val CHANNELS = 3L
        private const val DECODED_LUTS = 3L
        private const val INITIAL_CAPACITY = 8
        private const val LOAD_FACTOR = 0.75f

        /** 3 × 144³ × 3 B ≈ 25,6 MiB. */
        const val DEFAULT_MAX_BYTES = DECODED_LUTS * GRID_144 * GRID_144 * GRID_144 * CHANNELS
    }
}
