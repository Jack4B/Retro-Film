package app.retrofilm.feature.settings

import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import app.retrofilm.core.ui.RetroFilmTheme
import app.retrofilm.core.ui.lut.FailReason
import app.retrofilm.core.ui.lut.LutPack
import app.retrofilm.core.ui.lut.PackState
import app.retrofilm.feature.settings.lut.LutDownloadScreen
import app.retrofilm.feature.settings.lut.LutDownloadUiState
import app.retrofilm.feature.settings.lut.formatBytes
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private const val KB = 1024L
private const val FREE = 3_400L * KB * KB

private val names = listOf(
    "Kolor 160",
    "Kolor 400 ciepły",
    "Slajd 50 nasycony",
    "Slajd 100 neutralny",
    "Czarno-biały 400",
    "Czarno-biały 3200 ziarno"
)

/** 2 startowe + 6 do pobrania (skrót pełnej listy ~260). */
private fun packs(vararg states: PackState): List<LutPack> = listOf(
    LutPack("s1", "Start · Kolor 200", 300 * KB, bundled = true, state = PackState.Ready),
    LutPack("s2", "Start · Czarno-biały 100", 300 * KB, bundled = true, state = PackState.Ready)
) + names.mapIndexed { i, n -> LutPack("f$i", n, 820 * KB, bundled = false, state = states[i]) }

private val nd = PackState.NotDownloaded
private val ok = PackState.Ready

/** Rozpakowany cache jednego filmu N³ × RGB8, np. 256³ ≈ 50 MB. */
private fun cacheOf(n: Long): Long = n * n * n * 3

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w390dp-h844dp-night-xxhdpi")
class LutDownloadScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private fun shot(name: String, state: LutDownloadUiState) {
        compose.setContent {
            RetroFilmTheme { LutDownloadScreen(state, {}, {}, {}, {}, {}, {}, {}) }
        }
        compose.onRoot().captureRoboImage("src/test/screenshots/lut-$name.png")
    }

    @Test fun only_starter() = shot("tylko-startowe", LutDownloadUiState(packs(nd, nd, nd, nd, nd, nd), FREE))

    @Test fun downloading_mixed() = shot(
        "pobieranie",
        LutDownloadUiState(
            packs(
                ok,
                PackState.Downloading(0.62f),
                PackState.Downloading(0.08f),
                nd,
                PackState.Failed(FailReason.NETWORK),
                nd
            ),
            FREE
        )
    )

    @Test fun all_ready() = shot("wszystkie-gotowe", LutDownloadUiState(packs(ok, ok, ok, ok, ok, ok), FREE))

    @Test fun checksum_error() = shot(
        "blad-sumy",
        LutDownloadUiState(
            packs(ok, PackState.Failed(FailReason.CHECKSUM), PackState.Failed(FailReason.STORAGE), nd, nd, nd),
            40L * KB * KB
        )
    )

    @Test fun waiting_for_wifi() = shot(
        "czeka-na-wifi",
        LutDownloadUiState(
            packs(PackState.Downloading(0.3f), PackState.Downloading(0f), nd, nd, nd, nd),
            FREE,
            wifiAvailable = false
        )
    )

    @Test fun offline() = shot("offline", LutDownloadUiState(packs(ok, nd, nd, nd, nd, nd), FREE, offline = true))

    private fun cacheState(dialog: Boolean): LutDownloadUiState {
        val p = packs(ok, ok, ok, nd, nd, nd)
        // Pobrane PNG: realne rozmiary per film; cache: jeden film 256³, dwa 144³.
        val cache = cacheOf(256) + cacheOf(144) * 2
        return LutDownloadUiState(p, FREE, cacheBytes = cache, showClearCacheDialog = dialog)
    }

    @Test fun cache() = shot("cache", cacheState(dialog = false))

    @Test fun clear_dialog() {
        compose.setContent {
            RetroFilmTheme { LutDownloadScreen(cacheState(dialog = true), {}, {}, {}, {}, {}, {}, {}) }
        }
        compose.onNode(isDialog()).captureRoboImage("src/test/screenshots/lut-wyczysc-dialog.png")
    }

    @Test fun cacheSizeIsPerFilm() {
        assertEquals(50_331_648L, cacheOf(256))
        assertEquals("48 MB", formatBytes(cacheOf(256))) // formatBytes liczy w MiB
    }

    @Test fun formatsPolish() {
        assertEquals("820 KB", formatBytes(820 * KB))
        assertEquals("215 MB", formatBytes(215 * KB * KB))
        assertEquals("1,5 GB", formatBytes(1536L * KB * KB))
    }
}
