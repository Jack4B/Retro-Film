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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Backend GLES 3.0: offscreen (pbuffer 1x1 + FBO), LUT jako tekstura 3D RGBA8 z GL_LINEAR
 * (sprzętowa interpolacja trójliniowa). Kontekst EGL tworzony na czas jednego wywołania.
 */
class GlesLutEngine : LutEngine {
    override val name: String = "gles3"

    override suspend fun apply(src: Bitmap, lut: Lut, intensity: Float): Bitmap = withContext(Dispatchers.Default) {
        val pixels = BitmapPixels.read(src)
        val out = EglSession().use { it.render(pixels, src.width, src.height, lut, intensity.coerceIn(0f, 1f)) }
        BitmapPixels.write(out, src.width, src.height)
    }

    companion object {
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

    fun render(pixels: IntArray, width: Int, height: Int, lut: Lut, intensity: Float): IntArray {
        val maxSize = IntArray(1)
        GLES30.glGetIntegerv(GLES30.GL_MAX_TEXTURE_SIZE, maxSize, 0)
        check(width <= maxSize[0] && height <= maxSize[0]) { "Obraz większy niż GL_MAX_TEXTURE_SIZE" }

        val program = GlProgram.build(VERTEX, FRAGMENT)
        val textures = IntArray(TEXTURE_COUNT)
        GLES30.glGenTextures(TEXTURE_COUNT, textures, 0)
        val fbo = IntArray(1)
        GLES30.glGenFramebuffers(1, fbo, 0)
        try {
            upload2d(textures[0], width, height, argbToRgba(pixels))
            upload3d(textures[1], lut)
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, textures[OUTPUT_TEX])
            GLES30.glTexImage2D(
                GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA8, width, height, 0,
                GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, null
            )
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fbo[0])
            GLES30.glFramebufferTexture2D(
                GLES30.GL_FRAMEBUFFER,
                GLES30.GL_COLOR_ATTACHMENT0,
                GLES30.GL_TEXTURE_2D,
                textures[OUTPUT_TEX],
                0
            )
            check(GLES30.glCheckFramebufferStatus(GLES30.GL_FRAMEBUFFER) == GLES30.GL_FRAMEBUFFER_COMPLETE) {
                "FBO niekompletny"
            }
            GLES30.glViewport(0, 0, width, height)
            GLES30.glUseProgram(program)
            GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, textures[0])
            GLES30.glActiveTexture(GLES30.GL_TEXTURE1)
            GLES30.glBindTexture(GLES30.GL_TEXTURE_3D, textures[1])
            GLES30.glUniform1i(GLES30.glGetUniformLocation(program, "uImage"), 0)
            GLES30.glUniform1i(GLES30.glGetUniformLocation(program, "uLut"), 1)
            GLES30.glUniform1f(GLES30.glGetUniformLocation(program, "uSize"), lut.size.toFloat())
            GLES30.glUniform1f(GLES30.glGetUniformLocation(program, "uIntensity"), intensity)
            GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, TRIANGLE_VERTICES)

            val out = ByteBuffer.allocateDirect(width * height * RGBA).order(ByteOrder.nativeOrder())
            GLES30.glPixelStorei(GLES30.GL_PACK_ALIGNMENT, 1)
            GLES30.glReadPixels(0, 0, width, height, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, out)
            check(GLES30.glGetError() == GLES30.GL_NO_ERROR) { "Błąd GL podczas renderu" }
            return rgbaToArgb(out, pixels)
        } finally {
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
            GLES30.glDeleteFramebuffers(1, fbo, 0)
            GLES30.glDeleteTextures(TEXTURE_COUNT, textures, 0)
            GLES30.glDeleteProgram(program)
        }
    }

    private fun upload2d(tex: Int, width: Int, height: Int, data: ByteBuffer) {
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, tex)
        params(GLES30.GL_TEXTURE_2D, GLES30.GL_NEAREST)
        GLES30.glPixelStorei(GLES30.GL_UNPACK_ALIGNMENT, 1)
        GLES30.glTexImage2D(
            GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA8, width, height, 0,
            GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, data
        )
    }

    private fun upload3d(tex: Int, lut: Lut) {
        val n = lut.size
        val buf = ByteBuffer.allocateDirect(n * n * n * RGBA).order(ByteOrder.nativeOrder())
        var i = 0
        while (i < lut.data.size) {
            buf.put(lut.data[i]).put(lut.data[i + 1]).put(lut.data[i + 2]).put(OPAQUE_BYTE)
            i += Lut.CHANNELS
        }
        buf.position(0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_3D, tex)
        params(GLES30.GL_TEXTURE_3D, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_3D, GLES30.GL_TEXTURE_WRAP_R, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glPixelStorei(GLES30.GL_UNPACK_ALIGNMENT, 1)
        GLES30.glTexImage3D(
            GLES30.GL_TEXTURE_3D, 0, GLES30.GL_RGBA8, n, n, n, 0,
            GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, buf
        )
    }

    private fun params(target: Int, filter: Int) {
        GLES30.glTexParameteri(target, GLES30.GL_TEXTURE_MIN_FILTER, filter)
        GLES30.glTexParameteri(target, GLES30.GL_TEXTURE_MAG_FILTER, filter)
        GLES30.glTexParameteri(target, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(target, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
    }

    private fun argbToRgba(pixels: IntArray): ByteBuffer {
        val buf = ByteBuffer.allocateDirect(pixels.size * RGBA).order(ByteOrder.nativeOrder())
        for (p in pixels) {
            buf.put((p shr SHIFT_R).toByte())
            buf.put((p shr SHIFT_G).toByte())
            buf.put(p.toByte())
            buf.put((p ushr SHIFT_A).toByte())
        }
        buf.position(0)
        return buf
    }

    /** Alfa przepisywana ze źródła (shader nie zmienia alfy, a unikamy straty precyzji). */
    private fun rgbaToArgb(buf: ByteBuffer, src: IntArray): IntArray {
        val out = IntArray(src.size)
        for (i in out.indices) {
            val r = buf.get().toInt() and BYTE_MASK
            val g = buf.get().toInt() and BYTE_MASK
            val b = buf.get().toInt() and BYTE_MASK
            buf.get()
            out[i] = (src[i] and ALPHA_MASK) or (r shl SHIFT_R) or (g shl SHIFT_G) or b
        }
        return out
    }

    override fun close() {
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
        const val TEXTURE_COUNT = 3
        const val OUTPUT_TEX = 2
        const val BYTE_MASK = 0xFF
        const val SHIFT_R = 16
        const val SHIFT_G = 8
        const val SHIFT_A = 24
        const val ALPHA_MASK = -0x1000000
        const val OPAQUE_BYTE: Byte = -1

        // Pełnoekranowy trójkąt bez bufora wierzchołków; v=0 to pierwszy wiersz bitmapy (glReadPixels czyta od dołu,
        // więc tekstura i FBO mają tę samą orientację – brak odbicia).
        val VERTEX = """
            #version 300 es
            out vec2 vUv;
            void main() {
                vec2 p = vec2(float((gl_VertexID << 1) & 2), float(gl_VertexID & 2));
                vUv = p;
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
            out vec4 outColor;
            void main() {
                vec4 c = texture(uImage, vUv);
                vec3 coord = (clamp(c.rgb, 0.0, 1.0) * (uSize - 1.0) + 0.5) / uSize;
                vec3 graded = texture(uLut, coord).rgb;
                outColor = vec4(mix(c.rgb, graded, uIntensity), c.a);
            }
        """.trimIndent()
    }
}

private object GlProgram {
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
