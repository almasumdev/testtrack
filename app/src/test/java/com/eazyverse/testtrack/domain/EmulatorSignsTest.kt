package com.eazyverse.testtrack.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A wrong answer here locks a real person out, so real phones of every kind come first, including
 * the odd ones: old x86 phones, Chromebooks, cheap phones with few sensors, rooted and custom ROMs.
 */
class EmulatorSignsTest {
    private fun phone(
        hardware: String,
        product: String,
        model: String,
        maker: String,
        abi: String = "arm64-v8a",
        accelerometer: Boolean = true,
        chromebook: Boolean = false,
    ) = DeviceFacts(hardware, product, model, maker, false, emptySet(), abi, accelerometer, chromebook)

    private val realPhones =
        listOf(
            phone("mt6789", "a15ub", "SM-A155M", "samsung"),
            phone("qcom", "dm3qxxx", "SM-S918B", "samsung"),
            phone("exynos850", "a13nsxx", "SM-A135F", "samsung"),
            phone("shiba", "shiba", "Pixel 8", "Google"),
            phone("sunfish", "sunfish", "Pixel 4a", "Google"),
            phone("mt6768", "fire_global", "23053RN02A", "Xiaomi"),
            phone("qcom", "sapphire", "23129RAA4G", "Xiaomi"),
            phone("mt6765", "CPH2477", "CPH2477", "OPPO"),
            phone("mt6833", "RMX3710", "RMX3710", "realme"),
            phone("qcom", "PD2134F_EX", "V2134", "vivo"),
            phone("mt6765", "TECNO-KI5q", "TECNO KI5q", "TECNO"),
            phone("mt6768", "Infinix-X6831", "Infinix X6831", "INFINIX"),
            phone("mt6739", "Primo_H10", "Primo H10", "WALTON"),
            phone("sc9863a", "Z35", "Z35", "Symphony"),
            phone("kirin710", "MAR-LX1A", "MAR-LX1A", "HUAWEI"),
            phone("qcom", "OnePlus9", "LE2113", "OnePlus"),
            // A cheap phone with no motion sensor.
            phone("mt6580", "generic_arm", "Y5", "GENERIC", accelerometer = false),
            // A custom ROM, whose build names say little.
            phone("qcom", "lineage_lavender", "Redmi Note 7", "Xiaomi"),
            // An old Intel phone, and a Chromebook, which is x86 with no motion sensor at all.
            phone("redhookbay", "WW_Z00A", "ASUS_Z00AD", "asus", abi = "x86"),
            phone("cheets", "dedede_cheets", "Chromebook", "Google", abi = "x86_64", accelerometer = false, chromebook = true),
        )

    @Test
    fun noRealPhoneIsEverBlocked() {
        realPhones.forEach { assertEquals(it.model, emptyList<String>(), EmulatorSigns.of(it)) }
    }

    @Test
    fun aNameThatOnlyLooksLikeAnEmulatorIsNotOne() {
        // Only the exact names count: real products can begin with or contain these words.
        listOf("sdk845", "sdm660", "nox_plus", "goldfish_fan", "vbox").forEach { name ->
            assertTrue(name, EmulatorSigns.of(DeviceFacts(hardware = name, product = name)).isEmpty())
        }
    }

    @Test
    fun androidStudiosEmulatorsAreCaught() {
        val pixel = phone("ranchu", "sdk_gphone64_arm64", "sdk_gphone64_arm64", "Google")
        val older = phone("goldfish", "sdk_x86", "Android SDK built for x86", "Google", abi = "x86")
        assertTrue(EmulatorSigns.of(pixel).size >= 2)
        assertTrue(EmulatorSigns.of(older).size >= 3)
        assertEquals(listOf("qemu"), EmulatorSigns.of(DeviceFacts(qemu = true)))
    }

    @Test
    fun anEmulatorPosingAsASamsungIsCaughtByWhatItInstalled() {
        val posing = phone("qcom", "dm3qxxx", "SM-S918B", "samsung", abi = "x86_64")
        listOf("/boot/bstsetup.env", "/system/bin/ldinit", "/system/bin/nox-prop", "/system/bin/microvirtd").forEach { file ->
            assertEquals(listOf("file $file"), EmulatorSigns.of(posing.copy(files = setOf(file))))
        }
        assertEquals(
            listOf("maker Genymotion", "file /dev/socket/genyd"),
            EmulatorSigns.of(posing.copy(manufacturer = "Genymotion", files = setOf("/dev/socket/genyd"))),
        )
    }

    @Test
    fun aComputersChipWithNoMotionSensorIsCaught() {
        val pc = phone("android_x86", "android_x86_64", "Standard PC", "unknown", abi = "x86_64", accelerometer = false)
        assertEquals(listOf("x86 with no motion sensor"), EmulatorSigns.of(pc))
    }
}
