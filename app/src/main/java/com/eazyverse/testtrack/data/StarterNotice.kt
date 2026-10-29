package com.eazyverse.testtrack.data

import android.content.Context
import androidx.core.content.edit

/**
 * Telling an account, once, that its phone already gave the starter tix to another account.
 *
 * The starter tix are once per phone, so a second account on a phone starts at 0. Without a word
 * that reads as tix gone missing, and people wrote in about it. Sign-in answers which it was; this
 * keeps that answer until the sign-in sheet shows it, and remembers per account that it was shown,
 * so the same account signing in again is not told twice.
 */
object StarterNotice {
    private const val PREFS = "starter_notice"

    /** The account sign-in said should hear it, until the sheet takes it. */
    @Volatile private var pending: String? = null

    /** What sign-in answered for this account: given, phone_used or none. */
    fun heard(
        context: Context,
        account: String,
        starter: String,
    ) {
        if (starter != "phone_used") return
        if (prefs(context).getBoolean(account, false)) return
        pending = account
    }

    /** Whether to say it now; true once per account, and from then on false. */
    fun take(context: Context): Boolean {
        val account = pending ?: return false
        pending = null
        prefs(context).edit { putBoolean(account, true) }
        return true
    }

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
