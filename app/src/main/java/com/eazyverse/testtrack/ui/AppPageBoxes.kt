package com.eazyverse.testtrack.ui

import android.content.Context
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.eazyverse.testtrack.ui.theme.Tt
import com.eazyverse.testtrack.viewmodel.AppsViewModel

/*
 * The two boxes on an app's own page: the same ones the seat sheet asks, kept the same way, so an
 * owner can confirm them here and find the sheet has nothing left to ask about them.
 */

/** Whether the page has anything to ask about the boxes: a closed app Play already shows. */
internal fun AppEntry.takesBoxes(): Boolean = !live && verified

@Composable
internal fun AppSeatBoxes(
    app: AppEntry,
    vm: AppsViewModel,
    context: Context,
) {
    LaunchedEffect(Unit) { vm.attach(context) }
    SectionTitle("Before a crew")
    CrewCard {
        Text(
            "Both are needed, so every member can install it.",
            style = MaterialTheme.typography.bodySmall,
            color = Tt.colors.text2,
        )
        Spacer(Modifier.height(2.dp))
        CrewSeatBoxes(vm.boxes(app), onChange = { vm.setBoxes(context, app, it) })
    }
}
