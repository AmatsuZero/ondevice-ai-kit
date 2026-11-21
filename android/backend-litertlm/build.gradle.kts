plugins { alias(libs.plugins.android.library); id("maven-publish") }

android {
    publishing { singleVariant("release") { withSourcesJar() } }
    namespace = "io.ondeviceai.litertlm"
    compileSdk = 36
    defaultConfig {
        minSdk = 26
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}

dependencies {
    api(project(":android:multimodal"))
    api(project(":android:model-store"))
    implementation("com.google.ai.edge.litertlm:litertlm-android:0.18.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:2.4.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    testImplementation("org.json:json:20250517")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("junit:junit:4.13.2")
}

group = "io.ondeviceai"
version = "0.1.0-SNAPSHOT"
afterEvaluate {
    publishing { publications { create<MavenPublication>("release") { from(components["release"]); artifactId = project.name } } }
}
