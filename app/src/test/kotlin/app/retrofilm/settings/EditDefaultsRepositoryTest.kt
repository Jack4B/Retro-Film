package app.retrofilm.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import app.retrofilm.core.export.JpegExporter
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Domyślne ustawienia ramki, podpisu i jakości: zapis, odczyt po ponownym otwarciu, reset i granice. */
class EditDefaultsRepositoryTest {
    private val dir: File = Files.createTempDirectory("defaults").toFile()
    private val file = File(dir, "app_settings.preferences_pb")

    private fun <T> withRepo(block: suspend (AppSettingsRepository) -> T): T = runBlocking {
        val job = SupervisorJob()
        val scope = CoroutineScope(Dispatchers.IO + job)
        try {
            block(AppSettingsRepository(PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })))
        } finally {
            job.cancelAndJoin()
        }
    }

    private val custom = EditDefaults(
        quality = 85,
        maxSide = 2048,
        frameEnabled = true,
        frameAspect = "Square",
        frameMargin = 0.08f,
        signatureEnabled = true,
        signature = "Karesh",
        signatureFont = "MarckScript"
    )

    @Test fun nothingSavedMeansFactoryDefaults() {
        val d = withRepo { it.editDefaults.first() }
        assertTrue(d.isFactory)
        assertEquals(JpegExporter.DEFAULT_QUALITY, d.quality)
    }

    @Test fun savedDefaultsSurviveReopening() {
        withRepo { it.setEditDefaults(custom) }
        assertEquals(custom, withRepo { it.editDefaults.first() })
    }

    @Test fun clearingRestoresFactoryDefaults() {
        withRepo { it.setEditDefaults(custom) }
        withRepo { it.clearEditDefaults() }
        assertTrue(withRepo { it.editDefaults.first() }.isFactory)
    }

    @Test fun nullableFieldsCanBeClearedBackToFactory() {
        withRepo { it.setEditDefaults(custom) }
        withRepo { it.setEditDefaults(custom.copy(frameAspect = null, frameMargin = null, signatureFont = null)) }
        val d = withRepo { it.editDefaults.first() }
        assertEquals(null, d.frameAspect)
        assertEquals(null, d.frameMargin)
        assertEquals(null, d.signatureFont)
    }

    @Test fun outOfRangeValuesAreClamped() {
        withRepo { it.setEditDefaults(EditDefaults(quality = 5, maxSide = -3, frameMargin = Float.NaN)) }
        val d = withRepo { it.editDefaults.first() }
        assertEquals(EditDefaults.MIN_QUALITY, d.quality)
        assertEquals(0, d.maxSide)
        assertEquals(null, d.frameMargin)
    }
}
