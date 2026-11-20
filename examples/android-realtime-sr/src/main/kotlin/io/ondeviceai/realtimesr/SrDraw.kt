package io.ondeviceai.realtimesr

import android.opengl.GLES11Ext
import android.opengl.GLES30

class SrDraw : AutoCloseable {

    class Source(val tex: Int, val fbo: Int, val width: Int, val height: Int)

    val oes: Int = createOes()

    private val captureProgram: Int
    private val capTexLoc: Int
    private val capMatrixLoc: Int

    private val windowProgram: Int
    private val winSrcLoc: Int
    private val winEnhLoc: Int
    private val winHasEnhLoc: Int
    private val winWinAspectLoc: Int
    private val winSrcAspectLoc: Int

    private val vao = IntArray(1)
    private val viewport = IntArray(4)
    private var closed = false

    init {
        captureProgram = createProgram(VERTEX_SHADER, CAPTURE_FRAGMENT)
        capTexLoc = GLES30.glGetUniformLocation(captureProgram, "uTex")
        capMatrixLoc = GLES30.glGetUniformLocation(captureProgram, "uMatrix")

        windowProgram = createProgram(VERTEX_SHADER, WINDOW_FRAGMENT)
        winSrcLoc = GLES30.glGetUniformLocation(windowProgram, "uSrc")
        winEnhLoc = GLES30.glGetUniformLocation(windowProgram, "uEnh")
        winHasEnhLoc = GLES30.glGetUniformLocation(windowProgram, "uHasEnh")
        winWinAspectLoc = GLES30.glGetUniformLocation(windowProgram, "uWinAspect")
        winSrcAspectLoc = GLES30.glGetUniformLocation(windowProgram, "uSrcAspect")

        GLES30.glGenVertexArrays(1, vao, 0)
    }

    fun allocate(width: Int, height: Int): Source {
        val tex = IntArray(1)
        GLES30.glGenTextures(1, tex, 0)
        val t = tex[0]
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, t)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexImage2D(
            GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA8, width, height, 0,
            GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, null
        )

        val fbo = IntArray(1)
        GLES30.glGenFramebuffers(1, fbo, 0)
        val f = fbo[0]
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, f)
        GLES30.glFramebufferTexture2D(
            GLES30.GL_FRAMEBUFFER, GLES30.GL_COLOR_ATTACHMENT0, GLES30.GL_TEXTURE_2D, t, 0
        )
        val status = GLES30.glCheckFramebufferStatus(GLES30.GL_FRAMEBUFFER)
        if (status != GLES30.GL_FRAMEBUFFER_COMPLETE) {
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
            GLES30.glDeleteFramebuffers(1, fbo, 0)
            GLES30.glDeleteTextures(1, tex, 0)
            throw IllegalStateException("SrDraw FBO incomplete: 0x${Integer.toHexString(status)}")
        }
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0)
        return Source(t, f, width, height)
    }

    fun capture(dst: Source, matrix: FloatArray) {
        require(matrix.size >= 16) { "matrix must contain at least 16 floats" }
        saveViewport()
        GLES30.glBindVertexArray(vao[0])
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, dst.fbo)
        GLES30.glViewport(0, 0, dst.width, dst.height)

        GLES30.glUseProgram(captureProgram)
        GLES30.glBindSampler(0, 0)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, oes)
        GLES30.glUniform1i(capTexLoc, 0)
        GLES30.glUniformMatrix4fv(capMatrixLoc, 1, false, matrix, 0)

        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)

        GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, 0)
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
        GLES30.glBindVertexArray(0)
        restoreViewport()
    }

    fun window(src: Source, enhanced: Int, winWidth: Int, winHeight: Int) {
        saveViewport()
        GLES30.glBindVertexArray(vao[0])
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
        GLES30.glViewport(0, 0, winWidth, winHeight)

        GLES30.glUseProgram(windowProgram)
        GLES30.glBindSampler(0, 0)
        GLES30.glBindSampler(1, 0)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, src.tex)
        GLES30.glUniform1i(winSrcLoc, 0)

        if (enhanced != 0) {
            GLES30.glActiveTexture(GLES30.GL_TEXTURE1)
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, enhanced)
            GLES30.glUniform1i(winEnhLoc, 1)
            GLES30.glUniform1i(winHasEnhLoc, 1)
        } else {
            GLES30.glUniform1i(winHasEnhLoc, 0)
        }

        val winAspect = winWidth.toFloat() / winHeight.toFloat()
        val srcAspect = src.width.toFloat() / src.height.toFloat()
        GLES30.glUniform1f(winWinAspectLoc, winAspect)
        GLES30.glUniform1f(winSrcAspectLoc, srcAspect)

        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)

        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindVertexArray(0)
        restoreViewport()
    }

    fun release(src: Source) {
        val f = intArrayOf(src.fbo)
        val t = intArrayOf(src.tex)
        GLES30.glDeleteFramebuffers(1, f, 0)
        GLES30.glDeleteTextures(1, t, 0)
    }

    override fun close() {
        if (closed) return
        closed = true
        GLES30.glDeleteProgram(captureProgram)
        GLES30.glDeleteProgram(windowProgram)
        GLES30.glDeleteVertexArrays(1, vao, 0)
        GLES30.glDeleteTextures(1, intArrayOf(oes), 0)
    }

    private fun saveViewport() {
        GLES30.glGetIntegerv(GLES30.GL_VIEWPORT, viewport, 0)
    }

    private fun restoreViewport() {
        GLES30.glViewport(viewport[0], viewport[1], viewport[2], viewport[3])
    }

    private fun createOes(): Int {
        val t = IntArray(1)
        GLES30.glGenTextures(1, t, 0)
        val name = t[0]
        GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, name)
        GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, 0)
        return name
    }

    private fun compileShader(type: Int, source: String): Int {
        val shader = GLES30.glCreateShader(type)
        if (shader == 0) throw IllegalStateException("glCreateShader failed ($type)")
        GLES30.glShaderSource(shader, source)
        GLES30.glCompileShader(shader)
        val ok = IntArray(1)
        GLES30.glGetShaderiv(shader, GLES30.GL_COMPILE_STATUS, ok, 0)
        if (ok[0] == 0) {
            val log = GLES30.glGetShaderInfoLog(shader)
            GLES30.glDeleteShader(shader)
            throw IllegalStateException("Shader compile failed: $log")
        }
        return shader
    }

    private fun createProgram(vsSource: String, fsSource: String): Int {
        val vs = compileShader(GLES30.GL_VERTEX_SHADER, vsSource)
        val fs = compileShader(GLES30.GL_FRAGMENT_SHADER, fsSource)
        val p = GLES30.glCreateProgram()
        if (p == 0) {
            GLES30.glDeleteShader(vs)
            GLES30.glDeleteShader(fs)
            throw IllegalStateException("glCreateProgram failed")
        }
        GLES30.glAttachShader(p, vs)
        GLES30.glAttachShader(p, fs)
        GLES30.glLinkProgram(p)
        GLES30.glDeleteShader(vs)
        GLES30.glDeleteShader(fs)
        val ok = IntArray(1)
        GLES30.glGetProgramiv(p, GLES30.GL_LINK_STATUS, ok, 0)
        if (ok[0] == 0) {
            val log = GLES30.glGetProgramInfoLog(p)
            GLES30.glDeleteProgram(p)
            throw IllegalStateException("Program link failed: $log")
        }
        return p
    }

    companion object {
        private val VERTEX_SHADER = """
            #version 300 es
            precision highp float;
            out vec2 vUv;
            void main() {
                float x = float((gl_VertexID << 1) & 2);
                float y = float(gl_VertexID & 2);
                vUv = vec2(x, y) * 0.5;
                gl_Position = vec4(vec2(x, y) - 1.0, 0.0, 1.0);
            }
        """.trimIndent()

        private val CAPTURE_FRAGMENT = """
            #version 300 es
            #extension GL_OES_EGL_image_external_essl3 : require
            precision highp float;
            precision highp samplerExternalOES;
            in vec2 vUv;
            uniform samplerExternalOES uTex;
            uniform mat4 uMatrix;
            out vec4 fragColor;
            void main() {
                vec2 st = (uMatrix * vec4(vUv, 0.0, 1.0)).xy;
                fragColor = vec4(texture(uTex, st).rgb, 1.0);
            }
        """.trimIndent()

        private val WINDOW_FRAGMENT = """
            #version 300 es
            precision highp float;
            precision highp sampler2D;
            in vec2 vUv;
            uniform sampler2D uSrc;
            uniform sampler2D uEnh;
            uniform int uHasEnh;
            uniform float uWinAspect;
            uniform float uSrcAspect;
            out vec4 fragColor;
            void main() {
                vec2 scale = vec2(1.0);
                if (uWinAspect > uSrcAspect) {
                    scale.x = uSrcAspect / uWinAspect;
                } else {
                    scale.y = uWinAspect / uSrcAspect;
                }
                vec2 uv = (vUv - 0.5) / scale + 0.5;
                if (uv.x < 0.0 || uv.x > 1.0 || uv.y < 0.0 || uv.y > 1.0) {
                    fragColor = vec4(0.0, 0.0, 0.0, 1.0);
                    return;
                }
                vec3 rgb = texture(uSrc, uv).rgb;
                if (uHasEnh == 1) {
                    float ehY = texture(uEnh, uv).r;
                    float y = dot(rgb, vec3(0.2126, 0.7152, 0.0722));
                    rgb = clamp(rgb + (ehY - y), 0.0, 1.0);
                }
                fragColor = vec4(rgb, 1.0);
            }
        """.trimIndent()
    }
}
