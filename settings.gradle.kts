pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement { repositories { google(); mavenCentral() } }
rootProject.name = "ondevice-ai-kit"
include(":kmp-binding")
include(":android:sdk-core")
include(":android:model-store", ":android:face", ":android:backend-mlkit")
