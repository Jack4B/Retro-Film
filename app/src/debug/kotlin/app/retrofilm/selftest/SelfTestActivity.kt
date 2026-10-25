package app.retrofilm.selftest

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import app.retrofilm.core.engine.BackendComparison
import app.retrofilm.core.engine.Lut
import app.retrofilm.core.engine.LutCatalog
import app.retrofilm.core.engine.LutEngineFactory.Backend
import app.retrofilm.core.ui.RetroFilmTheme
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Tylko debug: samotest „GPU vs CPU” na urządzeniu – to samo porównanie co `GpuVsCpuTest`.
 * Każdy backend wymuszany jawnie (GLES także na API 33+). Wynik: diff.json w Pobranych + udostępnianie.
 */
class SelfTestActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { RetroFilmTheme { Surface(Modifier.fillMaxSize()) { SelfTestScreen() } } }
    }
}

private enum class Choice(val label: String) { ALL("Wszystkie"), AGSL("AGSL"), GLES("GLES"), CPU("CPU") }

@Suppress("LongMethod")
@Composable
private fun SelfTestScreen() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var choice by remember { mutableStateOf(Choice.ALL) }
    var running by remember { mutableStateOf(false) }
    var report by remember { mutableStateOf<BackendComparison.Report?>(null) }
    var saved by remember { mutableStateOf<String?>(null) }
    var shareFile by remember { mutableStateOf<File?>(null) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Test GPU vs CPU", style = MaterialTheme.typography.headlineSmall)
        Text("Urządzenie: ${Build.MANUFACTURER} ${Build.MODEL}")
        Text("Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        report?.let { Text("GPU: ${it.gpu}") }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Choice.entries.forEach { c ->
                val enabled = c != Choice.AGSL || Build.VERSION.SDK_INT >= AGSL_API
                FilterChip(
                    selected = choice == c,
                    onClick = { choice = c },
                    label = { Text(c.label) },
                    enabled = enabled && !running
                )
            }
        }
        Button(
            onClick = {
                running = true
                saved = null
                scope.launch {
                    val r = withContext(Dispatchers.Default) {
                        BackendComparison.run(backendsFor(choice), selfTestLuts(context), withTiming = true)
                    }
                    report = r
                    val json = r.toJson().toString(2)
                    shareFile = withContext(Dispatchers.IO) { writeCache(context, json) }
                    saved =
                        withContext(Dispatchers.IO) {
                            runCatching { saveToDownloads(context, json) }.getOrElse { "błąd zapisu: ${it.message}" }
                        }
                    running = false
                }
            },
            enabled = !running,
            modifier = Modifier.fillMaxWidth()
        ) { Text("Testuj GPU vs CPU") }
        if (running) CircularProgressIndicator()
        report?.let { r ->
            Verdict("WYNIK", r.pass)
            Text("Tolerancja: ${BackendComparison.TOLERANCE}/255")
            r.backends.forEach { b -> BackendCard(b) }
        }
        saved?.let { Text("Zapisano: $it") }
        shareFile?.let { f ->
            OutlinedButton(onClick = {
                share(context, f)
            }, modifier = Modifier.fillMaxWidth()) { Text("Udostępnij diff.json") }
        }
    }
}

private val PassColor = Color(0xFF1B7F3A)
private val FailColor = Color(0xFFC62828)

@Composable
private fun Verdict(label: String, pass: Boolean) {
    Text(
        "$label: ${if (pass) "PASS" else "FAIL"}",
        color = if (pass) PassColor else FailColor,
        fontWeight = FontWeight.Bold,
        style = MaterialTheme.typography.titleLarge
    )
}

@Composable
private fun BackendCard(b: BackendComparison.BackendResult) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Backend: ${b.backend}", fontWeight = FontWeight.Bold)
            Verdict(b.backend.uppercase(), b.pass)
            if (b.error != null) {
                Text("Błąd: ${b.error}")
            } else {
                Text("Maks. różnica: ${b.maxDiff}/255")
                b.luts.forEach { Text("• ${it.lut}: ${it.maxAll}/255") }
                b.timing?.let { t ->
                    Text("Render 12 MP: ${t.render12MpMs} ms")
                    Text("Wgranie LUT: " + t.uploadMs.entries.joinToString { (n, ms) -> "$n³ $ms ms" })
                }
            }
        }
    }
}

private const val AGSL_API = 33
private const val SELF_TEST_LUTS = 3

private fun backendsFor(c: Choice): List<Backend> = when (c) {
    Choice.ALL -> BackendComparison.available()
    Choice.AGSL -> listOf(Backend.AGSL)
    Choice.GLES -> listOf(Backend.GLES)
    Choice.CPU -> listOf(Backend.CPU)
}

/** Kilka LUT-ów z katalogu (jeśli dołączony), w tym Superia 200 XPRO; inaczej syntetyczne. */
private fun selfTestLuts(context: Context): List<Pair<String, Lut>> {
    val catalog = LutCatalog(context.assets)
    val entries = catalog.entries
    if (entries.isEmpty()) return BackendComparison.syntheticLuts()
    val picks = (entries.filter { "superia_200_xpro" in it.id } + entries).distinct().take(SELF_TEST_LUTS)
    return picks.map { it.id to catalog.open(it) }
}

private fun writeCache(context: Context, json: String): File =
    File(context.cacheDir, "selftest").apply { mkdirs() }.resolve("diff.json").apply { writeText(json) }

private fun saveToDownloads(context: Context, json: String): String {
    val name = "retrofilm-diff-${System.currentTimeMillis()}.json"
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, name)
            put(MediaStore.Downloads.MIME_TYPE, "application/json")
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
        }
        val uri: Uri = checkNotNull(context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values))
        context.contentResolver.openOutputStream(uri).use { checkNotNull(it).write(json.toByteArray()) }
        return "Pobrane/$name"
    }
    // API < 29: bez uprawnień zapisu – katalog aplikacji na pamięci zewnętrznej.
    val dir = checkNotNull(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS))
    return File(dir, name).apply { writeText(json) }.absolutePath
}

private fun share(context: Context, file: File) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.selftest.files", file)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "application/json"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, "Retro-Film: test GPU vs CPU (${Build.MODEL})")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(send, "Udostępnij diff.json"))
}
