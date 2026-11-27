#ifndef REALTIMEVOICE_H
#define REALTIMEVOICE_H

#include <stddef.h>

#ifdef __cplusplus
extern "C" {
#endif

typedef struct RVSession RVSession;
typedef enum { RV_UNCLE = 0, RV_GIRL = 1, RV_ANNOUNCER = 2 } RVRole;

RVSession* rv_create(int sample_rate, RVRole role);
void rv_process(RVSession* session, const float* input, float* output, size_t frames);
void rv_destroy(RVSession* session);

/* Call after rv_process, speaker path only. mic and output must be distinct.
   Returns 0 = safe (low-gain ramp + clamp applied), 1 = sticky trip (block zeroed). */
int rv_speaker_guard(RVSession* session, const float* mic, float* output, size_t frames);

#ifdef __cplusplus
}
#endif

#endif /* REALTIMEVOICE_H */
