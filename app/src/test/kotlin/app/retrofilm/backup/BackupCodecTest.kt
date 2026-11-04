package app.retrofilm.backup

import app.retrofilm.pipeline.Adjustments
import app.retrofilm.ui.Preset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Kopia zapasowa: pełny obieg, odporność na uszkodzone / obce / nowsze pliki, scalanie i zastępowanie. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BackupCodecTest {
    private val presets = listOf(
        Preset("Ciepło", "negative_new/kodak_portra_400", 0.8f, Adjustments(exposure = 0.3f)),
        Preset("Mono", "bw/kodak_tri_x_400", 1f, Adjustments())
    )
    private val prefs: Map<String, Any> = mapOf(
        "dynamic_color" to true,
        "theme_mode" to "Light",
        "default_quality" to 92,
        "default_frame_margin" to 0.05f,
        "favorite_films" to setOf("bw/kodak_tri_x_400", "negative_new/kodak_portra_400"),
        "some_long" to 123456789012L
    )
    private val backup = Backup(appVersion = "1.2.3", prefs = prefs, presets = presets)

    @Test
    fun roundTripKeepsEverything() {
        val text = BackupCodec.encode(backup)
        assertTrue(text.contains("\"schemaVersion\":${BackupCodec.SCHEMA_VERSION}"))
        val back = (BackupCodec.decode(text) as BackupResult.Ok).backup
        assertEquals(prefs, back.prefs)
        assertEquals(presets, back.presets)
        assertEquals("1.2.3", back.appVersion)
        assertEquals(BackupSummary(settings = 6, presets = 2, favorites = 2), back.summary())
    }

    @Test
    fun corruptInputsFailGracefully() {
        assertEquals(BackupError.NotJson, err("{ nie json"))
        assertEquals(BackupError.NotJson, err(""))
        assertEquals(BackupError.NotRetroFilm, err("""{"foo":1}"""))
        assertEquals(BackupError.NewerSchema, err("""{"app":"RetroFilm","schemaVersion":99}"""))
        assertEquals(BackupError.Corrupt, err("""{"app":"RetroFilm","schemaVersion":0}"""))
        assertEquals(BackupError.Corrupt, err("""{"app":"RetroFilm","schemaVersion":1,"prefs":[1,2]}"""))
        val truncated = BackupCodec.encode(backup).dropLast(20)
        assertEquals(BackupError.NotJson, err(truncated))
    }

    @Test
    fun unknownPrefTypesAndBadPresetsAreSkipped() {
        val text = """{"app":"RetroFilm","schemaVersion":1,"appVersion":"x",
            "prefs":{"a":{"t":"b","v":true},"b":{"t":"zzz","v":1},"c":{"t":"i","v":"nie"}},
            "presets":[{"name":"Ok","film":"f","intensity":1},{"oops":true}]}"""
        val b = (BackupCodec.decode(text) as BackupResult.Ok).backup
        assertEquals(mapOf<String, Any>("a" to true), b.prefs)
        assertEquals(listOf("Ok"), b.presets.map { it.name })
    }

    @Test
    fun legacyPresetArrayIsAccepted() {
        val legacy = app.retrofilm.ui.PresetCodec.encode(presets)
        val b = (BackupCodec.decode(legacy) as BackupResult.Ok).backup
        assertEquals(presets, b.presets)
        assertEquals(emptyMap<String, Any>(), b.prefs)
    }

    @Test
    fun mergeUnionsFavoritesAndPresetsBackupWins() {
        val current = Backup(
            appVersion = "1",
            prefs = mapOf("dynamic_color" to false, "keep" to 1, "favorite_films" to setOf("x")),
            presets = listOf(Preset("ciepło", "other", 0.1f, Adjustments()), Preset("Moje", "f", 1f, Adjustments()))
        )
        val merged = BackupMerge.apply(current, backup, RestoreMode.Merge)
        assertEquals(true, merged.prefs["dynamic_color"])
        assertEquals(1, merged.prefs["keep"])
        assertEquals(setOf("x", "bw/kodak_tri_x_400", "negative_new/kodak_portra_400"), merged.prefs["favorite_films"])
        assertEquals(listOf("Moje", "Ciepło", "Mono"), merged.presets.map { it.name })
        assertEquals(0.8f, merged.presets.first { it.name == "Ciepło" }.intensity)
    }

    @Test
    fun replaceDropsCurrentData() {
        val current =
            Backup(
                appVersion = "1",
                prefs = mapOf("keep" to 1),
                presets = listOf(Preset("Moje", "f", 1f, Adjustments()))
            )
        val replaced = BackupMerge.apply(current, backup, RestoreMode.Replace)
        assertEquals(prefs, replaced.prefs)
        assertEquals(presets, replaced.presets)
    }

    private fun err(text: String) = (BackupCodec.decode(text) as BackupResult.Invalid).error
}
