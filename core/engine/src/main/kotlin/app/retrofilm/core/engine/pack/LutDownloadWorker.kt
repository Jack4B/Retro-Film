package app.retrofilm.core.engine.pack

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import java.io.File
import java.util.concurrent.TimeUnit

/** Worker pobierający jeden pakiet. Przy błędzie sieci `retry()` – kolejka wraca po odzyskaniu sieci. */
class LutDownloadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    @Suppress("ReturnCount")
    override suspend fun doWork(): Result {
        val id = inputData.getString(KEY_ID) ?: return Result.failure()
        val entry = LutPackFiles.catalog(applicationContext).firstOrNull { it.id == id && it.downloadable }
            ?: return failure(DownloadFailure.CHECKSUM)
        val dest = LutPackFiles.pngFile(applicationContext, entry)
        var last = -1
        val result = PackDownloader().download(
            url = PackCatalog.urlFor(entry),
            dest = dest,
            expectedSha256 = entry.sha256,
            expectedBytes = entry.bytes,
            isCancelled = { isStopped }
        ) { p ->
            val pct = (p * PERCENT).toInt()
            if (pct != last) {
                last = pct
                setProgressAsync(workDataOf(KEY_PROGRESS to p))
            }
        }
        return when (result) {
            is DownloadResult.Ok -> Result.success()

            is DownloadResult.Failed -> when {
                result.reason == DownloadFailure.NETWORK && runAttemptCount < MAX_ATTEMPTS -> Result.retry()
                else -> failure(result.reason)
            }
        }
    }

    private fun failure(reason: DownloadFailure) = Result.failure(workDataOf(KEY_FAILURE to reason.name))

    companion object {
        const val KEY_ID = "id"
        const val KEY_PROGRESS = "progress"
        const val KEY_FAILURE = "failure"
        const val TAG = "lut-download"
        const val ID_TAG_PREFIX = "lut-id:"
        private const val PERCENT = 100
        private const val MAX_ATTEMPTS = 10
        private const val BACKOFF_SECONDS = 30L

        fun uniqueName(id: String) = "lut-download-$id"

        /** Wi‑Fi only ⇒ `UNMETERED`, inaczej dowolna sieć. */
        fun request(id: String, onlyWifi: Boolean): OneTimeWorkRequest = OneTimeWorkRequestBuilder<LutDownloadWorker>()
            .setInputData(workDataOf(KEY_ID to id))
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(if (onlyWifi) NetworkType.UNMETERED else NetworkType.CONNECTED)
                    .setRequiresStorageNotLow(true)
                    .build()
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
            .addTag(TAG)
            .addTag(ID_TAG_PREFIX + id)
            .build()

        fun enqueue(context: Context, id: String, onlyWifi: Boolean) {
            WorkManager.getInstance(context)
                .enqueueUniqueWork(uniqueName(id), ExistingWorkPolicy.KEEP, request(id, onlyWifi))
        }
    }
}

/** Ścieżki plików pakietów (pamięć wewnętrzna aplikacji, bez uprawnień do storage). */
object LutPackFiles {
    @Volatile private var cached: List<PackEntry>? = null

    fun catalog(context: Context): List<PackEntry> = cached ?: runCatching {
        context.assets.open(PackCatalog.ASSET).bufferedReader().use { PackCatalog.parse(it.readText()) }
    }.getOrDefault(emptyList()).also { cached = it }

    fun pngDir(context: Context): File = File(context.filesDir, "luts/png")

    fun cacheDir(context: Context): File = File(context.noBackupFilesDir, "luts/raw")

    fun pngFile(context: Context, entry: PackEntry): File = File(pngDir(context), "${entry.id}.png")
}
