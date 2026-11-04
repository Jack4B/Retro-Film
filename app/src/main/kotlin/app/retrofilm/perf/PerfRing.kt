package app.retrofilm.perf

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Jeden pomiar: czas ścienny, etap, ms, backend renderu, wymiary zdjęcia, HDR i model telefonu. */
data class PerfEntry(
    val timestamp: Long,
    val stage: String,
    val ms: Long,
    val backend: String,
    val width: Int,
    val height: Int,
    val hdr: Boolean,
    val device: String,
    /** Dodatkowe dane etapu, np. powód spadku backendu albo szczyt pamięci. */
    val note: String = ""
)

/** Lokalny bufor kołowy ostatnich [capacity] pomiarów (tylko w pamięci; eksport ręczny do pliku CSV). */
class PerfRing(val capacity: Int = DEFAULT_CAPACITY) {
    private val buffer = ArrayDeque<PerfEntry>(capacity)

    val size: Int get() = synchronized(buffer) { buffer.size }

    fun add(entry: PerfEntry) = synchronized(buffer) {
        if (buffer.size == capacity) buffer.removeFirst()
        buffer.addLast(entry)
    }

    fun entries(): List<PerfEntry> = synchronized(buffer) { buffer.toList() }

    fun clear() = synchronized(buffer) { buffer.clear() }

    fun toCsv(): String = buildString {
        appendLine(HEADER)
        entries().forEach { e ->
            appendLine(
                listOf(
                    TIME.format(Instant.ofEpochMilli(e.timestamp)),
                    e.stage,
                    e.ms.toString(),
                    e.backend,
                    e.width.toString(),
                    e.height.toString(),
                    if (e.hdr) "on" else "off",
                    e.device,
                    e.note
                ).joinToString(",") { csv(it) }
            )
        }
    }

    private fun csv(v: String) = if (v.any { it == ',' || it == '"' || it == '\n' }) {
        "\"" + v.replace("\"", "\"\"") + "\""
    } else {
        v
    }

    companion object {
        const val DEFAULT_CAPACITY = 200
        const val HEADER = "timestamp,stage,ms,backend,width,height,hdr,device,note"
        private val TIME = DateTimeFormatter.ISO_OFFSET_DATE_TIME.withZone(ZoneId.systemDefault())
    }
}
