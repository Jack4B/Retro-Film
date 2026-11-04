package app.retrofilm.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.retrofilm.R

private const val PILL_PERCENT = 50
private const val OFF_ALPHA = 0.55f
private const val OFF_TEXT_ALPHA = 0.7f

/**
 * Klikalny chip „HDR Wył./Wł.” (tylko dla zdjęć z gain mapą): przełącza „HDR ze zdjęcia” bieżącego zdjęcia.
 * Pole dotyku min. 48 dp; Wł. = wypełniony kolorem akcentu, Wył. = przygaszony z obrysem.
 */
@Composable
internal fun HdrChip(on: Boolean, onToggle: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val cd = stringResource(R.string.hdr_chip_cd)
    val state = stringResource(if (on) R.string.hdr_on else R.string.hdr_off)
    val accent = MaterialTheme.colorScheme.primary
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            .toggleable(value = on, role = Role.Switch, onValueChange = onToggle)
            .semantics {
                contentDescription = cd
                stateDescription = state
            }
    ) {
        Surface(
            shape = RoundedCornerShape(PILL_PERCENT),
            color = if (on) accent else Color.Black.copy(alpha = OFF_ALPHA),
            border = if (on) null else BorderStroke(1.dp, Color.White.copy(alpha = OFF_TEXT_ALPHA))
        ) {
            Text(
                text = stringResource(R.string.hdr_chip, state),
                color = if (on) MaterialTheme.colorScheme.onPrimary else Color.White.copy(alpha = OFF_TEXT_ALPHA),
                fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
            )
        }
    }
}
