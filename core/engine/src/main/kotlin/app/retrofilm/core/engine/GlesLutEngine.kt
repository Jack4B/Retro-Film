package app.retrofilm.core.engine

import android.graphics.Bitmap
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.opengl.GLES30
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.Executors
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * Backend GLES 3.0: offscreen (pbuffer 1x1 + FBO), LUT jako tekstura 3D z GL_LINEAR (sprzętowa interpolacja
 * trójliniowa; pomiar na Mali-G720: max różnica z CPU 1 poziom, więc interpolacja zostaje bez zmian).
 *
 * - Obraz renderowany jest kaflami nie większymi niż GL_MAX_TEXTURE_SIZE (i [tileLimit]), więc duże zdjęcia nie
 *   spadają już na wolny CPU.
 * - Piksele ARGB (int, little-endian = bajty B,G,R,A) wysyłane i czytane są hurtem bez konwersji na CPU; zamianę
 *   kanałów robi shader (`.bgr`). Dawna pętla bajt po bajcie dominowała czas 12 MP (2,4 s przy 0,17 s w AGSL).
 * - Kontekst EGL żyje na jednym wątku GL przez cały czas działania aplikacji, a wysłane tekstury LUT są
 *   cache'owane ([LUT_CACHE] ostatnich), więc film 144³ nie jest wysyłany przy każdym renderze (287–603 ms).
 */
class GlesLutEngine(private val tileLimit: Int = Int.MAX_VALUE, private val dither: Boolean = true) : LutEngine {
    override val name: String = "gles3"

    override suspend fun apply(src: Bitmap, lut: Lut, intensity: Float): Bitmap = withContext(glDispatcher) {
        val pixels = BitmapPixels.read(src)
        val out = session().render(
            EglSession.Frame(pixels, src.width, src.height),
            lut,
            intensity.coerceIn(0f, 1f),
            tileLimit,
            dither
        )
        BitmapPixels.write(out, src.width, src.height)
    }

    companion object {
        private val glDispatcher = Executors.newSingleThreadExecutor { r ->
            Thread(r, "retrofilm-gles").apply { isDaemon = true }
        }.asCoroutineDispatcher()

        private var shared: EglSession? = null

        /** Wywoływane tylko na wątku GL. */
        private fun session(): EglSession = shared ?: EglSession().also { shared = it }

        /** Sprawdza, czy urządzenie potrafi utworzyć kontekst GLES 3.0. */
        fun isSupported(): Boolean = runCatching { EglSession().close() }.isSuccess

        /** Nazwa GPU (GL_VENDOR / GL_RENDERER / GL_VERSION) albo null, gdy GLES 3 niedostępny. */
        fun gpuInfo(): String? = runCatching {
            EglSession().use {
                listOf(GLES30.GL_VENDOR, GLES30.GL_RENDERER, GLES30.GL_VERSION)
                    .joinToString(" / ") { name -> GLES30.glGetString(name).orEmpty() }
            }
        }.getOrNull()
    }
}

/** Podział obrazu [width]×[height] na kafle o boku ≤ [max]: lista (x, y, w, h). Czysta logika – test na JVM. */
internal object GlTiles {
    const val X = 0
    const val Y = 1
    const val W = 2
    const val H = 3

    fun plan(width: Int, height: Int, max: Int): List<IntArray> {
        require(max > 0)
        val out = ArrayList<IntArray>()
        var y = 0
        while (y < height) {
            val th = minOf(max, height - y)
            var x = 0
            while (x < width) {
                val tw = minOf(max, width - x)
                out.add(intArrayOf(x, y, tw, th))
                x += tw
            }
            y += th
        }
        return out
    }
}

private class EglSession : AutoCloseable {
    private val display: EGLDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
    private val context: EGLContext
    private val surface: EGLSurface

    init {
        val version = IntArray(2)
        check(EGL14.eglInitialize(display, version, 0, version, 1)) { "eglInitialize" }
        val configs = arrayOfNulls<EGLConfig>(1)
        val count = IntArray(1)
        val attrs = intArrayOf(
            EGL14.EGL_RENDERABLE_TYPE, EGL_OPENGL_ES3_BIT,
            EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
            EGL14.EGL_RED_SIZE, BITS, EGL14.EGL_GREEN_SIZE, BITS,
            EGL14.EGL_BLUE_SIZE, BITS, EGL14.EGL_ALPHA_SIZE, BITS,
            EGL14.EGL_NONE
        )
        check(EGL14.eglChooseConfig(display, attrs, 0, configs, 0, 1, count, 0) && count[0] > 0) {
            "Brak konfiguracji EGL dla GLES 3"
        }
        val config = configs[0]
        context = EGL14.eglCreateContext(
            display,
            config,
            EGL14.EGL_NO_CONTEXT,
            intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, GLES_VERSION, EGL14.EGL_NONE),
            0
        )
        check(context != EGL14.EGL_NO_CONTEXT) { "eglCreateContext" }
        surface = EGL14.eglCreatePbufferSurface(
            display,
            config,
            intArrayOf(EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE),
            0
        )
        check(EGL14.eglMakeCurrent(display, surface, surface, context)) { "eglMakeCurrent" }
    }

    private val program: Int by lazy { GlProgram.build(VERTEX, FRAGMENT) }
    private val luts = LinkedHashMap<Lut, Int>(LUT_CACHE, LOAD, true)

    fun render(frame: Frame, lut: Lut, intensity: Float, tileLimit: Int, dither: Boolean = false): IntArray {
        check(EGL14.eglMakeCurrent(display, surface, surface, context)) { "eglMakeCurrent" }
        check(ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN) { "Wymagany little-endian" }
        val maxSize = IntArray(1)
        GLES30.glGetIntegerv(GLES30.GL_MAX_TEXTURE_SIZE, maxSize, 0)
        val tile = minOf(maxSize[0], tileLimit, frame.width.coerceAtLeast(frame.height)).coerceAtLeast(1)
        val io = TileIo(frame, minOf(tile, frame.width), minOf(tile, frame.height))
        val textures = IntArray(2)
        GLES30.glGenTextures(2, textures, 0)
        val fbo = IntArray(1)
        GLES30.glGenFramebuffers(1, fbo, 0)
        try {
            prepare(io, textures, fbo[0], lut, intensity)
            GLES30.glUniform1f(GLES30.glGetUniformLocation(program, "uDither"), if (dither) 1f else 0f)
            val uScale = GLES30.glGetUniformLocation(program, "uScale")
            for (t in GlTiles.plan(frame.width, frame.height, tile)) drawTile(io, t, textures[0], uScale)
            check(GLES30.glGetError() == GLES30.GL_NO_ERROR) { "Błąd GL podczas renderu" }
            return io.out
        } finally {
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
            GLES30.glDeleteFramebuffers(1, fbo, 0)
            GLES30.glDeleteTextures(2, textures, 0)
        }
    }

    private fun prepare(io: TileIo, textures: IntArray, fbo: Int, lut: Lut, intensity: Float) {
        val lutTex = lutTexture(lut)
        allocate2d(textures[0], io.tw, io.th, GLES30.GL_NEAREST)
        allocate2d(textures[1], io.tw, io.th, GLES30.GL_NEAREST)
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fbo)
        GLES30.glFramebufferTexture2D(
            GLES30.GL_FRAMEBUFFER,
            GLES30.GL_COLOR_ATTACHMENT0,
            GLES30.GL_TEXTURE_2D,
            textures[1],
            0
        )
        check(GLES30.glCheckFramebufferStatus(GLES30.GL_FRAMEBUFFER) == GLES30.GL_FRAMEBUFFER_COMPLETE) {
            "FBO niekompletny"
        }
        GLES30.glUseProgram(program)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE1)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_3D, lutTex)
        GLES30.glUniform1i(GLES30.glGetUniformLocation(program, "uImage"), 0)
        GLES30.glUniform1i(GLES30.glGetUniformLocation(program, "uLut"), 1)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(program, "uSize"), lut.size.toFloat())
        GLES30.glUniform1f(GLES30.glGetUniformLocation(program, "uIntensity"), intensity)
        GLES30.glPixelStorei(GLES30.GL_UNPACK_ALIGNMENT, 1)
        GLES30.glPixelStorei(GLES30.GL_PACK_ALIGNMENT, 1)
    }

    /** Jeden kafel [t] = (x, y, w, h): hurtowe wysłanie, rysowanie i odczyt; alfa kopiowana ze źródła. */
    private fun drawTile(io: TileIo, t: IntArray, imageTex: Int, uScale: Int) {
        val x0 = t[GlTiles.X]
        val y0 = t[GlTiles.Y]
        val w = t[GlTiles.W]
        val h = t[GlTiles.H]
        val f = io.frame
        io.ints.clear()
        for (y in 0 until h) io.ints.put(f.pixels, (y0 + y) * f.width + x0, w)
        io.bytes.position(0)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, imageTex)
        GLES30.glTexSubImage2D(GLES30.GL_TEXTURE_2D, 0, 0, 0, w, h, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, io.bytes)
        GLES30.glUniform2f(uScale, w.toFloat() / io.tw, h.toFloat() / io.th)
        GLES30.glViewport(0, 0, w, h)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, TRIANGLE_VERTICES)
        io.bytes.position(0)
        GLES30.glReadPixels(0, 0, w, h, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, io.bytes)
        io.ints.position(0)
        for (y in 0 until h) {
            io.ints.get(io.row, 0, w)
            val base = (y0 + y) * f.width + x0
            for (i in 0 until w) io.out[base + i] = (f.pixels[base + i] and ALPHA_MASK) or (io.row[i] and RGB_MASK)
        }
    }

    class Frame(val pixels: IntArray, val width: Int, val height: Int)

    private class TileIo(val frame: Frame, val tw: Int, val th: Int) {
        val out = IntArray(frame.pixels.size)
        val bytes: ByteBuffer = ByteBuffer.allocateDirect(tw * th * RGBA).order(ByteOrder.LITTLE_ENDIAN)
        val ints: java.nio.IntBuffer = bytes.asIntBuffer()
        val row = IntArray(tw)
    }

    private fun allocate2d(tex: Int, width: Int, height: Int, filter: Int) {
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, tex)
        params(GLES30.GL_TEXTURE_2D, filter)
        GLES30.glTexImage2D(
            GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA8, width, height, 0,
            GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, null
        )
    }

    /** Tekstura LUT z cache (klucz: instancja [Lut], z [LutMemoryCache] ta sama dla tego samego filmu). */
    private fun lutTexture(lut: Lut): Int {
        luts[lut]?.let { return it }
        if (luts.size >= LUT_CACHE) {
            val eldest = luts.entries.iterator().next()
            GLES30.glDeleteTextures(1, intArrayOf(eldest.value), 0)
            luts.remove(eldest.key)
        }
        val tex = IntArray(1)
        GLES30.glGenTextures(1, tex, 0)
        upload3d(tex[0], lut)
        luts[lut] = tex[0]
        return tex[0]
    }

    /** RGB8 idzie wprost z tablicy LUT (układ r najszybciej = układ tekstury 3D); RGB16 zaokrąglany do 8 bitów. */
    private fun upload3d(tex: Int, lut: Lut) {
        val n = lut.size
        val raw = if (lut.bytesPerChannel == 1) {
            lut.data
        } else {
            ByteArray(n * n * n * Lut.CHANNELS).also { a ->
                var i = 0
                for (b in 0 until n) {
                    for (g in 0 until n) {
                        for (r in 0 until n) {
                            for (c in 0 until Lut.CHANNELS) a[i++] = lut.node8(r, g, b, c).toByte()
                        }
                    }
                }
            }
        }
        val buf = ByteBuffer.allocateDirect(raw.size).order(ByteOrder.nativeOrder())
        buf.put(raw).position(0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_3D, tex)
        params(GLES30.GL_TEXTURE_3D, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_3D, GLES30.GL_TEXTURE_WRAP_R, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glPixelStorei(GLES30.GL_UNPACK_ALIGNMENT, 1)
        GLES30.glTexImage3D(
            GLES30.GL_TEXTURE_3D, 0, GLES30.GL_RGB8, n, n, n, 0,
            GLES30.GL_RGB, GLES30.GL_UNSIGNED_BYTE, buf
        )
    }

    private fun params(target: Int, filter: Int) {
        GLES30.glTexParameteri(target, GLES30.GL_TEXTURE_MIN_FILTER, filter)
        GLES30.glTexParameteri(target, GLES30.GL_TEXTURE_MAG_FILTER, filter)
        GLES30.glTexParameteri(target, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(target, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
    }

    override fun close() {
        luts.values.forEach { GLES30.glDeleteTextures(1, intArrayOf(it), 0) }
        luts.clear()
        EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
        EGL14.eglDestroySurface(display, surface)
        EGL14.eglDestroyContext(display, context)
    }

    private companion object {
        const val EGL_OPENGL_ES3_BIT = 0x40
        const val GLES_VERSION = 3
        const val BITS = 8
        const val RGBA = 4
        const val TRIANGLE_VERTICES = 3
        const val ALPHA_MASK = -0x1000000
        const val RGB_MASK = 0x00FFFFFF
        const val LUT_CACHE = 2
        const val LOAD = 0.75f

        // Pełnoekranowy trójkąt bez bufora wierzchołków; v=0 to pierwszy wiersz bitmapy (glReadPixels czyta od dołu,
        // więc tekstura i FBO mają tę samą orientację – brak odbicia).
        val VERTEX = """
            #version 300 es
            uniform vec2 uScale;
            out vec2 vUv;
            void main() {
                vec2 p = vec2(float((gl_VertexID << 1) & 2), float(gl_VertexID & 2));
                vUv = p * uScale;
                gl_Position = vec4(p * 2.0 - 1.0, 0.0, 1.0);
            }
        """.trimIndent()

        val FRAGMENT = """
            #version 300 es
            precision highp float;
            precision highp sampler3D;
            in vec2 vUv;
            uniform sampler2D uImage;
            uniform sampler3D uLut;
            uniform float uSize;
            uniform float uIntensity;
            uniform float uDither;
            out vec4 outColor;
            vec3 toLinear(vec3 c) {
                return mix(c / 12.92, pow((c + 0.055) / 1.055, vec3(2.4)), step(vec3(0.04045), c));
            }
            vec3 toSrgb(vec3 c) {
                return mix(c * 12.92, 1.055 * pow(c, vec3(1.0 / 2.4)) - 0.055, step(vec3(0.0031308), c));
            }
            float ign(vec2 p) {
                return fract(52.9829189 * fract(dot(p, vec2(0.06711056, 0.00583715))));
            }
            void main() {
                // bajty ARGB little-endian = B,G,R,A: kanały zamieniane tutaj, nie na CPU
                vec4 c = texture(uImage, vUv);
                vec3 rgb = c.bgr;
                vec3 coord = (clamp(rgb, 0.0, 1.0) * (uSize - 1.0) + 0.5) / uSize;
                vec3 lut = texture(uLut, coord).rgb;
                vec3 graded = uIntensity >= 1.0 ? lut
                    : toSrgb(mix(toLinear(clamp(rgb, 0.0, 1.0)), toLinear(lut), uIntensity));
                float n = ign(gl_FragCoord.xy) + ign(gl_FragCoord.xy + vec2(37.0, 17.0)) - 1.0;
                graded = clamp(graded + uDither * n / 255.0, 0.0, 1.0);
                outColor = vec4(graded.bgr, c.a);
            }
        """.trimIndent()
    }
}

internal object GlProgram {
    fun build(vertex: String, fragment: String): Int {
        val vs = compile(GLES30.GL_VERTEX_SHADER, vertex)
        val fs = compile(GLES30.GL_FRAGMENT_SHADER, fragment)
        val program = GLES30.glCreateProgram()
        GLES30.glAttachShader(program, vs)
        GLES30.glAttachShader(program, fs)
        GLES30.glLinkProgram(program)
        GLES30.glDeleteShader(vs)
        GLES30.glDeleteShader(fs)
        val status = IntArray(1)
        GLES30.glGetProgramiv(program, GLES30.GL_LINK_STATUS, status, 0)
        check(status[0] != 0) { "Link programu: " + GLES30.glGetProgramInfoLog(program) }
        return program
    }

    private fun compile(type: Int, source: String): Int {
        val shader = GLES30.glCreateShader(type)
        GLES30.glShaderSource(shader, source)
        GLES30.glCompileShader(shader)
        val status = IntArray(1)
        GLES30.glGetShaderiv(shader, GLES30.GL_COMPILE_STATUS, status, 0)
        check(status[0] != 0) { "Kompilacja shadera: " + GLES30.glGetShaderInfoLog(shader) }
        return shader
    }
}
