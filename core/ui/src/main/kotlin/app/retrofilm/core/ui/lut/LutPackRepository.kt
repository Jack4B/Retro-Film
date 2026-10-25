package app.retrofilm.core.ui.lut

import androidx.compose.runtime.Immutable
import kotlinx.coroutines.flow.StateFlow

/**
 * Kontrakt pobierania LUT-ów (decyzja D8), uzgodniony z Forge.
 *
 * Implementacja (sieć, SHA-256, wznawianie, zapis) w `core:engine`; UI zależy tylko od tego pliku.
 * Pakiety `bundled` są w APK: działają offline, nie da się ich usunąć.
 */
interface LutPackRepository {
    val packs: StateFlow<List<LutPack>>

    fun download(id: String)

    fun cancel(id: String)

    fun delete(id: String)
}

@Immutable
data class LutPack(
    val id: String,
    val displayName: String,
    val sizeBytes: Long,
    val bundled: Boolean,
    val state: PackState
)

@Immutable
sealed interface PackState {
    data object NotDownloaded : PackState

    /** [progress] 0..1. */
    data class Downloading(val progress: Float) : PackState

    data object Ready : PackState

    data class Failed(val reason: FailReason) : PackState
}

enum class FailReason { NETWORK, CHECKSUM, STORAGE }

/** Ustawienie po stronie UI (poza kontraktem Forge): pobieranie tylko przez Wi‑Fi, domyślnie włączone. */
interface LutDownloadSettings {
    val onlyWifi: StateFlow<Boolean>

    fun setOnlyWifi(enabled: Boolean)
}
