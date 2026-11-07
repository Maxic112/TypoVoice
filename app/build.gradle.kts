import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

val localProps = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

// `-Ptypovoice.cleanStart` builds a debug APK without the seeded dev server (first-run testing).
val cleanStart = providers.gradleProperty("typovoice.cleanStart").isPresent

fun localProp(key: String): String = if (cleanStart) "" else localProps.getProperty(key, "")

android {
    namespace = "ru.maximv.typovoice"
    compileSdk = 37

    defaultConfig {
        applicationId = "ru.maximv.typovoice"
        minSdk = 31
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"

        // GitHub repository ("owner/name") whose releases the app updates from; set in gradle.properties.
        buildConfigField("String", "UPDATE_REPO", "\"${providers.gradleProperty("typovoice.updateRepo").getOrElse("")}\"")
        buildConfigField("String", "DEV_SERVER_NAME", "\"\"")
        buildConfigField("String", "DEV_SERVER_URL", "\"\"")
        buildConfigField("String", "DEV_SERVER_KEY", "\"\"")
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            resValue("string", "app_name", "TypoVoice Debug")
            buildConfigField("String", "DEV_SERVER_NAME", "\"${localProp("typovoice.dev.name")}\"")
            buildConfigField("String", "DEV_SERVER_URL", "\"${localProp("typovoice.dev.baseUrl")}\"")
            buildConfigField("String", "DEV_SERVER_KEY", "\"${localProp("typovoice.dev.apiKey")}\"")
        }
        release {
            isMinifyEnabled = false
            resValue("string", "app_name", "TypoVoice")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
        resValues = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.navigation.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    debugImplementation(libs.compose.ui.tooling)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.kotlinx.coroutines.test)
}
