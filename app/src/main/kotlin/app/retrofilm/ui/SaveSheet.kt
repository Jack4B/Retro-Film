package app.retrofilm.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.retrofilm.R
import app.retrofilm.core.frame.SignatureFitStatus
import app.retrofilm.pipeline.SaveProgress
import kotlinx.coroutines.delay

private val Pad = 20.dp
private val Gap = 12.dp
private val MinTouch = 48.dp

/** Zdarzenia arkusza zapisu (przekazywane z ekranu, który zna kontekst i launcher-y). */
internal class SaveActions(
    val onSave: () -> Unit,
    val onRetry: () -> Unit,
    val onShare: () -> Unit,
    val onSaveDefaults: () -> Unit,
    val onResetDefaults: () -> Unit,
    val onDismiss: () -> Unit
)

/**
 * Arkusz „Zapisz”: rozmiar i jakość JPEG, postęp zapisu serii, wynik („Zapisano / Nie udało się”), „Ponów nieudane”,
 * „Udostępnij” zapisane pliki i „Zapamiętaj jako domyślne” (jakość, rozmiar, ramka, podpis dla nowych zdjęć).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SaveSheet(state: MainState, result: UiMessage?, actions: SaveActions) {
    ModalBottomSheet(
        onDismissRequest = { if (!state.busy) actions.onDismiss() },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        SaveSheetContent(state, result, actions)
    }
}

@Composable
internal fun SaveSheetContent(state: MainState, result: UiMessage?, actions: SaveActions) {
    val total = state.uris.size.coerceAtLeast(1)
    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = Pad)
            .navigationBarsPadding().padding(bottom = Pad),
        verticalArrangement = Arrangement.spacedBy(Gap)
    ) {
        Text(
            if (total >
                1
            ) {
                stringResource(R.string.save_sheet_title_many, total)
            } else {
                stringResource(R.string.save_sheet_title)
            },
            style = MaterialTheme.typography.titleLarge
        )
        Text(
            stringResource(R.string.save_sheet_where),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        ExportSettings(state)
        DefaultsRow(state, actions)
        HorizontalDivider()
        Progress(state, result)
        ResultActions(state, result, actions)
        val enabled = !state.busy && state.signatureStatus() != SignatureFitStatus.TooLong
        Button(
            onClick = actions.onSave,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth().heightIn(min = MinTouch)
        ) {
            Text(
                if (total >
                    1
                ) {
                    stringResource(R.string.save_sheet_go_many, total)
                } else {
                    stringResource(R.string.save_sheet_go)
                }
            )
        }
    }
}

@Composable
private fun DefaultsRow(state: MainState, actions: SaveActions) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = actions.onSaveDefaults, modifier = Modifier.heightIn(min = MinTouch)) {
                Text(stringResource(R.string.save_sheet_as_default))
            }
            if (!state.defaults.isFactory) {
                TextButton(onClick = actions.onResetDefaults, modifier = Modifier.heightIn(min = MinTouch)) {
                    Text(stringResource(R.string.save_sheet_reset_defaults))
                }
            }
        }
        Text(
            stringResource(R.string.save_sheet_as_default_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun Progress(state: MainState, result: UiMessage?) {
    val total = state.saveTotal
    val live = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
    // I: postęp w obrębie jednego zdjęcia (kafle potoku), odczytywany co 100 ms – pasek określony także dla 1 zdjęcia
    val within by produceState(0f, state.busy) {
        while (state.busy) {
            value = SaveProgress.value
            delay(PROGRESS_POLL_MS)
        }
        value = 0f
    }
    when {
        state.busy && state.exportProgress > 0 && total > 0 -> {
            Text(stringResource(R.string.save_sheet_progress, state.exportProgress, total), modifier = live)
            LinearProgressIndicator(
                progress = { ((state.exportProgress - 1 + within) / total).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().testTag("save-progress")
            )
        }

        state.busy && within > 0f -> LinearProgressIndicator(
            progress = { within },
            modifier = Modifier.fillMaxWidth().testTag("save-progress")
        )

        state.busy -> LinearProgressIndicator(Modifier.fillMaxWidth())

        result is UiMessage.SavedMany -> {
            Text(stringResource(R.string.save_sheet_done, result.saved), modifier = live)
            if (result.reduced > 0) Text(stringResource(R.string.save_sheet_reduced_many, result.reduced))
            if (result.failed > 0) {
                Text(
                    stringResource(R.string.save_sheet_failed, result.failed),
                    color = MaterialTheme.colorScheme.error
                )
            }
        }

        result == UiMessage.Saved -> Text(stringResource(R.string.save_sheet_done, 1), modifier = live)

        result == UiMessage.SavedReduced -> Text(
            stringResource(R.string.save_sheet_done, 1) + "\n" + stringResource(R.string.save_reduced),
            modifier = live
        )

        result != null -> Text(
            UiTexts.from(LocalContext.current).of(result),
            color = MaterialTheme.colorScheme.error,
            modifier = live
        )

        else -> Unit
    }
}

@Composable
private fun ResultActions(state: MainState, result: UiMessage?, actions: SaveActions) {
    if (state.busy || result == null) return
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (state.failedIndices.isNotEmpty()) {
            OutlinedButton(
                onClick = actions.onRetry,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.heightIn(min = MinTouch)
            ) {
                Icon(Icons.Outlined.Refresh, contentDescription = null)
                Text(stringResource(R.string.save_sheet_retry, state.failedIndices.size))
            }
        }
        if (state.savedUris.isNotEmpty()) {
            OutlinedButton(onClick = actions.onShare, modifier = Modifier.heightIn(min = MinTouch)) {
                Icon(Icons.Outlined.Share, contentDescription = null)
                Text(stringResource(R.string.save_sheet_share))
            }
        }
        TextButton(onClick = actions.onDismiss, modifier = Modifier.heightIn(min = MinTouch)) {
            Text(stringResource(R.string.save_sheet_close))
        }
    }
}

private const val PROGRESS_POLL_MS = 100L
