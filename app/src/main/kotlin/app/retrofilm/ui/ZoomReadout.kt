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
private const val SUBTLE_SCRIM = 0.14f
private const val SUBTLE_TEXT = 0.55f
private val ReadoutPadH = 10.dp
private val ReadoutPadV = 4.dp

/** Półprzezroczysta etykieta u góry podglądu: skala, wymiary w pikselach i megapiksele. */
@Composable
internal fun ZoomReadout(text: String, description: String, modifier: Modifier = Modifier, subtle: Boolean = false) {
    Surface(
        color = Color.Black.copy(alpha = if (subtle) SUBTLE_SCRIM else SCRIM_ALPHA),
        shape = MaterialTheme.shapes.small,
        modifier = modifier.semantics { contentDescription = description }
    ) {
        Text(
            text,
            color = Color.White.copy(alpha = if (subtle) SUBTLE_TEXT else TEXT_ALPHA),
            style = if (subtle) MaterialTheme.typography.titleMedium else MaterialTheme.typography.labelMedium,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = ReadoutPadH, vertical = ReadoutPadV)
        )
    }
}
