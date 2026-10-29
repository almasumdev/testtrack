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
 *
 * The answer is kept with the account it is for. A session refreshed in the background answers
 * too, so without that one account's answer could be shown to the next account that signs in.
 */
object StarterNotice {
    private const val PREFS = "starter_notice"

    /** The account sign-in said should hear it, until the sheet takes it. */
    @Volatile private var pending: String? = null

    /** What sign-in answered for this account: given, phone_used or none. Replaces any older answer. */
    fun heard(
        context: Context,
        account: String,
        starter: String,
    ) {
        pending = pendingAfter(account, starter, shown = starter == PHONE_USED && prefs(context).getBoolean(account, false))
    }

    /** Whether to say it now to [account]; true once per account, and from then on false. */
    fun take(
        context: Context,
        account: String?,
    ): Boolean {
        val due = pending
        pending = null
        if (account == null || !isFor(due, account)) return false
        prefs(context).edit { putBoolean(account, true) }
        return true
    }

    /** Who should hear it after sign-in answered [starter] for [account]: only this answer counts. */
    internal fun pendingAfter(
        account: String,
        starter: String,
        shown: Boolean,
    ): String? = account.takeIf { starter == PHONE_USED && !shown }

    /** The notice waiting is for the account that just signed in. */
    internal fun isFor(
        pending: String?,
        account: String?,
    ) = pending != null && pending == account

    private const val PHONE_USED = "phone_used"

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
