plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}
android {
    namespace = "com.husarp.holdfast"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.husarp.holdfast"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1"
    }
    buildFeatures { compose = true; buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    testOptions { unitTests.isIncludeAndroidResources = true }
}
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }

dependencies {
    implementation("com.google.code.gson:gson:2.13.2")
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.material3)
    implementation("androidx.compose.material:material-icons-extended")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    testImplementation("io.github.takahirom.roborazzi:roborazzi:1.50.0")
    testImplementation("io.github.takahirom.roborazzi:roborazzi-compose:1.50.0")
    implementation(libs.activity.compose)
    implementation(libs.navigation.compose)
    implementation(libs.lifecycle.compose)
    implementation(libs.work.runtime)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test)
}

tasks.withType<Test>().configureEach {
    systemProperty("roborazzi.test.record", "true")
    outputs.dir(layout.projectDirectory.dir("screenshots"))
}
