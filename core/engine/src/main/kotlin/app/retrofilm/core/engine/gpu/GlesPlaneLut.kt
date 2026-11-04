package app.retrofilm.core.engine.gpu

import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.opengl.GLES30
import android.util.Half
import app.retrofilm.core.engine.GlProgram
import app.retrofilm.core.engine.Lut
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.concurrent.Executors

/**
 * K: zapasowy backend GPU – GLES 3.0 z teksturami i FBO RGBA32F (wymaga `GL_EXT_color_buffer_float`), LUT jako
 * tekstura 3D RGB16F czytana `texelFetch` z interpolacją czworościenną w shaderze (jak CPU; sprzętowe
 * filtrowanie trójliniowe dawałoby inne wyniki). Kontekst EGL żyje na własnym wątku.
 */
class GlesPlaneLut : PlaneLutKernel {
    override val name = "gpu-gles-f32" // wejście F32, wyjście F16 (L)

    private val thread = Executors.newSingleThreadExecutor { r ->
        Thread(r, "retrofilm-gles-f32").apply {
            isDaemon =
                true
        }
    }
    private var gl: Gl? = null
    override val times = KernelTimes()

    @Suppress("LongParameterList")
    override fun apply(r: FloatArray, g: FloatArray, b: FloatArray, from: Int, count: Int, lut: Lut, intensity: Float) {
        if (count <= 0) return
        thread.submit {
            val session = gl ?: Gl().also { gl = it }
            session.times.reset()
            session.run(r, g, b, from, count, lut, intensity.coerceIn(0f, 1f))
            times.uploadNs += session.times.uploadNs
            times.drawNs += session.times.drawNs
            times.readbackNs += session.times.readbackNs
            times.calls++
        }.get()
    }

    override fun close() {
        thread.submit {
            gl?.close()
            gl = null
        }.get()
        thread.shutdown()
    }

    private class Gl : AutoCloseable {
        private val display: EGLDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        private val context: EGLContext
        private val surface: EGLSurface
        private val program: Int
        private var lutKey: Lut? = null
        private var lutTex = 0

        init {
            val version = IntArray(2)
            check(EGL14.eglInitialize(display, version, 0, version, 1)) { "eglInitialize" }
            val configs = arrayOfNulls<EGLConfig>(1)
            val n = IntArray(1)
            val attrs = intArrayOf(
                EGL14.EGL_RENDERABLE_TYPE,
                ES3_BIT,
                EGL14.EGL_SURFACE_TYPE,
                EGL14.EGL_PBUFFER_BIT,
                EGL14.EGL_NONE
            )
            check(EGL14.eglChooseConfig(display, attrs, 0, configs, 0, 1, n, 0) && n[0] > 0) { "Brak EGL ES3" }
            context = EGL14.eglCreateContext(
                display,
                configs[0],
                EGL14.EGL_NO_CONTEXT,
                intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, ES_VERSION, EGL14.EGL_NONE),
                0
            )
            check(context != EGL14.EGL_NO_CONTEXT) { "eglCreateContext" }
            surface = EGL14.eglCreatePbufferSurface(
                display,
                configs[0],
                intArrayOf(EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE),
                0
            )
            check(EGL14.eglMakeCurrent(display, surface, surface, context)) { "eglMakeCurrent" }
            val ext = GLES30.glGetString(GLES30.GL_EXTENSIONS).orEmpty()
            check("GL_EXT_color_buffer_float" in ext) { "Brak GL_EXT_color_buffer_float" }
            program = GlProgram.build(VERTEX, FRAGMENT)
        }

        // L: zasoby trwałe między pasami (wcześniej tekstury, FBO i bufor tworzone przy każdym pasie)
        private val tex = IntArray(2)
        private val fbo = IntArray(1)
        private var texW = 0
        private var texH = 0
        private var buf: ByteBuffer? = null
        private var scratch = FloatArray(0)
        val times = KernelTimes()

        @Suppress("LongParameterList")
        fun run(r: FloatArray, g: FloatArray, b: FloatArray, from: Int, count: Int, lut: Lut, intensity: Float) {
            check(EGL14.eglMakeCurrent(display, surface, surface, context)) { "eglMakeCurrent" }
            val t0 = System.nanoTime()
            val grid = PlaneGrid(count)
            val w = grid.width
            val h = grid.height
            ensure(w, h)
            val n = w * h * RGBA
            if (scratch.size < n) scratch = FloatArray(n)
            val a = scratch
            for (i in 0 until count) {
                val at = i * RGBA
                a[at] = r[from + i] / MAX
                a[at + 1] = g[from + i] / MAX
                a[at + 2] = b[from + i] / MAX
            }
            val bytes = buf!!
            val f: FloatBuffer = bytes.asFloatBuffer()
            f.put(a, 0, n)
            bytes.position(0)
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, tex[0])
            GLES30.glTexSubImage2D(GLES30.GL_TEXTURE_2D, 0, 0, 0, w, h, GLES30.GL_RGBA, GLES30.GL_FLOAT, bytes)
            val lutTexture = lutTexture(lut)
            val t1 = System.nanoTime()
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fbo[0])
            draw(tex[0], lutTexture, lut.size, intensity, w, h)
            GLES30.glFinish()
            val t2 = System.nanoTime()
            bytes.position(0)
            GLES30.glReadPixels(0, 0, w, h, GLES30.GL_RGBA, GLES30.GL_FLOAT, bytes)
            val err = GLES30.glGetError()
            check(err == GLES30.GL_NO_ERROR) { "Błąd GL 0x${err.toString(HEX)}" }
            bytes.asFloatBuffer().get(a, 0, n)
            for (i in 0 until count) {
                val at = i * RGBA
                r[from + i] = a[at].coerceIn(0f, 1f) * MAX
                g[from + i] = a[at + 1].coerceIn(0f, 1f) * MAX
                b[from + i] = a[at + 2].coerceIn(0f, 1f) * MAX
            }
            val t3 = System.nanoTime()
            times.uploadNs += t1 - t0
            times.drawNs += t2 - t1
            times.readbackNs += t3 - t2
            times.calls++
        }

        /** Tekstura wejścia RGBA32F i wyjście RGBA16F (FBO) o co najmniej [w]×[h]; realokacja tylko przy wzroście. */
        private fun ensure(w: Int, h: Int) {
            if (w == texW && h <= texH) return
            if (texW != 0) {
                GLES30.glDeleteFramebuffers(1, fbo, 0)
                GLES30.glDeleteTextures(2, tex, 0)
            }
            GLES30.glGenTextures(2, tex, 0)
            GLES30.glGenFramebuffers(1, fbo, 0)
            texture2d(tex[0], GLES30.GL_RGBA32F, GLES30.GL_FLOAT, w, h)
            texture2d(tex[1], GLES30.GL_RGBA16F, GLES30.GL_HALF_FLOAT, w, h)
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fbo[0])
            GLES30.glFramebufferTexture2D(
                GLES30.GL_FRAMEBUFFER,
                GLES30.GL_COLOR_ATTACHMENT0,
                GLES30.GL_TEXTURE_2D,
                tex[1],
                0
            )
            check(GLES30.glCheckFramebufferStatus(GLES30.GL_FRAMEBUFFER) == GLES30.GL_FRAMEBUFFER_COMPLETE) {
                "FBO RGBA16F niekompletny"
            }
            texW = w
            texH = h
            buf = ByteBuffer.allocateDirect(w * h * RGBA * Float.SIZE_BYTES).order(ByteOrder.nativeOrder())
        }

        @Suppress("LongParameterList")
        private fun draw(input: Int, lutTexture: Int, size: Int, intensity: Float, w: Int, h: Int) {
            GLES30.glUseProgram(program)
            GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, input)
            GLES30.glActiveTexture(GLES30.GL_TEXTURE1)
            GLES30.glBindTexture(GLES30.GL_TEXTURE_3D, lutTexture)
            GLES30.glUniform1i(GLES30.glGetUniformLocation(program, "uImage"), 0)
            GLES30.glUniform1i(GLES30.glGetUniformLocation(program, "uLut"), 1)
            GLES30.glUniform1f(GLES30.glGetUniformLocation(program, "size"), size.toFloat())
            GLES30.glUniform1f(GLES30.glGetUniformLocation(program, "intensity"), intensity)
            GLES30.glViewport(0, 0, w, h)
            GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, TRIANGLE)
        }

        @Suppress("LongParameterList")
        private fun texture2d(t: Int, internal: Int, type: Int, w: Int, h: Int) {
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, t)
            nearest(GLES30.GL_TEXTURE_2D)
            GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, internal, w, h, 0, GLES30.GL_RGBA, type, null)
        }

        @Suppress("NestedBlockDepth")
        private fun lutTexture(lut: Lut): Int {
            if (lutKey === lut) return lutTex
            if (lutTex != 0) GLES30.glDeleteTextures(1, intArrayOf(lutTex), 0)
            val n = lut.size
            val buf = ByteBuffer.allocateDirect(n * n * n * Lut.CHANNELS * 2).order(ByteOrder.nativeOrder())
            val s = buf.asShortBuffer()
            var i = 0
            for (bb in 0 until n) {
                for (gg in 0 until n) {
                    for (rr in 0 until n) {
                        for (c in 0 until Lut.CHANNELS) s.put(i++, Half.toHalf(lut.nodeF(rr, gg, bb, c) / MAX))
                    }
                }
            }
            val t = IntArray(1)
            GLES30.glGenTextures(1, t, 0)
            GLES30.glBindTexture(GLES30.GL_TEXTURE_3D, t[0])
            nearest(GLES30.GL_TEXTURE_3D)
            GLES30.glPixelStorei(GLES30.GL_UNPACK_ALIGNMENT, 2)
            GLES30.glTexImage3D(
                GLES30.GL_TEXTURE_3D, 0, GLES30.GL_RGB16F, n, n, n, 0, GLES30.GL_RGB, GLES30.GL_HALF_FLOAT, buf
            )
            lutTex = t[0]
            lutKey = lut
            return lutTex
        }

        private fun nearest(target: Int) {
            GLES30.glTexParameteri(target, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_NEAREST)
            GLES30.glTexParameteri(target, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_NEAREST)
            GLES30.glTexParameteri(target, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
            GLES30.glTexParameteri(target, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
        }

        override fun close() {
            if (lutTex != 0) GLES30.glDeleteTextures(1, intArrayOf(lutTex), 0)
            if (texW != 0) {
                GLES30.glDeleteFramebuffers(1, fbo, 0)
                GLES30.glDeleteTextures(2, tex, 0)
            }
            GLES30.glDeleteProgram(program)
            EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
            EGL14.eglDestroySurface(display, surface)
            EGL14.eglDestroyContext(display, context)
        }
    }

    private companion object {
        const val ES3_BIT = 0x40
        const val ES_VERSION = 3
        const val RGBA = 4
        const val MAX = 255f
        const val TRIANGLE = 3
        const val HEX = 16

        val VERTEX = """
            #version 300 es
            void main() {
                vec2 p = vec2(float((gl_VertexID << 1) & 2), float(gl_VertexID & 2));
                gl_Position = vec4(p * 2.0 - 1.0, 0.0, 1.0);
            }
        """.trimIndent()

        val FRAGMENT = """
            #version 300 es
            precision highp float;
            precision highp sampler2D;
            precision highp sampler3D;
            uniform sampler2D uImage;
            uniform sampler3D uLut;
            uniform float size;
            uniform float intensity;
            out vec4 outColor;
            ${LutShaderMath.srgb("vec3")}
            vec3 node(float r, float g, float b) {
                return texelFetch(uLut, ivec3(int(r), int(g), int(b)), 0).rgb;
            }
            void main() {
                vec3 src = texelFetch(uImage, ivec2(gl_FragCoord.xy), 0).rgb;
                vec3 rgb = clamp(src, 0.0, 1.0);
                ${LutShaderMath.tetra("vec3")}
                ${LutShaderMath.mix("vec3")}
                outColor = vec4(outRgb, 1.0);
            }
        """.trimIndent()
    }
}
