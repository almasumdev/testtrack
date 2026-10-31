package com.eazyverse.testtrack.data

import android.content.Context
import android.os.Build
import android.provider.Settings
import com.eazyverse.testtrack.BuildConfig
import com.eazyverse.testtrack.domain.DeviceFacts
import com.eazyverse.testtrack.domain.EmulatorSigns
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import okhttp3.Interceptor
import java.io.File
import java.io.IOException

/**
 * Whether this app is running on an emulator, and so stays behind its block screen.
 *
 * Decided on the phone, before any screen, and while it is blocked nothing reaches our server
 * (net/Api.kt). The team can lift it for everybody from `config/app` without a release, by setting
 * `allowEmulators` to true, should it ever catch a real phone. Debug builds and Firebase Test Lab,
 * which runs Play's pre-launch report, are never blocked.
 */
object EmulatorGate {
    private const val PREFS = "emulator_gate"
    private const val ALLOWED = "allow_emulators"
    private const val CHECK_EVERY_MS = 60_000L

    private val _blocked = MutableStateFlow(false)
    val blocked = _blocked.asStateFlow()

    /** On our network client: while blocked nothing leaves this app, workers and the socket included. */
    val stopWhileBlocked =
        Interceptor { chain ->
            if (blocked.value) throw IOException("This app does not run on emulators.")
            chain.proceed(chain.request())
        }

    /** Why it looks like an emulator; said in the mail from the block screen. */
    var signs: List<String> = emptyList()
        private set

    fun init(context: Context) {
        if (BuildConfig.DEBUG || inTestLab(context)) return
        signs = EmulatorSigns.of(read())
        if (signs.isEmpty()) return
        val allowed = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(ALLOWED, false)
        _blocked.value = !allowed
        if (!allowed) Telemetry.emulatorBlocked(signs)
    }

    /**
     * Keeps asking whether the team has lifted the block, or put it back, for as long as the screen
     * is up, so a phone caught by mistake is let in without being reopened. A phone with no sign
     * never asks. Offline, the last answer stands. From the server, never Firestore's cached copy.
     */
    suspend fun keepChecking(context: Context) {
        if (signs.isEmpty() || BuildConfig.DEBUG) return
        while (true) {
            runCatching {
                FirebaseFirestore
                    .getInstance()
                    .collection("config")
                    .document("app")
                    .get(Source.SERVER)
                    .await()
                    .getBoolean("allowEmulators") ?: false
            }.getOrNull()?.let { allowed ->
                context
                    .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .edit()
                    .putBoolean(ALLOWED, allowed)
                    .apply()
                _blocked.value = !allowed
            }
            delay(CHECK_EVERY_MS)
        }
    }

    /** What this device says about itself. */
    fun read(): DeviceFacts =
        DeviceFacts(
            hardware = Build.HARDWARE.orEmpty(),
            product = Build.PRODUCT.orEmpty(),
            model = Build.MODEL.orEmpty(),
            manufacturer = Build.MANUFACTURER.orEmpty(),
            qemu = systemProperty("ro.kernel.qemu") == "1" || systemProperty("ro.boot.qemu") == "1",
            files = EmulatorSigns.FILES.filter { File(it).exists() }.toSet(),
        )

    private fun inTestLab(context: Context) = Settings.System.getString(context.contentResolver, "firebase.test.lab") == "true"

    /** Not in the public SDK. Unreadable reads as empty, which counts as no sign. */
    private fun systemProperty(name: String): String =
        runCatching {
            Class
                .forName("android.os.SystemProperties")
                .getMethod("get", String::class.java)
                .invoke(null, name) as? String
        }.getOrNull().orEmpty()
}
