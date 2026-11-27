#ifndef ODAI_REALTIME_VOICE_AUDIOUNIT_H
#define ODAI_REALTIME_VOICE_AUDIOUNIT_H
#ifdef __cplusplus
extern "C" {
#endif

/* Opaque audio engine. No DSP/ABI details are exposed here. */
typedef struct RVAudioEngine RVAudioEngine;

/* destination: 0 = headphones, 1 = built-in speaker. NULL on failure. */
RVAudioEngine* rvaudio_create(int sample_rate, int role, int destination);
/* Atomic mute gate. Must be 1 before rvaudio_start; 0 lets audio through. */
void rvaudio_set_muted(RVAudioEngine* engine, int muted);
/* Returns 0 on success. Refuses to start unless the mute gate was explicitly set. */
int rvaudio_start(RVAudioEngine* engine);
/* Stops rendering and joins the render callback. Non-RT queue only. */
void rvaudio_stop(RVAudioEngine* engine);
/* Stop (if needed), then free engine and its RVSession. Non-RT queue only. */
void rvaudio_destroy(RVAudioEngine* engine);
/* Sticky speaker-guard trip count, polled by the host on the main thread. */
int rvaudio_guard_trips(RVAudioEngine* engine);
/* Nonzero when a render callback failed; host must terminate the session. */
int rvaudio_render_error(RVAudioEngine* engine);

#ifdef __cplusplus
}
#endif
#endif
