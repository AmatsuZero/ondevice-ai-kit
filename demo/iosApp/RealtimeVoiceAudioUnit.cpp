#include "RealtimeVoiceAudioUnit.h"

/* C++ only: the opaque C engine/ABI lives here, never in the C-facing header. */
#include "../realtimevoice/RealtimeVoice.h"

#include <AudioToolbox/AudioToolbox.h>
#include <algorithm>
#include <atomic>
#include <cstring>
#include <thread>
#include <vector>

namespace {
constexpr UInt32 kMaxFrames = 4096;

struct RVAudioEngineImpl {
    AudioUnit unit = nullptr;
    bool initialized = false;
    RVSession* rv = nullptr;
    int destination = 0;
    std::atomic<int> muted{1};
    std::atomic<int> running{0};
    std::atomic<int> inCallback{0};
    std::atomic<int> guardTrips{0};
    std::atomic<int> renderError{0};
    /* Preallocated once in create(); never resized from the render callback. */
    std::vector<float> mic;
    std::vector<float> processed;
};

void silence(AudioBufferList* ioData, UInt32 frames) {
    if (!ioData) return;
    for (UInt32 b = 0; b < ioData->mNumberBuffers; ++b) {
        if (ioData->mBuffers[b].mData) {
            std::memset(ioData->mBuffers[b].mData, 0,
                        std::min<std::size_t>(ioData->mBuffers[b].mDataByteSize,
                                              (std::size_t)frames * sizeof(float)));
        }
    }
}

OSStatus render(void* ref, AudioUnitRenderActionFlags* flags, const AudioTimeStamp* ts,
                UInt32 bus, UInt32 frames, AudioBufferList* ioData) {
    auto* e = static_cast<RVAudioEngineImpl*>(ref);
    e->inCallback.fetch_add(1, std::memory_order_acq_rel);
    do {
        if (!e->running.load(std::memory_order_acquire) ||
            e->muted.load(std::memory_order_acquire) != 0 ||
            !e->rv || frames == 0 || !ioData) {
            silence(ioData, frames);
            break;
        }
        if (frames > kMaxFrames) {
            /* Oversized slice: fully silence the whole block. */
            silence(ioData, frames);
            e->renderError.store(1, std::memory_order_release);
            break;
        }
        const std::size_t n = static_cast<std::size_t>(frames);
        if (n > e->mic.size() || n > e->processed.size()) {
            silence(ioData, frames);
            e->renderError.store(1, std::memory_order_release);
            break;
        }
        AudioBufferList in{};
        in.mNumberBuffers = 1;
        in.mBuffers[0].mNumberChannels = 1;
        in.mBuffers[0].mDataByteSize = static_cast<UInt32>(n * sizeof(float));
        in.mBuffers[0].mData = e->mic.data();
        OSStatus status = AudioUnitRender(e->unit, flags, ts, 1, static_cast<UInt32>(n), &in);
        if (status != noErr) {
            /* Render error: silence now and terminate via flag (handled on main). */
            e->renderError.store(1, std::memory_order_release);
            silence(ioData, frames);
            break;
        }
        rv_process(e->rv, e->mic.data(), e->processed.data(), n);
        float* out = e->processed.data();
        if (e->destination == 1) {
            /* Speaker only: guard IN PLACE on the processed buffer. mic (input) and
               processed (output) are separate buffers, as the ABI requires. */
            if (rv_speaker_guard(e->rv, e->mic.data(), e->processed.data(), n) != 0) {
                e->guardTrips.fetch_add(1, std::memory_order_relaxed);
                silence(ioData, frames);
                break;
            }
        }
        /* A stop may have landed during processing: mute again before the output copy. */
        if (e->muted.load(std::memory_order_acquire) != 0 ||
            !e->running.load(std::memory_order_acquire)) {
            silence(ioData, frames);
            break;
        }
        for (UInt32 b = 0; b < ioData->mNumberBuffers; ++b) {
            float* dst = static_cast<float*>(ioData->mBuffers[b].mData);
            if (!dst) continue;
            const std::size_t cap = ioData->mBuffers[b].mDataByteSize / sizeof(float);
            std::copy(out, out + std::min<std::size_t>(n, cap), dst);
        }
    } while (0);
    e->inCallback.fetch_sub(1, std::memory_order_acq_rel);
    return noErr;
}
}  // namespace

extern "C" {

RVAudioEngine* rvaudio_create(int sampleRate, int role, int destination) {
    if (sampleRate <= 0 || role < 0 || role > 2 || (destination != 0 && destination != 1)) return nullptr;

    auto* e = new RVAudioEngineImpl();
    e->destination = destination;
    e->rv = rv_create(sampleRate, static_cast<RVRole>(role));
    if (!e->rv) { delete e; return nullptr; }

    e->mic.assign(kMaxFrames, 0.0f);
    e->processed.assign(kMaxFrames, 0.0f);

    auto fail = [&]() -> RVAudioEngine* {
        if (e->unit) {
            if (e->initialized) AudioUnitUninitialize(e->unit);
            AudioComponentInstanceDispose(e->unit);
            e->unit = nullptr;
        }
        if (e->rv) { rv_destroy(e->rv); e->rv = nullptr; }
        delete e;
        return nullptr;
    };

    AudioComponentDescription desc{};
    desc.componentType = kAudioUnitType_Output;
    desc.componentSubType = (destination == 1) ? kAudioUnitSubType_VoiceProcessingIO
                                               : kAudioUnitSubType_RemoteIO;
    desc.componentManufacturer = kAudioUnitManufacturer_Apple;
    AudioComponent comp = AudioComponentFindNext(nullptr, &desc);
    if (!comp) return fail();
    if (AudioComponentInstanceNew(comp, &e->unit) != noErr || !e->unit) return fail();

    UInt32 one = 1;
    if (AudioUnitSetProperty(e->unit, kAudioOutputUnitProperty_EnableIO,
                             kAudioUnitScope_Input, 1, &one, sizeof(one)) != noErr) return fail();

    if (destination == 1) {
        /* Documented proof: <AudioUnit/AudioUnitProperties.h> defines
           kAUVoiceIOProperty_BypassVoiceProcessing on kAudioUnitScope_Global;
           element 1 addresses the VoiceProcessingIO voice-processing INPUT (mic) bus.
           We keep voice processing enabled (bypass = 0) and require a verified GET == 0. */
        bool verified = false;
        const UInt32 elements[] = { 1, 0 };  /* 1 is the documented input bus; 0 is fallback. */
        for (UInt32 element : elements) {
            UInt32 bypass = 0;
            if (AudioUnitSetProperty(e->unit, kAUVoiceIOProperty_BypassVoiceProcessing,
                                     kAudioUnitScope_Global, element,
                                     &bypass, sizeof(bypass)) != noErr) continue;
            UInt32 value = 1;
            UInt32 size = sizeof(value);
            if (AudioUnitGetProperty(e->unit, kAUVoiceIOProperty_BypassVoiceProcessing,
                                     kAudioUnitScope_Global, element,
                                     &value, &size) == noErr && value == 0) {
                verified = true;
                break;
            }
        }
        if (!verified) return fail();

        UInt32 enabled = 0;
        UInt32 size = sizeof(enabled);
        if (AudioUnitGetProperty(e->unit, kAudioOutputUnitProperty_EnableIO,
                                 kAudioUnitScope_Input, 1, &enabled, &size) != noErr || enabled == 0) {
            return fail();
        }
    }

    AudioStreamBasicDescription fmt{};
    fmt.mSampleRate = static_cast<Float64>(sampleRate);
    fmt.mFormatID = kAudioFormatLinearPCM;
    fmt.mFormatFlags = kAudioFormatFlagIsFloat | kAudioFormatFlagIsPacked;
    fmt.mFramesPerPacket = 1;
    fmt.mChannelsPerFrame = 1;
    fmt.mBitsPerChannel = 32;
    fmt.mBytesPerFrame = 4;
    fmt.mBytesPerPacket = 4;

    if (AudioUnitSetProperty(e->unit, kAudioUnitProperty_StreamFormat,
                             kAudioUnitScope_Input, 0, &fmt, sizeof(fmt)) != noErr) return fail();
    if (AudioUnitSetProperty(e->unit, kAudioUnitProperty_StreamFormat,
                             kAudioUnitScope_Output, 1, &fmt, sizeof(fmt)) != noErr) return fail();

    UInt32 maxFrames = kMaxFrames;
    if (AudioUnitSetProperty(e->unit, kAudioUnitProperty_MaximumFramesPerSlice,
                             kAudioUnitScope_Global, 0, &maxFrames, sizeof(maxFrames)) != noErr) return fail();

    AURenderCallbackStruct cb{};
    cb.inputProc = render;
    cb.inputProcRefCon = e;
    if (AudioUnitSetProperty(e->unit, kAudioUnitProperty_SetRenderCallback,
                             kAudioUnitScope_Input, 0, &cb, sizeof(cb)) != noErr) return fail();

    if (AudioUnitInitialize(e->unit) != noErr) return fail();
    e->initialized = true;
    return reinterpret_cast<RVAudioEngine*>(e);
}

void rvaudio_set_muted(RVAudioEngine* engine, int muted) {
    if (engine) reinterpret_cast<RVAudioEngineImpl*>(engine)->muted.store(muted ? 1 : 0, std::memory_order_release);
}

int rvaudio_start(RVAudioEngine* engine) {
    auto* e = reinterpret_cast<RVAudioEngineImpl*>(engine);
    if (!e || e->muted.load(std::memory_order_acquire) == 0) return -1;
    if (e->running.load(std::memory_order_acquire) != 0) return -1;
    e->running.store(1, std::memory_order_release);
    OSStatus s = AudioOutputUnitStart(e->unit);
    if (s != noErr) { e->running.store(0, std::memory_order_release); return static_cast<int>(s); }
    return 0;
}

void rvaudio_stop(RVAudioEngine* engine) {
    auto* e = reinterpret_cast<RVAudioEngineImpl*>(engine);
    if (!e) return;
    e->muted.store(1, std::memory_order_release);
    e->running.store(0, std::memory_order_release);
    if (e->unit) AudioOutputUnitStop(e->unit);
    while (e->inCallback.load(std::memory_order_acquire) > 0) std::this_thread::yield();
}

void rvaudio_destroy(RVAudioEngine* engine) {
    auto* e = reinterpret_cast<RVAudioEngineImpl*>(engine);
    if (!e) return;
    rvaudio_stop(engine);
    if (e->unit) {
        if (e->initialized) AudioUnitUninitialize(e->unit);
        AudioComponentInstanceDispose(e->unit);
        e->unit = nullptr;
        e->initialized = false;
    }
    if (e->rv) { rv_destroy(e->rv); e->rv = nullptr; }
    delete e;
}

int rvaudio_guard_trips(RVAudioEngine* engine) {
    return engine ? reinterpret_cast<RVAudioEngineImpl*>(engine)->guardTrips.load(std::memory_order_relaxed) : 0;
}

int rvaudio_render_error(RVAudioEngine* engine) {
    return engine ? reinterpret_cast<RVAudioEngineImpl*>(engine)->renderError.load(std::memory_order_relaxed) : 0;
}

}  // extern "C"
