#ifndef WEMM_NATIVE_H
#define WEMM_NATIVE_H

#include <stddef.h>

#ifdef __cplusplus
extern "C" {
#endif

typedef enum odai_wemm_stage {
    ODAI_WEMM_LOAD = 0,
    ODAI_WEMM_PREPARE = 1,
    ODAI_WEMM_PREFILL = 2,
    ODAI_WEMM_GENERATE = 3
} odai_wemm_stage;

typedef int (*odai_wemm_checkpoint)(void *context, odai_wemm_stage stage,
                                    int generated, const char *bytes,
                                    size_t length);
typedef void (*odai_wemm_result)(void *context, const char *bytes,
                                 size_t length);

int odai_wemm_generate(const char *language, const char *projector,
                       const char *request_directory, void *context,
                       odai_wemm_checkpoint checkpoint,
                       odai_wemm_result result);

#ifdef __cplusplus
}
#endif

#endif
