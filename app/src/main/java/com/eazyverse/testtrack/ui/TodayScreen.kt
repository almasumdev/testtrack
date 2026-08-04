package com.eazyverse.testtrack.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionConfig
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import com.eazyverse.testtrack.data.*
import com.eazyverse.testtrack.findActivity
import kotlinx.coroutines.launch
import java.io.File

/** How long the app under test is left on screen before the grab. */
private const val DWELL_MS = 8_000L

class TodayViewModel : ViewModel() {

    var apps by mutableStateOf<List<TestApp>>(emptyList())
        private set
    var donePackages by mutableStateOf<Set<String>>(emptySet())
        private set
    var loading by mutableStateOf(true)
        private set
    var message by mutableStateOf<String?>(null)

    /** Packages currently uploading, so a row can show progress instead of a stale button. */
    var uploading by mutableStateOf<Set<String>>(emptySet())
        private set

    fun load() {
        loading = true
        viewModelScope.launch {
            val uid = AuthRepo.uid
            runCatching {
                apps = Repo.approvedApps().filter { it.ownerUid != uid }
                donePackages = if (uid != null) Repo.myProofsToday(uid) else emptySet()
            }.onFailure { message = it.message ?: "Could not load today's list" }
            loading = false
        }
    }

    /**
     * Uploads a finished capture and records it.
     *
     * Drive first, Firestore second: the proof document points at a Drive file, so writing the
     * document before the upload lands would leave a grid cell referencing nothing.
     */
    fun publish(activity: Activity, app: TestApp, path: String) {
        val uid = AuthRepo.uid ?: return
        val email = Session.email ?: return
        val day = app.dayIndex()
        if (day == null) {
            message = "${app.name} isn't inside its 14-day run right now."
            CaptureService.consume(app.packageName)
            return
        }

        uploading = uploading + app.packageName
        viewModelScope.launch {
            val token = AuthRepo.driveTokenOrNull(activity)
            if (token == null) {
                message = "Drive access expired — reconnect in setup."
            } else {
                val file = File(path)
                val name = "${app.packageName}_day${day}_${uid.take(6)}.jpg"
                when (val result = DriveRepo.upload(token, file, name)) {
                    is UploadResult.Failed -> message = result.reason
                    is UploadResult.Ok -> {
                        runCatching {
                            Repo.recordProof(
                                Proof(
                                    appId = app.id,
                                    testerUid = uid,
                                    testerEmail = email,
                                    day = day,
                                    fileId = result.file.id,
                                    imageUrl = result.file.thumbUrl,
                                    capturedAt = System.currentTimeMillis()
                                )
                            )
                            donePackages = donePackages + app.packageName
                            file.delete()
                        }.onFailure { message = it.message ?: "Could not record proof" }
                    }
                }
            }
            CaptureService.consume(app.packageName)
            uploading = uploading - app.packageName
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayScreen(
    onGrid: () -> Unit,
    onSignOut: () -> Unit,
    vm: TodayViewModel = viewModel()
) {
    val context = LocalContext.current
    val activity = context.findActivity()

    var canReturn by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) canReturn = Settings.canDrawOverlays(context)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) { vm.load() }

    // A capture finishes while this screen is in the background, so the upload is kicked off
    // when it comes back rather than from the service.
    LaunchedEffect(CaptureService.results.size) {
        CaptureService.results.forEach { (pkg, path) ->
            val app = vm.apps.firstOrNull { it.packageName == pkg }
            if (app != null && pkg !in vm.uploading) vm.publish(activity, app, path)
        }
    }

    val consentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val data = result.data
        if (result.resultCode == Activity.RESULT_OK && data != null) {
            CaptureService.startSession(context, result.resultCode, data)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Today") },
                actions = {
                    IconButton(onClick = onGrid) { Icon(Icons.Default.BarChart, "My app") }
                    IconButton(onClick = {
                        CaptureService.endSession(context)
                        onSignOut()
                    }) { Icon(Icons.Default.Logout, "Sign out") }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            Spacer(Modifier.height(8.dp))
            Text(
                "${vm.donePackages.size} of ${vm.apps.size} done today",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                if (CaptureService.sessionActive)
                    "Tap an app. It opens, proof is captured after ${DWELL_MS / 1000} seconds, " +
                        "and you land back here."
                else
                    "Confirm screen sharing once, then every app today is captured under that " +
                        "single grant.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(16.dp))

            if (!canReturn) {
                Card(
                    Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            "Allow returning to TestTrack",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Android blocks an app from pulling itself to the front. Without " +
                                "this, capture still works but you switch back by hand each time.",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Spacer(Modifier.height(10.dp))
                        Button(
                            onClick = {
                                activity.startActivity(
                                    Intent(
                                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                        Uri.parse("package:${context.packageName}")
                                    )
                                )
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Open settings") }
                    }
                }
            }

            if (!CaptureService.sessionActive) {
                Button(
                    onClick = {
                        val mpm = activity.getSystemService(Context.MEDIA_PROJECTION_SERVICE)
                            as MediaProjectionManager
                        // Android 14 defaults the sheet to "Share one app", which would capture
                        // TestTrack rather than the app under test. Asking for the default
                        // display removes the dropdown entirely.
                        val intent =
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
                                mpm.createScreenCaptureIntent(
                                    MediaProjectionConfig.createConfigForDefaultDisplay()
                                )
                            else
                                mpm.createScreenCaptureIntent()
                        consentLauncher.launch(intent)
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) { Text("Start capturing") }
            }

            CaptureService.status?.let {
                Spacer(Modifier.height(10.dp))
                Text(it, style = MaterialTheme.typography.bodySmall)
            }

            Spacer(Modifier.height(16.dp))

            when {
                vm.loading -> Box(Modifier.fillMaxWidth().padding(40.dp), Alignment.Center) {
                    CircularProgressIndicator()
                }

                vm.apps.isEmpty() -> Card(Modifier.fillMaxWidth()) {
                    Text(
                        "No approved apps yet. Once an admin approves the group's submissions " +
                            "they appear here.",
                        Modifier.padding(20.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                else -> vm.apps.forEach { app ->
                    AppRow(
                        app = app,
                        done = app.packageName in vm.donePackages,
                        busy = app.packageName in vm.uploading ||
                            CaptureService.capturing == app.packageName,
                        enabled = CaptureService.sessionActive,
                        capture = CaptureService.results[app.packageName],
                        onOpen = {
                            CaptureService.capture(context, app.packageName, DWELL_MS)
                            InstalledApps.launch(context, app.packageName)
                        }
                    )
                }
            }

            vm.message?.let {
                Spacer(Modifier.height(12.dp))
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        it,
                        Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }

            if (CaptureService.sessionActive) {
                Spacer(Modifier.height(16.dp))
                OutlinedButton(
                    onClick = { CaptureService.endSession(context) },
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) { Text("Stop capturing") }
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun AppRow(
    app: TestApp,
    done: Boolean,
    busy: Boolean,
    enabled: Boolean,
    capture: String?,
    onOpen: () -> Unit
) {
    val context = LocalContext.current
    val info = remember(app.packageName) { InstalledApps.info(context, app.packageName) }
    val day = app.dayIndex()

    Card(
        Modifier.fillMaxWidth().padding(bottom = 12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (done) MaterialTheme.colorScheme.surfaceVariant
            else MaterialTheme.colorScheme.surface
        )
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            capture?.let {
                AsyncImage(
                    model = File(it),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(44.dp, 78.dp).clip(RoundedCornerShape(6.dp))
                )
                Spacer(Modifier.width(12.dp))
            }

            Column(Modifier.weight(1f)) {
                Text(
                    app.name.ifBlank { app.packageName },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    buildString {
                        append(if (day != null) "Day ${day + 1} of $RUN_DAYS" else "Run finished")
                        if (info.installed) append(" · ${info.streakDays}d installed")
                        else append(" · not installed")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.width(10.dp))

            when {
                busy -> CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                done -> Icon(Icons.Default.Check, "Done", tint = MaterialTheme.colorScheme.primary)
                !info.installed -> Text(
                    "Install first",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error
                )
                else -> Button(onClick = onOpen, enabled = enabled) { Text("Open") }
            }
        }
    }
}
