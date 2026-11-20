#include <EGL/egl.h>
#include <EGL/eglext.h>
#include <GLES3/gl31.h>
#include <cstdio>
#include <cstdlib>
#include <cstddef>
#include <cstring>

#ifndef EGL_OPENGL_ES3_BIT_KHR
#define EGL_OPENGL_ES3_BIT_KHR 0x00000040
#endif
#ifndef GL_COMPUTE_SHADER
#define GL_COMPUTE_SHADER 0x91B9
#endif

static bool readFile(const char* path, char** out, size_t* n) {
    FILE* f = fopen(path, "rb");
    if (!f) return false;
    if (fseek(f, 0, SEEK_END) != 0) { fclose(f); return false; }
    long len = ftell(f);
    if (len < 0) { fclose(f); return false; }
    rewind(f);
    char* buf = (char*)malloc((size_t)len + 1);
    if (!buf) { fclose(f); return false; }
    size_t got = fread(buf, 1, (size_t)len, f);
    fclose(f);
    if (got != (size_t)len) { free(buf); return false; }
    buf[len] = '\0';
    *out = buf; if (n != nullptr) *n = (size_t)len;
    return true;
}

static bool glOk(const char* what) {
    GLenum e = glGetError();
    if (e != GL_NO_ERROR) {
        fprintf(stderr, "GL error at %s: 0x%x\n", what, (unsigned)e);
        return false;
    }
    return true;
}

static GLuint compile(GLenum type, const char* src, const char* name) {
    GLuint s = glCreateShader(type);
    glShaderSource(s, 1, &src, nullptr);
    glCompileShader(s);
    GLint ok = 0;
    glGetShaderiv(s, GL_COMPILE_STATUS, &ok);
    if (!ok) {
        char log[2048]; GLsizei l = 0;
        glGetShaderInfoLog(s, (GLsizei)sizeof(log), &l, log);
        fprintf(stderr, "%s compile failed: %s\n", name, log);
        glDeleteShader(s);
        return 0;
    }
    return s;
}

static GLuint link(GLuint a, GLuint b, const char* name) {
    GLuint p = glCreateProgram();
    glAttachShader(p, a);
    if (b) glAttachShader(p, b);
    glLinkProgram(p);
    GLint ok = 0;
    glGetProgramiv(p, GL_LINK_STATUS, &ok);
    if (!ok) {
        char log[2048]; GLsizei l = 0;
        glGetProgramInfoLog(p, (GLsizei)sizeof(log), &l, log);
        fprintf(stderr, "%s link failed: %s\n", name, log);
        glDeleteProgram(p);
        return 0;
    }
    return p;
}

static GLuint computeProgram(const char* src, const char* name) {
    GLuint s = compile(GL_COMPUTE_SHADER, src, name);
    if (!s) return 0;
    GLuint p = link(s, 0, name);
    glDeleteShader(s);
    return p;
}

static GLuint makeTex(GLenum ifmt, int w, int h, const void* data,
                      GLenum fmt, GLenum type, bool linear) {
    GLuint t; glGenTextures(1, &t);
    glBindTexture(GL_TEXTURE_2D, t);
    glPixelStorei(GL_UNPACK_ALIGNMENT, 1);
    glTexStorage2D(GL_TEXTURE_2D, 1, ifmt, w, h); if (data) glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, w, h, fmt, type, data);
    GLint f = linear ? GL_LINEAR : GL_NEAREST;
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, f);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, f);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
    return t;
}

static bool stage(GLuint prog, GLuint out, int ow, int oh, GLuint in, GLuint base) {
    glUseProgram(prog);
    glBindImageTexture(0, out, 0, GL_FALSE, 0, GL_WRITE_ONLY, GL_RGBA16F);
    glActiveTexture(GL_TEXTURE0); glBindTexture(GL_TEXTURE_2D, in);
    GLint l = glGetUniformLocation(prog, "uInput"); if (l >= 0) glUniform1i(l, 0);
    glActiveTexture(GL_TEXTURE1); glBindTexture(GL_TEXTURE_2D, base);
    l = glGetUniformLocation(prog, "uBase"); if (l >= 0) glUniform1i(l, 1);
    glDispatchCompute((ow + 7) / 8, (oh + 7) / 8, 1);
    glMemoryBarrier(GL_SHADER_IMAGE_ACCESS_BARRIER_BIT | GL_TEXTURE_FETCH_BARRIER_BIT);
    return glOk("dispatch");
}

static bool stageSpatial(GLuint prog, GLuint out, GLuint in, int w, int h) {
    glUseProgram(prog);
    glBindImageTexture(0, out, 0, GL_FALSE, 0, GL_WRITE_ONLY, GL_RGBA16F);
    glActiveTexture(GL_TEXTURE0); glBindTexture(GL_TEXTURE_2D, in);
    GLint l = glGetUniformLocation(prog, "uInput"); if (l >= 0) glUniform1i(l, 0);
    glDispatchCompute((w + 7) / 8, (h + 7) / 8, 1);
    glMemoryBarrier(GL_SHADER_IMAGE_ACCESS_BARRIER_BIT | GL_TEXTURE_FETCH_BARRIER_BIT);
    return glOk("dispatch");
}

static const char* kVS =
    "#version 310 es\n"
    "void main(){float x=float((gl_VertexID&1)<<2);float y=float((gl_VertexID&2)<<1);"
    "gl_Position=vec4(x-1.0,y-1.0,0.0,1.0);}";
static const char* kFS =
    "#version 310 es\n"
    "precision highp float;\n"
    "uniform highp sampler2D uFinal;\n"
    "out vec4 o;\n"
    "void main(){float r=texelFetch(uFinal,ivec2(gl_FragCoord.xy),0).r;o=vec4(r,r,r,1.0);}";

int main(int argc, char** argv) {
    EGLDisplay dpy = EGL_NO_DISPLAY;
    EGLContext ctx = EGL_NO_CONTEXT;
    EGLSurface surf = EGL_NO_SURFACE;
    char *s0 = nullptr, *s1 = nullptr, *s2 = nullptr, *sSpatial = nullptr, *yRaw = nullptr;
    unsigned char *rgba = nullptr, *rb = nullptr, *yout = nullptr;
    GLuint p0 = 0, p1 = 0, p2 = 0, pSpatial = 0, rp = 0, srcT = 0, tA = 0, tB = 0, tF = 0, outT = 0, fbo = 0;
    FILE* fo = nullptr;
    int rc = 1, W = 0, H = 0;
    GLint maj = 0, mnr = 0;
    bool spatial = false;

    if (argc != 6 && argc != 7) { fprintf(stderr, "usage: pixel_probe <assetdir> W H <y8in> <y8out> [spatial]\n"); return 1; }
    if (argc == 7) {
        if (strcmp(argv[6], "spatial") != 0) { fprintf(stderr, "invalid mode: %s\n", argv[6]); return 1; }
        spatial = true;
    }
    W = atoi(argv[2]); H = atoi(argv[3]);
    if (W <= 0 || H <= 0 || W > 4096 || H > 4096) {
        fprintf(stderr, "W/H out of range (1..4096)\n"); return 1;
    }
    const size_t inN = (size_t)W * (size_t)H;
    const size_t outN = (size_t)(2 * W) * (size_t)(2 * H);
    if (!readFile(argv[4], &yRaw, nullptr)) { fprintf(stderr, "cannot read input\n"); goto done; }
    {
        char* probe = nullptr; size_t pn = 0;
        if (!readFile(argv[4], &probe, &pn)) { fprintf(stderr, "cannot stat input\n"); goto done; }
        free(probe);
        if (pn != inN) { fprintf(stderr, "input size %zu != W*H %zu\n", pn, inN); goto done; }
    }

    dpy = eglGetDisplay(EGL_DEFAULT_DISPLAY);
    if (dpy == EGL_NO_DISPLAY || !eglInitialize(dpy, nullptr, nullptr)) { fprintf(stderr, "eglInitialize failed\n"); goto done; }
    eglBindAPI(EGL_OPENGL_ES_API);
    {
        EGLint cfgA[] = { EGL_SURFACE_TYPE, EGL_PBUFFER_BIT,
                          EGL_RENDERABLE_TYPE, EGL_OPENGL_ES3_BIT_KHR,
                          EGL_RED_SIZE, 8, EGL_GREEN_SIZE, 8,
                          EGL_BLUE_SIZE, 8, EGL_ALPHA_SIZE, 8, EGL_NONE };
        EGLConfig cfg; EGLint nc = 0;
        if (!eglChooseConfig(dpy, cfgA, &cfg, 1, &nc) || nc < 1) { fprintf(stderr, "eglChooseConfig failed\n"); goto done; }
        EGLint ctxA[] = { EGL_CONTEXT_CLIENT_VERSION, 3, EGL_NONE };
        ctx = eglCreateContext(dpy, cfg, EGL_NO_CONTEXT, ctxA);
        if (ctx == EGL_NO_CONTEXT) { fprintf(stderr, "eglCreateContext failed\n"); goto done; }
        EGLint pbA[] = { EGL_WIDTH, 1, EGL_HEIGHT, 1, EGL_NONE };
        surf = eglCreatePbufferSurface(dpy, cfg, pbA);
        if (surf == EGL_NO_SURFACE) { fprintf(stderr, "eglCreatePbufferSurface failed\n"); goto done; }
        if (!eglMakeCurrent(dpy, surf, surf, ctx)) { fprintf(stderr, "eglMakeCurrent failed\n"); goto done; }
    }
    glGetIntegerv(GL_MAJOR_VERSION, &maj);
    glGetIntegerv(GL_MINOR_VERSION, &mnr);
    if (maj < 3 || (maj == 3 && mnr < 1)) { fprintf(stderr, "need ES >= 3.1, got %d.%d\n", maj, mnr); goto done; }

    {
        char pa[4096];
        if (spatial) {
            snprintf(pa, sizeof(pa), "%s/spatial.comp", argv[1]);
            if (!readFile(pa, &sSpatial, nullptr)) { fprintf(stderr, "missing spatial.comp\n"); goto done; }
        } else {
            snprintf(pa, sizeof(pa), "%s/cunny0.comp", argv[1]); if (!readFile(pa, &s0, nullptr)) { fprintf(stderr, "missing cunny0.comp\n"); goto done; }
            snprintf(pa, sizeof(pa), "%s/cunny1.comp", argv[1]); if (!readFile(pa, &s1, nullptr)) { fprintf(stderr, "missing cunny1.comp\n"); goto done; }
            snprintf(pa, sizeof(pa), "%s/cunny2.comp", argv[1]); if (!readFile(pa, &s2, nullptr)) { fprintf(stderr, "missing cunny2.comp\n"); goto done; }
        }
    }
    if (spatial) {
        pSpatial = computeProgram(sSpatial, "spatial");
        if (!pSpatial) goto done;
    } else {
        p0 = computeProgram(s0, "cunny0"); p1 = computeProgram(s1, "cunny1"); p2 = computeProgram(s2, "cunny2");
        if (!p0 || !p1 || !p2) goto done;
    }
    rp = link(compile(GL_VERTEX_SHADER, kVS, "vs"), compile(GL_FRAGMENT_SHADER, kFS, "fs"), "readback");
    if (!rp || !glOk("programs")) goto done;

    rgba = (unsigned char*)malloc(inN * 4);
    if (!rgba) goto done;
    for (size_t i = 0; i < inN; ++i) {
        unsigned char b = (unsigned char)yRaw[i];
        rgba[i * 4] = b; rgba[i * 4 + 1] = b; rgba[i * 4 + 2] = b; rgba[i * 4 + 3] = 255;
    }
    srcT = makeTex(GL_RGBA8, W, H, rgba, GL_RGBA, GL_UNSIGNED_BYTE, true);
    if (!spatial) {
        tA = makeTex(GL_RGBA16F, 2 * W, H, nullptr, GL_RGBA, GL_HALF_FLOAT, true);
        tB = makeTex(GL_RGBA16F, 2 * W, H, nullptr, GL_RGBA, GL_HALF_FLOAT, true);
    }
    tF = makeTex(GL_RGBA16F, 2 * W, 2 * H, nullptr, GL_RGBA, GL_HALF_FLOAT, true);
    if (!glOk("textures")) goto done;

    if (spatial) {
        if (!stageSpatial(pSpatial, tF, srcT, W, H)) goto done;
    } else {
        if (!stage(p0, tA, W, H, srcT, srcT)) goto done;
        if (!stage(p1, tB, W, H, tA, srcT)) goto done;
        if (!stage(p2, tF, W, H, tB, srcT)) goto done;
    }

    outT = makeTex(GL_RGBA8, 2 * W, 2 * H, nullptr, GL_RGBA, GL_UNSIGNED_BYTE, false);
    glGenFramebuffers(1, &fbo); glBindFramebuffer(GL_FRAMEBUFFER, fbo);
    glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, outT, 0);
    if (glCheckFramebufferStatus(GL_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE) { fprintf(stderr, "FBO incomplete\n"); goto done; }
    glViewport(0, 0, 2 * W, 2 * H);
    glDisable(GL_BLEND); glDisable(GL_DEPTH_TEST);
    glUseProgram(rp);
    glActiveTexture(GL_TEXTURE0); glBindTexture(GL_TEXTURE_2D, tF);
    glUniform1i(glGetUniformLocation(rp, "uFinal"), 0);
    glDrawArrays(GL_TRIANGLES, 0, 3);
    if (!glOk("readback draw")) goto done;

    glPixelStorei(GL_PACK_ALIGNMENT, 1);
    rb = (unsigned char*)malloc(outN * 4);
    yout = (unsigned char*)malloc(outN);
    if (!rb || !yout) goto done;
    glReadPixels(0, 0, 2 * W, 2 * H, GL_RGBA, GL_UNSIGNED_BYTE, rb);
    if (!glOk("glReadPixels")) goto done;
    for (size_t i = 3; i < outN * 4; i += 4) {
        if (rb[i] != 255) { fprintf(stderr, "alpha != 255 at %zu\n", i / 4); goto done; }
    }
    /* GL row 0 is the bottom row; both input and output use that as byte row 0. */
    for (size_t i = 0; i < outN; ++i) yout[i] = rb[i * 4];
    fo = fopen(argv[5], "wb");
    if (!fo || fwrite(yout, 1, outN, fo) != outN) { fprintf(stderr, "output write failed\n"); goto done; }
    fclose(fo); fo = nullptr;

    printf("GPU vendor: %s\n", eglQueryString(dpy, EGL_VENDOR) ? eglQueryString(dpy, EGL_VENDOR) : "");
    printf("GL vendor: %s\n", glGetString(GL_VENDOR) ? (const char*)glGetString(GL_VENDOR) : "");
    printf("GL renderer: %s\n", glGetString(GL_RENDERER) ? (const char*)glGetString(GL_RENDERER) : "");
    printf("GL version: %s (ES %d.%d)\n", glGetString(GL_VERSION) ? (const char*)glGetString(GL_VERSION) : "", maj, mnr);
    printf("input %dx%d, output %dx%d\n", W, H, 2 * W, 2 * H);
    rc = 0;

done:
    if (fo) fclose(fo);
    free(yout); free(rb); free(rgba);
    free(s0); free(s1); free(s2); free(sSpatial); free(yRaw);
    if (dpy != EGL_NO_DISPLAY) {
        glDeleteFramebuffers(1, &fbo);
        glDeleteTextures(1, &outT); glDeleteTextures(1, &tF);
        glDeleteTextures(1, &tB); glDeleteTextures(1, &tA); glDeleteTextures(1, &srcT);
        glDeleteProgram(rp); glDeleteProgram(pSpatial); glDeleteProgram(p2); glDeleteProgram(p1); glDeleteProgram(p0);
        eglMakeCurrent(dpy, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
        if (surf != EGL_NO_SURFACE) eglDestroySurface(dpy, surf);
        if (ctx != EGL_NO_CONTEXT) eglDestroyContext(dpy, ctx);
        eglTerminate(dpy);
    }
    return rc;
}
