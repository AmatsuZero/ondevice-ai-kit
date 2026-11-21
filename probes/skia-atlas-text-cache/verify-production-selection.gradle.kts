import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.result.ResolvedArtifactResult
import java.io.File
import java.security.MessageDigest

fun sha256(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().use { input ->
        val buffer = ByteArray(8192)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            digest.update(buffer, 0, read)
        }
    }
    return digest.digest().joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
}

gradle.projectsEvaluated {
    val root = gradle.rootProject
    val demo = root.findProject(":demo:shared")
        ?: error("Required production project :demo:shared was not found")
    val patchedVersion = "0.144.6-ondevice-f02.6"
    val upstreamVersion = "0.144.6"
    val noticeVersion = demo.providers.gradleProperty("skikoNoticesVersion").orNull
        ?: error("Pass -PskikoNoticesVersion=$patchedVersion to collect the reviewed notices")
    check(noticeVersion == patchedVersion) {
        "Notice version must be $patchedVersion, got $noticeVersion"
    }

    val collectorScript = root.file("probes/skia-atlas-text-cache/collect-skiko-notices.gradle.kts")
    check(collectorScript.isFile) {
        "Existing notice collector is required at ${collectorScript.absolutePath}"
    }
    demo.apply(mapOf("from" to collectorScript))

    val deviceLink = demo.tasks.findByName("linkDebugFrameworkIosArm64")
        ?: error("Required iOS device framework link task was not found")
    val simulatorLink = demo.tasks.findByName("linkDebugFrameworkIosSimulatorArm64")
        ?: error("Required iOS simulator framework link task was not found")
    val noticeTask = demo.tasks.findByName("collectSkikoNotices")
        ?: error("Existing collectSkikoNotices task was not registered")

    root.tasks.register("verifySkikoSelection") {
        group = "verification"
        description = "Builds both production iOS frameworks and verifies Skiko selections and notices."
        dependsOn(deviceLink, simulatorLink, noticeTask)

        doLast {
            val report = root.layout.buildDirectory.file("reports/skiko-selection.txt").get().asFile
            val reportLines = mutableListOf<String>()
            val skikoGroup = "org.jetbrains.skiko"
            val expectedTargets = listOf(
                "iosArm64" to "skiko-iosarm64",
                "iosSimulatorArm64" to "skiko-iossimulatorarm64"
            )

            expectedTargets.forEach { (targetPrefix, nativeModule) ->
                val targetConfigurations = demo.configurations.filter { configuration ->
                    configuration.isCanBeResolved &&
                        configuration.name.startsWith(targetPrefix) &&
                        (configuration.name.contains("compile", ignoreCase = true) ||
                            configuration.name.contains("link", ignoreCase = true) ||
                            configuration.name.contains("framework", ignoreCase = true))
                }
                check(targetConfigurations.isNotEmpty()) {
                    "No resolvable compile/link configurations found for $targetPrefix"
                }

                val selectedModules = mutableSetOf<String>()
                val selectedArtifacts = mutableListOf<Pair<String, ResolvedArtifactResult>>()
                targetConfigurations.forEach { configuration ->
                    val modules = configuration.incoming.resolutionResult.allComponents
                        .mapNotNull { it.id as? ModuleComponentIdentifier }
                        .filter { it.group == skikoGroup }
                        .distinctBy { "${it.group}:${it.module}:${it.version}" }
                    if (modules.isNotEmpty()) {
                        modules.forEach { module ->
                            check(module.module == "skiko" || module.module == nativeModule) {
                                "$targetPrefix selected unexpected Skiko module ${module.module}"
                            }
                            check(module.version == patchedVersion) {
                                "$targetPrefix selected ${module.group}:${module.module}:${module.version}, expected $patchedVersion"
                            }
                            selectedModules += module.module
                            reportLines += "IOS\t${configuration.name}\t${module.group}:${module.module}:${module.version}"
                        }

                        configuration.incoming.artifacts.artifacts.forEach { artifact ->
                            val component = artifact.id.componentIdentifier as? ModuleComponentIdentifier
                            if (component != null && component.group == skikoGroup) {
                                check(component.module == "skiko" || component.module == nativeModule) {
                                    "$targetPrefix resolved unexpected Skiko artifact module ${component.module}"
                                }
                                check(component.version == patchedVersion) {
                                    "$targetPrefix resolved ${component.group}:${component.module}:${component.version}, expected $patchedVersion"
                                }
                                check(artifact.file.isFile) {
                                    "Resolved Skiko artifact is not a file: ${artifact.file}"
                                }
                                selectedArtifacts += configuration.name to artifact
                            }
                        }
                    }
                }

                check(selectedModules.containsAll(setOf("skiko", nativeModule))) {
                    "$targetPrefix is missing the patched metadata/native modules: expected skiko and $nativeModule, selected $selectedModules"
                }
                check(selectedArtifacts.any { (_, artifact) ->
                    val component = artifact.id.componentIdentifier as? ModuleComponentIdentifier
                    component != null &&
                        component.group == skikoGroup &&
                        component.module == nativeModule &&
                        component.version == patchedVersion
                }) {
                    "$targetPrefix resolved modules but no artifact for $skikoGroup:$nativeModule:$patchedVersion was returned"
                }
                selectedArtifacts
                    .distinctBy { (configurationName, artifact) ->
                        val component = artifact.id.componentIdentifier as ModuleComponentIdentifier
                        "$configurationName\t${component.group}:${component.module}:${component.version}\t${artifact.file.absolutePath}"
                    }
                    .sortedBy { (configurationName, artifact) ->
                        val component = artifact.id.componentIdentifier as ModuleComponentIdentifier
                        "$configurationName\t${component.group}:${component.module}:${component.version}\t${artifact.file.absolutePath}"
                    }
                    .forEach { (configurationName, artifact) ->
                        val component = artifact.id.componentIdentifier as ModuleComponentIdentifier
                        reportLines +=
                            "IOS_ARTIFACT\t$configurationName\t${component.group}:${component.module}:${component.version}\t${artifact.file.absolutePath}\t${sha256(artifact.file)}"
                    }
            }

            val androidConfigurations = demo.configurations.filter { configuration ->
                configuration.isCanBeResolved &&
                    configuration.name.startsWith("android", ignoreCase = true) &&
                    configuration.name.contains("compile", ignoreCase = true)
            }
            check(androidConfigurations.isNotEmpty()) {
                "No resolvable Android compile configurations were found"
            }
            var androidSkikoSelections = 0
            androidConfigurations.forEach { configuration ->
                val modules = configuration.incoming.resolutionResult.allComponents
                    .mapNotNull { it.id as? ModuleComponentIdentifier }
                    .filter { it.group == skikoGroup }
                    .distinctBy { "${it.group}:${it.module}:${it.version}" }
                if (modules.isEmpty()) {
                    reportLines += "ANDROID\t${configuration.name}\tNO_SKIKO_MODULE_SELECTED"
                } else {
                    modules.forEach { module ->
                        check(module.version == upstreamVersion) {
                            "Android configuration ${configuration.name} selected ${module.group}:${module.module}:${module.version}; expected unchanged upstream $upstreamVersion"
                        }
                        androidSkikoSelections++
                        reportLines += "ANDROID\t${configuration.name}\t${module.group}:${module.module}:${module.version}"
                    }
                }
            }
            reportLines += "ANDROID_SKIKO_SELECTION_COUNT\t$androidSkikoSelections"

            val noticeConfiguration = demo.configurations.getByName("skikoDistributionNotices")
            val noticeComponents = noticeConfiguration.incoming.resolutionResult.allComponents
                .mapNotNull { it.id as? ModuleComponentIdentifier }
                .filter { it.group == skikoGroup && it.module == "skiko" }
            check(noticeComponents.size == 1 && noticeComponents.single().version == patchedVersion) {
                "Notice classifier did not resolve exactly org.jetbrains.skiko:skiko:$patchedVersion"
            }
            val noticeArtifacts: List<ResolvedArtifactResult> =
                noticeConfiguration.incoming.artifacts.artifacts.toList()
            check(noticeArtifacts.size == 1) {
                "Expected one patched Skiko notice archive, got ${noticeArtifacts.size}"
            }
            val noticeArchive = noticeArtifacts.single()
            val noticeComponent = noticeArchive.id.componentIdentifier as? ModuleComponentIdentifier
                ?: error("Notice archive resolved from a non-module component")
            check(
                noticeComponent.group == skikoGroup &&
                    noticeComponent.module == "skiko" &&
                    noticeComponent.version == patchedVersion
            ) {
                "Unexpected notice archive ${noticeComponent.group}:${noticeComponent.module}:${noticeComponent.version}"
            }
            check(noticeArchive.file.isFile) {
                "Resolved notice archive is not a file: ${noticeArchive.file}"
            }
            reportLines += "NOTICE_ARCHIVE\t${noticeComponent.group}:${noticeComponent.module}:${noticeComponent.version}\t${noticeArchive.file.absolutePath}\t${sha256(noticeArchive.file)}"

            val noticeOutput = demo.layout.buildDirectory.dir(
                "distribution-materials/META-INF/ondevice-ai-kit/notices"
            ).get().asFile
            check(noticeOutput.isDirectory) {
                "Existing notice collector did not produce ${noticeOutput.absolutePath}"
            }
            val noticeFiles = noticeOutput.walkTopDown()
                .filter { it.isFile }
                .sortedBy { it.relativeTo(noticeOutput).invariantSeparatorsPath }
                .toList()
            check(noticeFiles.isNotEmpty()) {
                "Existing notice collector produced no notice files"
            }
            noticeFiles.forEach { file ->
                reportLines +=
                    "NOTICE_FILE\t${file.relativeTo(noticeOutput).invariantSeparatorsPath}\t${file.absolutePath}\t${sha256(file)}"
            }

            report.parentFile.mkdirs()
            report.writeText(reportLines.distinct().sorted().joinToString("\n", postfix = "\n"))
            println("SKIKO_SELECTION_REPORT=${report.absolutePath}")
        }
    }
}
