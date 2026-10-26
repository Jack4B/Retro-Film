package app.retrofilm.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.retrofilm.R
import app.retrofilm.ui.library.LibraryModel

private val MinTouch = 48.dp
private val CtaHeight = 56.dp
private val ScreenPad = 24.dp
private val BlockGap = 16.dp

/** Start: obietnica i dwa wejścia: galeria (Photo Picker, otwiera się sama) oraz pliki z dowolnego folderu. */
@OptIn(ExperimentalMaterial3Api::class)
@Suppress("LongParameterList") // zdarzenia ekranu przekazywane jawnie
@Composable
fun StartScreen(
    state: MainState,
    onPick: () -> Unit,
    onPickFiles: () -> Unit,
    onSettings: () -> Unit,
    onDownloadFilms: () -> Unit,
    snackbar: @Composable () -> Unit
) {
    val packs by state.library.packs.collectAsState()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_title)) },
                actions = {
                    IconButton(onClick = onSettings, modifier = Modifier.size(MinTouch)) {
                        Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.cd_open_settings))
                    }
                }
            )
        },
        snackbarHost = snackbar
    ) { inner ->
        Column(
            Modifier.fillMaxSize().padding(inner).padding(horizontal = ScreenPad),
            verticalArrangement = Arrangement.spacedBy(BlockGap, Alignment.CenterVertically)
        ) {
            Text(
                stringResource(R.string.start_headline),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.semantics { heading() }
            )
            Text(
                stringResource(R.string.start_promise),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(onClick = onPick, enabled = !state.busy, modifier = Modifier.fillMaxWidth().height(CtaHeight)) {
                Text(stringResource(R.string.start_pick), fontWeight = FontWeight.SemiBold)
            }
            OutlinedButton(
                onClick = onPickFiles,
                enabled = !state.busy,
                modifier = Modifier.fillMaxWidth().height(CtaHeight)
            ) { Text(stringResource(R.string.start_pick_files)) }
            if (LibraryModel.hasMoreToDownload(packs)) {
                FilledTonalButton(
                    onClick = onDownloadFilms,
                    modifier = Modifier.fillMaxWidth().height(CtaHeight)
                ) { Text(stringResource(R.string.start_download_films)) }
            }
            Text(
                stringResource(R.string.start_multi_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (state.busy) {
                CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
            }
        }
    }
}
