package com.eko.constellation

import android.app.TimePickerDialog
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.time.LocalDate

@Composable
fun SettingsScreen() {
    val ctx = LocalContext.current
    val s = Repo.graph.settings
    var pendingImport by remember { mutableStateOf<String?>(null) }

    // Re-check permissions whenever we come back from the system settings screens.
    var resumes by remember { mutableIntStateOf(0) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) resumes++ }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }
    val notificationsOn = remember(resumes) { NotificationManagerCompat.from(ctx).areNotificationsEnabled() }
    val batteryOk = remember(resumes) {
        ctx.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(ctx.packageName)
    }

    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            runCatching { ctx.contentResolver.openOutputStream(uri)?.use { it.write(Repo.exportJson().toByteArray()) } }
                .onSuccess { toast(ctx, "Backup saved") }
                .onFailure { toast(ctx, "Couldn't save the backup") }
        }
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            pendingImport = runCatching {
                ctx.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            }.getOrNull()
            if (pendingImport == null) toast(ctx, "Couldn't read that file")
        }
    }
    val version = remember { runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: "" }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { ScreenTitle("Settings") }
        item {
            Card("Daily resurfacing") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Nudge me every day", color = Space.text, modifier = Modifier.weight(1f))
                    Switch(
                        checked = s.notify,
                        onCheckedChange = { v -> Repo.updateSettings { it.copy(notify = v) }; Reminders.schedule(ctx) },
                        colors = SwitchDefaults.colors(checkedTrackColor = Space.cyan, checkedThumbColor = Space.ink),
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Time", color = Space.text, modifier = Modifier.weight(1f))
                    Pill(clock(s.hour, s.minute), Space.cyan) {
                        TimePickerDialog(ctx, { _, h, m ->
                            Repo.updateSettings { it.copy(hour = h, minute = m) }
                            Reminders.schedule(ctx)
                        }, s.hour, s.minute, false).show()
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Thoughts per day", color = Space.text, modifier = Modifier.weight(1f))
                    Pill("−", Space.dim) { Repo.updateSettings { it.copy(perDay = (it.perDay - 1).coerceAtLeast(1)) } }
                    Spacer(Modifier.width(12.dp))
                    Text("${s.perDay}", color = Space.text, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.width(12.dp))
                    Pill("+", Space.dim) { Repo.updateSettings { it.copy(perDay = (it.perDay + 1).coerceAtMost(7)) } }
                }
                Text(
                    "Unlinked thoughts come back first. Parking doubles the wait each time: 2, 4, 8, 16 days.",
                    color = Space.dim,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        if (!notificationsOn || !batteryOk) {
            item {
                Card("Fix so the nudge arrives") {
                    if (!notificationsOn) FixRow("Notifications are off") {
                        ctx.startActivity(
                            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
                        )
                    }
                    if (!batteryOk) FixRow("Battery saver may delay the daily nudge") {
                        runCatching {
                            ctx.startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${ctx.packageName}")))
                        }.onFailure { toast(ctx, "Open battery settings manually") }
                    }
                }
            }
        }
        item {
            Card("Backup") {
                Text("Everything lives only on this phone. Export a backup now and then.", color = Space.dim, style = MaterialTheme.typography.bodyMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill("Export", Space.cyan) { exporter.launch("constellation-${LocalDate.now()}.json") }
                    Pill("Import", Space.violet) { importer.launch(arrayOf("application/json", "text/plain", "*/*")) }
                }
            }
        }
        item {
            Card("Ways to capture") {
                listOf(
                    "Home-screen widget: long-press the home screen → Widgets → Constellation",
                    "Share text from any app → Constellation",
                    "Select text anywhere → ⋮ → Save to Constellation",
                    "Long-press the app icon → New thought or Speak a thought",
                ).forEach { Text("• $it", color = Space.text, style = MaterialTheme.typography.bodyMedium) }
            }
        }
        item { Text("Constellation $version", color = Space.dim, style = MaterialTheme.typography.labelMedium) }
    }

    pendingImport?.let { raw ->
        ConfirmDialog(
            "Replace everything?",
            "Importing replaces all thoughts on this phone with the backup.",
            "Replace",
            onDismiss = { pendingImport = null },
        ) {
            if (Repo.importJson(raw)) {
                Reminders.schedule(ctx)
                toast(ctx, "Backup restored")
            } else {
                toast(ctx, "That file isn't a Constellation backup")
            }
            pendingImport = null
        }
    }
}

@Composable
private fun FixRow(text: String, onFix: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(text, color = Space.text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Pill("Fix", Space.amber, onClick = onFix)
    }
}
