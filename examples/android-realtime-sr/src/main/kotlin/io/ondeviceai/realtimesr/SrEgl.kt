package io.ondeviceai.realtimesr

import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLExt
import android.opengl.EGLSurface
import android.view.Surface

class SrEgl(window: Surface) : AutoCloseable {

    private var display: EGLDisplay = EGL14.EGL_NO_DISPLAY
    private var config: EGLConfig? = null
    private var renderContext: EGLContext = EGL14.EGL_NO_CONTEXT
    private var workerContext: EGLContext = EGL14.EGL_NO_CONTEXT
    private var windowSurface: EGLSurface = EGL14.EGL_NO_SURFACE
    private var workerSurface: EGLSurface = EGL14.EGL_NO_SURFACE
    private var ownsDisplay = false
    private var released = false

    init {
        try {
            display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
            if (display == EGL14.EGL_NO_DISPLAY) {
                fail("eglGetDisplay")
            }

            val version = IntArray(2)
            if (!EGL14.eglInitialize(display, version, 0, version, 1)) {
                fail("eglInitialize")
            }
            ownsDisplay = true

            // GLES is Android's default, but bind explicitly where supported.
            EGL14.eglBindAPI(EGL14.EGL_OPENGL_ES_API)

            config = chooseConfig()

            // GLES3 context (client version 3); failure is reported cleanly.
            renderContext = createContext(EGL14.EGL_NO_CONTEXT)

            // The Android window is borrowed; never released by this class.
            windowSurface = EGL14.eglCreateWindowSurface(
                display, config, window, WINDOW_ATTRIBS, 0
            )
            if (windowSurface == EGL14.EGL_NO_SURFACE) {
                fail("eglCreateWindowSurface")
            }

            workerSurface = EGL14.eglCreatePbufferSurface(
                display, config, PBUFFER_ATTRIBS, 0
            )
            if (workerSurface == EGL14.EGL_NO_SURFACE) {
                fail("eglCreatePbufferSurface")
            }

            // Worker context shares textures with the render context.
            workerContext = createContext(renderContext)
        } catch (t: Throwable) {
            releaseInternal()
            throw t
        }
    }

    fun renderCurrent() {
        if (!EGL14.eglMakeCurrent(display, windowSurface, windowSurface, renderContext)) {
            fail("eglMakeCurrent(render)")
        }
    }

    fun workerCurrent() {
        if (!EGL14.eglMakeCurrent(display, workerSurface, workerSurface, workerContext)) {
            fail("eglMakeCurrent(worker)")
        }
    }

    fun releaseWorker() {
        EGL14.eglMakeCurrent(
            display,
            EGL14.EGL_NO_SURFACE,
            EGL14.EGL_NO_SURFACE,
            EGL14.EGL_NO_CONTEXT
        )
    }

    fun swap(targetNs: Long): Boolean {
        EGLExt.eglPresentationTimeANDROID(display, windowSurface, targetNs)
        return EGL14.eglSwapBuffers(display, windowSurface)
    }

    fun width(): Int = queryWindow(EGL14.EGL_WIDTH)

    fun height(): Int = queryWindow(EGL14.EGL_HEIGHT)

    override fun close() {
        releaseInternal()
    }

    private fun queryWindow(what: Int): Int {
        val out = IntArray(1)
        if (!EGL14.eglQuerySurface(display, windowSurface, what, out, 0)) {
            fail("eglQuerySurface")
        }
        return out[0]
    }

    private fun chooseConfig(): EGLConfig {
        val attribs = intArrayOf(
            EGL14.EGL_SURFACE_TYPE,
            EGL14.EGL_WINDOW_BIT or EGL14.EGL_PBUFFER_BIT,
            EGL14.EGL_RENDERABLE_TYPE, EGL_ES3_BIT,
            EGL14.EGL_RED_SIZE, 8,
            EGL14.EGL_GREEN_SIZE, 8,
            EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_NONE
        )
        val configs = arrayOfNulls<EGLConfig>(1)
        val count = IntArray(1)
        if (!EGL14.eglChooseConfig(display, attribs, 0, configs, 0, configs.size, count, 0) ||
            count[0] < 1 || configs[0] == null
        ) {
            fail("eglChooseConfig")
        }
        return configs[0]!!
    }

    private fun createContext(share: EGLContext): EGLContext {
        val attribs = intArrayOf(
            EGL14.EGL_CONTEXT_CLIENT_VERSION, 3,
            EGL14.EGL_NONE
        )
        val ctx = EGL14.eglCreateContext(display, config, share, attribs, 0)
        if (ctx == EGL14.EGL_NO_CONTEXT) {
            fail("eglCreateContext(GLES3)")
        }
        return ctx
    }

    private fun releaseInternal() {
        if (released) return
        released = true
        if (display == EGL14.EGL_NO_DISPLAY) return
        try {
            EGL14.eglMakeCurrent(
                display,
                EGL14.EGL_NO_SURFACE,
                EGL14.EGL_NO_SURFACE,
                EGL14.EGL_NO_CONTEXT
            )
            if (windowSurface != EGL14.EGL_NO_SURFACE) {
                EGL14.eglDestroySurface(display, windowSurface)
                windowSurface = EGL14.EGL_NO_SURFACE
            }
            if (workerSurface != EGL14.EGL_NO_SURFACE) {
                EGL14.eglDestroySurface(display, workerSurface)
                workerSurface = EGL14.EGL_NO_SURFACE
            }
            if (workerContext != EGL14.EGL_NO_CONTEXT) {
                EGL14.eglDestroyContext(display, workerContext)
                workerContext = EGL14.EGL_NO_CONTEXT
            }
            if (renderContext != EGL14.EGL_NO_CONTEXT) {
                EGL14.eglDestroyContext(display, renderContext)
                renderContext = EGL14.EGL_NO_CONTEXT
            }
        } finally {
            // Tear down only display state this object initialized; never touch
            // EGL state that may belong to unrelated contexts in this process.
            if (ownsDisplay) {
                EGL14.eglTerminate(display)
                ownsDisplay = false
            }
            display = EGL14.EGL_NO_DISPLAY
            config = null
        }
    }

    private fun fail(op: String): Nothing {
        val err = EGL14.eglGetError()
        throw IllegalStateException("$op failed: EGL error 0x${Integer.toHexString(err)}")
    }

    private companion object {
        const val EGL_ES3_BIT = 0x40
        val WINDOW_ATTRIBS = intArrayOf(EGL14.EGL_NONE)
        val PBUFFER_ATTRIBS = intArrayOf(
            EGL14.EGL_WIDTH, 1,
            EGL14.EGL_HEIGHT, 1,
            EGL14.EGL_NONE
        )
    }
}
