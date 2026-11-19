import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// TMDB key: local.properties (TMDB_API_KEY=...) or the TMDB_API_KEY env var (CI secret).
// Never committed. It can also be typed in the app's Settings screen.
val localProps = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
fun secret(name: String): String = (localProps.getProperty(name) ?: System.getenv(name) ?: "").trim().replace("\"", "")
val tmdbApiKey: String = secret("TMDB_API_KEY")
// Optional OMDb key for IMDb ratings (OMDB_API_KEY), same rules as the TMDB key.
val omdbApiKey: String = secret("OMDB_API_KEY")

android {
    namespace = "com.ak.vodcalendar"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.ak.vodcalendar"
        minSdk = 26
        targetSdk = 35
        versionCode = 4
        versionName = "0.4.0"
        buildConfigField("String", "TMDB_API_KEY", "\"$tmdbApiKey\"")
        buildConfigField("String", "OMDB_API_KEY", "\"$omdbApiKey\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    implementation(project(":core"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation(libs.androidx.tv.material)

    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
}
