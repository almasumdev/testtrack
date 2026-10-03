package com.eazyverse.testtrack.domain

/** Publish login success only after the account has a usable application session. */
suspend fun <T> completeSignIn(
    authenticate: suspend () -> T,
    establishSession: suspend (T) -> Unit,
    publish: (T) -> Unit,
): T {
    val account = authenticate()
    establishSession(account)
    publish(account)
    return account
}
