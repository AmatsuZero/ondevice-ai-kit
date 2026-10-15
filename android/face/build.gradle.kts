plugins { alias(libs.plugins.android.library) }
android {
    namespace = "io.ondeviceai.face"
    compileSdk = 36
    defaultConfig { minSdk = 26 }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}
dependencies {
    api(project(":android:sdk-core"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:2.4.0")
    testImplementation("org.json:json:20250517")
}
