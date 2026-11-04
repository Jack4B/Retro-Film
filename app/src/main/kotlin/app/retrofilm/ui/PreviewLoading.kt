package app.retrofilm.ui

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.retrofilm.R

private val LabelOffset = 96.dp

/** Kółko postępu; przy RAW bez osadzonego podglądu dodatkowo napis, bo dekodowanie trwa dłużej. */
@Composable
internal fun BoxScope.LoadingIndicator(visible: Boolean, raw: Boolean) {
    if (visible) CircularProgressIndicator()
    if (raw) {
        Text(
            stringResource(R.string.raw_loading),
            modifier = Modifier.align(Alignment.Center).padding(top = LabelOffset).testTag("raw-loading")
        )
    }
}
