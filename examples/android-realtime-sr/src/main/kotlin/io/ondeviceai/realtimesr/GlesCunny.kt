package io.ondeviceai.realtimesr

import android.content.res.AssetManager
import android.opengl.GLES20
import android.opengl.GLES30
import android.opengl.GLES31

class GlesCunny(val assets: AssetManager, private val spatialOnly: Boolean = false) : AutoCloseable {
    private var width = 0
    private var height = 0
    private var programs = IntArray(0)
    private var textures = IntArray(0)
    private var sampler = 0
    private var inputLocations = IntArray(0)
    private var baseLocations = IntArray(0)

    fun prepare(width: Int, height: Int) {
        close()
        require(width > 0 && height > 0) { "Image dimensions must be positive" }
        checkGl("prepare start")
        val version = IntArray(1)
        val minor = IntArray(1)
        GLES30.glGetIntegerv(GLES30.GL_MAJOR_VERSION, version, 0)
        GLES30.glGetIntegerv(GLES30.GL_MINOR_VERSION, minor, 0)
        checkGl("query GLES version")
        check(version[0] > 3 || version[0] == 3 && minor[0] >= 1) {
            "OpenGL ES 3.1 is required"
        }
        val limits = IntArray(1)
        GLES30.glGetIntegerv(GLES30.GL_MAX_TEXTURE_SIZE, limits, 0)
        val maxTexture = limits[0]
        GLES31.glGetIntegerv(GLES31.GL_MAX_IMAGE_UNITS, limits, 0)
        checkGl("query GLES capabilities")
        check(limits[0] >= 1) { "At least one image unit is required" }
        check(width <= maxTexture / 2 && height <= maxTexture / 2) {
            "Image dimensions exceed the RGBA16F texture size limit ($maxTexture)"
        }
        val groups = IntArray(1)
        GLES31.glGetIntegeri_v(GLES31.GL_MAX_COMPUTE_WORK_GROUP_COUNT, 0, groups, 0)
        val maxGroupsX = groups[0]
        GLES31.glGetIntegeri_v(GLES31.GL_MAX_COMPUTE_WORK_GROUP_COUNT, 1, groups, 0)
        checkGl("query compute limits")
        check((width + 7) / 8 <= maxGroupsX && (height + 7) / 8 <= groups[0]) {
            "Image dimensions exceed the compute work-group limits"
        }

        val stageCount = if (spatialOnly) 1 else 3
        val newPrograms = IntArray(stageCount)
        val newTextures = IntArray(stageCount)
        var newSampler = 0
        try {
            if (spatialOnly) {
                newPrograms[0] = loadProgram("sr/spatial.comp")
                newTextures[0] = createTexture(width * 2, height * 2)
            } else {
                for (i in 0..2) newPrograms[i] = loadProgram("sr/cunny$i.comp")
                newTextures[0] = createTexture(width * 2, height)
                newTextures[1] = createTexture(width * 2, height)
                newTextures[2] = createTexture(width * 2, height * 2)
            }
            newSampler = run {
                val id = IntArray(1)
                GLES30.glGenSamplers(1, id, 0)
                id[0]
            }
            checkGl("create sampler")
            check(newSampler != 0) { "Could not create texture sampler" }
            GLES30.glSamplerParameteri(newSampler, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
            GLES30.glSamplerParameteri(newSampler, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
            GLES30.glSamplerParameteri(newSampler, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
            GLES30.glSamplerParameteri(newSampler, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
            val inputs = IntArray(stageCount)
            val bases = IntArray(stageCount)
            if (spatialOnly) {
                inputs[0] = GLES20.glGetUniformLocation(newPrograms[0], "uInput")
                check(inputs[0] >= 0) { "Shader sr/spatial.comp must declare uInput" }
            } else {
                for (i in 0..2) {
                    inputs[i] = GLES20.glGetUniformLocation(newPrograms[i], "uInput")
                    bases[i] = GLES20.glGetUniformLocation(newPrograms[i], "uBase")
                    check(inputs[i] >= 0 && (i != 2 || bases[i] >= 0)) {
                        "Shader sr/cunny$i.comp must declare uInput and uBase"
                    }
                }
            }
            checkGl("prepare shaders and textures")
            this.width = width
            this.height = height
            programs = newPrograms
            textures = newTextures
            sampler = newSampler
            inputLocations = inputs
            baseLocations = bases
        } catch (e: Exception) {
            newPrograms.forEach { if (it != 0) GLES20.glDeleteProgram(it) }
            newTextures.forEach { if (it != 0) GLES30.glDeleteTextures(1, intArrayOf(it), 0) }
            if (newSampler != 0) GLES30.glDeleteSamplers(1, intArrayOf(newSampler), 0)
            throw e
        }
    }

    fun dispatch(rgbTex: Int): Int {
        require(rgbTex != 0) { "Input texture must be valid" }
        if (spatialOnly) {
            check(programs.size == 1 && textures.size == 1) { "Call prepare before dispatch" }
            GLES20.glUseProgram(programs[0])
            GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, rgbTex)
            GLES30.glBindSampler(0, sampler)
            GLES20.glUniform1i(inputLocations[0], 0)
            GLES31.glBindImageTexture(0, textures[0], 0, false, 0, GLES31.GL_WRITE_ONLY, GLES30.GL_RGBA16F)
            checkGl("bind spatial shader")
            GLES31.glDispatchCompute((width + 7) / 8, (height + 7) / 8, 1)
            GLES31.glMemoryBarrier(
                GLES31.GL_SHADER_IMAGE_ACCESS_BARRIER_BIT or GLES31.GL_TEXTURE_FETCH_BARRIER_BIT
            )
            checkGl("dispatch spatial shader")
            return textures[0]
        }

        check(programs.size == 3 && textures.size == 3) { "Call prepare before dispatch" }
        for (i in 0..2) {
            GLES20.glUseProgram(programs[i])
            GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, if (i == 0) rgbTex else textures[i - 1])
            GLES30.glBindSampler(0, sampler)
            GLES30.glActiveTexture(GLES30.GL_TEXTURE1)
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, rgbTex)
            GLES30.glBindSampler(1, sampler)
            GLES20.glUniform1i(inputLocations[i], 0)
            GLES20.glUniform1i(baseLocations[i], 1)
            GLES31.glBindImageTexture(0, textures[i], 0, false, 0, GLES31.GL_WRITE_ONLY, GLES30.GL_RGBA16F)
            checkGl("bind CuNNy stage $i")
            GLES31.glDispatchCompute((width + 7) / 8, (height + 7) / 8, 1)
            GLES31.glMemoryBarrier(
                GLES31.GL_SHADER_IMAGE_ACCESS_BARRIER_BIT or GLES31.GL_TEXTURE_FETCH_BARRIER_BIT
            )
            checkGl("dispatch CuNNy stage $i")
        }
        return textures[2]
    }

    override fun close() {
        programs.forEach { if (it != 0) GLES20.glDeleteProgram(it) }
        textures.forEach { if (it != 0) GLES30.glDeleteTextures(1, intArrayOf(it), 0) }
        if (sampler != 0) GLES30.glDeleteSamplers(1, intArrayOf(sampler), 0)
        programs = IntArray(0)
        textures = IntArray(0)
        sampler = 0
        inputLocations = IntArray(0)
        baseLocations = IntArray(0)
        width = 0
        height = 0
        checkGl("close")
    }

    private fun createTexture(w: Int, h: Int): Int {
        val id = IntArray(1)
        GLES30.glGenTextures(1, id, 0)
        check(id[0] != 0) { "Could not allocate output texture" }
        try {
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, id[0])
            GLES30.glTexStorage2D(GLES30.GL_TEXTURE_2D, 1, GLES30.GL_RGBA16F, w, h)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
            checkGl("allocate ${w}x$h RGBA16F texture")
            return id[0]
        } catch (e: Exception) {
            GLES30.glDeleteTextures(1, id, 0)
            throw e
        }
    }

    private fun loadProgram(path: String): Int {
        val source = assets.open(path).bufferedReader().use { it.readText() }
        val shader = GLES20.glCreateShader(GLES31.GL_COMPUTE_SHADER)
        check(shader != 0) { "Could not create compute shader: $path" }
        try {
            GLES20.glShaderSource(shader, source)
            GLES20.glCompileShader(shader)
            val status = IntArray(1)
            GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0)
            checkGl("compile $path")
            check(status[0] != 0) { "Compute shader $path failed: ${GLES20.glGetShaderInfoLog(shader)}" }
            val program = GLES20.glCreateProgram()
            check(program != 0) { "Could not create program for $path" }
            GLES20.glAttachShader(program, shader)
            GLES20.glLinkProgram(program)
            GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, status, 0)
            checkGl("link $path")
            if (status[0] == 0) {
                val log = GLES20.glGetProgramInfoLog(program)
                GLES20.glDeleteProgram(program)
                error("Compute program $path failed: $log")
            }
            return program
        } finally {
            GLES20.glDeleteShader(shader)
        }
    }

    private fun checkGl(stage: String) {
        val errors = mutableListOf<String>()
        var error = GLES20.glGetError()
        while (error != GLES20.GL_NO_ERROR) {
            errors += "0x${error.toString(16)}"
            error = GLES20.glGetError()
        }
        check(errors.isEmpty()) { "OpenGL error during $stage: ${errors.joinToString()}" }
    }
}
