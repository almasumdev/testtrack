package com.eazyverse.testtrack.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.edit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.composables.icons.lucide.Bell
import com.composables.icons.lucide.BellOff
import com.composables.icons.lucide.Lucide
import com.eazyverse.testtrack.findActivity

private const val PREFS = "testing_notifications"
private const val ASKED = "asked"

/**
 * Whether Android has notifications off for the app, read again on every resume. A list uses it to
 * emit the tile's item only when the tile will draw, so an empty one leaves no gap behind.
 */
@Composable
fun rememberNotificationsOff(): Boolean {
    val context = LocalContext.current
    var off by remember { mutableStateOf(!NotificationManagerCompat.from(context).areNotificationsEnabled()) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) off = !NotificationManagerCompat.from(context).areNotificationsEnabled()
            }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    return off
}

/**
 * An opt-in tile above the crew tabs while a crew is running, never a launch or sign-in
 * requirement. Drawn only while notifications are off.
 *
 * While Android 13 or later can still show its own dialog it says "Turn on" and shows it. After
 * that, and on Android 12 and lower where there is no such dialog, it says "Settings" and opens
 * the app's notification settings.
 *
 * Whether the dialog has been shown is saved, not remembered by the screen. Android says to ask
 * again only after one denial, so without the saved flag a tile that had been asked would look the
 * same as one that had not, and flip when the tab is left and come back.
 */
@Composable
fun TestingNotifications(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(NotificationManagerCompat.from(context).areNotificationsEnabled()) }
    var asked by remember { mutableStateOf(wasAsked(context)) }
    // Bumped when Android's answer may have changed, so the rationale is read again.
    var checks by remember { mutableIntStateOf(0) }
    val permission =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
            granted = it
            checks++
        }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    granted = NotificationManagerCompat.from(context).areNotificationsEnabled()
                    checks++
                }
            }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    if (granted) return

    val canAsk =
        remember(asked, checks) {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                (
                    !asked ||
                        ActivityCompat.shouldShowRequestPermissionRationale(context.findActivity(), Manifest.permission.POST_NOTIFICATIONS)
                )
        }
    val ask = {
        saveAsked(context)
        asked = true
        permission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
    val settings = {
        context.startActivity(
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
        )
    }
    if (canAsk) {
        AskRow(
            "Turn on reminders",
            "When apps are left at 8 pm",
            ask,
            modifier,
            icon = Lucide.Bell,
            trailing = { RowPill("Turn on", PillKind.SOFT, touchTarget = false, onClick = ask) },
        )
    } else {
        AskRow(
            "Reminders are off",
            "Off in Android settings",
            settings,
            modifier,
            icon = Lucide.BellOff,
            trailing = { RowPill("Settings", PillKind.GHOST, touchTarget = false, onClick = settings) },
        )
    }
}

/** A row's pill with no 48dp of its own: the row around it is the target. Still used by CrewOpenTab; wave 3 moves it to [RowPill]. */
@Composable
internal fun LookOnlyPill(
    label: String,
    kind: PillKind,
    onClick: () -> Unit,
) {
    RowPill(label, kind, touchTarget = false, onClick = onClick)
}

private fun wasAsked(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(ASKED, false)

private fun saveAsked(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { putBoolean(ASKED, true) }
