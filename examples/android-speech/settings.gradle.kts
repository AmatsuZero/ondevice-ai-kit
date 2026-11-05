pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }

require(providers.gradleProperty("usePublishedSdk").orNull == "true") {
    "Standalone speech consumer requires -PusePublishedSdk=true"
}
require(!System.getProperty("maven.repo.local").isNullOrBlank()) {
    "Specify an isolated SDK repository with -Dmaven.repo.local=/absolute/path"
}

dependencyResolutionManagement {
    versionCatalogs { create("libs") { from(files("../../gradle/libs.versions.toml")) } }
    repositories {
        mavenLocal { content { includeGroup("io.ondeviceai") } }
        providers.gradleProperty("sherpaMavenRepo").orNull?.let { localRepo ->
            maven { url = uri(localRepo); content { includeGroup("com.k2fsa.sherpa.onnx") } }
        }
        google(); mavenCentral()
    }
}
rootProject.name = "android-speech-consumer"
