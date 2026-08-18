import java.util.Properties

plugins {
    // No kotlin-android: AGP 9 compiles Kotlin itself. The Compose compiler is still its own plugin.
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.services)
}

/**
 * Config that must not be committed.
 *
 * Precedence is environment variable first so CI can inject without a file, then
 * `local.properties`, which is gitignored. Nothing here has a real default: a missing key
 * produces an empty string and a loud warning rather than a value that silently half-works.
 *
 * See local.properties.example for the keys.
 */
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

fun secret(key: String): String {
    val value = System.getenv(key) ?: localProperties.getProperty(key)
    if (value.isNullOrBlank()) {
        logger.warn("WARNING: $key is not set. Add it to local.properties — see local.properties.example.")
        return ""
    }
    return value
}

android {
    namespace = "com.eazyverse.testtrack"
    compileSdk = 37

    defaultConfig {
        // No build-type suffix: the Firebase app and the registered signing SHA-1 are both bound
        // to this exact id, and a suffixed debug build would match neither.
        applicationId = "com.eazyverse.testtrack"
        minSdk = 26
        targetSdk = 37
        versionCode = 48
        versionName = "1.3.8"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "WEB_CLIENT_ID", "\"${secret("TESTTRACK_WEB_CLIENT_ID")}\"")
        buildConfigField("String", "GATE_URL", "\"${secret("TESTTRACK_GATE_URL")}\"")
        buildConfigField("String", "GROUP_URL", "\"${secret("TESTTRACK_GROUP_URL")}\"")
    }

    /**
     * Release signing, if a keystore is configured. Skipped otherwise so a fresh clone still
     * builds debug without any setup. Google sign-in is bound to the signing certificate, so a
     * release build needs its SHA-1 registered in the Cloud console or sign-in fails at runtime.
     */
    val storePath = System.getenv("TESTTRACK_KEYSTORE") ?: localProperties.getProperty("TESTTRACK_KEYSTORE")
    signingConfigs {
        if (!storePath.isNullOrBlank() && file(storePath).exists()) {
            create("release") {
                storeFile = file(storePath)
                storePassword = System.getenv("TESTTRACK_KEYSTORE_PASSWORD")
                    ?: localProperties.getProperty("TESTTRACK_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("TESTTRACK_KEY_ALIAS")
                    ?: localProperties.getProperty("TESTTRACK_KEY_ALIAS")
                keyPassword = System.getenv("TESTTRACK_KEY_PASSWORD")
                    ?: localProperties.getProperty("TESTTRACK_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release")
        }

        /**
         * Signed with the release key too, where there is one.
         *
         * Android refuses to install a build over one signed with a different key, and the two
         * defaults are different keys, so a phone carrying a release build could only take a debug
         * one after an uninstall. That is not a small cost on a real phone: it takes the signed-in
         * session, the Drive grant, and the usage access that had to be argued out of Samsung's
         * restricted settings.
         *
         * One key means `install -r` always lands, whichever variant is already there.
         *
         * It also settles a smaller nuisance. Google checks the calling app's signature, so the
         * debug key needs its own OAuth client registered or sign-in fails in debug and works in
         * release. Sharing the key means one registration governs both.
         *
         * Falls back to the ordinary debug key where the keystore is absent, so a checkout without
         * local.properties still builds.
         */
        debug {
            signingConfig = signingConfigs.findByName("release") ?: signingConfig
        }
    }

    // No kotlinOptions block: AGP 9 removed it, and built-in Kotlin takes its jvmTarget from
    // targetCompatibility below, so the two can no longer drift apart.
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    debugImplementation(libs.androidx.ui.tooling)

    // Identity — the Google ID token the membership service verifies.
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.googleid)

    // Authorization — a Drive access token. Separate concern from identity.
    implementation(libs.play.services.auth)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.messaging)

    implementation(libs.okhttp)
    implementation(libs.coil.compose)

    // Reminders are computed and raised on the phone, not sent to it.
    implementation(libs.androidx.work.runtime)

    // Compliance is duplicated in firestore.rules and the two have to agree, so the sums are
    // worth pinning somewhere that runs without a device.
    testImplementation(libs.junit)

    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.runner)
    // Lets the reminder worker be run on demand. Its real schedule is an evening delay, so
    // without this the only way to see it work is to wait for one.
    androidTestImplementation(libs.androidx.work.testing)
}
