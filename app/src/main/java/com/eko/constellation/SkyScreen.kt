package com.eko.constellation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.ZoneId

@Composable
fun SkyScreen(onOpen: (String) -> Unit) {
    val g = Repo.graph
    val lc = remember(g) { Repo.linkCounts(g) }
    val ideas = remember(g) { Repo.ideas(g) }
    val roots = remember(g) { Repo.roots(g) }
    var query by rememberSaveable { mutableStateOf("") }
    var expanded by remember { mutableStateOf(setOf<String>()) }
    var newTree by remember { mutableStateOf(false) }
    var renameTree by remember { mutableStateOf<Thought?>(null) }
    var deleteTree by remember { mutableStateOf<Thought?>(null) }
    val monthStart = remember { LocalDate.now().withDayOfMonth(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() }
    val heatOf: (Thought) -> Float = { t -> Repo.heat(t, lc[t.id] ?: 0) }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 150.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { ScreenTitle("Sky", "${ideas.size} thoughts in ${roots.size} trees · brighter stars are the ones you keep returning to") }
        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search thoughts") },
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
            )
        }
        if (query.isNotBlank()) {
            val hits = ideas.filter { it.text.contains(query.trim(), ignoreCase = true) }.sortedByDescending(heatOf)
            if (hits.isEmpty()) item { Text("No matches", color = Space.dim) }
            items(hits, key = { it.id }) { t ->
                ThoughtRow(t, heatOf(t), lc[t.id] ?: 0, subtitle = Repo.byId(t.treeId)?.text ?: "No tree") { onOpen(t.id) }
            }
        } else {
            val rootIds = roots.map { it.id }.toSet()
            val groups: List<Pair<Thought?, List<Thought>>> =
                roots.map { r -> r to ideas.filter { it.treeId == r.id } } +
                    listOf(null to ideas.filter { it.treeId == null || it.treeId !in rootIds })
            groups.forEach { (root, members) ->
                val key = root?.id ?: "loose"
                val isOpen = key in expanded
                item(key = "tree-$key") {
                    TreeHeader(
                        name = root?.text ?: "Loose thoughts",
                        count = members.size,
                        grown = members.count { it.createdAt >= monthStart },
                        open = isOpen,
                        onToggle = { expanded = if (isOpen) expanded - key else expanded + key },
                        onRename = root?.let { r -> { renameTree = r } },
                        onDelete = root?.let { r -> { deleteTree = r } },
                    )
                }
                if (isOpen) {
                    if (members.isEmpty()) item(key = "empty-$key") {
                        Text("Nothing here yet.", color = Space.dim, modifier = Modifier.padding(start = 16.dp))
                    }
                    items(members.sortedByDescending(heatOf), key = { it.id }) { t ->
                        ThoughtRow(t, heatOf(t), lc[t.id] ?: 0, subtitle = ago(t.createdAt)) { onOpen(t.id) }
                    }
                }
            }
            item { Pill("+ New tree", Space.violet) { newTree = true } }
        }
    }

    if (newTree) TextDialog("New tree", "", "Create", onDismiss = { newTree = false }) { Repo.addTree(it); newTree = false }
    renameTree?.let { r ->
        TextDialog("Rename tree", r.text, "Save", onDismiss = { renameTree = null }) { Repo.edit(r.id, it); renameTree = null }
    }
    deleteTree?.let { r ->
        ConfirmDialog(
            "Delete \"${r.text}\"?",
            "The thoughts inside stay. They just become loose.",
            "Delete",
            onDismiss = { deleteTree = null },
        ) { Repo.deleteForever(r.id); deleteTree = null }
    }
}

@Composable
private fun TreeHeader(
    name: String,
    count: Int,
    grown: Int,
    open: Boolean,
    onToggle: () -> Unit,
    onRename: (() -> Unit)?,
    onDelete: (() -> Unit)?,
) {
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .glass(tint = if (open) Space.violet.copy(alpha = 0.10f) else Space.glass)
            .clickable(onClick = onToggle)
            .padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.titleMedium, color = Space.text)
            Text(
                "$count thoughts" + if (grown > 0) " · +$grown this month" else "",
                style = MaterialTheme.typography.labelMedium,
                color = if (grown > 0) Space.mint else Space.dim,
            )
        }
        if (onRename != null && onDelete != null) {
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "Tree options", tint = Space.dim) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Rename") }, onClick = { menu = false; onRename() })
                    DropdownMenuItem(text = { Text("Delete tree") }, onClick = { menu = false; onDelete() })
                }
            }
        }
        Text(if (open) "−" else "+", style = MaterialTheme.typography.titleLarge, color = Space.cyan, modifier = Modifier.padding(horizontal = 10.dp))
    }
}
