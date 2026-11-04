package app.retrofilm.pipeline

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import org.junit.Assert.assertTrue
import org.junit.Test

/** Mikser HSL: brak skoku na granicy 359° → 0° (interpolacja z zawinięciem). */
class HslMixerWrapTest {
    @Test
    fun noJumpAcrossHueWrap() {
        val adj = (0 until HslAdjust.BANDS).fold(HslAdjust()) { a, b ->
            a.with(b, HslAdjust.HUE, if (b % 2 == 0) 1f else -1f).with(b, HslAdjust.SATURATION, 0.5f)
        }
        val mixer = HslMixer(adj)
        val ok = OkLab()
        val prev = FloatArray(3)
        val rgb = FloatArray(3)
        var maxJump = 0f
        val steps = 3600
        for (i in 0..steps) {
            val h = Math.toRadians(-5.0 + 10.0 * i / steps)
            ok.toRgb(0.6f, 0.1f * cos(h).toFloat(), 0.1f * sin(h).toFloat(), rgb)
            mixer.apply(rgb)
            if (i > 0) maxJump = maxOf(maxJump, (0..2).maxOf { abs(rgb[it] - prev[it]) })
            rgb.copyInto(prev)
        }
        assertTrue("skok $maxJump przy 0°", maxJump < 0.004f)
    }
}
