package com.eazyverse.testtrack.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Work that must finish even when the page that started it is gone, such as a chat message on its
 * way. It runs on the main thread like a view model's own scope, so state a view model keeps in
 * plain maps is still touched from one thread. A failure in one job does not stop the others.
 */
object AppScope {
    val main = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
}
