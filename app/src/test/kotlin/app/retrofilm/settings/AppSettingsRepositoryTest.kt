package app.retrofilm.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Czysty JVM: zapis i odczyt flagi dynamicznych kolorów, także po ponownym otwarciu pliku. */
class AppSettingsRepositoryTest {
    private val dir: File = Files.createTempDirectory("settings").toFile()
    private val file = File(dir, "app_settings.preferences_pb")

    private fun <T> withRepo(block: suspend (AppSettingsRepository) -> T): T {
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        try {
            val store = PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })
            return runBlocking { block(AppSettingsRepository(store)) }
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun defaultIsOff() {
        assertFalse(withRepo { it.dynamicColor.first() })
    }

    @Test
    fun valueIsPersistedAcrossInstances() {
        withRepo { it.setDynamicColor(true) }
        Thread.sleep(SETTLE_MS)
        assertTrue(withRepo { it.dynamicColor.first() })
        withRepo { it.setDynamicColor(false) }
        Thread.sleep(SETTLE_MS)
        assertFalse(withRepo { it.dynamicColor.first() })
    }

    private companion object {
        const val SETTLE_MS = 100L
    }
}
