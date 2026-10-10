package com.eko.constellation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

data class CaptureRequest(
    val text: String = "",
    val parentId: String? = null,
    val treeId: String? = null,
    val type: LinkType = LinkType.BUILDS_ON,
    val fromQueue: Boolean = false,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CaptureDialog(
    req: CaptureRequest,
    onChange: (CaptureRequest) -> Unit,
    onVoice: () -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
) {
    val g = Repo.graph
    val parent = Repo.byId(req.parentId)
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(250)
        runCatching { focus.requestFocus() }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Space.panel,
        title = { Text(if (parent != null && !parent.isRoot) "Branch a thought" else "New thought") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (parent != null && !parent.isRoot) {
                    Text("from: ${parent.text}", color = Space.dim, style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
                OutlinedTextField(
                    value = req.text,
                    onValueChange = { onChange(req.copy(text = it)) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 110.dp).focusRequester(focus),
                    placeholder = { Text("What's on your mind?") },
                    trailingIcon = { IconButton(onClick = onVoice) { Icon(Icons.Filled.Mic, "Speak", tint = Space.cyan) } },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                )
                if (parent != null && !parent.isRoot) {
                    Text("How it connects", style = MaterialTheme.typography.labelMedium, color = Space.dim)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(LinkType.BUILDS_ON, LinkType.CONTRADICTS, LinkType.INSPIRED_BY, LinkType.RELATED).forEach { lt ->
                            Pill(lt.label, lt.color(), filled = req.type == lt) { onChange(req.copy(type = lt)) }
                        }
                    }
                }
                if (parent?.isRoot != true) {
                    Text("Tree", style = MaterialTheme.typography.labelMedium, color = Space.dim)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Pill("None", Space.dim, filled = req.treeId == null) { onChange(req.copy(treeId = null)) }
                        Repo.roots(g).forEach { r ->
                            Pill(r.text, Space.violet, filled = req.treeId == r.id) { onChange(req.copy(treeId = r.id)) }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onSave, enabled = req.text.isNotBlank()) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = Space.dim) } },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LinkPickerDialog(source: Thought, onDismiss: () -> Unit, onPick: (String, LinkType) -> Unit) {
    val g = Repo.graph
    var query by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(LinkType.RELATED) }
    var picked by remember { mutableStateOf<String?>(null) }
    val existing = remember(g) { Repo.neighbors(source.id, g).map { it.other.id }.toSet() }
    val options = Repo.ideas(g)
        .filter { it.id != source.id && it.id !in existing && (query.isBlank() || it.text.contains(query.trim(), ignoreCase = true)) }
        .sortedByDescending { it.lastTouched }
        .take(60)
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Space.panel,
        title = { Text("Link this thought") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(source.text, color = Space.dim, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    LinkType.entries.forEach { lt -> Pill(lt.verb, lt.color(), filled = type == lt) { type = lt } }
                }
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Search thoughts") },
                    leadingIcon = { Icon(Icons.Filled.Search, null) },
                    singleLine = true,
                )
                Column(
                    Modifier.heightIn(max = 260.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (options.isEmpty()) Text("Nothing to link to yet.", color = Space.dim)
                    options.forEach { t ->
                        val sel = picked == t.id
                        Text(
                            t.text,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Space.text,
                            modifier = Modifier
                                .fillMaxWidth()
                                .glass(
                                    RoundedCornerShape(12.dp),
                                    tint = if (sel) Space.cyan.copy(alpha = 0.15f) else Space.glass,
                                    border = if (sel) Space.cyan else Space.glassBorder,
                                )
                                .clickable { picked = t.id }
                                .padding(12.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { picked?.let { onPick(it, type) } }, enabled = picked != null) { Text("Link") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = Space.dim) } },
    )
}

@Composable
fun KillDialog(t: Thought, onDismiss: () -> Unit, onKill: (String) -> Unit) {
    var why by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Space.panel,
        title = { Text("Let it go?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(t.text, color = Space.dim, maxLines = 3, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                Text("Why not? Optional, but your reasons become filters for the next idea.", style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    value = why,
                    onValueChange = { why = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("e.g. needs servers, too niche") },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onKill(why) }) { Text("Let it go", color = Space.red) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Keep", color = Space.dim) } },
    )
}

@Composable
fun TextDialog(title: String, initial: String, confirm: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var value by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Space.panel,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            )
        },
        confirmButton = { TextButton(onClick = { onSave(value) }, enabled = value.isNotBlank()) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = Space.dim) } },
    )
}

@Composable
fun ConfirmDialog(title: String, message: String, confirm: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Space.panel,
        title = { Text(title) },
        text = { Text(message, color = Space.dim) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirm, color = Space.red) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = Space.dim) } },
    )
}
