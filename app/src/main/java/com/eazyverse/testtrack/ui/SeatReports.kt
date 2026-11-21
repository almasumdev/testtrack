package com.eazyverse.testtrack.ui

import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.ChevronDown
import com.composables.icons.lucide.ChevronUp
import com.composables.icons.lucide.Lucide
import com.eazyverse.testtrack.domain.pluralOf
import com.eazyverse.testtrack.domain.shortDate
import java.time.LocalDate

/** How many reports show before "Show all". */
private const val REPORTS_SHOWN = 3

/**
 * When a report was filed: "Night 3, Nov 21", "Before the run, Nov 18", or just "Nov 2" when its
 * night is not known. The day is dropped when [SeatReport.on] is not a date. [dateOnly] says only
 * the day, for reports that are all from before the run.
 */
internal fun reportWhen(
    r: SeatReport,
    dateOnly: Boolean = false,
): String {
    val date = runCatching { LocalDate.parse(r.on) }.getOrNull()?.let(::shortDate)
    if (dateOnly && date != null) return date
    val night =
        when {
            r.night == null -> null
            r.night == 0 -> "Before the run"
            else -> "Night ${r.night}"
        }
    return listOfNotNull(night, date).joinToString(", ")
}

/**
 * What members wrote about the reader's own app, newest first: the latest three, then "Show all N"
 * that opens the rest in place. Draws nothing without reports. [title] adds the count over the
 * lines, for places that do not already show it; [dateOnly] is for reports from before the run.
 */
@Composable
internal fun SeatReports(
    seat: CrewSeat,
    footer: String,
    tone: Tone = Tone.WARN,
    title: Boolean = false,
    dateOnly: Boolean = false,
) {
    val all = seat.reportList
    if (all.isEmpty()) return
    var open by rememberSaveable(seat.id) { mutableStateOf(false) }
    val shown = if (open) all else all.take(REPORTS_SHOWN)
    Quotes(
        lines = shown.map { it.reason },
        footer = footer,
        tone = tone,
        labels = shown.map { reportWhen(it, dateOnly) },
        title = if (title) "${all.size} ${pluralOf(all.size, "report")}" else "",
        more =
            if (all.size > REPORTS_SHOWN) {
                {
                    InlineAction(
                        if (open) "Show less" else "Show all ${all.size}",
                        Modifier.offset(x = (-8).dp),
                        trailing = if (open) Lucide.ChevronUp else Lucide.ChevronDown,
                    ) { open = !open }
                }
            } else {
                null
            },
    )
}
