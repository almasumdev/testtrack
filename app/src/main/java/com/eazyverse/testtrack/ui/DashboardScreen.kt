package com.eazyverse.testtrack.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.eazyverse.testtrack.data.*
import kotlinx.coroutines.launch

/** How a tester's day reads. Four states, each with a shape of its own — see [Cell]. */
enum class DayState { POSTED, SHORT, MISSED, UPCOMING }

private val CELL = 26.dp
private val CELL_GAP = 2.dp
private val NAME_COLUMN = 96.dp

class DashboardViewModel : ViewModel() {

    var app by mutableStateOf<TestApp?>(null)
        private set
    var group by mutableStateOf<TestGroup?>(null)
        private set
    var testers by mutableStateOf<List<Tester>>(emptyList())
        private set
    var proofs by mutableStateOf<Map<Pair<String, Int>, Proof>>(emptyMap())
        private set
    var loading by mutableStateOf(true)
        private set
    var message by mutableStateOf<String?>(null)

    val day: Int? get() = group?.dayIndex()

    fun load(appId: String) {
        Cache.get<TestApp>(Cache.app(appId))?.let { cached ->
            app = cached
            cached.groupId?.let { gid ->
                Cache.get<TestGroup>(Cache.group(gid))?.let { group = it }
                Cache.get<List<Tester>>(Cache.testers(gid))?.let { testers = it }
            }
            Cache.get<Map<Pair<String, Int>, Proof>>(Cache.proofs(appId))?.let { proofs = it }
            loading = false
        }

        viewModelScope.launch {
            runCatching {
                val found = Repo.app(appId)
                app = found
                found?.let { Cache.put(Cache.app(appId), it) }

                val cohort = found?.groupId?.let { Repo.group(it) }
                group = cohort

                if (cohort != null) {
                    Cache.put(Cache.group(cohort.id), cohort)
                    // The owner never tests their own app, so they are not a row in their grid.
                    testers = Repo.testers(cohort.memberUids).filter { it.uid != found.ownerUid }
                    Cache.put(Cache.testers(cohort.id), testers)
                    proofs = Repo.proofsForApp(appId).associateBy { it.testerUid to it.day }
                    Cache.put(Cache.proofs(appId), proofs)
                }
            }.onFailure { message = it.message ?: "Could not load this dashboard" }
            loading = false
        }
    }

    fun stateOf(uid: String, forDay: Int): DayState {
        val today = day
        val proof = proofs[uid to forDay]
        return when {
            proof != null && proof.meetsBar -> DayState.POSTED
            proof != null -> DayState.SHORT
            today == null || forDay < today -> DayState.MISSED
            else -> DayState.UPCOMING
        }
    }

    /** Reported today and stayed the full half-minute. */
    fun reportedToday(): List<Pair<Tester, Proof>> {
        val today = day ?: return emptyList()
        return testers.mapNotNull { tester ->
            proofs[tester.uid to today]?.takeIf { it.meetsBar }?.let { tester to it }
        }
    }

    fun behind(): List<Tester> {
        val today = day ?: return emptyList()
        return testers.filter { stateOf(it.uid, today) != DayState.POSTED }
    }

    fun daysPosted(uid: String): Int =
        (0 until RUN_DAYS).count { stateOf(uid, it) == DayState.POSTED }

    fun totalUsage(uid: String): Long =
        (0 until RUN_DAYS).sumOf { proofs[uid to it]?.usageMs ?: 0L }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    appId: String,
    onBack: () -> Unit,
    vm: DashboardViewModel = viewModel()
) {
    LaunchedEffect(appId) { vm.load(appId) }
    var preview by remember { mutableStateOf<Pair<Tester, Proof>?>(null) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(vm.app?.label ?: "Your app", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            when {
                vm.app == null && vm.loading -> {
                    Spacer(Modifier.height(12.dp))
                    Column(Modifier.padding(horizontal = Gutter)) {
                        Skeleton(width = 130.dp, height = 34.dp, corner = 10.dp)
                        Spacer(Modifier.height(10.dp))
                        Skeleton(width = 190.dp, height = 12.dp)
                    }
                    Spacer(Modifier.height(28.dp))
                    SkeletonRows(3)
                }

                vm.app == null -> Blank(vm.message ?: "That app is no longer registered.")
                vm.group == null -> Blank("This app hasn't been placed in a group yet.")

                else -> {
                    Headline(vm)
                    Spacer(Modifier.height(32.dp))

                    Reporters(vm) { preview = it }
                    Spacer(Modifier.height(32.dp))

                    Behind(vm)

                    SectionLabel("The 14 days")
                    Legend()
                    Spacer(Modifier.height(14.dp))
                    Grid(vm) { tester, proof -> preview = tester to proof }

                    Spacer(Modifier.height(32.dp))
                    PerTester(vm)

                    vm.message?.let { Failure(it) }
                    Spacer(Modifier.height(40.dp))
                }
            }
        }
    }

    preview?.let { (tester, proof) ->
        AlertDialog(
            onDismissRequest = { preview = null },
            confirmButton = {
                TextButton(onClick = { preview = null }) { Text("Close") }
            },
            title = { Text("${tester.shortName} · day ${proof.day + 1}") },
            text = {
                Column {
                    Text(
                        "${formatDuration(proof.usageMs)} in the app",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (proof.meetsBar) Status.posted else Status.missed
                    )
                    Spacer(Modifier.height(12.dp))
                    if (proof.imageUrl.isNotBlank()) {
                        AsyncImage(
                            model = proof.imageUrl,
                            contentDescription = "Proof screenshot",
                            contentScale = ContentScale.FillWidth,
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                        )
                    }
                }
            }
        )
    }
}

/**
 * The one number that matters, stated rather than plotted.
 *
 * A count out of thirteen is not a chart — the bar underneath is there to make "well behind" and
 * "nearly there" readable at a glance, not to carry the value.
 */
@Composable
private fun Headline(vm: DashboardViewModel) {
    val reported = vm.reportedToday().size
    val expected = vm.testers.size
    val day = vm.day

    Column(Modifier.padding(start = Gutter, end = Gutter, top = 12.dp)) {
        Text(
            if (day != null) "$reported of $expected" else "Not started",
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(4.dp))
        Text(
            if (day != null) "reported today · day ${day + 1} of $RUN_DAYS"
            else "the run begins when the group reaches ${TestGroup.THRESHOLD} members",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (day != null) {
            Spacer(Modifier.height(16.dp))
            Meter(if (expected == 0) 0f else reported.toFloat() / expected)
        }
    }
}

@Composable
private fun Reporters(vm: DashboardViewModel, onOpen: (Pair<Tester, Proof>) -> Unit) {
    val reported = vm.reportedToday()

    SectionLabel("Today")
    if (reported.isEmpty()) {
        Blank("Nobody has reported yet today.")
        return
    }

    Row(
        Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = Gutter),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        reported.forEach { entry ->
            val (tester, proof) = entry
            Column(
                Modifier.width(86.dp).clickable { onOpen(entry) },
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (proof.imageUrl.isNotBlank()) {
                    AsyncImage(
                        model = proof.imageUrl,
                        contentDescription = "${tester.shortName}'s screenshot",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(86.dp, 152.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Status.upcoming)
                    )
                } else {
                    Box(
                        Modifier
                            .size(86.dp, 152.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Status.upcoming),
                        contentAlignment = Alignment.Center
                    ) { Initial(tester.initial) }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    tester.shortName,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1
                )
                Text(
                    formatDuration(proof.usageMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun Behind(vm: DashboardViewModel) {
    val behind = vm.behind()
    if (vm.day == null || behind.isEmpty()) return

    SectionLabel("Still to report")
    Row(
        Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = Gutter),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        behind.forEach { tester ->
            Row(
                Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.08f))
                    .padding(start = 6.dp, end = 14.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Initial(tester.initial, 26.dp)
                Spacer(Modifier.width(8.dp))
                Text(tester.shortName, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
    Spacer(Modifier.height(32.dp))
}

/**
 * Legend for the grid.
 *
 * Present because the cells encode state, and state must never be carried by colour alone — each
 * swatch here shows the shape that goes with it, and every cell also announces itself to
 * accessibility services.
 */
@Composable
private fun Legend() {
    Row(
        Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = Gutter),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LegendKey(DayState.POSTED, "reported")
        LegendKey(DayState.SHORT, "under 30s")
        LegendKey(DayState.MISSED, "missed")
        LegendKey(DayState.UPCOMING, "to come")
    }
}

@Composable
private fun LegendKey(state: DayState, label: String) {
    Row(Modifier.padding(end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Cell(state, 14.dp)
        Spacer(Modifier.width(6.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun Grid(vm: DashboardViewModel, onCell: (Tester, Proof) -> Unit) {
    val horizontal = rememberScrollState()

    Column(Modifier.horizontalScroll(horizontal).padding(horizontal = Gutter)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.width(NAME_COLUMN))
            repeat(RUN_DAYS) { day ->
                Box(Modifier.size(CELL + CELL_GAP), Alignment.Center) {
                    Text(
                        "${day + 1}",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        vm.testers.forEach { tester ->
            Row(Modifier.padding(vertical = 1.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    tester.shortName,
                    Modifier.width(NAME_COLUMN).padding(end = 8.dp),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1
                )
                repeat(RUN_DAYS) { day ->
                    val state = vm.stateOf(tester.uid, day)
                    val proof = vm.proofs[tester.uid to day]
                    Box(
                        Modifier
                            .padding(end = CELL_GAP)
                            .semantics {
                                contentDescription =
                                    "${tester.shortName}, day ${day + 1}, ${state.spoken()}"
                            }
                            .then(
                                if (proof != null) Modifier.clickable { onCell(tester, proof) }
                                else Modifier
                            )
                    ) { Cell(state, CELL) }
                }
            }
        }

        if (vm.testers.isEmpty()) {
            Text(
                "No other members in this group yet.",
                Modifier.padding(vertical = 24.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun DayState.spoken() = when (this) {
    DayState.POSTED -> "reported"
    DayState.SHORT -> "reported but under thirty seconds"
    DayState.MISSED -> "missed"
    DayState.UPCOMING -> "still to come"
}

/**
 * One day for one tester.
 *
 * Fill, outline and a struck-through slash separate the four states without relying on hue, so
 * the grid still reads under colour-vision deficiency, in greyscale, and when printed.
 */
@Composable
private fun Cell(state: DayState, size: androidx.compose.ui.unit.Dp) {
    val posted = Status.posted
    val missed = Status.missed
    val upcoming = Status.upcoming

    Canvas(Modifier.size(size)) {
        val radius = CornerRadius(this.size.minDimension * 0.28f)
        val strokeWidth = this.size.minDimension * 0.12f

        when (state) {
            DayState.POSTED ->
                drawRoundRect(posted, cornerRadius = radius)

            DayState.SHORT ->
                drawRoundRect(
                    posted, cornerRadius = radius,
                    style = Stroke(strokeWidth)
                )

            DayState.MISSED -> {
                drawRoundRect(missed.copy(alpha = 0.18f), cornerRadius = radius)
                val inset = this.size.minDimension * 0.26f
                drawLine(
                    color = missed,
                    start = Offset(inset, this.size.height - inset),
                    end = Offset(this.size.width - inset, inset),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round
                )
            }

            DayState.UPCOMING ->
                drawRoundRect(upcoming, cornerRadius = radius)
        }
    }
}

@Composable
private fun PerTester(vm: DashboardViewModel) {
    if (vm.testers.isEmpty()) return
    val best = vm.testers.maxOfOrNull { vm.totalUsage(it.uid) }?.coerceAtLeast(1L) ?: 1L
    val elapsed = (vm.day?.plus(1)) ?: 0

    SectionLabel("Every tester")
    Panel {
        vm.testers.sortedByDescending { vm.daysPosted(it.uid) }.forEach { tester ->
            val posted = vm.daysPosted(tester.uid)
            val usage = vm.totalUsage(tester.uid)

            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Initial(tester.initial)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        tester.shortName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        "$posted of $elapsed days · ${formatDuration(usage)} total",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    Meter(usage.toFloat() / best, height = 4.dp)
                }
            }
        }
    }
}
