package app.retrofilm.backup

import android.content.Context
import android.net.Uri
import app.retrofilm.settings.AppSettingsRepository
import app.retrofilm.ui.PresetCodec
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Zapis i odczyt kopii przez SAF (Uri od CreateDocument/OpenDocument). Offline: tylko lokalne pliki.
 * Presety w `filesDir/presets.json` (ten sam plik co PresetBook – edytor wczyta je ponownie po powrocie).
 */
class BackupStore(private val context: Context, private val settings: AppSettingsRepository) {
    private val presetsFile get() = File(context.filesDir, PRESETS_FILE)

    private fun appVersion(): String = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }.getOrNull().orEmpty()

    suspend fun current(): Backup = withContext(Dispatchers.IO) {
        val presets = if (presetsFile.exists()) PresetCodec.decode(presetsFile.readText()) else emptyList()
        Backup(appVersion(), settings.snapshot(), presets)
    }

    /** Zapisuje kopię; `false` przy błędzie wejścia/wyjścia. */
    suspend fun export(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        val text = BackupCodec.encode(current())
        try {
            context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray()) } != null
        } catch (_: IOException) {
            false
        } catch (_: SecurityException) {
            false
        }
    }

    /** Czyta i waliduje plik; `null` przy błędzie odczytu. Limit rozmiaru chroni przed przypadkowym dużym plikiem. */
    suspend fun read(uri: Uri): BackupResult? = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val bytes = input.readNBytesCompat(MAX_BYTES + 1)
                if (bytes.size >
                    MAX_BYTES
                ) {
                    BackupResult.Invalid(BackupError.Corrupt)
                } else {
                    BackupCodec.decode(String(bytes))
                }
            }
        } catch (_: IOException) {
            null
        } catch (_: SecurityException) {
            null
        }
    }

    suspend fun restore(incoming: Backup, mode: RestoreMode) {
        val merged = BackupMerge.apply(current(), incoming, mode)
        settings.restore(merged.prefs, replace = mode == RestoreMode.Replace)
        withContext(Dispatchers.IO) { presetsFile.writeText(PresetCodec.encode(merged.presets)) }
    }

    private fun java.io.InputStream.readNBytesCompat(limit: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(BUFFER)
        while (out.size() < limit) {
            val n = read(buf, 0, minOf(buf.size, limit - out.size()))
            if (n < 0) break
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }

    private companion object {
        const val PRESETS_FILE = "presets.json"
        const val MAX_BYTES = 4 * 1024 * 1024
        const val BUFFER = 8192
    }
}
