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

// Version: `-Ptypovoice.versionName=1.2.3` (CI passes the tag); versionCode is derived from it.
val appVersionName = providers.gradleProperty("typovoice.versionName").getOrElse("0.1.0")
val appVersionCode = appVersionName.substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 }
    .let { (it.getOrElse(0) { 0 } * 10_000) + (it.getOrElse(1) { 0 } * 100) + it.getOrElse(2) { 0 } }
    .coerceAtLeast(1)

// Release signing: environment variables (CI) or the git-ignored keystore.properties (local builds).
// Without them the release APK is built unsigned.
val keystoreProps = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

fun signingValue(env: String, key: String): String? =
    System.getenv(env)?.takeIf { it.isNotBlank() } ?: keystoreProps.getProperty(key)?.takeIf { it.isNotBlank() }

val releaseStoreFile = signingValue("SIGNING_STORE_FILE", "storeFile")

android {
    namespace = "ru.maximv.typovoice"
    compileSdk = 37

    defaultConfig {
        applicationId = "ru.maximv.typovoice"
        minSdk = 31
        targetSdk = 37
        versionCode = appVersionCode
        versionName = appVersionName

        // GitHub repository ("owner/name") whose releases the app updates from; set in gradle.properties.
        buildConfigField("String", "UPDATE_REPO", "\"${providers.gradleProperty("typovoice.updateRepo").getOrElse("")}\"")
        buildConfigField("String", "DEV_SERVER_NAME", "\"\"")
        buildConfigField("String", "DEV_SERVER_URL", "\"\"")
        buildConfigField("String", "DEV_SERVER_KEY", "\"\"")
    }

    signingConfigs {
        if (releaseStoreFile != null) {
            create("release") {
                storeFile = rootProject.file(releaseStoreFile)
                storePassword = signingValue("SIGNING_STORE_PASSWORD", "storePassword")
                keyAlias = signingValue("SIGNING_KEY_ALIAS", "keyAlias")
                keyPassword = signingValue("SIGNING_KEY_PASSWORD", "keyPassword")
            }
        }
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
            signingConfigs.findByName("release")?.let { signingConfig = it }
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
