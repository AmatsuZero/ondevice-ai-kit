pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement { repositories {
    if (providers.gradleProperty("usePublishedSdk").orNull == "true") mavenLocal { content { includeGroup("io.ondeviceai") } }
    providers.gradleProperty("litertlmMavenRepo").orNull?.let { localRepo ->
        maven { url = uri(localRepo); content { includeGroup("com.google.ai.edge.litertlm") } }
    }
    google(); mavenCentral()
} }
rootProject.name = "ondevice-ai-kit"
include(":kmp-binding")
include(":android:sdk-core", ":android:identity")
include(":android:video", ":android:multimodal")
include(":android:model-store", ":android:face", ":android:backend-mlkit", ":android:speech", ":android:backend-system-tts", ":android:backend-system-asr")
include(":android:backend-litertlm")
include(":demo:shared", ":demo:androidApp")
include(":examples:android-native")
include(":examples:android-speech")

include(":examples:android-identity-native")

include(":examples:android-multimodal-native")
include(":probes:android-litertlm")
