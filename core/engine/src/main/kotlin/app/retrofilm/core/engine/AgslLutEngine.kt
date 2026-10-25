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
 * Backend AGSL (API 33+). AGSL nie ma tekstur 3D, więc LUT jest pakowany do siatki kafli 2D:
 * plaster `b` (size×size, x = r, y = g) trafia do kafla (b % tiles, b / tiles), gdzie
 * tiles = ceil(sqrt(size)). Dla 33 → 6×6 kafli = 198×198 px, 65 → 9×9 = 585×585,
 * 144 → 12×12 = 1728×1728 (pasek 1D miałby 20736 px szerokości – ponad typowy limit 16384).
 * Tekstura próbkowana bez filtrowania; interpolacja trójliniowa (także między plastrami b)
 * liczona w shaderze (8 odczytów), identycznie jak w [CpuLut]. Ostatnie 2 tekstury LUT są
 * buforowane (LRU).
 */
@TargetApi(Build.VERSION_CODES.TIRAMISU)
class AgslLutEngine(private val interpolation: Interpolation = Interpolation.TRILINEAR) : LutEngine {
    override val name: String = if (interpolation == Interpolation.TRILINEAR) "agsl" else "agsl-tetra"

    override suspend fun apply(src: Bitmap, lut: Lut, intensity: Float): Bitmap = withContext(Dispatchers.Default) {
        render(src, lut, intensity)
    }

    private fun render(src: Bitmap, lut: Lut, intensity: Float): Bitmap {
        val w = src.width
        val h = src.height
        val input = if (src.config == Bitmap.Config.ARGB_8888) src else src.copy(Bitmap.Config.ARGB_8888, false)
        val strip = cachedTiles(lut)
        val shader = RuntimeShader(SHADER).apply {
            setInputShader("image", nearest(input))
            setInputShader("lut", nearest(strip))
            setFloatUniform("size", lut.size.toFloat())
            setFloatUniform("tiles", tilesPerRow(lut.size).toFloat())
            setFloatUniform("intensity", intensity.coerceIn(0f, 1f))
            setFloatUniform("tetra", if (interpolation == Interpolation.TETRAHEDRAL) 1f else 0f)
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
        }
    }

    private fun nearest(bitmap: Bitmap): BitmapShader =
        BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
            filterMode = BitmapShader.FILTER_MODE_NEAREST
        }

    private fun cachedTiles(lut: Lut): Bitmap = synchronized(cache) {
        cache.remove(lut)?.also { cache[lut] = it }
            ?: packTiles(lut).also {
                cache[lut] = it
                while (cache.size > CACHE_SIZE) {
                    val eldest = cache.keys.first()
                    cache.remove(eldest)?.recycle()
                }
            }
    }

    /** Czyści bufor tekstur LUT. */
    fun clearCache() = synchronized(cache) {
        cache.values.forEach { it.recycle() }
        cache.clear()
    }

    private val cache = LinkedHashMap<Lut, Bitmap>()

    companion object {
        /** Maksymalna liczba buforowanych tekstur LUT. */
        const val CACHE_SIZE = 2

        fun tilesPerRow(size: Int): Int = Math.ceil(Math.sqrt(size.toDouble())).toInt()

        /** Wymiar boku tekstury kafli dla LUT-a [size] (px). */
        fun atlasSide(size: Int): Int = tilesPerRow(size) * size

        /** Pakuje LUT do siatki kafli (patrz opis klasy); publiczne dla pomiarów w self-teście. */
        fun packTiles(lut: Lut): Bitmap {
            val n = lut.size
            val tiles = tilesPerRow(n)
            val side = tiles * n
            val pixels = IntArray(side * side)
            for (b in 0 until n) {
                val ox = (b % tiles) * n
                val oy = (b / tiles) * n
                for (g in 0 until n) {
                    for (r in 0 until n) {
                        val rgb = (lut.node8(r, g, b, 0) shl SHIFT_R) or
                            (lut.node8(r, g, b, 1) shl SHIFT_G) or
                            lut.node8(r, g, b, 2)
                        pixels[(oy + g) * side + ox + r] = OPAQUE or rgb
                    }
                }
            }
            return Bitmap.createBitmap(pixels, side, side, Bitmap.Config.ARGB_8888)
        }

        private const val SHIFT_R = 16
        private const val SHIFT_G = 8
        private const val OPAQUE = -0x1000000

        private val SHADER = """
            uniform shader image;
            uniform shader lut;
            uniform float size;
            uniform float tiles;
            uniform float intensity;
            uniform float tetra;

            float3 node(float r, float g, float b) {
                float ty = floor(b / tiles);
                float tx = b - ty * tiles;
                return lut.eval(float2(tx * size + r + 0.5, ty * size + g + 0.5)).rgb;
            }

            half4 main(float2 xy) {
                float4 c = float4(image.eval(xy));
                float a = c.a;
                float3 rgb = a > 0.0 ? clamp(c.rgb / a, 0.0, 1.0) : float3(0.0);
                float3 p = rgb * (size - 1.0);
                float3 i0 = min(floor(p), float3(size - 1.0));
                float3 i1 = min(i0 + 1.0, float3(size - 1.0));
                float3 f = p - i0;
                float3 graded;
                if (tetra > 0.5) {
                    float3 c000 = node(i0.r, i0.g, i0.b);
                    float3 c111 = node(i1.r, i1.g, i1.b);
                    if (f.r >= f.g && f.g >= f.b) {
                        float3 a = node(i1.r, i0.g, i0.b); float3 b = node(i1.r, i1.g, i0.b);
                        graded = c000 + f.r * (a - c000) + f.g * (b - a) + f.b * (c111 - b);
                    } else if (f.r >= f.b && f.b >= f.g) {
                        float3 a = node(i1.r, i0.g, i0.b); float3 b = node(i1.r, i0.g, i1.b);
                        graded = c000 + f.r * (a - c000) + f.b * (b - a) + f.g * (c111 - b);
                    } else if (f.b >= f.r && f.r >= f.g) {
                        float3 a = node(i0.r, i0.g, i1.b); float3 b = node(i1.r, i0.g, i1.b);
                        graded = c000 + f.b * (a - c000) + f.r * (b - a) + f.g * (c111 - b);
                    } else if (f.g >= f.r && f.r >= f.b) {
                        float3 a = node(i0.r, i1.g, i0.b); float3 b = node(i1.r, i1.g, i0.b);
                        graded = c000 + f.g * (a - c000) + f.r * (b - a) + f.b * (c111 - b);
                    } else if (f.g >= f.b && f.b >= f.r) {
                        float3 a = node(i0.r, i1.g, i0.b); float3 b = node(i0.r, i1.g, i1.b);
                        graded = c000 + f.g * (a - c000) + f.b * (b - a) + f.r * (c111 - b);
                    } else {
                        float3 a = node(i0.r, i0.g, i1.b); float3 b = node(i0.r, i1.g, i1.b);
                        graded = c000 + f.b * (a - c000) + f.g * (b - a) + f.r * (c111 - b);
                    }
                } else {
                float3 c00 = mix(node(i0.r, i0.g, i0.b), node(i1.r, i0.g, i0.b), f.r);
                float3 c10 = mix(node(i0.r, i1.g, i0.b), node(i1.r, i1.g, i0.b), f.r);
                float3 c01 = mix(node(i0.r, i0.g, i1.b), node(i1.r, i0.g, i1.b), f.r);
                float3 c11 = mix(node(i0.r, i1.g, i1.b), node(i1.r, i1.g, i1.b), f.r);
                graded = mix(mix(c00, c10, f.g), mix(c01, c11, f.g), f.b);
                }
                float3 outRgb = mix(rgb, graded, intensity);
                return half4(half3(outRgb * a), half(a));
            }
        """.trimIndent()
    }
}
