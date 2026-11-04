package app.retrofilm.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import app.retrofilm.core.ui.ThemeMode
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

    @Test
    fun themeModeDefaultsToDarkAndIsPersisted() {
        assertEquals(ThemeMode.Dark, withRepo { it.themeMode.first() })
        withRepo { it.setThemeMode(ThemeMode.Light) }
        assertEquals(ThemeMode.Light, withRepo { it.themeMode.first() })
        withRepo { it.setThemeMode(ThemeMode.System) }
        assertEquals(ThemeMode.System, withRepo { it.themeMode.first() })
    }

    @Test
    fun defaultHdrIsOffAndIndependentOfSaveSheetDefaults() {
        assertFalse(withRepo { it.defaultHdrFromPhoto.first() })
        withRepo { it.setDefaultHdrFromPhoto(true) }
        assertTrue(withRepo { it.defaultHdrFromPhoto.first() })
        // „Zapamiętaj jako domyślne” z arkusza zapisu i „Przywróć fabryczne” nie ruszają ustawienia z Ustawień
        withRepo { it.setEditDefaults(EditDefaults(hdrFromPhoto = false)) }
        withRepo { it.clearEditDefaults() }
        assertTrue(withRepo { it.defaultHdrFromPhoto.first() })
        assertTrue(withRepo { it.editDefaults.first().hdrFromPhoto })
    }

    @Test
    fun perfLogIsOffByDefaultAndPersisted() {
        assertFalse(withRepo { it.perfLogEnabled.first() })
        withRepo { it.setPerfLogEnabled(true) }
        assertTrue(withRepo { it.perfLogEnabled.first() })
    }

    @Test
    fun snapshotAndRestoreRoundTrip() {
        withRepo {
            it.setDynamicColor(true)
            it.setThemeMode(ThemeMode.Light)
            it.setFavorite("bw/kodak_tri_x_400", true)
        }
        val snap = withRepo { it.snapshot() }
        assertEquals(true, snap["dynamic_color"])
        assertEquals(setOf("bw/kodak_tri_x_400"), snap["favorite_films"])
        withRepo { it.restore(mapOf("theme_mode" to "Dark", "unknown_key" to 5), replace = true) }
        assertFalse(withRepo { it.dynamicColor.first() })
        assertEquals(ThemeMode.Dark, withRepo { it.themeMode.first() })
        withRepo { it.restore(snap, replace = false) }
        assertTrue(withRepo { it.dynamicColor.first() })
        assertEquals(ThemeMode.Light, withRepo { it.themeMode.first() })
    }
}
