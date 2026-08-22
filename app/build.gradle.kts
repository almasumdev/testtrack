import java.util.Properties

plugins {
    // No kotlin-android: AGP 9 compiles Kotlin itself. The Compose compiler is still its own plugin.
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.services)
    alias(libs.plugins.firebase.crashlytics)
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
        // The id Play knows. The debug build appends `.debug` to it and is a separate app on the
        // phone with its own Firebase registration; see the debug block below.
        applicationId = "com.eazyverse.testtrack"
        minSdk = 26
        targetSdk = 37
        versionCode = 59
        versionName = "1.5.7"
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
            manifestPlaceholders["crashlytics"] = "true"
            manifestPlaceholders["analytics"] = "true"
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release")
        }

        /**
         * Its own app on the phone, beside the release build rather than on top of it.
         *
         * The two used to share an id, and sharing one meant that installing what was being worked
         * on replaced what was being tested. On a phone that is also a tester's phone that is the
         * wrong trade: the release build is the one in the cohort, owing days to twelve other
         * people, and it should not go anywhere because somebody wanted to look at a screen.
         *
         * The suffix is what separates them, and it is not free. An application id is the primary
         * key for Firebase, for Google sign-in and for Play, so `com.eazyverse.testtrack.debug`
         * needs its own Android app in the Firebase project with its own signing SHA-1s
         * registered, or sign-in comes back refused with nothing wrong in the code. That
         * registration exists; `app/google-services.json` carries the client for it.
         *
         * Play's own in-app update check has no such app, and says so rather than failing: see
         * AppUpdateService, which treats a sideloaded install as "no update, no block".
         */
        debug {
            applicationIdSuffix = ".debug"

            /*
             * Signed with the release key where there is one, and it is no longer load-bearing.
             *
             * It was: with one shared id, two different keys meant a debug build could not be
             * installed over a release one without an uninstall, and an uninstall costs the
             * signed-in session, the Drive grant, and the usage access that had to be argued out
             * of Samsung's restricted settings. The suffix settles that by keeping the two apart
             * altogether.
             *
             * Kept because both keys are registered against the debug package either way, and
             * because one key across every build this machine makes is one fewer thing that can
             * refuse an `install -r` for a reason nobody remembers. Falls back to the ordinary
             * debug key where the keystore is absent, so a fresh checkout still builds.
             */
            signingConfig = signingConfigs.findByName("release") ?: signingConfig

            /*
             * No crash reports and no analytics off this machine.
             *
             * Every build here is a debug build, and a debug build spends its life being force
             * stopped, reinstalled over, and crashed on purpose. Left on, it would fill the very
             * dashboard that exists to show what is happening to testers with what is happening
             * to me.
             */
            manifestPlaceholders["crashlytics"] = "false"
            manifestPlaceholders["analytics"] = "false"
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
    // Crash reports and the handful of automatic events. Both are opt-out for the tester through
    // Play, and neither is given anything about who they are beyond what Firebase collects itself.
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.crashlytics)

    // Play's in-app update flow. It answers whether *this* install can update right now, which is
    // a question only Play can answer: it knows the track the copy came from and the rollout it
    // sits in.
    implementation(libs.play.app.update)
    // Task.await(), which the update service is written against. The rest of this app hand-rolls
    // it with suspendCancellableCoroutine; this one file is kept identical to the original.
    implementation(libs.coroutines.play.services)

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
