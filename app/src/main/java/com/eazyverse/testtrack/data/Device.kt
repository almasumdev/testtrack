package com.eazyverse.testtrack.data

import android.annotation.SuppressLint
import android.content.Context
import android.provider.Settings
import java.security.MessageDigest

/**
 * Which phone this is, as a value that can be compared but not read.
 *
 * Play counts testers, not accounts, and two Google accounts on one handset are one tester to it.
 * A cohort that spends a fortnight with two of its fourteen slots filled by the same phone finds
 * out on day fifteen, when somebody's submission is turned down and thirteen other people's time
 * has gone with it. Nothing here prevents that. It exists so an admin can see it on day two.
 *
 * [Settings.Secure.ANDROID_ID] is the right identifier for this and the wrong one to store. Since
 * Android 8 it is scoped to the app's signing key and the Android user, so it is stable across
 * reinstalls and cannot be used to follow anybody between apps. It is still a device identifier,
 * so what leaves the phone is a SHA-256 of it under a constant that only this app carries. Two
 * accounts on one handset produce the same string; the string says nothing about the handset.
 *
 * What it does not catch, said plainly rather than discovered later:
 *
 *  - A factory reset issues a new id, so the same phone comes back as a new one.
 *  - A second Android user or a work profile has its own id, so one phone can present two.
 *  - Anyone determined to get around it can.
 *
 * Which is why this feeds a flag an admin reads and not a door the app closes. It catches the
 * ordinary case, which is somebody helping a friend out without knowing it does not work.
 */
object Device {

    /**
     * Not a secret and not pretending to be one.
     *
     * It is in the APK, so anybody who wants the constant can have it. Its job is narrower than
     * that: without it the stored value would be a bare hash of an id that is guessable in bulk,
     * and a leaked export could be matched against a rainbow table of every possible ANDROID_ID.
     * With it, a leaked export is a column of numbers that mean nothing outside this app.
     */
    private const val SALT = "testtrack.device.v1"

    @SuppressLint("HardwareIds")
    fun fingerprint(context: Context): String? {
        val raw = Settings.Secure.getString(
            context.applicationContext.contentResolver,
            Settings.Secure.ANDROID_ID
        )

        // Null on a device that will not say, and the string of zeroes is a known emulator answer.
        // Either way there is nothing to compare, and a shared placeholder would flag every one of
        // those phones as the same handset.
        if (raw.isNullOrBlank() || raw.all { it == '0' }) return null

        val digest = MessageDigest.getInstance("SHA-256").digest((SALT + raw).toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }
}
