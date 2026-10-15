pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement { repositories {
    if (providers.gradleProperty("usePublishedSdk").orNull == "true") mavenLocal { content { includeGroup("io.ondeviceai") } }
    google(); mavenCentral()
} }
rootProject.name = "ondevice-ai-kit"
include(":kmp-binding")
include(":android:sdk-core")
include(":android:model-store", ":android:face", ":android:backend-mlkit")
include(":demo:shared", ":demo:androidApp")
include(":examples:android-native")
