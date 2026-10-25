package app.retrofilm.core.engine.pack

import androidx.work.NetworkType
import org.junit.Assert.assertEquals
import org.junit.Test

class LutDownloadWorkerRequestTest {
    @Test
    fun wifiOnlySetsUnmeteredConstraint() {
        val r = LutDownloadWorker.request("a", onlyWifi = true)
        assertEquals(NetworkType.UNMETERED, r.workSpec.constraints.requiredNetworkType)
        assertEquals("a", r.workSpec.input.getString(LutDownloadWorker.KEY_ID))
    }

    @Test
    fun anyNetworkWhenWifiOnlyOff() {
        val r = LutDownloadWorker.request("a", onlyWifi = false)
        assertEquals(NetworkType.CONNECTED, r.workSpec.constraints.requiredNetworkType)
    }
}
