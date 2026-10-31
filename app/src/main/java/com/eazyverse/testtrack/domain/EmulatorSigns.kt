package com.eazyverse.testtrack.domain

/**
 * What a phone says about itself, read once at start (data/EmulatorGate.kt). Plain values, so what
 * follows from them can be tested against phones nobody here owns.
 */
data class DeviceFacts(
    val hardware: String = "",
    val product: String = "",
    val model: String = "",
    val manufacturer: String = "",
    /** ro.kernel.qemu or ro.boot.qemu says 1. */
    val qemu: Boolean = false,
    /** Which of [EmulatorSigns.FILES] exist here. */
    val files: Set<String> = emptySet(),
)

/**
 * Whether this is an emulator, and how we know. Testers have to be real people on real phones.
 *
 * It blocks somebody outright, and a real phone blocked is worse than an emulator missed, so only
 * names and files that belong to emulator products count. That leaves out the signs most lists use:
 * "generic" in a fingerprint and test-keys are on cheap phones and custom ROMs, a missing sensor or
 * SIM on cheap phones and tablets, x86 on old Asus phones, Chromebooks and Android mini-PCs, and a
 * few qemu files on some real phones' ROMs. Root is no sign at all. Somebody who changes an emulator
 * to hide these gets past it, and somebody who makes a real phone claim these names gets blocked;
 * the off switch is for that (data/EmulatorGate.kt).
 */
object EmulatorSigns {
    /** What the emulator products themselves put on the system. */
    val FILES =
        listOf(
            "/dev/socket/genyd",
            "/dev/socket/baseband_genyd",
            "/boot/bstmods",
            "/sys/module/bstinput",
            "/mnt/windows/BstSharedFolder",
            "/data/.bluestacks.prop",
            "/system/bin/nox-prop",
            "/system/lib/libnoxspeedup.so",
            "/system/bin/microvirt-prop",
            "/system/bin/microvirtd",
            "/system/bin/ldinit",
            "/system/bin/ldmountsf",
        )

    private val HARDWARE = setOf("goldfish", "ranchu", "vbox86", "cutf_cvm")
    private val PRODUCTS = setOf("sdk_x86", "sdk_x86_64", "vbox86p")
    private val PRODUCT_STARTS = listOf("sdk_gphone", "sdk_google", "google_sdk")

    /** Every reason this looks like an emulator; empty for a real phone. */
    fun of(facts: DeviceFacts): List<String> =
        buildList {
            if (facts.qemu) add("qemu")
            if (facts.hardware.lowercase() in HARDWARE) add("hardware ${facts.hardware}")
            val product = facts.product.lowercase()
            if (product in PRODUCTS || PRODUCT_STARTS.any(product::startsWith)) add("product ${facts.product}")
            if (facts.model.startsWith("Android SDK built for")) add("model ${facts.model}")
            if (facts.manufacturer.equals("Genymotion", ignoreCase = true)) add("maker Genymotion")
            facts.files.forEach { add("file $it") }
        }
}
