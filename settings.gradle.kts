pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement { repositories {
    if (providers.gradleProperty("usePublishedSdk").orNull == "true") mavenLocal { content { includeGroup("io.ondeviceai") } }
    google(); mavenCentral()
} }
rootProject.name = "ondevice-ai-kit"
include(":kmp-binding")
include(":android:sdk-core", ":android:identity")
include(":android:video", ":android:multimodal")
include(":android:model-store", ":android:face", ":android:backend-mlkit", ":android:speech", ":android:backend-system-tts", ":android:backend-system-asr")
include(":demo:shared", ":demo:androidApp")
include(":examples:android-native")
include(":examples:android-speech")

include(":examples:android-identity-native")
