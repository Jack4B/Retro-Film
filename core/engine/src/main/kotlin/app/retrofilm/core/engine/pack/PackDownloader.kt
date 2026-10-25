package app.retrofilm.core.engine.pack

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

enum class DownloadFailure { NETWORK, CHECKSUM, STORAGE }

sealed interface DownloadResult {
    data class Ok(val file: File) : DownloadResult

    data class Failed(val reason: DownloadFailure) : DownloadResult
}

/** Polityka adresów: tylko HTTPS i jeden host (także dla każdego przekierowania). */
class UrlPolicy(private val allowedHosts: Set<String>, private val httpsOnly: Boolean = true) {
    fun isAllowed(url: URL): Boolean = (!httpsOnly || url.protocol == "https") && url.host.lowercase() in allowedHosts

    companion object {
        val PRODUCTION = UrlPolicy(setOf(PackCatalog.HOST))
    }
}

/**
 * Pobieranie pliku do `<dest>.part` z wznawianiem (HTTP Range), weryfikacja SHA-256 **przed**
 * przyjęciem i atomowa zmiana nazwy. Brak telemetrii: tylko stały User-Agent, żadnych ID urządzenia.
 */
class PackDownloader(
    private val policy: UrlPolicy = UrlPolicy.PRODUCTION,
    private val freeSpace: (File) -> Long = { it.usableSpace },
    private val timeoutMs: Int = TIMEOUT_MS
) {
    @Suppress("ReturnCount", "LongParameterList")
    fun download(
        url: String,
        dest: File,
        expectedSha256: String,
        expectedBytes: Long,
        isCancelled: () -> Boolean = { false },
        onProgress: (Float) -> Unit = {}
    ): DownloadResult {
        val dir = dest.parentFile ?: return DownloadResult.Failed(DownloadFailure.STORAGE)
        if (!dir.isDirectory && !dir.mkdirs()) return DownloadResult.Failed(DownloadFailure.STORAGE)
        val part = File(dir, dest.name + PART_SUFFIX)
        if (part.length() > expectedBytes) part.delete()
        if (freeSpace(dir) < expectedBytes - part.length() + SPACE_MARGIN) {
            return DownloadResult.Failed(DownloadFailure.STORAGE)
        }
        if (part.length() < expectedBytes) {
            val failure = fetch(URL(url), part, expectedBytes, isCancelled, onProgress)
            if (failure != null) return DownloadResult.Failed(failure)
        }
        if (part.length() != expectedBytes || !sha256(part).equals(expectedSha256, ignoreCase = true)) {
            part.delete()
            return DownloadResult.Failed(DownloadFailure.CHECKSUM)
        }
        if (!part.renameTo(dest)) {
            part.delete()
            return DownloadResult.Failed(DownloadFailure.STORAGE)
        }
        onProgress(1f)
        return DownloadResult.Ok(dest)
    }

    @Suppress("ReturnCount", "CyclomaticComplexMethod", "LongMethod")
    private fun fetch(
        start: URL,
        part: File,
        total: Long,
        isCancelled: () -> Boolean,
        onProgress: (Float) -> Unit
    ): DownloadFailure? {
        var url = start
        repeat(MAX_REDIRECTS) {
            if (!policy.isAllowed(url)) return DownloadFailure.NETWORK
            val offset = part.length()
            val conn = url.openConnection() as HttpURLConnection
            try {
                conn.instanceFollowRedirects = false
                conn.connectTimeout = timeoutMs
                conn.readTimeout = timeoutMs
                conn.useCaches = false
                conn.setRequestProperty("User-Agent", USER_AGENT)
                conn.setRequestProperty("Accept-Encoding", "identity")
                if (offset > 0) conn.setRequestProperty("Range", "bytes=$offset-")
                val code = conn.responseCode
                when {
                    code in REDIRECT_CODES -> {
                        val location = conn.getHeaderField("Location") ?: return DownloadFailure.NETWORK
                        url = URL(url, location)
                        return@repeat
                    }

                    code == HttpURLConnection.HTTP_PARTIAL && offset > 0 -> {
                        if (contentRangeStart(conn.getHeaderField("Content-Range")) != offset) {
                            // Serwer zwrócił inny fragment niż prosiliśmy – zaczynamy od zera.
                            part.delete()
                            return@repeat
                        }
                    }

                    code == HttpURLConnection.HTTP_PARTIAL -> {
                        part.delete()
                        return DownloadFailure.NETWORK
                    }

                    code == HttpURLConnection.HTTP_OK -> if (offset > 0) part.delete()

                    code == HTTP_RANGE_NOT_SATISFIABLE -> {
                        part.delete()
                        return DownloadFailure.NETWORK
                    }

                    else -> return DownloadFailure.NETWORK
                }
                return copy(conn, part, total, isCancelled, onProgress)
            } catch (_: IOException) {
                return DownloadFailure.NETWORK
            } finally {
                conn.disconnect()
            }
        }
        return DownloadFailure.NETWORK
    }

    @Suppress("ReturnCount", "NestedBlockDepth")
    private fun copy(
        conn: HttpURLConnection,
        part: File,
        total: Long,
        isCancelled: () -> Boolean,
        onProgress: (Float) -> Unit
    ): DownloadFailure? {
        val buf = ByteArray(BUFFER)
        conn.inputStream.use { input ->
            FileOutputStream(part, true).use { out ->
                var written = part.length()
                while (true) {
                    if (isCancelled()) return DownloadFailure.NETWORK
                    val n = input.read(buf)
                    if (n < 0) break
                    if (written + n > total) return DownloadFailure.CHECKSUM.also { part.delete() }
                    out.write(buf, 0, n)
                    written += n
                    onProgress(written.toFloat() / total)
                }
                out.fd.sync()
            }
        }
        return null
    }

    companion object {
        const val PART_SUFFIX = ".part"
        const val USER_AGENT = "RetroFilm"
        private const val TIMEOUT_MS = 30_000
        private const val MAX_REDIRECTS = 5
        private const val BUFFER = 64 * 1024
        private const val SPACE_MARGIN = 16L * 1024 * 1024
        private const val HTTP_RANGE_NOT_SATISFIABLE = 416
        private val REDIRECT_CODES = setOf(301, 302, 303, 307, 308)

        /** Początek zakresu z nagłówka `Content-Range: bytes X-Y/Z`; `null` gdy brak/niepoprawny. */
        fun contentRangeStart(header: String?): Long? =
            header?.trim()?.removePrefix("bytes ")?.substringBefore('-')?.trim()?.toLongOrNull()

        fun sha256(file: File): String {
            val md = MessageDigest.getInstance("SHA-256")
            FileInputStream(file).use { input ->
                val buf = ByteArray(BUFFER)
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    md.update(buf, 0, n)
                }
            }
            return md.digest().joinToString("") { "%02x".format(it) }
        }
    }
}
