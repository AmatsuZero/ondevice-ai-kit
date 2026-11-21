plugins { alias(libs.plugins.android.application) }

android {
    namespace = "io.ondeviceai.example.litertlm"
    compileSdk = 36
    defaultConfig {
        applicationId = "io.ondeviceai.example.litertlm"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    if (providers.gradleProperty("usePublishedSdk").orNull == "true") {
        implementation("io.ondeviceai:backend-litertlm:0.1.0-SNAPSHOT")
    } else {
        implementation(project(":android:backend-litertlm"))
    }
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("junit:junit:4.13.2")
}
