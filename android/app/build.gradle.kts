plugins {
    id("com.android.application")
    kotlin("android")
    kotlin("plugin.serialization")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}
val gleoAssets = tasks.register<Copy>("generateGleoAssets") {
    from(rootProject.file("../contracts/gleo-app-schema.json")) { rename { "schema.json" } }
    into(layout.buildDirectory.dir("generated/gleoAssets"))
}
android {
    namespace = "com.pleos.routine"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.pleos.routine"
        minSdk = 28
        targetSdk = 35
        versionCode = 2
        versionName = "0.1.1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildFeatures { compose = true; buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildTypes {
        release {
            isMinifyEnabled = false
            // No production signing secret is committed or generated implicitly.
        }
    }
    sourceSets["main"].assets.srcDir("../../contracts")
    sourceSets["main"].assets.srcDir(layout.buildDirectory.dir("generated/gleoAssets"))
    sourceSets["androidTest"].assets.srcDir("schemas")
    packaging { resources.excludes += setOf("/META-INF/AL2.0", "/META-INF/LGPL2.1") }
}
tasks.named("preBuild") { dependsOn(gleoAssets) }
ksp { arg("room.schemaLocation", "$projectDir/schemas") }
dependencies {
    constraints {
        implementation("com.google.guava:guava:33.7.2-android") {
            because("CVE-2026-102554: patch the existing transitive Guava deserialization allocation vulnerability")
        }
    }
    implementation(project(":core"))
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation(platform("androidx.compose:compose-bom:2024.04.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("androidx.room:room-runtime:2.7.2")
    implementation("androidx.room:room-ktx:2.7.2")
    ksp("androidx.room:room-compiler:2.7.2")
    implementation("androidx.media3:media3-exoplayer:1.5.1")
    implementation("androidx.media3:media3-session:1.5.1")
    implementation("ai.pleos.playground:Vehicle:2.3.2")
    implementation("ai.pleos.playground:NaviHelper:3.0.5")
    // Voice input for the request fields; ON_DEVICE mode needs no network or app registration.
    implementation("ai.pleos.playground:SpeechToText:3.3.1")
    compileOnly(files("${android.sdkDirectory}/platforms/android-35/optional/android.car.jar"))
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.04.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
