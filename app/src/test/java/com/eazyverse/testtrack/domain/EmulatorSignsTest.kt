package com.eazyverse.testtrack.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A wrong answer here locks a real person out, so real phones of every kind come first, including
 * the odd ones: old x86 phones, Chromebooks, mini-PCs, cheap phones and custom ROMs.
 */
class EmulatorSignsTest {
    private fun device(
        hardware: String,
        product: String,
        model: String,
        maker: String,
    ) = DeviceFacts(hardware, product, model, maker)

    private val realDevices =
        listOf(
            device("mt6789", "a15ub", "SM-A155M", "samsung"),
            device("qcom", "dm3qxxx", "SM-S918B", "samsung"),
            device("exynos850", "a13nsxx", "SM-A135F", "samsung"),
            device("shiba", "shiba", "Pixel 8", "Google"),
            device("sunfish", "sunfish", "Pixel 4a", "Google"),
            device("mt6768", "fire_global", "23053RN02A", "Xiaomi"),
            device("qcom", "sapphire", "23129RAA4G", "Xiaomi"),
            device("mt6765", "CPH2477", "CPH2477", "OPPO"),
            device("mt6833", "RMX3710", "RMX3710", "realme"),
            device("qcom", "PD2134F_EX", "V2134", "vivo"),
            device("mt6765", "TECNO-KI5q", "TECNO KI5q", "TECNO"),
            device("mt6768", "Infinix-X6831", "Infinix X6831", "INFINIX"),
            device("mt6739", "Primo_H10", "Primo H10", "WALTON"),
            device("sc9863a", "Z35", "Z35", "Symphony"),
            device("kirin710", "MAR-LX1A", "MAR-LX1A", "HUAWEI"),
            device("qcom", "OnePlus9", "LE2113", "OnePlus"),
            // Build names that say little: a cheap phone, and a custom ROM.
            device("mt6580", "generic_arm", "Y5", "GENERIC"),
            device("qcom", "lineage_lavender", "Redmi Note 7", "Xiaomi"),
            // An old Intel phone, a Chromebook and an Android mini-PC: x86, and not emulators.
            device("redhookbay", "WW_Z00A", "ASUS_Z00AD", "asus"),
            device("cheets", "dedede_cheets", "Chromebook", "Google"),
            device("android_x86", "android_x86_64", "Standard PC", "unknown"),
        )

    @Test
    fun noRealDeviceIsEverBlocked() {
        realDevices.forEach { assertEquals(it.model, emptyList<String>(), EmulatorSigns.of(it)) }
    }

    @Test
    fun aNameThatOnlyLooksLikeAnEmulatorIsNotOne() {
        // Only the exact names count: real products can begin with or contain these words, and a
        // short word like these could be a real phone's codename.
        listOf("sdk", "nox", "sdk845", "sdm660", "nox_plus", "goldfish_fan", "vbox").forEach { name ->
            assertTrue(name, EmulatorSigns.of(DeviceFacts(hardware = name, product = name)).isEmpty())
        }
    }

    @Test
    fun androidStudiosEmulatorsAreCaught() {
        val pixel = device("ranchu", "sdk_gphone64_arm64", "sdk_gphone64_arm64", "Google")
        val older = device("goldfish", "sdk_x86", "Android SDK built for x86", "Google")
        assertTrue(EmulatorSigns.of(pixel).size >= 2)
        assertTrue(EmulatorSigns.of(older).size >= 3)
        assertEquals(listOf("qemu"), EmulatorSigns.of(DeviceFacts(qemu = true)))
    }

    @Test
    fun anEmulatorPosingAsASamsungIsCaughtByWhatItInstalled() {
        val posing = device("qcom", "dm3qxxx", "SM-S918B", "samsung")
        listOf("/boot/bstmods", "/system/bin/ldinit", "/system/bin/nox-prop", "/system/bin/microvirtd").forEach { file ->
            assertEquals(listOf("file $file"), EmulatorSigns.of(posing.copy(files = setOf(file))))
        }
        assertEquals(
            listOf("maker Genymotion", "file /dev/socket/genyd"),
            EmulatorSigns.of(posing.copy(manufacturer = "Genymotion", files = setOf("/dev/socket/genyd"))),
        )
    }
}
