plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace  = "com.jassun16.flow"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.jassun16.flow"
        minSdk        = 36
        targetSdk     = 36
        versionCode   = 1
        versionName   = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled   = true
            isShrinkResources = true
            signingConfig     = signingConfigs.getByName("debug")
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
        compose     = true
        buildConfig = true
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    // ── Core ──────────────────────────────────────────────────
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.splashscreen)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.foundation)

    // ── Jetpack Compose ───────────────────────────────────────
    val composeBom = platform(libs.compose.bom)
    implementation(composeBom)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling)
    implementation(libs.compose.material3)
    implementation(libs.compose.icons)

    // ── Navigation ────────────────────────────────────────────
    implementation(libs.navigation.compose)

    // ── Room ──────────────────────────────────────────────────
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // ── Hilt ──────────────────────────────────────────────────
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation)

    // ── Network ───────────────────────────────────────────────
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)

    // ── HTML Parsing ──────────────────────────────────────────
    implementation(libs.jsoup)
    implementation("net.dankito.readability4j:readability4j:1.0.8")

    // ── Image Loading ─────────────────────────────────────────
    implementation(libs.coil.compose)
    implementation(libs.coil.network)

    // ── Preferences ───────────────────────────────────────────
    implementation(libs.datastore.prefs)

    // ── Coroutines ────────────────────────────────────────────
    implementation(libs.coroutines.android)

    implementation("org.jetbrains.kotlinx:kotlinx-collections-immutable:0.3.7")

    // ── Gemini Nano (on-device AI) ────────────────────────────
    implementation("com.google.mediapipe:tasks-genai:0.10.25")

    implementation("androidx.compose.material:material-icons-extended")

}
