plugins { alias(libs.plugins.android.library); id("maven-publish") }
android {
    publishing { singleVariant("release") { withSourcesJar() } }
    namespace = "io.ondeviceai.multimodal"
    compileSdk = 36
    defaultConfig { minSdk = 26 }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}
dependencies {
    api(project(":android:sdk-core"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:2.4.0")
}
group = "io.ondeviceai"
version = "0.1.0-SNAPSHOT"
afterEvaluate {
    publishing { publications { create<MavenPublication>("release") { from(components["release"]); artifactId = project.name } } }
}
