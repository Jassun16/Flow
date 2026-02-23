plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace   = "com.jassun16.flow"
    compileSdk  = 36

    defaultConfig {
        applicationId   = "com.jassun16.flow"
        minSdk          = 36          // Android 16 only — your Pixel 8 Pro
        targetSdk       = 36
        versionCode     = 1
        versionName     = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled   = true   // shrinks APK by removing unused code
            isShrinkResources = true   // removes unused image/file assets
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }


    buildFeatures {
        compose = true   // enables Jetpack Compose
    }
}

dependencies {
    // ── Core ──────────────────────────────────────────────────
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.splashscreen)

    // ── Jetpack Compose ───────────────────────────────────────
    // BOM = Bill of Materials: ensures ALL compose libraries
    // use compatible versions automatically — no version conflicts
    val composeBom = platform(libs.compose.bom)
    implementation(composeBom)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling)
    implementation(libs.compose.material3)
    implementation(libs.compose.icons)

    // ── Navigation ────────────────────────────────────────────
    implementation(libs.navigation.compose)

    // ── Room (local database) ─────────────────────────────────
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)          // ksp = Kotlin Symbol Processing
    // generates Room boilerplate at build time

    // ── Hilt (dependency injection) ───────────────────────────
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation)

    // ── Network ───────────────────────────────────────────────
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)  // logs network calls in debug mode

    // ── HTML Parsing ──────────────────────────────────────────
    implementation(libs.jsoup)
    implementation("net.dankito.readability4j:readability4j:1.0.8")  // ← ADD THIS

    // ── Image Loading ─────────────────────────────────────────
    implementation(libs.coil.compose)
    implementation(libs.coil.network)

    // ── Preferences ───────────────────────────────────────────
    implementation(libs.datastore.prefs)

    // ── Coroutines ────────────────────────────────────────────
    implementation(libs.coroutines.android)

    // ── Gemini Nano (on-device AI) ────────────────────────────
    implementation(libs.mlkit.genai.common)
    implementation(libs.mlkit.genai.summarization)

}
