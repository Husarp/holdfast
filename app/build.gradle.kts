import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}
val appVersion = "0.1.0"
val signingProperties = Properties()
val signingFile = file("${System.getProperty("user.home")}/.keystores/holdfast-signing.properties")
if (signingFile.exists()) signingFile.inputStream().use { signingProperties.load(it) }
android {
    namespace = "com.husarp.holdfast"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.husarp.holdfast"
        minSdk = 26
        targetSdk = 36
        versionCode = appVersion.split('.').map(String::toInt).let { it[0] * 1_000_000 + it[1] * 1_000 + it[2] }
        versionName = appVersion
    }
    signingConfigs {
        if (signingFile.exists()) create("release") {
            storeFile = file(signingProperties.getProperty("storeFile"))
            storePassword = signingProperties.getProperty("storePassword")
            keyAlias = signingProperties.getProperty("keyAlias")
            keyPassword = signingProperties.getProperty("keyPassword")
        }
    }
    buildTypes { getByName("release") { signingConfig = signingConfigs.getByName(if (signingFile.exists()) "release" else "debug") } }
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
