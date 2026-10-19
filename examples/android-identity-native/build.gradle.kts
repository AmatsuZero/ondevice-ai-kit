plugins { alias(libs.plugins.android.application) }
android {
    namespace="io.ondeviceai.identityconsumer"; compileSdk=36
    defaultConfig { applicationId="io.ondeviceai.identityconsumer"; minSdk=26; targetSdk=36; versionCode=1; versionName="0.1.0" }
    compileOptions { sourceCompatibility=JavaVersion.VERSION_17; targetCompatibility=JavaVersion.VERSION_17 }
}
dependencies {
    if(providers.gradleProperty("usePublishedSdk").orNull=="true")
        implementation("io.ondeviceai:identity:0.1.0-SNAPSHOT")
    else implementation(project(":android:identity"))
}
