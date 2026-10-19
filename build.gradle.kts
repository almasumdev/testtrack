plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.google.services) apply false
    alias(libs.plugins.firebase.crashlytics) apply false
    alias(libs.plugins.ktlint)
}

// Applied at the root so `./gradlew ktlintCheck` / `ktlintFormat` cover the whole project from
// one command, the same way `compileDebugKotlin` already does: this app is one module, so a
// per-module apply would just be this with extra steps.
ktlint {
    // The default ktlint_official ruleset reformats every file it touches on first run, which
    // would be a wall of unrelated diff noise laid over real feature work. android is the
    // narrower, Kotlin-style-guide-for-Android ruleset ktlint ships for exactly this: it doesn't
    // fight Android/Compose conventions (trailing lambdas, longer line lengths for Composable
    // parameter lists) the way the strict default does.
    android.set(true)
}
