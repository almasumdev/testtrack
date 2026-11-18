package com.eazyverse.testtrack.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.eazyverse.testtrack.ui.theme.Tt

/** A running crew: the reader's app, their work, and the testers opening their app. */
@Composable
fun CrewListRow(
    crew: Crew,
    onOpen: () -> Unit,
) {
    val mine = crew.mine
    val tone =
        when {
            crew.out -> Tone.BAD
            crew.atRisk && crew.owed > 0 -> Tone.WARN
            crew.nightsDone || crew.owed == 0 -> Tone.OK
            else -> Tone.ACCENT
        }
    CrewCard(padding = PaddingValues(16.dp), onClick = onOpen) {
        CrewTileHeading(crew.name, "Night ${crew.day.coerceIn(0, crew.runDays)} of ${crew.runDays}")
        val showOpens = !crew.out && !crew.nightsDone && crew.apps.isNotEmpty()
        if (showOpens) {
            Spacer(Modifier.height(14.dp))
            CrewRunAppIcons(crew)
        }
        Spacer(Modifier.height(if (showOpens) 9.dp else 14.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Text(
                when {
                    crew.out -> "Your app is out"
                    crew.nightsDone -> "Your nights are done"
                    crew.owed == 0 -> "Done for tonight"
                    else -> "${crew.owed} ${if (crew.owed == 1) "app" else "apps"} left tonight"
                },
                Modifier.weight(1f),
                style = tileStyle(15f, 20f, FontWeight.Medium),
                color = if (tone == Tone.ACCENT) Tt.colors.text else tone.ink(),
            )
            if (!crew.out && mine != null && crew.testers.isNotEmpty()) {
                Spacer(Modifier.width(12.dp))
                Text(
                    "${crew.openedMineTonight} of ${crew.testers.size} opened yours",
                    Modifier.weight(1f),
                    style = tileStyle(12f, 17f, FontWeight.Normal),
                    color = Tt.colors.text3,
                    textAlign = TextAlign.End,
                )
            }
        }
        if (crew.atRisk && !crew.out && crew.owed > 0) {
            Spacer(Modifier.height(8.dp))
            Text(
                "Missed yesterday. Finish tonight to keep your app in.",
                style = tileStyle(12f, 17f, FontWeight.Normal),
                color = Tone.WARN.ink(),
            )
        }
        CrewTileApp(mine) { CrewChatTileBadge(crew) }
    }
}

@Composable
internal fun CrewRunRowLoading() {
    CrewCard(padding = PaddingValues(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Skeleton(width = 78.dp, height = 23.dp)
            Skeleton(width = 65.dp, height = 17.dp)
        }
        Spacer(Modifier.height(14.dp))
        CrewRunAppIconsLoading()
        Spacer(Modifier.height(9.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Skeleton(height = 20.dp, modifier = Modifier.weight(1f))
            Skeleton(height = 17.dp, modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Skeleton(width = 28.dp, height = 28.dp, corner = 8.dp)
            Spacer(Modifier.width(8.dp))
            Skeleton(width = 130.dp, height = 18.dp)
        }
    }
}
