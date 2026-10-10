package com.eko.constellation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun GraveyardScreen(onOpen: (String) -> Unit) {
    val g = Repo.graph
    val gone = remember(g) { g.thoughts.filter { !it.isRoot && it.status != Status.ACTIVE }.sortedByDescending { it.endedAt } }
    val filters = remember(g) { gone.mapNotNull { it.note }.distinct() }
    var toDelete by remember { mutableStateOf<Thought?>(null) }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 150.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { ScreenTitle("Graveyard", "Ideas you decided against. A decided no is progress.") }
        if (filters.isNotEmpty()) {
            item {
                Card("Your filters") {
                    filters.take(12).forEach { Text("• $it", color = Space.text, style = MaterialTheme.typography.bodyMedium) }
                }
            }
        }
        if (gone.isEmpty()) {
            item { Card { Text("Nothing here yet. When you kill an idea, its reason lands here and becomes a filter.", color = Space.dim) } }
        }
        items(gone, key = { it.id }) { t ->
            val merged = t.status == Status.MERGED
            val into = if (merged) g.links.firstOrNull { it.from == t.id && it.type == LinkType.MERGED_INTO }
                ?.let { l -> g.thoughts.find { it.id == l.to } } else null
            Column(
                Modifier.fillMaxWidth().glass().clickable { onOpen(t.id) }.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(t.text, color = Space.text.copy(alpha = 0.8f), style = MaterialTheme.typography.bodyMedium, maxLines = 4, overflow = TextOverflow.Ellipsis)
                Text((if (merged) "merged " else "let go ") + ago(t.endedAt), color = Space.dim, style = MaterialTheme.typography.labelMedium)
                if (into != null) Text("into: ${into.text}", color = Space.mint, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                t.note?.let { Text("Why: $it", color = Space.red.copy(alpha = 0.85f), style = MaterialTheme.typography.bodyMedium) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill("Revive", Space.mint) { Repo.revive(t.id) }
                    Pill("Delete forever", Space.red) { toDelete = t }
                }
            }
        }
    }

    toDelete?.let { victim ->
        ConfirmDialog(
            "Delete forever?",
            "This removes the thought and its links. It can't be undone.",
            "Delete",
            onDismiss = { toDelete = null },
        ) { Repo.deleteForever(victim.id); toDelete = null }
    }
}
