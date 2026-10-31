package com.eazyverse.testtrack.data

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.Build
import android.provider.Settings
import com.eazyverse.testtrack.BuildConfig
import com.eazyverse.testtrack.domain.DeviceFacts
import com.eazyverse.testtrack.domain.EmulatorSigns
import com.google.firebase.firestore.FirebaseFirestore
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
        signs = EmulatorSigns.of(read(context))
        if (signs.isEmpty()) return
        val allowed = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(ALLOWED, false)
        _blocked.value = !allowed
        if (!allowed) Telemetry.emulatorBlocked(signs)
    }

    /** Asks whether the team has lifted the block. Offline, the last answer stands. */
    suspend fun refresh(context: Context) {
        if (signs.isEmpty()) return
        val allowed =
            runCatching {
                FirebaseFirestore
                    .getInstance()
                    .collection("config")
                    .document("app")
                    .get()
                    .await()
                    .getBoolean("allowEmulators") ?: false
            }.getOrNull() ?: return
        context
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(ALLOWED, allowed)
            .apply()
        _blocked.value = !allowed
    }

    /** What this device says about itself. */
    fun read(context: Context): DeviceFacts {
        val sensors = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        return DeviceFacts(
            hardware = Build.HARDWARE.orEmpty(),
            product = Build.PRODUCT.orEmpty(),
            model = Build.MODEL.orEmpty(),
            manufacturer = Build.MANUFACTURER.orEmpty(),
            qemu = systemProperty("ro.kernel.qemu") == "1" || systemProperty("ro.boot.qemu") == "1",
            files = EmulatorSigns.FILES.filter { File(it).exists() }.toSet(),
            firstAbi = Build.SUPPORTED_ABIS.firstOrNull().orEmpty(),
            hasAccelerometer = sensors?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null,
            chromebook = context.packageManager.hasSystemFeature("org.chromium.arc"),
        )
    }

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
