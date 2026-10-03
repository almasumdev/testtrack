package com.eazyverse.testtrack.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.SubcomposeAsyncImage
import com.composables.icons.lucide.ArrowLeft
import com.composables.icons.lucide.Lucide
import com.eazyverse.testtrack.data.*

/** How a tester's day reads. Five states, each with a shape of its own — see [Cell]. */
enum class DayState { POSTED, SHORT, MISSED, UPCOMING, NOT_JOINED, REPORTED }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    appId: String,
    /** What the caller already knew this app as, shown until [vm]'s own load answers the same
     *  question — see Routes.DASHBOARD and the note on GroupScreen's title. */
    appName: String,
    onBack: () -> Unit,
    vm: DashboardViewModel = viewModel(),
) {
    LaunchedEffect(appId) { vm.load(appId) }
    var preview by remember { mutableStateOf<Pair<Tester, Proof>?>(null) }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = {
                    val label = vm.app?.label?.ifBlank { null } ?: appName.ifBlank { null }
                    if (label != null) Text(label, fontWeight = FontWeight.SemiBold)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Lucide.ArrowLeft, "Back")
                    }
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                    ),
            )
        },
    ) { padding ->
        RefreshBox(
            // Bound to the pull, not the general refresh — see HomeScreen's own note on this.
            isRefreshing = vm.pulling,
            onRefresh = { vm.load(appId, fromPull = true) },
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
            ) {
                when {
                    // A pull gets the same full skeleton a first load does — see HomeScreen's own
                    // note on this.
                    !vm.ready || vm.pulling -> DashboardSkeleton()

                    vm.app == null -> Blank(vm.message ?: "This app isn't registered any more.")
                    vm.group == null -> Blank("This app is waiting for an admin to place it in a crew.")

                    else -> {
                        Headline(vm)
                        Spacer(Modifier.height(22.dp))

                        Reporters(vm) { preview = it }
                        Spacer(Modifier.height(22.dp))

                        Behind(vm)

                        SectionLabel("The 14 days")
                        Legend()
                        Spacer(Modifier.height(10.dp))
                        Grid(vm) { tester, proof -> preview = tester to proof }

                        Spacer(Modifier.height(22.dp))
                        PerTester(vm)

                        if (vm.feedback.isNotEmpty()) {
                            Spacer(Modifier.height(22.dp))
                            FeedbackList(vm)
                        }

                        vm.message?.let { Failure(it) }
                        Spacer(Modifier.height(Gutter))
                    }
                }
            }
        }
    }

    preview?.let { (tester, proof) ->
        Ask(
            title = "${tester.shortName} · day ${proof.day + 1}",
            confirm = "Close",
            onConfirm = { preview = null },
            onDismiss = { preview = null },
            // Nothing to cancel: this is a look at something, not a decision about it.
            dismiss = null,
        ) {
            Text(
                "${formatDuration(proof.usageMs)} in the app",
                style = MaterialTheme.typography.bodyMedium,
                color = if (proof.meetsBar) Status.posted else Status.missed,
            )
            if (proof.imageUrl.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                // Subcompose so the wait has a shape. These come off Drive over whatever
                // connection the tester has, and an image with no loading state is a page that
                // reflows under the reader when it lands.
                SubcomposeAsyncImage(
                    model = proof.imageUrl,
                    contentDescription = "Proof screenshot",
                    contentScale = ContentScale.FillWidth,
                    loading = { Skeleton(height = 260.dp, corner = 8.dp) },
                    error = { Skeleton(height = 260.dp, corner = 8.dp) },
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)),
                )
            }
        }
    }
}
