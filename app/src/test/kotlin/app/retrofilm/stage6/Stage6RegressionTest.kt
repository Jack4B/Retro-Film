package app.retrofilm.stage6

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import app.retrofilm.backup.BackupCodec
import app.retrofilm.backup.BackupResult
import app.retrofilm.backup.BackupStore
import app.retrofilm.backup.RestoreMode
import app.retrofilm.perf.CrashLog
import app.retrofilm.perf.PerfLog
import app.retrofilm.settings.AppSettingsRepository
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * J (etap 6): jawne testy regresji – wiersze crash handlera / save_error w logu, eksport logów CSV,
 * kopia zapasowa → przywrócenie. (Chip HDR: RedesignScreenshotTest.hdrChipToggles.)
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class Stage6RegressionTest {
    private val context = ApplicationProvider.getApplicationContext<android.app.Application>()

    @Test fun crashFromPreviousRunAndSaveErrorReachTheExportedLog() {
        PerfLog.ring.clear()
        File(context.filesDir, "last-crash.txt").writeText("thread=main java.lang.OutOfMemoryError: test")
        CrashLog.install(context)
        CrashLog.record("save", IllegalStateException("boom"))
        val names = PerfLog.ring.entries().map { it.stage }
        assertTrue(names.toString(), "last_crash" in names)
        assertTrue(names.toString(), "save_error" in names)
        val csv = PerfLog.ring.toCsv()
        assertTrue(csv, csv.contains("save_error") && csv.contains("where=save") && csv.contains("boom"))
        assertTrue(File(context.filesDir, "last-crash.txt").readText().contains("boom"))
    }

    @Test fun backupExportThenRestoreReplaceBringsSettingsBack() = runBlocking {
        val job = SupervisorJob()
        val scope = CoroutineScope(Dispatchers.IO + job)
        val file = File(context.filesDir, "stage6.preferences_pb").also { it.delete() }
        try {
            val settings = AppSettingsRepository(PreferenceDataStoreFactory.create(scope = scope) { file })
            val store = BackupStore(context, settings)
            settings.setDynamicColor(true)
            settings.setFavorite("portra", true)
            val text = BackupCodec.encode(store.current())
            settings.setDynamicColor(false)
            settings.setFavorite("portra", false)
            val decoded = (BackupCodec.decode(text) as BackupResult.Ok).backup
            store.restore(decoded, RestoreMode.Replace)
            assertEquals(true, settings.dynamicColor.first())
            assertEquals(store.current().prefs, decoded.prefs)
        } finally {
            job.cancelAndJoin()
        }
    }
}
