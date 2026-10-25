package app.retrofilm.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import app.retrofilm.R
import app.retrofilm.core.frame.TightFrame
import app.retrofilm.pipeline.SignatureFont
import kotlin.math.roundToInt

private val SwatchGap = 8.dp
private val SwatchSize = 48.dp
private val SwatchDot = 32.dp
private val SwatchBorder = 2.dp
private const val MIN_SCALE = 0.32f
private const val MAX_SCALE = 1f
private const val PERCENT = 100

/** Atramenty do wyboru: ARGB zapisywany w ramce i nazwa dla TalkBack. */
private class Ink(val argb: Int, val name: Int)

private val Inks = listOf(
    Ink(0xFF2B2B2B.toInt(), R.string.ink_graphite),
    Ink(0xFF1B2A5C.toInt(), R.string.ink_navy),
    Ink(0xFF1F4FD8.toInt(), R.string.ink_blue),
    Ink(0xFF5A3A1E.toInt(), R.string.ink_sepia),
    Ink(0xFF7A1F2B.toInt(), R.string.ink_burgundy),
    Ink(0xFF1E5B3A.toInt(), R.string.ink_green)
)

/** Wygląd podpisu: krój pisma, wielkość i kolor atramentu. */
@Composable
fun SignatureStyleControls(state: MainState) {
    val f = state.frame
    Column(verticalArrangement = Arrangement.spacedBy(SwatchGap)) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(SwatchGap)) {
            items(SignatureFont.entries, key = { it.name }) { font ->
                FilterChip(
                    selected = f.font == font,
                    onClick = { state.frame = f.copy(font = font) },
                    label = { Text(stringResource(font.label), fontFamily = FontFamily(Font(font.res))) }
                )
            }
        }
        Text(
            stringResource(
                R.string.signature_size_label,
                (
                    f.signatureScale / TightFrame.DEFAULT_SIGNATURE_SCALE *
                        PERCENT
                    ).roundToInt()
            )
        )
        Slider(
            value = f.signatureScale,
            onValueChange = { state.frame = f.copy(signatureScale = it) },
            valueRange = MIN_SCALE..MAX_SCALE,
            modifier = Modifier.heightIn(min = SwatchSize)
        )
        Text(stringResource(R.string.signature_ink_label))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(SwatchGap)) {
            items(Inks, key = { it.argb }) { ink ->
                InkSwatch(ink, f.inkColor == ink.argb) { state.frame = f.copy(inkColor = ink.argb) }
            }
        }
    }
}

@Composable
private fun InkSwatch(ink: Ink, selected: Boolean, onClick: () -> Unit) {
    val cd = stringResource(ink.name)
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(SwatchSize)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = cd }
    ) {
        Box(
            Modifier
                .size(SwatchDot)
                .clip(CircleShape)
                .background(Color(ink.argb))
                .border(
                    BorderStroke(
                        SwatchBorder,
                        if (selected) MaterialTheme.colorScheme.primary else Color.Transparent
                    ),
                    CircleShape
                )
        )
    }
}
