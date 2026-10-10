package com.eko.constellation

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.speech.RecognizerIntent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

enum class Tab(val label: String, val icon: ImageVector) {
    TODAY("Today", Icons.Filled.WbSunny),
    SKY("Sky", Icons.Filled.AutoAwesome),
    GRAVEYARD("Graveyard", Icons.Filled.Archive),
    SETTINGS("Settings", Icons.Filled.Settings),
}

private data class LinkReq(val source: Thought, val fromQueue: Boolean)
private data class KillReq(val thought: Thought)

@Composable
fun App(pending: MutableState<Launch?>) {
    val ctx = LocalContext.current
    var tab by rememberSaveable { mutableStateOf(Tab.TODAY) }
    val stack = remember { mutableStateListOf<String>() }
    var capture by remember { mutableStateOf<CaptureRequest?>(null) }
    var linkReq by remember { mutableStateOf<LinkReq?>(null) }
    var killReq by remember { mutableStateOf<KillReq?>(null) }

    val voice = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val said = r.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (!said.isNullOrBlank()) {
            val c = capture
            capture = if (c == null) CaptureRequest(text = said) else c.copy(text = (c.text + " " + said).trim())
        }
    }
    val startVoice: () -> Unit = {
        try {
            voice.launch(
                Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    .putExtra(RecognizerIntent.EXTRA_PROMPT, "Say the thought")
            )
        } catch (e: ActivityNotFoundException) {
            toast(ctx, "No voice input available on this phone")
        }
    }

    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        Reminders.schedule(ctx)
    }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED &&
            !Repo.flag("askedNotif")
        ) {
            Repo.setFlag("askedNotif")
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // Widget, share sheet, text selection, app shortcuts and the notification land here.
    val launch = pending.value
    LaunchedEffect(launch) {
        when (launch) {
            is Launch.Capture -> capture = CaptureRequest(text = launch.text)
            Launch.Voice -> { capture = CaptureRequest(); startVoice() }
            Launch.Today -> { tab = Tab.TODAY; stack.clear() }
            null -> Unit
        }
        if (launch != null) pending.value = null
    }

    val open: (String) -> Unit = { id -> stack.add(id) }
    val pop: () -> Unit = { if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex) }
    BackHandler(enabled = stack.isNotEmpty()) { pop() }
    BackHandler(enabled = stack.isEmpty() && tab != Tab.TODAY) { tab = Tab.TODAY }

    fun newRequest(): CaptureRequest {
        val f = Repo.byId(stack.lastOrNull())
        return if (f != null && f.status == Status.ACTIVE) CaptureRequest(parentId = f.id, treeId = f.treeId) else CaptureRequest()
    }

    Box(Modifier.fillMaxSize()) {
        SpaceBackground()
        Scaffold(
            containerColor = Color.Transparent,
            bottomBar = {
                NavigationBar(containerColor = Color(0xD90B0D22), tonalElevation = 0.dp) {
                    Tab.entries.forEach { t ->
                        NavigationBarItem(
                            selected = tab == t && stack.isEmpty(),
                            onClick = { tab = t; stack.clear() },
                            icon = { Icon(t.icon, t.label) },
                            label = { Text(t.label) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Space.cyan,
                                selectedTextColor = Space.cyan,
                                indicatorColor = Space.cyan.copy(alpha = 0.12f),
                                unselectedIconColor = Space.dim,
                                unselectedTextColor = Space.dim,
                            ),
                        )
                    }
                }
            },
            floatingActionButton = {
                if (tab != Tab.SETTINGS || stack.isNotEmpty()) {
                    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        SmallFloatingActionButton(
                            onClick = { capture = newRequest(); startVoice() },
                            containerColor = Color(0xFF1C1F46),
                            contentColor = Space.cyan,
                        ) { Icon(Icons.Filled.Mic, "Speak a thought") }
                        FloatingActionButton(
                            onClick = { capture = newRequest() },
                            containerColor = Space.cyan,
                            contentColor = Space.ink,
                        ) { Icon(Icons.Filled.Add, "New thought") }
                    }
                }
            },
        ) { pad ->
            Box(Modifier.padding(pad)) {
                val focused = stack.lastOrNull()
                if (focused != null) {
                    FocusScreen(
                        id = focused,
                        onOpen = open,
                        onBack = pop,
                        onBranch = { t -> capture = CaptureRequest(parentId = t.id, treeId = t.treeId) },
                        onLink = { t -> linkReq = LinkReq(t, false) },
                        onKill = { t -> killReq = KillReq(t) },
                    )
                } else when (tab) {
                    Tab.TODAY -> TodayScreen(
                        onOpen = open,
                        onBranch = { t -> capture = CaptureRequest(parentId = t.id, treeId = t.treeId, fromQueue = true) },
                        onLink = { t -> linkReq = LinkReq(t, true) },
                        onKill = { t -> killReq = KillReq(t) },
                    )
                    Tab.SKY -> SkyScreen(onOpen = open)
                    Tab.GRAVEYARD -> GraveyardScreen(onOpen = open)
                    Tab.SETTINGS -> SettingsScreen()
                }
            }
        }
    }

    capture?.let { req ->
        CaptureDialog(
            req = req,
            onChange = { capture = it },
            onVoice = startVoice,
            onDismiss = { capture = null },
            onSave = {
                Repo.capture(req.text, req.treeId, req.parentId, req.type)
                if (req.fromQueue && req.parentId != null) Repo.engaged(req.parentId)
                capture = null
                toast(ctx, "Saved")
            },
        )
    }
    linkReq?.let { r ->
        LinkPickerDialog(
            source = r.source,
            onDismiss = { linkReq = null },
            onPick = { to, type ->
                Repo.link(r.source.id, to, type)
                if (r.fromQueue) Repo.engaged(r.source.id)
                linkReq = null
                toast(ctx, if (type == LinkType.MERGED_INTO) "Merged" else "Linked")
            },
        )
    }
    killReq?.let { r ->
        KillDialog(
            t = r.thought,
            onDismiss = { killReq = null },
            onKill = { why ->
                Repo.kill(r.thought.id, why)
                killReq = null
                toast(ctx, "Moved to the graveyard")
            },
        )
    }
}
