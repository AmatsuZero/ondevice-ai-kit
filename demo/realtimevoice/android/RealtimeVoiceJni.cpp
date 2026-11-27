#include <jni.h>
#include <array>
#include <cstddef>
#include <cstdint>
#include "RealtimeVoice.h"

namespace {

constexpr int kMaxFrames = 4096;

RVSession* asSession(jlong handle) {
    return reinterpret_cast<RVSession*>(static_cast<intptr_t>(handle));
}

RVRole toRole(jint role) {
    switch (role) {
        case 1: return RV_GIRL;
        case 2: return RV_ANNOUNCER;
        case 0:
        default: return RV_UNCLE;
    }
}

}  // namespace

extern "C" {

JNIEXPORT jlong JNICALL
Java_io_ondeviceai_demo_LiveVoiceNative_create(JNIEnv*, jclass, jint sampleRate, jint role) {
    RVSession* session = rv_create(static_cast<int>(sampleRate), toRole(role));
    return static_cast<jlong>(reinterpret_cast<intptr_t>(session));
}

JNIEXPORT void JNICALL
Java_io_ondeviceai_demo_LiveVoiceNative_destroy(JNIEnv*, jclass, jlong handle) {
    RVSession* session = asSession(handle);
    if (session != nullptr) rv_destroy(session);
}

JNIEXPORT jint JNICALL
Java_io_ondeviceai_demo_LiveVoiceNative_render(
        JNIEnv* env, jclass, jlong handle, jfloatArray mic, jfloatArray output,
        jint frames, jboolean guard) {
    RVSession* session = asSession(handle);
    if (session == nullptr || mic == nullptr || output == nullptr || frames <= 0 || frames > kMaxFrames) {
        return 1;
    }
    if (env->GetArrayLength(mic) < frames || env->GetArrayLength(output) < frames) return 1;
    if (env->ExceptionCheck()) return 1;

    static thread_local std::array<float, kMaxFrames> inBuf{};
    static thread_local std::array<float, kMaxFrames> outBuf{};

    env->GetFloatArrayRegion(mic, 0, frames, inBuf.data());
    if (env->ExceptionCheck()) return 1;

    rv_process(session, inBuf.data(), outBuf.data(), static_cast<size_t>(frames));

    jint result = 0;
    if (guard == JNI_TRUE) {
        result = static_cast<jint>(
            rv_speaker_guard(session, inBuf.data(), outBuf.data(), static_cast<size_t>(frames)));
    }

    env->SetFloatArrayRegion(output, 0, frames, outBuf.data());
    if (env->ExceptionCheck()) return 1;
    return result;
}

}  // extern "C"
