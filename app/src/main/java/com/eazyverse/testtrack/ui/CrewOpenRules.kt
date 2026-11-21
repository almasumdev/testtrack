package com.eazyverse.testtrack.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.CalendarDays
import com.composables.icons.lucide.ChevronDown
import com.composables.icons.lucide.Coins
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Moon
import com.composables.icons.lucide.TriangleAlert
import com.eazyverse.testtrack.ui.theme.Tt

/*
 * The Open tab's rules card (docs/design/crew-open/rules.html, option A). Closed it is six fact
 * tiles; "All rules" opens the checked list in place. No rules between anything: spacing only.
 * Figures come from CrewRules and the crew's own run length, never a literal.
 */

private val TileFigure = TextStyles.RuleFigure
private val TileUnit = TextStyles.RuleUnit
private val TileLabel = TextStyles.RuleLabel
private val LineStyle = TextStyles.RuleLine
private val HeadingStyle = TextStyles.RuleHeading

@Composable
internal fun CrewRulesCard(runDays: Int = CrewRules.RUN_DAYS) {
    var open by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth()) {
        SmallHeading("The rules", top = 0.dp)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FactTile("${CrewRules.SEATS}", "apps", "in a crew")
                FactTile("$runDays", "nights", "from your midnight")
                FactTile("${CrewRules.SEAT_COST}", "tix", "a seat")
            }
            Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FactTile("${CrewRules.OPEN_SECONDS}", "s", "each app, each night")
                FactTile("${CrewRules.FEEDBACK_BY_NIGHT}", "nights", "to send Play feedback")
                FactTile("${CrewRules.MISSES_IN_A_ROW}", "misses", "in a row and you're out", Tone.BAD)
            }
        }
        AnimatedVisibility(
            visible = open,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            AllRules()
        }
        RulesToggle(open) { open = !open }
    }
}

/** The one control that opens the rest: a full-width soft row at the bottom of the card. */
@Composable
private fun RulesToggle(
    open: Boolean,
    onToggle: () -> Unit,
) {
    val turn by animateFloatAsState(if (open) 180f else 0f, label = "rulesChevron")
    val shape = RoundedCornerShape(10.dp)
    Box(
        Modifier
            .padding(top = 12.dp)
            .fillMaxWidth()
            .height(48.dp)
            .clip(shape)
            .background(Tt.colors.tile)
            .clickable(onClick = onToggle),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (open) "Show less" else "All rules",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = Tt.colors.accentInk,
                maxLines = 1,
            )
            Spacer(Modifier.width(4.dp))
            Icon(Lucide.ChevronDown, null, Modifier.size(18.dp).rotate(turn), tint = Tt.colors.accentInk)
        }
    }
}

/** A big figure with its unit on one line and a short label under it. */
@Composable
private fun RowScope.FactTile(
    figure: String,
    unit: String,
    label: String,
    tone: Tone = Tone.ACCENT,
) {
    val ink = if (tone == Tone.BAD) tone.ink() else Tt.colors.tabSelected
    Column(
        Modifier
            .weight(1f)
            .fillMaxHeight()
            .tileFrame(RoundedCornerShape(12.dp), fill = Color.Transparent)
            .padding(start = 10.dp, top = 9.dp, end = 10.dp, bottom = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(figure, style = TileFigure, color = ink, maxLines = 1)
            Spacer(Modifier.width(3.dp))
            Text(
                unit,
                Modifier.padding(bottom = 2.dp),
                style = TileUnit,
                color = Tt.colors.text3,
                maxLines = 1,
                overflow = TextOverflow.Clip,
            )
        }
        Text(label, Modifier.padding(top = 3.dp), style = TileLabel, color = Tt.colors.text3)
    }
}

// ------------------------------------------------------------------ the full list

@Composable
private fun AllRules() {
    val bold = SpanStyle(fontWeight = FontWeight.SemiBold, color = Tt.colors.text)
    val n = { value: Int -> "$value" }

    fun line(build: androidx.compose.ui.text.AnnotatedString.Builder.() -> Unit) = buildAnnotatedString(build)

    Column {
        RuleGroup(Lucide.CalendarDays, Tone.ACCENT, "The run") {
            RuleLine(
                line {
                    append("Starts at each member's first midnight after all ")
                    withStyle(bold) { append(n(CrewRules.SEATS)) }
                    append(" apps are checked in. Each day runs midnight to midnight, your time.")
                },
            )
            RuleLine(line { append("An app sent back puts the crew back to filling.") })
        }
        RuleGroup(Lucide.Moon, Tone.ACCENT, "Each night") {
            RuleLine(
                line {
                    append("Each app is open ")
                    withStyle(bold) { append(n(CrewRules.OPEN_SECONDS)) }
                    append(" seconds. It counts with ")
                    withStyle(bold) { append(n(CrewRules.VISIT_SECONDS)) }
                    append(" in front and its picture.")
                },
            )
            RuleLine(line { append("Only installs from Google Play count. A copy from anywhere else doesn't.") })
            RuleLine(
                line {
                    append("An old version stops counting ")
                    withStyle(bold) { append(n(CrewRules.UPDATE_GRACE_HOURS)) }
                    append(" hours after the owner's update.")
                },
            )
            RuleLine(
                line {
                    append("Send private feedback on Play by night ")
                    withStyle(bold) { append(n(CrewRules.FEEDBACK_BY_NIGHT)) }
                    append(" with each app, or its opens stop counting.")
                },
            )
        }
        RuleGroup(Lucide.TriangleAlert, Tone.WARN, "Missed days") {
            RuleLine(
                line {
                    append("Only apps you owed that night count. A day is missed when ")
                    withStyle(bold) { append(n(CrewRules.MISS_UNOPENED)) }
                    append(" or more of them stay unopened, or you open none.")
                },
            )
            RuleLine(
                line {
                    withStyle(bold) { append("One") }
                    append(" missed day is a warning. ")
                    withStyle(bold) { append(n(CrewRules.MISSES_IN_A_ROW)) }
                    append(" in a row and your app leaves the run.")
                },
            )
        }
        RuleGroup(Lucide.Coins, Tone.ACCENT, "Your ${CrewRules.SEAT_COST} tix") {
            RuleLine(line { append("All back if you leave while the crew fills.") })
            RuleLine(
                line {
                    append("All back if ")
                    withStyle(bold) { append(n(CrewRules.REPORTS_TO_RETURN)) }
                    append(" members can't install it before the run starts.")
                },
            )
            RuleLine(line { append("All back if the Play check fails, or the crew starts before Play could check it.") })
            RuleLine(line { append("All back if the team takes it out before the run.") })
            RuleLine(line { append("All back if you open every app on your list, every night until the end.") })
            RuleLine(line { append("Otherwise they are kept, even for one app on your list left unopened on one night.") })
        }
    }
}

/** A section: a 28dp icon square and its heading, then its lines 6dp apart. 16dp of air above every group. */
@Composable
private fun RuleGroup(
    icon: ImageVector,
    tone: Tone,
    title: String,
    lines: @Composable () -> Unit,
) {
    Column(Modifier.padding(top = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(icon, 28.dp, tone, corner = 9.dp, glyph = 14.dp, tint = tone.ink())
            Spacer(Modifier.width(10.dp))
            Text(title, style = HeadingStyle, color = Tt.colors.text)
        }
        Column(
            Modifier.padding(top = 6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) { lines() }
    }
}

/** A 4dp dot flush at the content edge; wrapped lines hang under the text. */
@Composable
internal fun RuleLine(text: androidx.compose.ui.text.AnnotatedString) {
    Row(Modifier.fillMaxWidth()) {
        Box(
            Modifier
                .padding(top = 6.5.dp)
                .size(4.dp)
                .clip(CircleShape)
                .background(Tt.colors.text4),
        )
        Spacer(Modifier.width(8.dp))
        Text(text, Modifier.weight(1f), style = LineStyle, color = Tt.colors.text2)
    }
}
