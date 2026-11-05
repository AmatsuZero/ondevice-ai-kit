plugins { alias(libs.plugins.android.application);alias(libs.plugins.kotlin.compose) }
val enableLiteRtLm = providers.gradleProperty("enableLiteRtLm").orNull == "true"
val enableLiteRtSr = providers.gradleProperty("enableLiteRtSr").orNull == "true"
val enableOpenCvIdentity = providers.gradleProperty("enableOpenCvIdentity").orNull == "true"
val enableSherpaAsr = providers.gradleProperty("enableSherpaAsr").orNull == "true"
android {
 namespace="io.ondeviceai.demo";compileSdk=36
 defaultConfig {applicationId="io.ondeviceai.demo";minSdk=26;targetSdk=36;versionCode=1;versionName="0.1.0";testInstrumentationRunner=providers.gradleProperty("odaiTestRunner").orNull?:"io.ondeviceai.demo.TtsProbeInstrumentation";buildConfigField("String","ODAI_SDK_COMMIT","\""+(providers.gradleProperty("sdkCommit").orNull?:"working-tree")+"\"")}
 buildFeatures {compose=true;buildConfig=true}
 sourceSets.getByName("main").kotlin.srcDir(if (enableSherpaAsr) "src/sherpaasr/kotlin" else "src/noSherpaasr/kotlin")
 if (enableSherpaAsr) {
  val assetsDirectory = providers.gradleProperty("sherpaAsrAssetsDir").orNull
      ?: error("enableSherpaAsr requires sherpaAsrAssetsDir; run tools/prepare_sherpa_asr.py on the build host")
  sourceSets.getByName("main").assets.srcDir(assetsDirectory)
  sourceSets.getByName("androidTest").kotlin.srcDir("src/sherpaasrAndroidTest/kotlin")
  providers.gradleProperty("sherpaAsrFixtureDir").orNull?.let {
   sourceSets.getByName("androidTest").assets.srcDir(it)
  }
 }
 compileOptions {sourceCompatibility=JavaVersion.VERSION_17;targetCompatibility=JavaVersion.VERSION_17}
 sourceSets.getByName("main").kotlin.srcDir(if (enableOpenCvIdentity) "src/opencvidentity/kotlin" else "src/noOpencvidentity/kotlin")
 if (enableOpenCvIdentity) {
  sourceSets.getByName("main").assets.srcDir("src/opencvidentity/assets")
  sourceSets.getByName("test").kotlin.srcDir("src/opencvidentityTest/kotlin")
  sourceSets.getByName("androidTest").kotlin.srcDir("src/opencvidentityAndroidTest/kotlin")
 }
 sourceSets.getByName("main").kotlin.srcDir(if (enableLiteRtSr) "src/litertsr/kotlin" else "src/noLitertsr/kotlin")
 if (enableLiteRtSr) sourceSets.getByName("androidTest").kotlin.srcDir("src/litertsrAndroidTest/kotlin")
 sourceSets.getByName("main").kotlin.srcDir(if (enableLiteRtLm) "src/litertlm/kotlin" else "src/noLitertlm/kotlin")
 if (enableLiteRtLm) {
  sourceSets.getByName("test").kotlin.srcDir("src/litertlmTest/kotlin")
  sourceSets.getByName("androidTest").kotlin.srcDir("src/litertlmAndroidTest/kotlin")
 }
}
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    if (name.contains("AndroidTest")) {
        compilerOptions.freeCompilerArgs.addAll(
            "-Xno-param-assertions",
            "-Xno-call-assertions",
            "-Xno-receiver-assertions",
        )
    }
}
dependencies {
 if (enableSherpaAsr) implementation(project(":android:backend-sherpa-asr"))
 implementation(project(":android:identity"))
 if (enableOpenCvIdentity) implementation(project(":android:backend-opencv-identity"))
 if (enableOpenCvIdentity) androidTestImplementation("org.opencv:opencv:4.12.0")
 implementation(project(":android:video"))
 if (enableLiteRtSr) implementation(project(":android:backend-litert-sr"))
 if (enableLiteRtLm) {
  implementation(project(":android:backend-litertlm"))
  testImplementation("org.json:json:20250517")
 }
 implementation("androidx.compose.foundation:foundation:1.9.3")
 implementation("androidx.compose.material3:material3:1.4.0")
 testImplementation("org.jetbrains.kotlin:kotlin-test-junit:2.4.0")
 implementation("androidx.camera:camera-camera2:1.5.1")
 implementation("androidx.camera:camera-lifecycle:1.5.1")
 implementation("androidx.camera:camera-view:1.5.1")
 implementation(project(":demo:shared"));implementation(project(":kmp-binding"));implementation(project(":android:backend-mlkit"))
 implementation("androidx.activity:activity-compose:1.12.4")
 implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
 implementation(project(":android:speech"))
 implementation(project(":android:backend-system-tts"))
 implementation(project(":android:backend-system-asr"))
    // Kotlin-only androidTest providers run in the test APK process, whose
    // classpath is separate from the target APK. Keep the fixture in Kotlin
    // and package its runtime explicitly so provider startup is deterministic.
    androidTestImplementation("org.jetbrains.kotlin:kotlin-stdlib:2.4.0")
 androidTestImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
}
