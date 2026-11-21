#include <jni.h>

#include <EGL/egl.h>
#include <EGL/eglext.h>
#include <GLES3/gl3.h>
#include <GLES2/gl2ext.h>

#include <cstdint>
#include <cstdio>
#include <cstring>
#include <unordered_map>

#ifndef EGL_TIMESTAMPS_ANDROID
#define EGL_TIMESTAMPS_ANDROID 0x3480
#endif
#ifndef EGL_DISPLAY_PRESENT_TIME_ANDROID
#define EGL_DISPLAY_PRESENT_TIME_ANDROID 0x348A
#endif
#ifndef EGL_TIMESTAMP_PENDING_ANDROID
#define EGL_TIMESTAMP_PENDING_ANDROID ((EGLnsecsANDROID)0x7fffffffffffffffLL)
#endif
#ifndef EGL_TIMESTAMP_INVALID_ANDROID
#define EGL_TIMESTAMP_INVALID_ANDROID ((EGLnsecsANDROID)-1)
#endif
#ifndef GL_QUERY_RESULT_EXT
#define GL_QUERY_RESULT_EXT 0x8866
#endif
#ifndef GL_QUERY_RESULT_AVAILABLE_EXT
#define GL_QUERY_RESULT_AVAILABLE_EXT 0x8867
#endif
#ifndef GL_TIME_ELAPSED_EXT
#define GL_TIME_ELAPSED_EXT 0x88BF
#endif
#ifndef GL_GPU_DISJOINT_EXT
#define GL_GPU_DISJOINT_EXT 0x8FBB
#endif

using GetNextFrameIdFn = PFNEGLGETNEXTFRAMEIDANDROIDPROC;
typedef EGLBoolean(EGLAPIENTRY* GetFrameTimestampsFn)(EGLDisplay, EGLSurface, EGLuint64KHR,
                                                     EGLint, const EGLint*, EGLnsecsANDROID*);
typedef EGLBoolean(EGLAPIENTRY* GetFrameTimestampSupportedFn)(EGLDisplay, EGLSurface, EGLint);

static GetNextFrameIdFn gGetNextFrameId = nullptr;
static GetFrameTimestampsFn gGetFrameTimestamps = nullptr;
static GetFrameTimestampSupportedFn gGetFrameTimestampSupported = nullptr;
static bool gFrameTimestampsReady = false;

static bool hasExactExtensionToken(const char* list, const char* token) {
    if (list == nullptr || token == nullptr) return false;
    const size_t n = std::strlen(token);
    const char* p = list;
    while (*p != '\0') {
        while (*p == ' ') ++p;
        if (*p == '\0') break;
        const char* start = p;
        while (*p != '\0' && *p != ' ') ++p;
        if (static_cast<size_t>(p - start) == n && std::strncmp(start, token, n) == 0) return true;
    }
    return false;
}

static bool currentDrawSurface(EGLDisplay* outDisplay, EGLSurface* outSurface) {
    EGLDisplay display = eglGetCurrentDisplay();
    if (display == EGL_NO_DISPLAY) return false;
    EGLSurface surface = eglGetCurrentSurface(EGL_DRAW);
    if (surface == EGL_NO_SURFACE) return false;
    *outDisplay = display;
    *outSurface = surface;
    return true;
}

using GenQueriesFn = PFNGLGENQUERIESEXTPROC;
using DeleteQueriesFn = PFNGLDELETEQUERIESEXTPROC;
using BeginQueryFn = PFNGLBEGINQUERYEXTPROC;
using EndQueryFn = PFNGLENDQUERYEXTPROC;
using GetObjectIvanFn = PFNGLGETQUERYOBJECTIVEXTPROC;
using GetObjectUi64Fn = PFNGLGETQUERYOBJECTUI64VEXTPROC;

struct GpuQuery {
    GLuint query;
    bool ended;
};

struct GpuState {
    EGLContext context;
    bool probed;
    bool supported;
    GenQueriesFn genQueries;
    DeleteQueriesFn deleteQueries;
    BeginQueryFn beginQuery;
    EndQueryFn endQuery;
    GetObjectIvanFn getObjectIvan;
    GetObjectUi64Fn getObjectUi64;
    std::unordered_map<int, GpuQuery> queries;
    int nextHandle;

    GpuState()
        : context(EGL_NO_CONTEXT), probed(false), supported(false),
          genQueries(nullptr), deleteQueries(nullptr), beginQuery(nullptr),
          endQuery(nullptr), getObjectIvan(nullptr), getObjectUi64(nullptr),
          nextHandle(1) {}
};

static thread_local GpuState gGpu;

static void refreshGpuState() {
    const EGLContext context = eglGetCurrentContext();
    if (context == EGL_NO_CONTEXT) {
        gGpu = GpuState();
        return;
    }
    if (gGpu.context != context) {
        gGpu = GpuState();
        gGpu.context = context;
    }
    if (gGpu.probed) return;
    gGpu.probed = true;

    const char* version = reinterpret_cast<const char*>(glGetString(GL_VERSION));
    if (version == nullptr) return;
    int major = 0;
    if (std::sscanf(version, "OpenGL ES %d", &major) != 1 || major < 3) return;

    GLint count = 0;
    glGetIntegerv(GL_NUM_EXTENSIONS, &count);
    bool found = false;
    for (GLint i = 0; i < count; ++i) {
        const char* ext = reinterpret_cast<const char*>(glGetStringi(GL_EXTENSIONS, static_cast<GLuint>(i)));
        if (ext != nullptr && std::strcmp(ext, "GL_EXT_disjoint_timer_query") == 0) {
            found = true;
            break;
        }
    }
    if (!found) return;

    GenQueriesFn gen = reinterpret_cast<GenQueriesFn>(eglGetProcAddress("glGenQueriesEXT"));
    DeleteQueriesFn del = reinterpret_cast<DeleteQueriesFn>(eglGetProcAddress("glDeleteQueriesEXT"));
    BeginQueryFn begin = reinterpret_cast<BeginQueryFn>(eglGetProcAddress("glBeginQueryEXT"));
    EndQueryFn end = reinterpret_cast<EndQueryFn>(eglGetProcAddress("glEndQueryEXT"));
    GetObjectIvanFn getIv = reinterpret_cast<GetObjectIvanFn>(eglGetProcAddress("glGetQueryObjectivEXT"));
    GetObjectUi64Fn getUi64 = reinterpret_cast<GetObjectUi64Fn>(eglGetProcAddress("glGetQueryObjectui64vEXT"));
    if (gen == nullptr || del == nullptr || begin == nullptr || end == nullptr ||
        getIv == nullptr || getUi64 == nullptr) return;

    gGpu.genQueries = gen;
    gGpu.deleteQueries = del;
    gGpu.beginQuery = begin;
    gGpu.endQuery = end;
    gGpu.getObjectIvan = getIv;
    gGpu.getObjectUi64 = getUi64;
    gGpu.supported = true;
}

extern "C" {

JNIEXPORT jboolean JNICALL
Java_io_ondeviceai_realtimesr_EglTiming_enable(JNIEnv*, jobject) {
    gFrameTimestampsReady = false;
    gGetNextFrameId = nullptr;
    gGetFrameTimestamps = nullptr;
    gGetFrameTimestampSupported = nullptr;

    EGLDisplay display = EGL_NO_DISPLAY;
    EGLSurface surface = EGL_NO_SURFACE;
    if (!currentDrawSurface(&display, &surface)) return JNI_FALSE;

    const char* extensions = eglQueryString(display, EGL_EXTENSIONS);
    if (!hasExactExtensionToken(extensions, "EGL_ANDROID_get_frame_timestamps")) return JNI_FALSE;

    GetNextFrameIdFn next = reinterpret_cast<GetNextFrameIdFn>(eglGetProcAddress("eglGetNextFrameIdANDROID"));
    GetFrameTimestampsFn stamps = reinterpret_cast<GetFrameTimestampsFn>(eglGetProcAddress("eglGetFrameTimestampsANDROID"));
    GetFrameTimestampSupportedFn supported =
        reinterpret_cast<GetFrameTimestampSupportedFn>(eglGetProcAddress("eglGetFrameTimestampSupportedANDROID"));
    if (next == nullptr || stamps == nullptr || supported == nullptr) return JNI_FALSE;

    if (!supported(display, surface, EGL_DISPLAY_PRESENT_TIME_ANDROID)) return JNI_FALSE;
    if (!eglSurfaceAttrib(display, surface, EGL_TIMESTAMPS_ANDROID, EGL_TRUE)) return JNI_FALSE;

    gGetNextFrameId = next;
    gGetFrameTimestamps = stamps;
    gGetFrameTimestampSupported = supported;
    gFrameTimestampsReady = true;
    return JNI_TRUE;
}

JNIEXPORT jlong JNICALL
Java_io_ondeviceai_realtimesr_EglTiming_nextId(JNIEnv*, jobject) {
    if (!gFrameTimestampsReady || gGetNextFrameId == nullptr) return -1;
    EGLDisplay display = EGL_NO_DISPLAY;
    EGLSurface surface = EGL_NO_SURFACE;
    if (!currentDrawSurface(&display, &surface)) return -1;
    EGLuint64KHR id=0; if(!gGetNextFrameId(display,surface,&id)) return -1; return static_cast<jlong>(id);
}

JNIEXPORT jlong JNICALL
Java_io_ondeviceai_realtimesr_EglTiming_displayNs(JNIEnv*, jobject, jlong id) {
    if (!gFrameTimestampsReady || gGetFrameTimestamps == nullptr) return -1;
    if (id < 0) return -1;
    EGLDisplay display = EGL_NO_DISPLAY;
    EGLSurface surface = EGL_NO_SURFACE;
    if (!currentDrawSurface(&display, &surface)) return -1;

    const EGLint names[1] = {EGL_DISPLAY_PRESENT_TIME_ANDROID};
    EGLnsecsANDROID values[1] = {0};
    if (!gGetFrameTimestamps(display, surface, static_cast<EGLuint64KHR>(id), 1, names, values)) return -1;

    const EGLnsecsANDROID value = values[0];
    if (value == EGL_TIMESTAMP_PENDING_ANDROID) return 0;
    if (value == EGL_TIMESTAMP_INVALID_ANDROID || value < 0) return -1;
    return static_cast<jlong>(value);
}

JNIEXPORT jint JNICALL
Java_io_ondeviceai_realtimesr_EglTiming_startGpu(JNIEnv*, jobject) {
    refreshGpuState();
    if (!gGpu.supported) return 0;
    GLuint query = 0;
    gGpu.genQueries(1, &query);
    if (query == 0) return 0;
    int handle = gGpu.nextHandle++;
    if (gGpu.nextHandle <= 0) gGpu.nextHandle = 1;
    gGpu.queries[handle] = GpuQuery{query, false};
    gGpu.beginQuery(GL_TIME_ELAPSED_EXT, query);
    return static_cast<jint>(handle);
}

JNIEXPORT void JNICALL
Java_io_ondeviceai_realtimesr_EglTiming_endGpu(JNIEnv*, jobject, jint id) {
    refreshGpuState();
    auto it = gGpu.queries.find(static_cast<int>(id));
    if (it == gGpu.queries.end() || !gGpu.supported) return;
    if (!it->second.ended) {
        gGpu.endQuery(GL_TIME_ELAPSED_EXT);
        it->second.ended = true;
    }
}

JNIEXPORT jlong JNICALL
Java_io_ondeviceai_realtimesr_EglTiming_elapsedGpuNs(JNIEnv*, jobject, jint id) {
    refreshGpuState();
    if (!gGpu.supported) return -1;
    auto it = gGpu.queries.find(static_cast<int>(id));
    if (it == gGpu.queries.end()) return -1;
    GpuQuery& query = it->second;
    if (!query.ended) {
        gGpu.endQuery(GL_TIME_ELAPSED_EXT);
        query.ended = true;
    }
    GLint disjoint = 0;
    glGetIntegerv(GL_GPU_DISJOINT_EXT, &disjoint);
    if (disjoint) return -1;
    GLint available = 0;
    gGpu.getObjectIvan(query.query, GL_QUERY_RESULT_AVAILABLE_EXT, &available);
    if (!available) return 0;
    GLuint64 nanos = 0;
    gGpu.getObjectUi64(query.query, GL_QUERY_RESULT_EXT, &nanos);
    glGetIntegerv(GL_GPU_DISJOINT_EXT, &disjoint);
    if (disjoint || nanos == 0) return -1;
    return static_cast<jlong>(nanos);
}

JNIEXPORT void JNICALL
Java_io_ondeviceai_realtimesr_EglTiming_deleteGpu(JNIEnv*, jobject, jint id) {
    refreshGpuState();
    auto it = gGpu.queries.find(static_cast<int>(id));
    if (it == gGpu.queries.end()) return;
    if (gGpu.deleteQueries != nullptr) gGpu.deleteQueries(1, &it->second.query);
    gGpu.queries.erase(it);
}

}  // extern "C"
