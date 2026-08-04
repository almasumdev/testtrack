package com.eazyverse.testtrack.ui

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.eazyverse.testtrack.data.*
import kotlinx.coroutines.launch

private val CELL = 30.dp
private val NAME_COLUMN = 108.dp

class GridViewModel : ViewModel() {
    var app by mutableStateOf<TestApp?>(null)
        private set
    var testers by mutableStateOf<List<Tester>>(emptyList())
        private set
    var proofs by mutableStateOf<Map<Pair<String, Int>, Proof>>(emptyMap())
        private set
    var loading by mutableStateOf(true)
        private set
    var message by mutableStateOf<String?>(null)

    fun load() {
        loading = true
        viewModelScope.launch {
            val uid = AuthRepo.uid
            runCatching {
                val mine = if (uid != null) Repo.myApp(uid) else null
                app = mine
                if (mine != null) {
                    testers = Repo.testers().filter { it.uid != uid }
                    proofs = Repo.proofsForApp(mine.id)
                        .associateBy { it.testerUid to it.day }
                }
            }.onFailure { message = it.message ?: "Could not load the grid" }
            loading = false
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GridScreen(onBack: () -> Unit, vm: GridViewModel = viewModel()) {
    LaunchedEffect(Unit) { vm.load() }
    var preview by remember { mutableStateOf<Proof?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("My app") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        val app = vm.app
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
            when {
                vm.loading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    CircularProgressIndicator()
                }

                app == null -> Box(Modifier.fillMaxSize().padding(24.dp), Alignment.Center) {
                    Text(
                        vm.message ?: "You haven't submitted an app yet. Add it in setup and an " +
                            "admin will approve it.",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                else -> {
                    val today = app.dayIndex()
                    Spacer(Modifier.height(8.dp))
                    Text(
                        app.name.ifBlank { app.packageName },
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        buildString {
                            append(if (app.approved) "Approved" else "Awaiting admin approval")
                            append(" · ")
                            append(
                                if (today != null) "day ${today + 1} of $RUN_DAYS"
                                else "run finished"
                            )
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(Modifier.height(16.dp))
                    Legend()
                    Spacer(Modifier.height(12.dp))

                    Row(Modifier.weight(1f)) {
                        val hScroll = rememberScrollState()
                        Column(Modifier.verticalScroll(rememberScrollState())) {
                            Row(Modifier.horizontalScroll(hScroll)) {
                                Column {
                                    HeaderRow()
                                    vm.testers.forEach { tester ->
                                        TesterRow(
                                            tester = tester,
                                            today = today,
                                            proofFor = { day -> vm.proofs[tester.uid to day] },
                                            onCell = { preview = it }
                                        )
                                    }
                                }
                            }
                            if (vm.testers.isEmpty()) {
                                Text(
                                    "No other testers have signed in yet.",
                                    Modifier.padding(vertical = 24.dp),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(Modifier.height(24.dp))
                        }
                    }
                }
            }
        }
    }

    preview?.let { proof ->
        AlertDialog(
            onDismissRequest = { preview = null },
            confirmButton = {
                TextButton(onClick = { preview = null }) { Text("Close") }
            },
            title = { Text("Day ${proof.day + 1} · ${proof.testerEmail.substringBefore('@')}") },
            text = {
                AsyncImage(
                    model = proof.imageUrl,
                    contentDescription = "Proof screenshot",
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                )
            }
        )
    }
}

@Composable
private fun Legend() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        LegendKey(MaterialTheme.colorScheme.primary, "posted")
        LegendKey(MaterialTheme.colorScheme.errorContainer, "missed")
        LegendKey(MaterialTheme.colorScheme.surfaceVariant, "upcoming")
    }
}

@Composable
private fun LegendKey(color: Color, label: String) {
    Row(Modifier.padding(end = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(12.dp).clip(RoundedCornerShape(3.dp)).background(color))
        Spacer(Modifier.width(5.dp))
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun HeaderRow() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Spacer(Modifier.width(NAME_COLUMN))
        repeat(RUN_DAYS) { day ->
            Box(Modifier.size(CELL), Alignment.Center) {
                Text(
                    "${day + 1}",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun TesterRow(
    tester: Tester,
    today: Int?,
    proofFor: (Int) -> Proof?,
    onCell: (Proof) -> Unit
) {
    Row(Modifier.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            tester.shortName,
            Modifier.width(NAME_COLUMN).padding(end = 6.dp),
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1
        )
        repeat(RUN_DAYS) { day ->
            val proof = proofFor(day)
            // Cell state: posted -> green; the day has passed with nothing -> red; still to
            // come -> grey. `today` being null means the run is over, so every day counts.
            val color = when {
                proof != null -> MaterialTheme.colorScheme.primary
                today == null || day < today -> MaterialTheme.colorScheme.errorContainer
                else -> MaterialTheme.colorScheme.surfaceVariant
            }
            Box(
                Modifier
                    .padding(1.dp)
                    .size(CELL - 2.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(color)
                    .then(if (proof != null) Modifier.clickable { onCell(proof) } else Modifier)
            )
        }
    }
}
