package app.retrofilm.core.engine

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Golden GPU vs CPU: zapisuje diff.json w filesDir aplikacji testowej.
 * Wybór backendu argumentem instrumentacji `backend` = `agsl` | `gles` | `all` (domyślnie),
 * np. `./gradlew :core:engine:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.backend=gles`.
 * Backend wymuszany jawnie (GLES także na API 33+), bez awaryjnego CPU.
 */
@RunWith(AndroidJUnit4::class)
class GpuVsCpuTest {
    @Test
    fun gpuMatchesCpu() = runBlocking {
        val arg = InstrumentationRegistry.getArguments().getString("backend", "all").lowercase()
        val backends = BackendComparison.available().filter {
            it != LutEngineFactory.Backend.CPU && (arg == "all" || it.name.lowercase() == arg)
        }
        assertTrue("Backend '$arg' niedostępny na tym urządzeniu", backends.isNotEmpty())
        val report = BackendComparison.run(backends, BackendComparison.syntheticLuts())
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        File(ctx.filesDir, "diff.json").writeText(report.toJson().toString(2))
        val failures = report.backends.filterNot {
            it.pass
        }.map { "${it.backend}: max=${it.maxDiff} ${it.error ?: ""}" }
        assertTrue("Przekroczona tolerancja ${BackendComparison.TOLERANCE}/255: $failures", failures.isEmpty())
    }
}
