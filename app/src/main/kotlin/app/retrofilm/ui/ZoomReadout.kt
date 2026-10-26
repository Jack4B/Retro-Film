package app.retrofilm.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

private const val SCRIM_ALPHA = 0.38f
private const val TEXT_ALPHA = 0.85f
private val ReadoutPadH = 10.dp
private val ReadoutPadV = 4.dp

/** Półprzezroczysta etykieta u góry podglądu: skala, wymiary w pikselach i megapiksele. */
@Composable
internal fun ZoomReadout(text: String, description: String, modifier: Modifier = Modifier) {
    Surface(
        color = Color.Black.copy(alpha = SCRIM_ALPHA),
        shape = MaterialTheme.shapes.small,
        modifier = modifier.semantics { contentDescription = description }
    ) {
        Text(
            text,
            color = Color.White.copy(alpha = TEXT_ALPHA),
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = ReadoutPadH, vertical = ReadoutPadV)
        )
    }
}
