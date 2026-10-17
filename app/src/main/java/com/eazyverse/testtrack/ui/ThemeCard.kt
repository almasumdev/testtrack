package com.eazyverse.testtrack.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.SunMoon
import com.eazyverse.testtrack.ui.theme.OceanDark
import com.eazyverse.testtrack.ui.theme.OceanLight
import com.eazyverse.testtrack.ui.theme.Tt
import com.eazyverse.testtrack.ui.theme.TtTokens
import com.eazyverse.testtrack.viewmodel.ThemeChoice
import com.eazyverse.testtrack.viewmodel.ThemeMode

/**
 * Appearance on the You page: the theme card Value Guard and ListKin have, here without their
 * colour row, because TestTrack has the one palette.
 *
 * The app in miniature in each theme, its name under it, and a ring with a tick round the one in
 * use. System shows both, split corner to corner, because it is both depending on the phone. It
 * needs no account, so it is on the page whether or not anybody is signed in.
 */
@Composable
internal fun AppearanceGroup() {
    val context = LocalContext.current
    val t = Tt.colors
    val shape = TilePlace.ONLY.shape()
    Column {
        SectionTitle("Appearance")
        Column(
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(t.glass)
                .border(1.dp, t.glassBorder, shape)
                .padding(6.dp),
        ) {
            // Laid out like a settings row, so the card reads as one of the rows that opens up.
            Row(Modifier.padding(start = 8.dp, top = 6.dp, end = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconTile(Lucide.SunMoon)
                Spacer(Modifier.width(14.dp))
                Text(
                    "Theme",
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp, lineHeight = 20.sp),
                    fontWeight = FontWeight.SemiBold,
                    color = t.text,
                )
            }
            Row {
                for (mode in ThemeMode.entries) {
                    ThemeOption(
                        mode,
                        selected = ThemeChoice.mode == mode,
                        modifier = Modifier.weight(1f),
                    ) { ThemeChoice.choose(context, mode) }
                }
            }
        }
    }
}

private val ThemeMode.label
    get() =
        when (this) {
            ThemeMode.SYSTEM -> "System"
            ThemeMode.LIGHT -> "Light"
            ThemeMode.DARK -> "Dark"
        }

@Composable
private fun ThemeOption(
    mode: ThemeMode,
    selected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val t = Tt.colors
    val ring by animateColorAsState(if (selected) t.accentInk else Color.Transparent, tween(200), label = "ring")
    // Inset from the card's corner by its 6 of padding, so the two curves run parallel.
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .selectable(selected, role = Role.RadioButton, onClick = onClick)
            .padding(start = 6.dp, top = 6.dp, end = 6.dp, bottom = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box {
            Box(Modifier.border(2.dp, ring, RoundedCornerShape(14.dp)).padding(3.dp)) {
                // A hairline round the thumbnail itself, or the light one melts into the light card.
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(80.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .border(1.dp, t.glassBorder, RoundedCornerShape(10.dp)),
                ) {
                    when (mode) {
                        ThemeMode.LIGHT -> MiniScreen(OceanLight)
                        ThemeMode.DARK -> MiniScreen(OceanDark)
                        ThemeMode.SYSTEM -> {
                            MiniScreen(OceanLight)
                            MiniScreen(OceanDark, Modifier.clip(LowerRightHalf))
                        }
                    }
                }
            }
            if (selected) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 5.dp, y = (-5).dp)
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(t.accent)
                        .border(2.dp, t.glass, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Lucide.Check, null, Modifier.size(11.dp), tint = t.onAccent)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            mode.label,
            fontSize = 13.5.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
            color = if (selected) t.accentInk else t.text2,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** The lower-right half of a rectangle, cut corner to corner. */
private val LowerRightHalf =
    GenericShape { size, _ ->
        moveTo(size.width, 0f)
        lineTo(size.width, size.height)
        lineTo(0f, size.height)
        close()
    }

/**
 * TestTrack's Crew tab in miniature, in one theme whichever is showing: the header with the tix
 * pill, a crew card with its app tile and nights in boxes, a second card, and the floating dock.
 */
@Composable
private fun MiniScreen(
    t: TtTokens,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxSize()
            .background(t.bg)
            .padding(start = 6.dp, top = 6.dp, end = 6.dp, bottom = 5.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Bar(22.dp, 3.5.dp, t.text)
            Spacer(Modifier.weight(1f))
            Box(
                Modifier.size(15.dp, 7.dp).clip(CircleShape).background(t.tixBottom),
                contentAlignment = Alignment.CenterStart,
            ) {
                Box(
                    Modifier
                        .padding(start = 2.dp)
                        .size(3.5.dp)
                        .clip(CircleShape)
                        .background(t.gold),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        MiniCard(t) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(9.dp).clip(RoundedCornerShape(3.dp)).background(t.tile))
                Spacer(Modifier.width(4.dp))
                Bar(18.dp, 2.5.dp, t.text2)
            }
            Spacer(Modifier.height(3.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                repeat(7) { night ->
                    Box(
                        Modifier
                            .weight(1f)
                            .height(3.dp)
                            .clip(RoundedCornerShape(1.dp))
                            .background(if (night < 4) t.accent else t.glass2),
                    )
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        MiniCard(t) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(7.dp).clip(RoundedCornerShape(2.dp)).background(t.tile))
                Spacer(Modifier.width(4.dp))
                Bar(16.dp, 2.5.dp, t.text4)
            }
        }
        Spacer(Modifier.weight(1f))
        Row(
            Modifier
                .align(Alignment.CenterHorizontally)
                .clip(CircleShape)
                .background(t.dock)
                .border(0.6.dp, t.glassBorder, CircleShape)
                .padding(horizontal = 4.dp, vertical = 2.5.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Bar(9.dp, 4.dp, t.leafAccent)
            repeat(3) { Box(Modifier.size(3.dp).clip(CircleShape).background(t.dockInk)) }
        }
    }
}

@Composable
private fun MiniCard(
    t: TtTokens,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(4.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(t.glass)
            .border(0.6.dp, t.glassBorder, shape)
            .padding(3.dp),
        content = content,
    )
}

@Composable
private fun Bar(
    width: Dp,
    height: Dp,
    color: Color,
) {
    Box(Modifier.size(width, height).clip(RoundedCornerShape(height / 2)).background(color))
}
