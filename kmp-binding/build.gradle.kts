plugins { alias(libs.plugins.kotlin.multiplatform); alias(libs.plugins.android.kmp.library) }
kotlin {
    androidLibrary { namespace = "io.ondeviceai.binding"; compileSdk = 36; minSdk = 26 }
    iosArm64 { binaries.framework { baseName = "ODAIKitBinding"; isStatic = true } }
    iosSimulatorArm64 { binaries.framework { baseName = "ODAIKitBinding"; isStatic = true } }
    sourceSets { commonTest.dependencies { implementation(kotlin("test")) } }
}
