package app.retrofilm.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.retrofilm.R
import app.retrofilm.pipeline.ToneCurve
import app.retrofilm.pipeline.ToneCurves
import java.util.Locale
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt

private val Pad = 16.dp
private val Gap = 8.dp
private val MinTouch = 48.dp
private val PickRadius = 28.dp
private val DotRadius = 7.dp
private const val SAMPLES = 64
private const val GRID = 4
private const val PERCENT = 100
private const val SLIDER_RANGE = 0.25f
private val SliderXs = floatArrayOf(0.25f, 0.5f, 0.75f)

private val ChannelColors = listOf(Color.White, Color(0xFFE53935), Color(0xFF43A047), Color(0xFF1E88E5))

/** Krzywe tonalne: wykres do przeciągania punktów (dotknięcie dodaje punkt, dwukrotne usuwa) i suwaki dla TalkBack. */
@Composable
internal fun CurveControls(state: MainState) {
    var channel by rememberSaveable { mutableIntStateOf(ToneCurves.CHANNEL_MASTER) }
    val curves = state.adjustments.curves
    val names = listOf(R.string.curve_rgb, R.string.curve_red, R.string.curve_green, R.string.curve_blue)
    val set = { c: ToneCurve -> state.adjustments = state.adjustments.copy(curves = curves.with(channel, c)) }
    Column(Modifier.padding(horizontal = Pad), verticalArrangement = Arrangement.spacedBy(Gap)) {
        Row(horizontalArrangement = Arrangement.spacedBy(Gap)) {
            names.forEachIndexed { i, n ->
                FilterChip(selected = channel == i, onClick = { channel = i }, label = { Text(stringResource(n)) })
            }
        }
        CurveCanvas(curves.channel(channel), ChannelColors[channel], set)
        Text(
            stringResource(R.string.curve_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    CurveSliders(curves.channel(channel), set)
    OutlinedButton(
        onClick = { set(ToneCurve()) },
        enabled = !curves.channel(channel).isIdentity,
        modifier = Modifier.padding(horizontal = Pad).heightIn(min = MinTouch)
    ) { Text(stringResource(R.string.curve_reset)) }
}

@Composable
private fun CurveCanvas(curve: ToneCurve, color: Color, onChange: (ToneCurve) -> Unit) {
    var live by remember(curve) { mutableStateOf(curve) }
    var active by remember { mutableIntStateOf(-1) }
    val description = stringResource(R.string.curve_cd, curve.points.size)
    val grid = MaterialTheme.colorScheme.outlineVariant
    val dot = MaterialTheme.colorScheme.primary
    Canvas(
        Modifier.fillMaxWidth().aspectRatio(1f)
            .semantics { contentDescription = description }
            .pointerInput(curve) {
                val radius = PickRadius.toPx()
                detectDragGestures(
                    onDragStart = { at ->
                        val i = pick(live, at, size.width.toFloat(), size.height.toFloat(), radius)
                        active = if (i >= 0) {
                            i
                        } else {
                            val (c, idx) = live.inserted(at.x / size.width, 1f - at.y / size.height)
                            live = c
                            idx
                        }
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        live = live.moved(active, change.position.x / size.width, 1f - change.position.y / size.height)
                    },
                    onDragEnd = { onChange(live) },
                    onDragCancel = { onChange(live) }
                )
            }
            .pointerInput(curve) {
                detectTapGestures(
                    onTap = { at ->
                        val (c, _) = live.inserted(at.x / size.width, 1f - at.y / size.height)
                        live = c
                        onChange(c)
                    },
                    onDoubleTap = { at ->
                        val i = pick(live, at, size.width.toFloat(), size.height.toFloat(), PickRadius.toPx())
                        if (i >= 0) onChange(live.removed(i).also { live = it })
                    }
                )
            }
    ) { draw(live, color, grid, dot) }
}

private fun DrawScope.draw(curve: ToneCurve, color: Color, grid: Color, dot: Color) {
    for (i in 0..GRID) {
        val x = size.width * i / GRID
        val y = size.height * i / GRID
        drawLine(grid, Offset(x, 0f), Offset(x, size.height), 1.dp.toPx())
        drawLine(grid, Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
    }
    drawLine(grid, Offset(0f, size.height), Offset(size.width, 0f), 1.dp.toPx())
    val lut = curve.lut(SAMPLES)
    val path = Path()
    for (i in 0..SAMPLES) {
        val x = size.width * i / SAMPLES
        val y = size.height * (1f - lut[i])
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    drawPath(path, color, style = Stroke(width = 2.dp.toPx()))
    curve.points.forEach {
        drawCircle(dot, DotRadius.toPx(), Offset(it.x * size.width, (1f - it.y) * size.height))
    }
    drawRect(grid, Offset.Zero, Size(size.width, size.height), style = Stroke(1.dp.toPx()))
}

/** Najbliższy punkt w promieniu [radius] px od [at] albo −1. */
private fun pick(curve: ToneCurve, at: Offset, w: Float, h: Float, radius: Float): Int {
    var best = -1
    var bestDistance = radius
    curve.points.forEachIndexed { i, p ->
        val d = hypot(p.x * w - at.x, (1f - p.y) * h - at.y)
        if (d <= bestDistance) {
            bestDistance = d
            best = i
        }
    }
    return best
}

/** Trzy suwaki (ciemne, średnie, jasne tony) przesuwają krzywą w stałych punktach – wygodne też dla TalkBack. */
@Composable
private fun CurveSliders(curve: ToneCurve, onChange: (ToneCurve) -> Unit) {
    val labels = listOf(R.string.curve_darks, R.string.curve_mids, R.string.curve_lights)
    SliderXs.forEachIndexed { i, x ->
        val offset = (curve.eval(x) - x) / SLIDER_RANGE
        AdjustSlider(
            label = stringResource(labels[i]),
            value = offset.coerceIn(-1f, 1f),
            display = { "%+d%%".format(Locale.forLanguageTag("pl"), (it * PERCENT).roundToInt()) }
        ) { v -> if (abs(v - offset) > 0f) onChange(curve.with(x, (x + v * SLIDER_RANGE).coerceIn(0f, 1f))) }
    }
}
