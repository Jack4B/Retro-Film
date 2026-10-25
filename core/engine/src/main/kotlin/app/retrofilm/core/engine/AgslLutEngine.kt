package app.retrofilm.core.engine

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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Backend AGSL (API 33+). AGSL nie ma tekstur 3D, więc LUT jest pakowany do paska 2D
 * o szerokości size*size i wysokości size (x = r + b*size, y = g), próbkowanego bez filtrowania;
 * interpolacja trójliniowa liczona w shaderze (8 odczytów), identycznie jak w [CpuLut].
 */
@TargetApi(Build.VERSION_CODES.TIRAMISU)
class AgslLutEngine : LutEngine {
    override val name: String = "agsl"

    override suspend fun apply(src: Bitmap, lut: Lut, intensity: Float): Bitmap = withContext(Dispatchers.Default) {
        render(src, lut, intensity)
    }

    private fun render(src: Bitmap, lut: Lut, intensity: Float): Bitmap {
        val w = src.width
        val h = src.height
        val input = if (src.config == Bitmap.Config.ARGB_8888) src else src.copy(Bitmap.Config.ARGB_8888, false)
        val strip = lutStrip(lut)
        val shader = RuntimeShader(SHADER).apply {
            setInputShader("image", nearest(input))
            setInputShader("lut", nearest(strip))
            setFloatUniform("size", lut.size.toFloat())
            setFloatUniform("intensity", intensity.coerceIn(0f, 1f))
        }
        val paint = Paint().apply {
            this.shader = shader
            blendMode = BlendMode.SRC
        }
        val node = RenderNode("lut").apply { setPosition(0, 0, w, h) }
        val canvas = node.beginRecording()
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
        node.endRecording()

        val usage = HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE or HardwareBuffer.USAGE_GPU_COLOR_OUTPUT
        val reader = ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, 1, usage)
        val renderer = HardwareRenderer()
        try {
            renderer.setSurface(reader.surface)
            renderer.setContentRoot(node)
            renderer.createRenderRequest().setWaitForPresent(true).syncAndDraw()
            reader.acquireNextImage().use { image ->
                val buffer = checkNotNull(image.hardwareBuffer) { "Brak HardwareBuffer" }
                buffer.use {
                    val hw = checkNotNull(Bitmap.wrapHardwareBuffer(it, ColorSpace.get(ColorSpace.Named.SRGB)))
                    return hw.copy(Bitmap.Config.ARGB_8888, false)
                }
            }
        } finally {
            renderer.destroy()
            reader.close()
            node.discardDisplayList()
            strip.recycle()
        }
    }

    private fun nearest(bitmap: Bitmap): BitmapShader =
        BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
            filterMode = BitmapShader.FILTER_MODE_NEAREST
        }

    private fun lutStrip(lut: Lut): Bitmap {
        val n = lut.size
        val pixels = IntArray(n * n * n)
        for (b in 0 until n) {
            for (g in 0 until n) {
                for (r in 0 until n) {
                    val o = lut.offset(r, g, b)
                    val rgb = (lut.data[o].toInt() and BYTE_MASK shl SHIFT_R) or
                        (lut.data[o + 1].toInt() and BYTE_MASK shl SHIFT_G) or
                        (lut.data[o + 2].toInt() and BYTE_MASK)
                    pixels[g * n * n + b * n + r] = OPAQUE or rgb
                }
            }
        }
        return Bitmap.createBitmap(pixels, n * n, n, Bitmap.Config.ARGB_8888)
    }

    private companion object {
        const val BYTE_MASK = 0xFF
        const val SHIFT_R = 16
        const val SHIFT_G = 8
        const val OPAQUE = -0x1000000

        val SHADER = """
            uniform shader image;
            uniform shader lut;
            uniform float size;
            uniform float intensity;

            float3 node(float r, float g, float b) {
                return lut.eval(float2(r + b * size + 0.5, g + 0.5)).rgb;
            }

            half4 main(float2 xy) {
                float4 c = float4(image.eval(xy));
                float a = c.a;
                float3 rgb = a > 0.0 ? clamp(c.rgb / a, 0.0, 1.0) : float3(0.0);
                float3 p = rgb * (size - 1.0);
                float3 i0 = min(floor(p), float3(size - 1.0));
                float3 i1 = min(i0 + 1.0, float3(size - 1.0));
                float3 f = p - i0;
                float3 c00 = mix(node(i0.r, i0.g, i0.b), node(i1.r, i0.g, i0.b), f.r);
                float3 c10 = mix(node(i0.r, i1.g, i0.b), node(i1.r, i1.g, i0.b), f.r);
                float3 c01 = mix(node(i0.r, i0.g, i1.b), node(i1.r, i0.g, i1.b), f.r);
                float3 c11 = mix(node(i0.r, i1.g, i1.b), node(i1.r, i1.g, i1.b), f.r);
                float3 graded = mix(mix(c00, c10, f.g), mix(c01, c11, f.g), f.b);
                float3 outRgb = mix(rgb, graded, intensity);
                return half4(half3(outRgb * a), half(a));
            }
        """.trimIndent()
    }
}
