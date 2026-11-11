package com.eazyverse.testtrack.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

/**
 * What the phone says about its connection, in two strengths that are used for different things.
 *
 * [hasNetwork] is the loose one: some network is active and meant to carry traffic. It is what
 * gates starting a sign-in, because VALIDATED can be false while the internet works (some OEM
 * ROMs, private DNS, some VPNs and carriers), and blocking on it would shut those phones out.
 *
 * [isOnline] is the strict one: Android also got a reply from the internet over it. A Wi-Fi with
 * no uplink has the first flag and not the second, so this is only for sorting a failure after
 * the fact, never for refusing to try.
 */
object Connectivity {
    fun hasNetwork(context: Context): Boolean {
        val manager = context.getSystemService(ConnectivityManager::class.java) ?: return true
        val caps = manager.getNetworkCapabilities(manager.activeNetwork ?: return false) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    fun isOnline(context: Context): Boolean {
        val manager = context.getSystemService(ConnectivityManager::class.java) ?: return true
        val caps = manager.getNetworkCapabilities(manager.activeNetwork ?: return false) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
}
