import org.gradle.api.attributes.Attribute
import org.gradle.api.attributes.Category
import org.gradle.api.attributes.Usage
import org.gradle.api.tasks.Sync

// Apply from a Gradle build that already declares the chosen Maven repository.
// Set -PskikoNoticesVersion to the exact reviewed candidate version.
// This collects distribution materials; final App packaging must retain them.
val skikoNoticesVersion = providers.gradleProperty("skikoNoticesVersion").get()
val skikoDistributionNotices = configurations.create("skikoDistributionNotices") {
    isCanBeConsumed = false
    isTransitive = false
    // Select the root metadata variant even in builds without the KMP plugin.
    attributes {
        attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category::class.java, Category.LIBRARY))
        attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage::class.java, "kotlin-metadata"))
        attribute(Attribute.of("org.jetbrains.kotlin.platform.type", String::class.java), "common")
    }
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
