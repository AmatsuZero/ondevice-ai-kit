plugins { alias(libs.plugins.android.library); id("maven-publish") }
android { publishing { singleVariant("release") { withSourcesJar() } }; namespace = "io.ondeviceai.identity"; compileSdk = 36; defaultConfig { minSdk = 26; testInstrumentationRunner = "io.ondeviceai.identity.AndroidKeystoreProbeInstrumentation" }; compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 } }
dependencies {
    api(project(":android:sdk-core"))
    api(project(":android:model-store"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:2.4.0")
    testImplementation("org.json:json:20250517")
}
group="io.ondeviceai"; version="0.1.0-SNAPSHOT"
afterEvaluate { publishing { publications { create<MavenPublication>("release") { from(components["release"]); artifactId=project.name } } } }
