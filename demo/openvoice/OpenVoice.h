#pragma once
#ifdef __cplusplus
extern "C" {
#endif
// input is a canonical mono PCM16 WAV owned by the demo. Returns 0 on success.
int openvoice_render(const char *assets, const char *input, const char *voice, const char *output);
#ifdef __APPLE__
// The caller owns an empty profile directory for this render and removes it after use.
int openvoice_render_coreml(const char *assets, const char *input, const char *voice, const char *output,
                            const char *profile_directory);
#endif
#ifdef __cplusplus
}
#endif
