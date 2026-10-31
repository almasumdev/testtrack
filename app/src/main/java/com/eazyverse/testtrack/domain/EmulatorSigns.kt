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
    val firstAbi: String = "",
    val hasAccelerometer: Boolean = true,
    val chromebook: Boolean = false,
)

/**
 * Whether this is an emulator, and how we know. Testers have to be real people on real phones.
 *
 * It blocks somebody outright, so only what no real phone has counts. That leaves out the signs
 * most lists use: "generic" in a fingerprint and test-keys are on cheap phones and custom ROMs,
 * a missing sensor or SIM on cheap phones and tablets, x86 on old Asus phones and Chromebooks, and
 * a few qemu files on some real phones' ROMs. Root is no sign at all. Somebody who changes an
 * emulator to hide these gets past it; this is for everybody else.
 */
object EmulatorSigns {
    /** What the emulator products themselves put on the system. No phone maker ships these. */
    val FILES =
        listOf(
            "/dev/socket/genyd",
            "/dev/socket/baseband_genyd",
            "/sys/module/vboxsf",
            "/boot/bstsetup.env",
            "/boot/bstmods",
            "/sys/module/bstinput",
            "/sys/module/bstvmsg",
            "/mnt/windows/BstSharedFolder",
            "/data/.bluestacks.prop",
            "/system/bin/nox",
            "/system/bin/nox-prop",
            "/system/lib/libnoxspeedup.so",
            "/system/bin/microvirt-prop",
            "/system/bin/microvirtd",
            "/system/bin/ldinit",
            "/system/bin/ldmountsf",
            "/system/lib/libldutils.so",
        )

    private val HARDWARE = setOf("goldfish", "ranchu", "vbox86", "cutf_cvm")
    private val PRODUCTS = setOf("sdk", "sdk_x86", "sdk_x86_64", "vbox86p", "nox")
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
            // Two weak signs together: a computer's chip with no motion sensor. Neither alone is
            // enough, and a Chromebook is both and is a real device somebody can test on.
            if (facts.firstAbi.startsWith("x86") && !facts.hasAccelerometer && !facts.chromebook) {
                add("x86 with no motion sensor")
            }
        }
}
