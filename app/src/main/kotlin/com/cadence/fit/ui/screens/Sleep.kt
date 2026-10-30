package com.cadence.fit.ui.screens

import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cadence.fit.domain.insight.RecoveryAdvisor
import com.cadence.fit.domain.model.SleepAvailability
import com.cadence.fit.domain.model.SleepConnectionStatus
import com.cadence.fit.domain.model.SleepPermissionState
import com.cadence.fit.domain.model.SleepSummary
import com.cadence.fit.domain.time.TimeRanges
import com.cadence.fit.integrations.healthconnect.HealthConnectPermissions
import com.cadence.fit.ui.CadenceViewModel
import com.cadence.fit.ui.ScreenModels
import com.cadence.fit.ui.components.Badge
import com.cadence.fit.ui.components.CadenceCard
import com.cadence.fit.ui.components.CadenceIcon
import com.cadence.fit.ui.components.CadenceTopBar
import com.cadence.fit.ui.components.EmptyState
import com.cadence.fit.ui.components.InfoNote
import com.cadence.fit.ui.components.PrimaryButton
import com.cadence.fit.ui.components.SecondaryButton
import com.cadence.fit.ui.components.SectionHeader
import com.cadence.fit.ui.components.Sparkline
import com.cadence.fit.ui.components.StatTile
import com.cadence.fit.ui.theme.CadenceTheme

/**
 * The sleep page states the situation exactly: what can be read, whether
 * permission is granted, and what happens if the sleep app does not publish to
 * Health Connect at all. Cadence never claims a connection it does not have.
 */
@Composable
fun SleepScreen(viewModel: CadenceViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val status by viewModel.sleepStatus.collectAsStateWithLifecycle()
    val sessions by viewModel.sleepSessions.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val now = viewModel.nowMs()

    var showAboutConnection by remember { mutableStateOf(false) }

    val permissionContract = remember {
        runCatching {
            // Health Connect must be installed for its permission screen to
            // launch at all, so probe it first and fall back to the unavailable
            // contract when it is missing.
            HealthConnectClient.getOrCreate(context)
            PermissionController.createRequestPermissionResultContract()
        }.getOrNull() ?: UnavailablePermissionContract
    }
    val launcher = rememberLauncherForActivityResult(permissionContract) { granted: Set<String> ->
        viewModel.recordSleepPermission(granted.containsAll(HealthConnectPermissions.requested))
    }

    LaunchedEffect(Unit) { viewModel.refreshSleep() }

    val recovery = remember(sessions, status.isConnected, now) {
        ScreenModels.recovery(sessions, status.isConnected, now)
    }

    Column(Modifier.fillMaxSize()) {
        CadenceTopBar(
            title = "Sleep & recovery",
            subtitle = "Optional, read-only, off by default",
            onBack = onBack,
        )

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            StatusCard(
                status = status,
                sessionCount = sessions.size,
                onEnable = {
                    // Turning reading on is the moment the permission is needed, and the
                    // explanation is already on this card. One tap, one system dialog.
                    viewModel.enableSleep()
                    launcher.launch(HealthConnectPermissions.requested)
                },
                onRequestPermission = { launcher.launch(HealthConnectPermissions.requested) },
                onRefresh = { viewModel.refreshSleep() },
                onDisconnect = { viewModel.disconnectSleep() },
                onOpenHealthConnect = { openHealthConnectSettings(context) },
                nowMs = now,
            )

            if (status.availability != SleepAvailability.AVAILABLE) {
                CadenceCard {
                    SectionHeader(
                        title = "What this means",
                        subtitle = "Nothing is broken in Cadence",
                    )
                    Spacer(Modifier.size(10.dp))
                    Text(
                        "Everything except sleep context works without Health Connect. " +
                            "Workout logging, history, charts and the body map are all local " +
                            "and need no permission at all.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            recovery?.let { context ->
                CadenceCard {
                    SectionHeader(
                        title = context.headline,
                        subtitle = "Sleep context, shown for information only",
                    )
                    Spacer(Modifier.size(10.dp))
                    Text(context.detail, style = MaterialTheme.typography.bodyMedium)
                    if (context.trend.size >= 2) {
                        Spacer(Modifier.size(14.dp))
                        Sparkline(
                            values = context.trend.map { it.minutes.toFloat() },
                            accent = CadenceTheme.tones.rest,
                        )
                        Spacer(Modifier.size(6.dp))
                        Text(
                            "Nightly sleep duration for the sessions read so far.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.size(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                        StatTile(
                            "Last night",
                            context.lastNightMinutes?.let { RecoveryAdvisor.formatMinutes(it) } ?: "—",
                            modifier = Modifier.weight(1f),
                        )
                        StatTile(
                            "Usual",
                            context.recentAverageMinutes
                                ?.let { RecoveryAdvisor.formatMinutes(it) } ?: "—",
                            modifier = Modifier.weight(1f),
                        )
                        StatTile(
                            "Nights read",
                            context.trend.size.toString(),
                            modifier = Modifier.weight(1f),
                        )
                    }
                    context.sourceLabel?.let { label ->
                        Spacer(Modifier.size(10.dp))
                        Badge(label)
                    }
                    Spacer(Modifier.size(14.dp))
                    InfoNote(context.disclaimer, icon = CadenceIcon.Info)
                }
            }

            CadenceCard {
                SectionHeader(
                    title = "Nights read",
                    subtitle = if (sessions.isEmpty()) {
                        "Nothing to show yet"
                    } else {
                        "Newest first"
                    },
                    trailing = {
                        if (status.isConnected) {
                            CadenceIcon(
                                CadenceIcon.Sleep,
                                size = 18.dp,
                                tint = CadenceTheme.tones.rest,
                            )
                        }
                    },
                )
                Spacer(Modifier.size(10.dp))
                if (sessions.isEmpty()) {
                    Text(
                        if (status.isConnected) {
                            "No sleep sessions were found. Cadence only reads duration and " +
                                "session timing, and only from apps that publish to Health Connect."
                        } else {
                            "Sleep reading is off, so nothing has been read."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    sessions.take(10).forEach { session -> SleepRow(session, now) }
                }
            }

            CadenceCard {
                SectionHeader(
                    title = "Where the numbers come from",
                    subtitle = "Health Connect is the only channel",
                )
                Spacer(Modifier.size(10.dp))
                Text(
                    "Cadence reads sleep sessions that another app has already written to " +
                        "Android Health Connect, using only the read-sleep permission. There is " +
                        "no Cadence server, no account, and no direct link to the sleep app.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.size(12.dp))
                SecondaryButton(
                    text = if (showAboutConnection) "Hide the details" else "What does the sleep app need to do?",
                    icon = CadenceIcon.Info,
                    onClick = { showAboutConnection = !showAboutConnection },
                )
                if (showAboutConnection) {
                    Spacer(Modifier.size(12.dp))
                    Text(
                        "If your sleep app does not publish to Health Connect, Cadence will " +
                            "correctly show no sessions. Publishing is a small change on the " +
                            "sleep app's side — it needs write-sleep permission and one " +
                            "SleepSessionRecord per night. Nothing needs to change in Cadence, " +
                            "and no data is duplicated anywhere.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.size(10.dp))
                    InfoNote(
                        "Cadence never asks for write-sleep permission, and it never writes " +
                            "sleep data back to Health Connect.",
                    )
                }
            }

            CadenceCard {
                SectionHeader(title = "What Cadence stores", subtitle = "Local to this device")
                Spacer(Modifier.size(10.dp))
                Text(
                    "Sleep sessions are read on demand and never copied into the app's database " +
                        "or into an export file. Only the on/off switch and the time of the last " +
                        "read are saved. Turning sync off stops all reading immediately.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            Spacer(Modifier.size(16.dp))
        }
    }
}

@Composable
private fun StatusCard(
    status: SleepConnectionStatus,
    sessionCount: Int,
    onEnable: () -> Unit,
    onRequestPermission: () -> Unit,
    onRefresh: () -> Unit,
    onDisconnect: () -> Unit,
    onOpenHealthConnect: () -> Unit,
    nowMs: Long,
) {
    CadenceCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CadenceIcon(CadenceIcon.Sleep, size = 20.dp, tint = CadenceTheme.tones.rest)
            Spacer(Modifier.size(10.dp))
            Column(Modifier.weight(1f)) {
                Text(statusHeadline(status, sessionCount), style = MaterialTheme.typography.titleMedium)
                Text(
                    statusSubtitle(status, sessionCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.size(14.dp))

        when {
            status.availability != SleepAvailability.AVAILABLE -> {
                InfoNote(
                    status.availability.explanation,
                    icon = CadenceIcon.Warning,
                )
                Spacer(Modifier.size(12.dp))
                SecondaryButton(
                    text = "Open Health Connect",
                    icon = CadenceIcon.Share,
                    onClick = onOpenHealthConnect,
                )
            }

            !status.enabled -> {
                Text(
                    "Before you turn this on",
                    style = MaterialTheme.typography.titleSmall,
                )
                Spacer(Modifier.size(6.dp))
                Bullet("Reads only sleep duration and session timing from Health Connect.")
                Bullet("Requests one permission, read-sleep, and nothing else.")
                Bullet("Never sends sleep data anywhere by default.")
                Bullet("Can be disconnected at any time, which stops all reading.")
                Spacer(Modifier.size(14.dp))
                PrimaryButton(
                    text = "Turn on sleep reading",
                    icon = CadenceIcon.Check,
                    onClick = onEnable,
                )
            }

            status.permission != SleepPermissionState.GRANTED -> {
                InfoNote(
                    "Sleep reading is on, but the permission is not granted, so nothing is read. " +
                        "Android may also have revoked it.",
                    icon = CadenceIcon.Warning,
                )
                Spacer(Modifier.size(12.dp))
                PrimaryButton(
                    text = "Allow sleep access",
                    icon = CadenceIcon.Check,
                    onClick = onRequestPermission,
                )
                Spacer(Modifier.size(8.dp))
                SecondaryButton(text = "Open Health Connect settings", onClick = onOpenHealthConnect)
                Spacer(Modifier.size(8.dp))
                SecondaryButton(text = "Turn sleep reading off", onClick = onDisconnect)
            }

            sessionCount == 0 -> {
                InfoNote(
                    "Connected, but no sleep sessions were found in the last two weeks. That " +
                        "usually means no app on this device is publishing sleep to Health Connect.",
                )
                Spacer(Modifier.size(12.dp))
                SecondaryButton(
                    text = "Read again",
                    icon = CadenceIcon.Undo,
                    onClick = onRefresh,
                )
                Spacer(Modifier.size(8.dp))
                SecondaryButton(text = "Disconnect", destructive = true, onClick = onDisconnect)
            }

            else -> {
                Text(
                    "Reading sleep duration and timing for the last two weeks.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                status.lastSyncMs?.let { lastSync ->
                    Spacer(Modifier.size(6.dp))
                    Text(
                        "Last read ${TimeRanges.relativeDay(lastSync, nowMs)}.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.size(14.dp))
                PrimaryButton(
                    text = "Read again",
                    icon = CadenceIcon.Undo,
                    onClick = onRefresh,
                )
                Spacer(Modifier.size(8.dp))
                SecondaryButton(text = "Disconnect", destructive = true, onClick = onDisconnect)
            }
        }
    }
}

@Composable
private fun Bullet(text: String) {
    Row(Modifier.padding(vertical = 3.dp)) {
        CadenceIcon(CadenceIcon.Check, size = 16.dp, tint = CadenceTheme.tones.success)
        Spacer(Modifier.size(8.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun SleepRow(session: SleepSummary, nowMs: Long) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                TimeRanges.relativeDay(session.endMs, nowMs),
                style = MaterialTheme.typography.titleSmall,
            )
            session.sourceLabel?.let { label ->
                Text(
                    label,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(
            RecoveryAdvisor.formatMinutes(session.durationMinutes),
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

private fun statusHeadline(status: SleepConnectionStatus, sessionCount: Int): String = when {
    status.availability == SleepAvailability.PROVIDER_UPDATE_REQUIRED -> "Health Connect needs an update"
    status.availability == SleepAvailability.NOT_SUPPORTED -> "Sleep sync is unavailable here"
    !status.enabled -> "Sleep reading is off"
    status.permission != SleepPermissionState.GRANTED -> "Permission needed"
    sessionCount > 0 -> "Connected"
    else -> "Connected, no sessions found"
}

private fun statusSubtitle(status: SleepConnectionStatus, sessionCount: Int): String = when {
    status.availability != SleepAvailability.AVAILABLE -> status.availability.displayName
    !status.enabled -> "Turn it on to add optional recovery context"
    status.permission != SleepPermissionState.GRANTED -> "Health Connect permission not granted"
    sessionCount > 0 -> "$sessionCount sessions, last two weeks"
    else -> "Health Connect"
}

/** Used when Health Connect is not installed, so the launcher can still exist. */
private val UnavailablePermissionContract = object : ActivityResultContract<Set<String>, Set<String>>() {
    override fun createIntent(context: Context, input: Set<String>): Intent = Intent()

    override fun parseResult(resultCode: Int, intent: Intent?): Set<String> = emptySet()
}

private fun openHealthConnectSettings(context: Context) {
    val settingsIntent = Intent(HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    if (runCatching { context.startActivity(settingsIntent) }.isSuccess) return

    val rationale = Intent(HealthConnectPermissions.RATIONALE_ACTION)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(rationale) }
}
