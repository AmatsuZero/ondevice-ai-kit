import org.gradle.api.tasks.Sync

// Apply from a Gradle build that already declares the chosen Maven repository.
// Set -PskikoNoticesVersion to the exact reviewed candidate version.
// This collects distribution materials; final App packaging must retain them.
val skikoNoticesVersion = providers.gradleProperty("skikoNoticesVersion").get()
val skikoDistributionNotices = configurations.create("skikoDistributionNotices") {
    isCanBeConsumed = false
    isTransitive = false
}
dependencies.add(
    skikoDistributionNotices.name,
    "org.jetbrains.skiko:skiko:$skikoNoticesVersion:notices@jar"
)
tasks.register<Sync>("collectSkikoNotices") {
    from({ zipTree(skikoDistributionNotices.singleFile) }) {
        include("META-INF/ondevice-ai-kit/notices/**")
        includeEmptyDirs = false
    }
    into(layout.buildDirectory.dir("distribution-materials"))
}
