#include "OpenVoice.h"
#include <jni.h>

namespace {
class UtfChars {
public:
    UtfChars(JNIEnv *env, jstring value) : env(env), value(value), text(env->GetStringUTFChars(value, nullptr)) {}
    ~UtfChars() { if (text) env->ReleaseStringUTFChars(value, text); }
    JNIEnv *env;
    jstring value;
    const char *text;
};
}
extern "C" JNIEXPORT jint JNICALL Java_io_ondeviceai_demo_OpenVoiceAudio_render(
    JNIEnv *env, jobject, jstring assets, jstring input, jstring voice, jstring output) {
    UtfChars a(env,assets); if (!a.text) return 1;
    UtfChars i(env,input); if (!i.text) return 1;
    UtfChars v(env,voice); if (!v.text) return 1;
    UtfChars o(env,output); if (!o.text) return 1;
    return openvoice_render(a.text,i.text,v.text,o.text);
}
