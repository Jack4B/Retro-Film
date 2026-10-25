package app.retrofilm.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Czysty JVM: zapis i odczyt flagi dynamicznych kolorów, także po ponownym otwarciu pliku. */
class AppSettingsRepositoryTest {
    private val dir: File = Files.createTempDirectory("settings").toFile()
    private val file = File(dir, "app_settings.preferences_pb")

    /** Jedna instancja DataStore na blok; zamknięcie czeka na zakończenie zapisu (bez Thread.sleep). */
    private fun <T> withRepo(block: suspend (AppSettingsRepository) -> T): T = runBlocking {
        val job = SupervisorJob()
        val scope = CoroutineScope(Dispatchers.IO + job)
        try {
            val store = PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })
            block(AppSettingsRepository(store))
        } finally {
            job.cancelAndJoin()
        }
    }

    @Test
    fun defaultIsOff() {
        assertFalse(withRepo { it.dynamicColor.first() })
    }

    @Test
    fun valueIsPersistedAcrossInstances() {
        withRepo { it.setDynamicColor(true) }
        assertTrue(withRepo { it.dynamicColor.first() })
        withRepo { it.setDynamicColor(false) }
        assertFalse(withRepo { it.dynamicColor.first() })
    }

    @Test
    fun favoritesAreEmptyByDefaultAndPersisted() {
        assertEquals(emptySet<String>(), withRepo { it.favoriteFilms.first() })
        withRepo {
            it.setFavorite("bw/kodak_tri_x_400", true)
            it.setFavorite("negative_new/kodak_portra_400", true)
        }
        assertEquals(
            setOf("bw/kodak_tri_x_400", "negative_new/kodak_portra_400"),
            withRepo { it.favoriteFilms.first() }
        )
        withRepo { it.setFavorite("bw/kodak_tri_x_400", false) }
        assertEquals(setOf("negative_new/kodak_portra_400"), withRepo { it.favoriteFilms.first() })
    }
}
