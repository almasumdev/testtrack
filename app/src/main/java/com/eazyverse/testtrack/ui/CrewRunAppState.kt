package com.eazyverse.testtrack.ui

import androidx.compose.ui.graphics.vector.ImageVector
import com.composables.icons.lucide.CircleCheck
import com.composables.icons.lucide.CloudUpload
import com.composables.icons.lucide.Download
import com.composables.icons.lucide.Flag
import com.composables.icons.lucide.Image
import com.composables.icons.lucide.LogOut
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.MessageCircle
import com.composables.icons.lucide.Moon
import com.composables.icons.lucide.RefreshCw

internal data class CrewRunAppState(
    val text: String,
    val icon: ImageVector,
    val tone: Tone = Tone.QUIET,
    val action: String? = null,
    val run: () -> Unit = {},
    val feedbackAction: Boolean = false,
    val explanation: String? = null,
    val alternate: String? = null,
    val runAlternate: () -> Unit = {},
)

/** Installation and upload facts stay on the phone; obligations still come from the server. */
internal fun crewRunAppState(
    app: CrewApp,
    seat: CrewSeat,
    kind: CrewRunAppKind,
    actions: CrewActions,
): CrewRunAppState {
    val installed = actions.isInstalled(app.packageName)
    val notPlay = installed && !actions.fromPlay(app.packageName)
    val old = installed && olderHere(app, actions)
    return when {
        kind == CrewRunAppKind.LEFT ->
            CrewRunAppState(
                "Left the run",
                Lucide.LogOut,
                action = if (outLongEnough(app.outAt)) "Uninstall" else null,
                run = { actions.onUninstall(app.packageName) },
                explanation = "You no longer need to test this app.",
            )
        app.reported ->
            CrewRunAppState(
                when {
                    !installed -> "Reported · not installed"
                    notPlay -> "Reported · Play copy needed"
                    old -> "Reported · update needed"
                    else -> "Reported for tonight"
                },
                Lucide.Flag,
                Tone.WARN,
                action =
                    when {
                        !installed || notPlay -> "Install"
                        old -> "Update"
                        else -> "Test again"
                    },
                run = {
                    when {
                        !installed || notPlay -> actions.onInstall(seat)
                        old -> actions.onUpdateApp(app)
                        else -> actions.onOpen(app)
                    }
                },
            )
        !installed || notPlay ->
            CrewRunAppState(
                if (notPlay) "Play copy required" else "Not installed",
                Lucide.Download,
                action = "Install",
                run = { actions.onInstall(seat) },
                explanation =
                    if (notPlay) {
                        "This copy was installed outside Google Play. Install the Play copy to count your tests."
                    } else {
                        null
                    },
            )
        old ->
            CrewRunAppState(
                if (app.updateRequired) "Update required" else "Update available",
                Lucide.RefreshCw,
                Tone.WARN,
                action = "Update",
                run = { actions.onUpdateApp(app) },
                explanation = updateLine(app, app.owner),
                alternate = if (app.updateRequired) null else "Open",
                runAlternate = { actions.onOpen(app) },
            )
        actions.sendingPicture(app) -> {
            val late = actions.latePicture(app.id)
            val failed = actions.picturesStuck != null
            CrewRunAppState(
                when {
                    late -> "Upload waiting"
                    failed -> "Upload failed"
                    else -> "Uploading screenshot"
                },
                Lucide.CloudUpload,
                if (late || failed) Tone.WARN else Tone.QUIET,
                action = if (late || failed) "Retry" else null,
                run = actions.onRetryPictures,
                explanation =
                    when {
                        late -> "Your screenshot is saved on this phone and has waited over 3 hours. Retry the upload to finish this test."
                        failed -> "Your screenshot is saved on this phone. Retry to finish this test. ${actions.picturesStuck.orEmpty()}"
                        else -> "This test will count once the upload finishes."
                    },
            )
        }
        app.pictureDue ->
            CrewRunAppState(
                "Screenshot needed",
                Lucide.Image,
                Tone.WARN,
                "Add screenshot",
                { actions.onOpen(app) },
                explanation = "Your app time is saved. Add an app screenshot to count this test.",
            )
        app.done -> CrewRunAppState("Opened tonight", Lucide.CircleCheck, Tone.OK)
        app.feedbackLate && kind == CrewRunAppKind.REQUIRED ->
            CrewRunAppState(
                "Feedback overdue",
                Lucide.MessageCircle,
                Tone.WARN,
                "Write",
                { actions.onFeedback(app) },
                feedbackAction = true,
                explanation = "Give private feedback on Google Play before testing can count.",
            )
        kind == CrewRunAppKind.OPTIONAL -> CrewRunAppState("Optional tonight", Lucide.Moon, action = "Open", run = { actions.onOpen(app) })
        else -> CrewRunAppState("Ready to test", Lucide.Moon, action = "Open", run = { actions.onOpen(app) })
    }
}
