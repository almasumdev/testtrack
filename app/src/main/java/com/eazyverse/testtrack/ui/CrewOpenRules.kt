package com.eazyverse.testtrack.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.CalendarDays
import com.composables.icons.lucide.ChevronDown
import com.composables.icons.lucide.Clock
import com.composables.icons.lucide.Coins
import com.composables.icons.lucide.Download
import com.composables.icons.lucide.Lock
import com.composables.icons.lucide.LogOut
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.MessageCircle
import com.composables.icons.lucide.Moon
import com.composables.icons.lucide.RefreshCw
import com.composables.icons.lucide.RotateCcw
import com.composables.icons.lucide.Scale
import com.composables.icons.lucide.TriangleAlert
import com.eazyverse.testtrack.ui.theme.Barlow
import com.eazyverse.testtrack.ui.theme.Tt

/*
 * The Open tab's rules card (docs/design/crew-open/rules.html, option A). Closed it is six fact
 * tiles; "All rules" opens the checked list in place. No rules between anything: spacing only.
 * Figures come from CrewRules and the crew's own run length, never a literal.
 */

private val TileFigure =
    TextStyle(fontFamily = Barlow, fontSize = 21.sp, lineHeight = 24.sp, fontWeight = FontWeight.Bold, fontFeatureSettings = "tnum")
private val TileUnit = TextStyle(fontFamily = Barlow, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold)
private val TileLabel = TextStyle(fontFamily = Barlow, fontSize = 11.5.sp, lineHeight = 14.sp)
private val LineStyle = TextStyle(fontFamily = Barlow, fontSize = 13.sp, lineHeight = 17.sp)
private val MoreStyle = TextStyle(fontFamily = Barlow, fontSize = 13.5.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold)

@Composable
internal fun CrewRulesCard(runDays: Int = CrewRules.RUN_DAYS) {
    var open by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth()) {
        RulesHead(open) { open = !open }
        Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
    }
}

/** The label and the control to open the rest: a 24dp row, the whole width of it a tap. */
@Composable
private fun RulesHead(
    open: Boolean,
    onToggle: () -> Unit,
) {
    val turn by animateFloatAsState(if (open) 180f else 0f, label = "rulesChevron")
    val ink = Tt.colors.tabSelected
    Row(
        Modifier
            .fillMaxWidth()
            .height(24.dp)
            .clickable(role = Role.Button, onClickLabel = if (open) "Show fewer rules" else "Show all rules", onClick = onToggle),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f)) { Eyebrow(Lucide.Scale, "The rules") }
        Text(if (open) "Less" else "All rules", style = MoreStyle, color = ink)
        Spacer(Modifier.width(2.dp))
        Icon(Lucide.ChevronDown, null, Modifier.size(16.dp).rotate(turn), tint = ink)
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
    val shape = RoundedCornerShape(12.dp)
    val ink = if (tone == Tone.BAD) tone.ink() else Tt.colors.tabSelected
    Column(
        Modifier
            .weight(1f)
            .fillMaxHeight()
            .clip(shape)
            .border(1.dp, Tt.colors.cardLine, shape)
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

    Column(Modifier.padding(top = 4.dp)) {
        RuleGroup(Lucide.CalendarDays, "The run") {
            RuleLine(
                Lucide.CalendarDays,
                Tone.ACCENT,
                line {
                    append("Starts at each member's first midnight after all ")
                    withStyle(bold) { append(n(CrewRules.SEATS)) }
                    append(" apps are checked in. Each day runs midnight to midnight, your time.")
                },
            )
            RuleLine(Lucide.RefreshCw, Tone.ACCENT, line { append("An app sent back puts the crew back to filling.") })
        }
        RuleGroup(Lucide.Moon, "Each night") {
            RuleLine(
                Lucide.Clock,
                Tone.ACCENT,
                line {
                    append("Each app is open ")
                    withStyle(bold) { append(n(CrewRules.OPEN_SECONDS)) }
                    append(" seconds. It counts with ")
                    withStyle(bold) { append(n(CrewRules.VISIT_SECONDS)) }
                    append(" in front and its picture.")
                },
            )
            RuleLine(
                Lucide.Download,
                Tone.ACCENT,
                line { append("Only installs from Google Play count. A copy from anywhere else doesn't.") },
            )
            RuleLine(
                Lucide.RefreshCw,
                Tone.ACCENT,
                line {
                    append("An old version stops counting ")
                    withStyle(bold) { append(n(CrewRules.UPDATE_GRACE_HOURS)) }
                    append(" hours after the owner's update.")
                },
            )
            RuleLine(
                Lucide.MessageCircle,
                Tone.ACCENT,
                line {
                    append("Send private feedback on Play by night ")
                    withStyle(bold) { append(n(CrewRules.FEEDBACK_BY_NIGHT)) }
                    append(" with each app, or its opens stop counting.")
                },
            )
        }
        RuleGroup(Lucide.TriangleAlert, "Missed days") {
            RuleLine(
                Lucide.TriangleAlert,
                Tone.WARN,
                line {
                    append("Only apps you owed that night count. A day is missed when ")
                    withStyle(bold) { append(n(CrewRules.MISS_UNOPENED)) }
                    append(" or more of them stay unopened, or you open none.")
                },
            )
            RuleLine(
                Lucide.LogOut,
                Tone.BAD,
                line {
                    withStyle(bold) { append("One") }
                    append(" missed day is a warning. ")
                    withStyle(bold) { append(n(CrewRules.MISSES_IN_A_ROW)) }
                    append(" in a row and your app leaves the run.")
                },
            )
        }
        RuleGroup(Lucide.Coins, "Your ${CrewRules.SEAT_COST} tix") {
            RuleLine(Lucide.RotateCcw, Tone.OK, line { append("All back if you leave while the crew fills.") })
            RuleLine(
                Lucide.RotateCcw,
                Tone.OK,
                line {
                    append("All back if ")
                    withStyle(bold) { append(n(CrewRules.REPORTS_TO_RETURN)) }
                    append(" members can't install it before the run starts.")
                },
            )
            RuleLine(
                Lucide.RotateCcw,
                Tone.OK,
                line { append("All back if the Play check fails, or the crew starts before Play could check it.") },
            )
            RuleLine(Lucide.RotateCcw, Tone.OK, line { append("All back if the team takes it out before the run.") })
            RuleLine(Lucide.RotateCcw, Tone.OK, line { append("All back if you open every app on your list, every night until the end.") })
            RuleLine(
                Lucide.Lock,
                Tone.BAD,
                line { append("Otherwise they are kept, even for one app on your list left unopened on one night.") },
            )
        }
    }
}

/** A section: its eyebrow, 6dp, then its lines 6dp apart, with 16dp of air above. */
@Composable
private fun RuleGroup(
    icon: ImageVector,
    title: String,
    lines: @Composable () -> Unit,
) {
    Column(Modifier.padding(top = 16.dp)) {
        Eyebrow(icon, title)
        Column(Modifier.padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { lines() }
    }
}

@Composable
private fun RuleLine(
    icon: ImageVector,
    tone: Tone,
    text: androidx.compose.ui.text.AnnotatedString,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Box(Modifier.size(width = 16.dp, height = 17.dp), contentAlignment = Alignment.Center) {
            Icon(icon, null, Modifier.size(14.dp), tint = tone.ink())
        }
        Spacer(Modifier.width(8.dp))
        Text(text, Modifier.defaultMinSize(minHeight = 17.dp), style = LineStyle, color = Tt.colors.text2)
    }
}
