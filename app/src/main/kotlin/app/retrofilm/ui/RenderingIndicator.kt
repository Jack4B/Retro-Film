package app.retrofilm.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
/** Subtelny pasek „liczę podgląd”: pokazywany dopiero, gdy render trwa dłużej niż [RENDER_HINT_MS]. */
@Composable
internal fun RenderingIndicator(rendering: Boolean, modifier: Modifier) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(rendering) {
        visible = false
        if (rendering) {
            delay(RENDER_HINT_MS)
            visible = true
        }
    }
    if (visible) LinearProgressIndicator(modifier.fillMaxWidth().height(2.dp).testTag("preview-rendering"))
}

private const val RENDER_HINT_MS = 150L
