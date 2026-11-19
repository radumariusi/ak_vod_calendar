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
// Simkl client id for the TV login (SIMKL_CLIENT_ID). Not a secret: the PIN login uses no
// client secret. The Settings field overrides it.
val simklClientId: String = secret("SIMKL_CLIENT_ID")

// Version: the base changes per stage; CI release builds add the run number (BUILD_NUMBER),
// so every published APK has a higher versionCode and installs over the previous one.
val baseVersion = "0.5"
val buildNumber: Int? = System.getenv("BUILD_NUMBER")?.toIntOrNull()

android {
    namespace = "com.ak.vodcalendar"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.ak.vodcalendar"
        minSdk = 26
        targetSdk = 35
        versionCode = if (buildNumber != null) 100 + buildNumber else 5
        versionName = if (buildNumber != null) "$baseVersion.$buildNumber" else "$baseVersion.0"
        buildConfigField("String", "TMDB_API_KEY", "\"$tmdbApiKey\"")
        buildConfigField("String", "OMDB_API_KEY", "\"$omdbApiKey\"")
        buildConfigField("String", "SIMKL_CLIENT_ID", "\"$simklClientId\"")
        // GitHub repo whose latest release the in-app updater installs.
        buildConfigField("String", "UPDATE_REPO", "\"radumariusi/ak_vod_calendar\"")
    }

    // Release signing key from secrets, never committed: the keystore file path and its
    // password come from local.properties or env (CI decodes the SIGNING_KEYSTORE_B64 secret).
    // The same key on every release lets the in-app update install over the previous version.
    val keystorePath = secret("SIGNING_KEYSTORE_PATH")
    val hasReleaseKey = keystorePath.isNotBlank() && file(keystorePath).exists()
    signingConfigs {
        if (hasReleaseKey) {
            create("release") {
                storeFile = file(keystorePath)
                storePassword = secret("SIGNING_PASSWORD")
                keyAlias = secret("SIGNING_KEY_ALIAS").ifBlank { "akvod" }
                keyPassword = secret("SIGNING_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            // Without the key (PR builds, local builds) the release APK is signed with the
            // debug key; it works but won't update a version installed from Releases.
            signingConfig = if (hasReleaseKey) signingConfigs.getByName("release") else signingConfigs.getByName("debug")
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

    lint {
        // Lint must not block publishing a release.
        checkReleaseBuilds = false
        abortOnError = false
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

    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.tvprovider)

    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
}
