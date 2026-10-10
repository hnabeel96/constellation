package com.eko.constellation

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun FocusScreen(
    id: String,
    onOpen: (String) -> Unit,
    onBack: () -> Unit,
    onBranch: (Thought) -> Unit,
    onLink: (Thought) -> Unit,
    onKill: (Thought) -> Unit,
) {
    val ctx = LocalContext.current
    val g = Repo.graph
    val t = g.thoughts.find { it.id == id }
    LaunchedEffect(id) { if (t == null) onBack() else Repo.visit(id) }
    if (t == null) return

    val nbrs = remember(g, id) { Repo.neighbors(id, g) }
    val lc = remember(g) { Repo.linkCounts(g) }
    var editing by remember { mutableStateOf(false) }
    var treeMenu by remember { mutableStateOf(false) }
    var unlink by remember { mutableStateOf<Neighbor?>(null) }
    val alive = t.status == Status.ACTIVE

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 150.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Space.text) }
                Spacer(Modifier.weight(1f))
                Box {
                    Tag((Repo.byId(t.treeId)?.text ?: "No tree") + "  ▾", Space.violet, Modifier.clickable { treeMenu = true })
                    DropdownMenu(expanded = treeMenu, onDismissRequest = { treeMenu = false }) {
                        DropdownMenuItem(text = { Text("No tree") }, onClick = { Repo.setTree(id, null); treeMenu = false })
                        Repo.roots(g).forEach { r ->
                            DropdownMenuItem(text = { Text(r.text) }, onClick = { Repo.setTree(id, r.id); treeMenu = false })
                        }
                    }
                }
            }
        }
        item { GraphView(t, nbrs, onOpen) }
        item {
            Column(
                Modifier.fillMaxWidth().glass().clickable { editing = true }.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    t.text,
                    style = MaterialTheme.typography.bodyLarge,
                    color = Space.text,
                    textDecoration = if (alive) null else TextDecoration.LineThrough,
                )
                Text(
                    buildString {
                        append("captured ").append(ago(t.createdAt))
                        append(" · opened ").append(t.visits).append("×")
                        append(" · heat ").append((Repo.heat(t, lc[t.id] ?: 0) * 100).toInt())
                        if (t.status == Status.MERGED) append(" · merged")
                        if (t.status == Status.KILLED) append(" · let go")
                    },
                    color = Space.dim,
                    style = MaterialTheme.typography.labelMedium,
                )
                t.note?.let { Text("Why not: $it", color = Space.red.copy(alpha = 0.85f), style = MaterialTheme.typography.bodyMedium) }
                Text("Tap to edit", color = Space.dim.copy(alpha = 0.6f), style = MaterialTheme.typography.labelMedium)
            }
        }
        item {
            if (alive) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill("Branch", Space.cyan, Modifier.weight(1f)) { onBranch(t) }
                    Pill("Link", Space.violet, Modifier.weight(1f)) { onLink(t) }
                    Pill("Park", Space.dim, Modifier.weight(1f)) { toast(ctx, "Parked. Back in ${Repo.park(id)} days") }
                    Pill("Kill", Space.red, Modifier.weight(1f)) { onKill(t) }
                }
            } else {
                Pill("Revive", Space.mint) { Repo.revive(id) }
            }
        }
        item { SectionLabel(if (nbrs.isEmpty()) "No connections yet" else "Connections · ${nbrs.size}") }
        items(nbrs, key = { it.link.id }) { n ->
            ConnectionRow(n, onOpen = { onOpen(n.other.id) }, onUnlink = { unlink = n })
        }
    }

    if (editing) {
        TextDialog("Edit thought", t.text, "Save", onDismiss = { editing = false }) { Repo.edit(id, it); editing = false }
    }
    unlink?.let { n ->
        val label = if (n.outgoing) n.link.type.label else n.link.type.incoming
        ConfirmDialog("Remove link?", "$label: ${n.other.text.take(80)}", "Remove", onDismiss = { unlink = null }) {
            Repo.unlink(n.link.id)
            unlink = null
        }
    }
}

@Composable
private fun ConnectionRow(n: Neighbor, onOpen: () -> Unit, onUnlink: () -> Unit) {
    val label = if (n.outgoing) n.link.type.label else n.link.type.incoming
    Row(
        Modifier
            .fillMaxWidth()
            .glass(RoundedCornerShape(16.dp))
            .clickable(onClick = onOpen)
            .padding(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, color = n.link.type.color(), style = MaterialTheme.typography.labelMedium)
            Text(
                n.other.text,
                color = Space.text,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textDecoration = if (n.other.status == Status.ACTIVE) null else TextDecoration.LineThrough,
            )
        }
        IconButton(onClick = onUnlink) { Icon(Icons.Filled.Close, "Remove link", tint = Space.dim) }
    }
}

private fun orbitPoints(n: Int, w: Float, h: Float, padX: Float, padY: Float): List<Offset> {
    if (n == 0) return emptyList()
    val rx = (w / 2 - padX).coerceAtLeast(10f)
    val ry = (h / 2 - padY).coerceAtLeast(10f)
    val shift = if (n == 4) PI / 4 else 0.0
    return List(n) { i ->
        val a = -PI / 2 + 2 * PI * i / n + shift
        Offset((w / 2 + rx * cos(a)).toFloat(), (h / 2 + ry * sin(a)).toFloat())
    }
}

/** The thought in the middle, its neighbors on an orbit around it, lines colored by link type. */
@Composable
private fun GraphView(center: Thought, nbrs: List<Neighbor>, onOpen: (String) -> Unit) {
    val shown = nbrs.take(8)
    val density = LocalDensity.current
    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2400), RepeatMode.Reverse),
        label = "p",
    )
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .height(360.dp)
            .glass(RoundedCornerShape(24.dp), tint = Color(0x08FFFFFF))
    ) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        val padX = with(density) { 58.dp.toPx() }
        val padY = with(density) { 34.dp.toPx() }
        val points = remember(shown.size, w, h) { orbitPoints(shown.size, w, h, padX, padY) }
        val mid = Offset(w / 2, h / 2)

        Canvas(Modifier.fillMaxSize()) {
            if (shown.isNotEmpty()) {
                drawOval(
                    Color(0x14FFFFFF),
                    topLeft = Offset(padX, padY),
                    size = Size(w - 2 * padX, h - 2 * padY),
                    style = Stroke(width = 1.dp.toPx()),
                )
            }
            val glowR = 120.dp.toPx()
            drawCircle(
                Brush.radialGradient(listOf(Space.cyan.copy(alpha = 0.22f * pulse), Color.Transparent), center = mid, radius = glowR),
                radius = glowR,
                center = mid,
            )
            shown.forEachIndexed { i, n ->
                val p = points[i]
                drawLine(n.link.type.color().copy(alpha = 0.6f), mid, p, strokeWidth = 1.6.dp.toPx())
            }
        }

        Layout(
            content = {
                Node(center.text, Space.cyan, big = true) {}
                shown.forEach { n -> Node(n.other.text, n.link.type.color(), big = false) { onOpen(n.other.id) } }
            },
            modifier = Modifier.fillMaxSize(),
        ) { measurables, cons ->
            val loose = cons.copy(minWidth = 0, minHeight = 0)
            val placeables = measurables.map { it.measure(loose) }
            layout(cons.maxWidth, cons.maxHeight) {
                placeables.forEachIndexed { i, p ->
                    val pt = if (i == 0) mid else points.getOrElse(i - 1) { mid }
                    val x = (pt.x - p.width / 2).toInt().coerceIn(0, (cons.maxWidth - p.width).coerceAtLeast(0))
                    val y = (pt.y - p.height / 2).toInt().coerceIn(0, (cons.maxHeight - p.height).coerceAtLeast(0))
                    p.place(x, y)
                }
            }
        }

        if (shown.isEmpty()) {
            Text(
                "A lone star. Branch or link to grow it.",
                color = Space.dim,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
            )
        }
        if (nbrs.size > shown.size) {
            Text(
                "+${nbrs.size - shown.size} more below",
                color = Space.dim,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp),
            )
        }
    }
}

@Composable
private fun Node(text: String, color: Color, big: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Text(
        text,
        maxLines = if (big) 4 else 2,
        overflow = TextOverflow.Ellipsis,
        textAlign = TextAlign.Center,
        color = Space.text,
        style = if (big) MaterialTheme.typography.titleMedium else MaterialTheme.typography.labelMedium,
        modifier = Modifier
            .widthIn(max = if (big) 132.dp else 100.dp)
            .clip(shape)
            .background(Color(0xEB0F1230))
            .border(1.dp, color.copy(alpha = if (big) 0.9f else 0.6f), shape)
            .clickable(enabled = !big, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
    )
}
