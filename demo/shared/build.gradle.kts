plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
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
