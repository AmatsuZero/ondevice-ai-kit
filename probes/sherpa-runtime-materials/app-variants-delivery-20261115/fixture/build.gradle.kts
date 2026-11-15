import groovy.json.JsonOutput
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import java.io.File

plugins {
    id("com.android.application") version "9.1.0"
}

android {
    namespace = "io.ondeviceai.noticeconsumer"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.ondeviceai.noticeconsumer"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("io.ondeviceai:backend-sherpa-asr:0.1.0-SNAPSHOT")
}

val consumerVariant = providers.gradleProperty("consumerVariant").orNull ?: "debug"
require(consumerVariant == "debug" || consumerVariant == "release") {
    "-PconsumerVariant must be either debug or release, found: $consumerVariant"
}

val sdkRepoForCheck = providers.gradleProperty("sdkRepo").orNull
    ?: error("Specify an absolute SDK repository with -PsdkRepo=/absolute/path")
val sherpaRepoForCheck = providers.gradleProperty("sherpaRepo").orNull
    ?: error("Specify an absolute Sherpa repository with -PsherpaRepo=/absolute/path")
require(File(sdkRepoForCheck).isAbsolute) {
    "-PsdkRepo must be an absolute path: $sdkRepoForCheck"
}
require(File(sherpaRepoForCheck).isAbsolute) {
    "-PsherpaRepo must be an absolute path: $sherpaRepoForCheck"
}
val artifactReportProvider = providers.gradleProperty("artifactReport")

tasks.register("verifyNoticeConsumer") {
    doLast {
        val artifactReport = artifactReportProvider.orNull
            ?: error("Specify an absolute artifact report path with -PartifactReport=/absolute/path")
        require(File(artifactReport).isAbsolute) {
            "-PartifactReport must be an absolute path: $artifactReport"
        }

        val sdkRoot = File(sdkRepoForCheck).canonicalFile
        val sherpaRoot = File(sherpaRepoForCheck).canonicalFile
        val sdkPrefix = sdkRoot.absolutePath + File.separator
        val sherpaPrefix = sherpaRoot.absolutePath + File.separator

        fun under(prefix: String, path: String): Boolean {
            return File(path).canonicalPath.startsWith(prefix)
        }

        val runtimeClasspathName = "${consumerVariant}RuntimeClasspath"
        val configuration = configurations[runtimeClasspathName]

        configuration.incoming.resolutionResult.allComponents.forEach { component ->
            val id = component.id
            if (id is ProjectComponentIdentifier && id.projectPath != ":") {
                error("Unexpected project component on $runtimeClasspathName: ${id.projectPath}")
            }
        }

        val entries = configuration.incoming.artifacts.map { artifact ->
            val id = artifact.id.componentIdentifier
            if (id is ProjectComponentIdentifier && id.projectPath != ":") {
                error("Unexpected project artifact on $runtimeClasspathName: ${id.projectPath}")
            }
            mapOf(
                "component" to id.displayName,
                "path" to artifact.file.canonicalPath
            )
        }.sortedWith(compareBy<Map<String, String>> { it.getValue("component") }.thenBy { it.getValue("path") })

        val backend = entries.filter { it.getValue("component") == "io.ondeviceai:backend-sherpa-asr:0.1.0-SNAPSHOT" }
        require(backend.size == 1) {
            "Expected exactly one backend-sherpa-asr artifact, found: ${backend.map { it.getValue("path") }}"
        }
        val sherpaRuntime = entries.filter { it.getValue("component") == "com.k2fsa.sherpa.onnx:sherpa-onnx:2.0.1" }
        require(sherpaRuntime.size == 1) {
            "Expected exactly one sherpa-onnx:2.0.1 artifact, found: ${sherpaRuntime.map { it.getValue("path") }}"
        }

        val ioArtifacts = entries.filter { it.getValue("component").startsWith("io.ondeviceai:") }
        require(ioArtifacts.all { under(sdkPrefix, it.getValue("path")) }) {
            "io.ondeviceai artifacts must resolve under sdkRepo: ${ioArtifacts.map { it.getValue("path") }}"
        }
        require(sherpaRuntime.all { under(sherpaPrefix, it.getValue("path")) }) {
            "sherpa-onnx:2.0.1 must resolve under sherpaRepo: ${sherpaRuntime.map { it.getValue("path") }}"
        }
        val sherpaArtifacts = entries.filter { it.getValue("component").startsWith("com.k2fsa.sherpa.onnx:") }
        require(sherpaArtifacts.all { under(sherpaPrefix, it.getValue("path")) }) {
            "com.k2fsa.sherpa.onnx artifacts must resolve under sherpaRepo: ${sherpaArtifacts.map { it.getValue("path") }}"
        }

        File(artifactReport).writeText(
            JsonOutput.prettyPrint(JsonOutput.toJson(entries)) + "\n",
            Charsets.UTF_8
        )
    }
}
