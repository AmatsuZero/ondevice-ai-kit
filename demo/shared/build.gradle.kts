plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
}

val skikoUpstreamVersion = "0.144.6"
val skikoPatchedVersion = "0.144.6-ondevice-f02.6"
val skikoModules = listOf("skiko", "skiko-iosarm64", "skiko-iossimulatorarm64")

configurations.configureEach {
    if (name.startsWith("iosArm64") || name.startsWith("iosSimulatorArm64")) {
        resolutionStrategy.dependencySubstitution {
            skikoModules.forEach { moduleName ->
                substitute(module("org.jetbrains.skiko:$moduleName:$skikoUpstreamVersion"))
                    .using(module("org.jetbrains.skiko:$moduleName:$skikoPatchedVersion"))
            }
        }
    }
}

kotlin {
    androidLibrary { namespace="io.ondeviceai.demo.shared";compileSdk=36;minSdk=26 }
    iosArm64 { binaries.framework { baseName="ODAIKitDemo";isStatic=true;export(project(":kmp-binding")) } }
    iosSimulatorArm64 { binaries.framework { baseName="ODAIKitDemo";isStatic=true;export(project(":kmp-binding")) } }
    sourceSets {
        commonMain.dependencies { api(project(":kmp-binding"));implementation(compose.runtime);implementation(compose.foundation);implementation(compose.material3);implementation(compose.ui) }
        commonTest.dependencies {implementation(kotlin("test"))}
    }
}
