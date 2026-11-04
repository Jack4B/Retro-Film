package app.retrofilm.core.engine.gpu

import android.annotation.TargetApi
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.BlendMode
import android.graphics.ColorSpace
import android.graphics.HardwareRenderer
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RenderNode
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.hardware.HardwareBuffer
import android.media.ImageReader
import android.os.Build
import android.util.Half
import app.retrofilm.core.engine.AgslLutEngine
import app.retrofilm.core.engine.Lut
import app.retrofilm.core.engine.Srgb
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.ShortBuffer

/**
 * K: AGSL na bitmapach RGBA_F16 (API 33+). Kanały 0..255 → half 0..1 w rozszerzonym sRGB (ta sama przestrzeń na
 * wejściu, w atlasie LUT-a i na wyjściu, więc Skia nie konwertuje barw), wynik czytany z bufora FP16. Atlas LUT-a
 * (kafle 2D jak w [AgslLutEngine]) też w F16 – węzły RGB16 bez zaokrąglenia do 8 bitów.
 */
@TargetApi(Build.VERSION_CODES.TIRAMISU)
class AgslPlaneLut(private val linearOut: Boolean = false) : PlaneLutKernel {
    /**
     * [linearOut] = HWUI zapisuje bufor FP16 w liniowym rozszerzonym sRGB (zależne od wersji/urządzenia) – wtedy
     * wynik wraca przez krzywą sRGB przy odczycie. Który wariant jest właściwy, sprawdza próbka przy pierwszym
     * zapisie (porównanie z CPU), nie założenie.
     */
    override val name = if (linearOut) "gpu-agsl-f16-lin" else "gpu-agsl-f16"

    private val space: ColorSpace = ColorSpace.get(ColorSpace.Named.EXTENDED_SRGB)
    private var atlasKey: Lut? = null
    private var atlas: Bitmap? = null

    @Suppress("LongParameterList")
    override fun apply(r: FloatArray, g: FloatArray, b: FloatArray, from: Int, count: Int, lut: Lut, intensity: Float) {
        if (count <= 0) return
        val grid = PlaneGrid(count)
        val halfs = ByteBuffer.allocateDirect(grid.width * grid.height * RGBA * 2).order(ByteOrder.nativeOrder())
        val shorts = halfs.asShortBuffer()
        pack(shorts, r, g, b, from, count)
        val input = Bitmap.createBitmap(grid.width, grid.height, Bitmap.Config.RGBA_F16, true, space)
        halfs.position(0)
        input.copyPixelsFromBuffer(halfs)
        try {
            val out = render(input, grid, lut, intensity)
            halfs.position(0)
            out.copyPixelsToBuffer(halfs)
            out.recycle()
        } finally {
            input.recycle()
        }
        unpack(shorts, Planes3(r, g, b), from, count, linearOut)
    }

    private fun render(input: Bitmap, grid: PlaneGrid, lut: Lut, intensity: Float): Bitmap {
        val shader = RuntimeShader(SHADER).apply {
            setInputShader("image", nearest(input))
            setInputShader("lut", nearest(atlasFor(lut)))
            setFloatUniform("size", lut.size.toFloat())
            setFloatUniform("tiles", AgslLutEngine.tilesPerRow(lut.size).toFloat())
            setFloatUniform("intensity", intensity.coerceIn(0f, 1f))
        }
        val paint = Paint().apply {
            this.shader = shader
            blendMode = BlendMode.SRC
        }
        val node = RenderNode("lut-f16").apply { setPosition(0, 0, grid.width, grid.height) }
        node.beginRecording().drawRect(0f, 0f, grid.width.toFloat(), grid.height.toFloat(), paint)
        node.endRecording()
        val usage = HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE or HardwareBuffer.USAGE_GPU_COLOR_OUTPUT
        val reader = ImageReader.newInstance(grid.width, grid.height, PixelFormat.RGBA_F16, 1, usage)
        val renderer = HardwareRenderer()
        try {
            renderer.setSurface(reader.surface)
            renderer.setContentRoot(node)
            val status = renderer.createRenderRequest().setWaitForPresent(true).syncAndDraw()
            check(status == HardwareRenderer.SYNC_OK) { "syncAndDraw=$status" }
            reader.acquireNextImage().use { image ->
                val buffer = checkNotNull(image.hardwareBuffer) { "Brak HardwareBuffer" }
                buffer.use {
                    check(it.format == HardwareBuffer.RGBA_FP16) { "Bufor ${it.format} zamiast RGBA_FP16" }
                    val hw = checkNotNull(Bitmap.wrapHardwareBuffer(it, space)) { "wrapHardwareBuffer" }
                    val soft = hw.copy(Bitmap.Config.RGBA_F16, false)
                    hw.recycle()
                    return checkNotNull(soft) { "Kopia F16" }
                }
            }
        } finally {
            renderer.destroy()
            reader.close()
            node.discardDisplayList()
        }
    }

    @Suppress("NestedBlockDepth")
    private fun atlasFor(lut: Lut): Bitmap {
        atlas?.takeIf { atlasKey === lut }?.let { return it }
        atlas?.recycle()
        val n = lut.size
        val tiles = AgslLutEngine.tilesPerRow(n)
        val side = tiles * n
        val buf = ByteBuffer.allocateDirect(side * side * RGBA * 2).order(ByteOrder.nativeOrder())
        val s = buf.asShortBuffer()
        for (bb in 0 until n) {
            val ox = (bb % tiles) * n
            val oy = (bb / tiles) * n
            for (gg in 0 until n) {
                for (rr in 0 until n) {
                    val at = ((oy + gg) * side + ox + rr) * RGBA
                    for (c in 0 until Lut.CHANNELS) s.put(at + c, Half.toHalf(lut.nodeF(rr, gg, bb, c) / MAX))
                    s.put(at + Lut.CHANNELS, ONE)
                }
            }
        }
        val soft = Bitmap.createBitmap(side, side, Bitmap.Config.RGBA_F16, true, space)
        buf.position(0)
        soft.copyPixelsFromBuffer(buf)
        val hw = runCatching { soft.copy(Bitmap.Config.HARDWARE, false) }.getOrNull()
        if (hw != null) soft.recycle()
        return (hw ?: soft).also {
            atlas = it
            atlasKey = lut
        }
    }

    override fun close() {
        atlas?.recycle()
        atlas = null
        atlasKey = null
    }

    private fun nearest(bitmap: Bitmap) = BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
        filterMode = BitmapShader.FILTER_MODE_NEAREST
    }

    internal companion object {
        const val RGBA = 4
        const val ALPHA = 3
        const val MAX = 255f
        private val ONE: Short = Half.toHalf(1f)

        @Suppress("LongParameterList")
        fun pack(s: ShortBuffer, r: FloatArray, g: FloatArray, b: FloatArray, from: Int, count: Int) {
            for (i in 0 until count) {
                val at = i * RGBA
                s.put(at, Half.toHalf(r[from + i] / MAX))
                s.put(at + 1, Half.toHalf(g[from + i] / MAX))
                s.put(at + 2, Half.toHalf(b[from + i] / MAX))
                s.put(at + ALPHA, ONE)
            }
        }

        class Planes3(val r: FloatArray, val g: FloatArray, val b: FloatArray)

        fun unpack(s: ShortBuffer, p: Planes3, from: Int, count: Int, linear: Boolean) {
            for (i in 0 until count) {
                val at = i * RGBA
                p.r[from + i] = decode(s.get(at), linear)
                p.g[from + i] = decode(s.get(at + 1), linear)
                p.b[from + i] = decode(s.get(at + 2), linear)
            }
        }

        private fun decode(h: Short, linear: Boolean): Float {
            val v = Half.toFloat(h).coerceIn(0f, 1f)
            return (if (linear) Srgb.fromLinear(v) else v) * MAX
        }

        private val SHADER = """
            uniform shader image;
            uniform shader lut;
            uniform float size;
            uniform float tiles;
            uniform float intensity;

            ${LutShaderMath.srgb("float3")}

            // jak toSrgb, ale bez przycinania (wartości ujemne – liniowy odcinek krzywej, jak Srgb na CPU)
            float3 toSrgbExt(float3 c) {
                return mix(c * 12.92, 1.055 * pow(max(c, float3(0.0)), float3(1.0 / 2.4)) - 0.055,
                    step(float3(0.0031308), c));
            }

            float3 node(float r, float g, float b) {
                float ty = floor(b / tiles);
                float tx = b - ty * tiles;
                half3 v = lut.eval(float2(tx * size + r + 0.5, ty * size + g + 0.5)).rgb;
                return toSrgbExt(float3(toLinearSrgb(v)));
            }

            half4 main(float2 xy) {
                // niezależnie od przestrzeni roboczej HWUI: przestrzeń robocza → liniowe sRGB → zakodowane sRGB
                float3 src = toSrgbExt(float3(toLinearSrgb(image.eval(xy).rgb)));
                float3 rgb = clamp(src, 0.0, 1.0);
                ${LutShaderMath.tetra("float3")}
                ${LutShaderMath.mix("float3")}
                return half4(fromLinearSrgb(half3(toLinear(outRgb))), 1.0);
            }
        """.trimIndent()
    }
}
