package com.eko.constellation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.inset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun TodayScreen(
    onOpen: (String) -> Unit,
    onBranch: (Thought) -> Unit,
    onLink: (Thought) -> Unit,
    onKill: (Thought) -> Unit,
) {
    val ctx = LocalContext.current
    val g = Repo.graph
    LaunchedEffect(Unit) { Repo.ensureQueue() }
    val lc = remember(g) { Repo.linkCounts(g) }
    val ideas = remember(g) { Repo.ideas(g) }
    val queue = g.queue.ids.mapNotNull { id -> g.thoughts.find { it.id == id && it.status == Status.ACTIVE } }
    val recent = ideas.sortedByDescending { it.createdAt }.take(5)
    val unlinked = ideas.count { (lc[it.id] ?: 0) == 0 }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 150.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Today", style = MaterialTheme.typography.headlineLarge, color = Space.text)
                    Text(LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, d MMM")), color = Space.dim, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(6.dp))
                    Text("${ideas.size} thoughts · ${g.links.size} links · $unlinked unlinked", color = Space.dim, style = MaterialTheme.typography.labelMedium)
                }
                OrbitRing(done = g.queue.done, total = g.queue.done + queue.size)
            }
        }
        if (queue.isEmpty()) {
            item {
                Card("Nothing waiting") {
                    Text(
                        if (ideas.isEmpty()) "Capture your first thought with +. It comes back tomorrow so you can grow it."
                        else "Everything due today has been decided. Pull one more if you want to keep going.",
                        color = Space.dim,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (ideas.isNotEmpty()) Pill("Pull one more", Space.cyan) { if (!Repo.pullOne()) toast(ctx, "Nothing else to pull") }
                }
            }
        } else {
            item { SectionLabel("Back for a decision") }
        }
        items(queue, key = { it.id }) { t ->
            ResurfaceCard(
                t = t,
                links = lc[t.id] ?: 0,
                tree = Repo.byId(t.treeId)?.text,
                onOpen = { onOpen(t.id) },
                onBranch = { onBranch(t) },
                onLink = { onLink(t) },
                onPark = { toast(ctx, "Parked. Back in ${Repo.park(t.id)} days") },
                onKill = { onKill(t) },
            )
        }
        if (recent.isNotEmpty()) {
            item { SectionLabel("Recently captured") }
            items(recent, key = { "recent-" + it.id }) { t ->
                ThoughtRow(t, Repo.heat(t, lc[t.id] ?: 0), lc[t.id] ?: 0, subtitle = ago(t.createdAt)) { onOpen(t.id) }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ResurfaceCard(
    t: Thought,
    links: Int,
    tree: String?,
    onOpen: () -> Unit,
    onBranch: () -> Unit,
    onLink: () -> Unit,
    onPark: () -> Unit,
    onKill: () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().glass().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Tag(tree ?: "No tree", Space.violet)
            Tag("captured " + ago(t.createdAt), Space.dim)
            if (links == 0) Tag("unlinked", Space.amber) else Tag(if (links == 1) "1 link" else "$links links", Space.cyan)
        }
        Text(t.text, style = MaterialTheme.typography.bodyLarge, color = Space.text, modifier = Modifier.clickable(onClick = onOpen))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill("Branch", Space.cyan, Modifier.weight(1f), onClick = onBranch)
            Pill("Link", Space.violet, Modifier.weight(1f), onClick = onLink)
            Pill("Park", Space.dim, Modifier.weight(1f), onClick = onPark)
            Pill("Kill", Space.red, Modifier.weight(1f), onClick = onKill)
        }
    }
}

@Composable
private fun OrbitRing(done: Int, total: Int) {
    Box(Modifier.size(72.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
            inset(4.dp.toPx()) {
                drawArc(Color(0x22FFFFFF), 0f, 360f, false, style = stroke)
                if (total > 0 && done > 0) drawArc(Space.mint, -90f, 360f * done / total, false, style = stroke)
            }
        }
        Text(if (total == 0) "–" else "$done/$total", style = MaterialTheme.typography.titleMedium, color = Space.text)
    }
}
