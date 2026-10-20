plugins { alias(libs.plugins.android.application);alias(libs.plugins.kotlin.compose) }
android {
 namespace="io.ondeviceai.demo";compileSdk=36
 defaultConfig {applicationId="io.ondeviceai.demo";minSdk=26;targetSdk=36;versionCode=1;versionName="0.1.0";testInstrumentationRunner=providers.gradleProperty("odaiTestRunner").orNull?:"io.ondeviceai.demo.TtsProbeInstrumentation";buildConfigField("String","ODAI_SDK_COMMIT","\""+(providers.gradleProperty("sdkCommit").orNull?:"working-tree")+"\"")}
 buildFeatures {compose=true;buildConfig=true}
 compileOptions {sourceCompatibility=JavaVersion.VERSION_17;targetCompatibility=JavaVersion.VERSION_17}
}
dependencies {
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
 androidTestImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
}
