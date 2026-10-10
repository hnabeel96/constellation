package com.eko.constellation

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.time.LocalTime
import java.time.format.DateTimeFormatter

fun Modifier.glass(
    shape: Shape = RoundedCornerShape(20.dp),
    tint: Color = Space.glass,
    border: Color = Space.glassBorder,
): Modifier = this.clip(shape).background(tint).border(1.dp, border, shape)

@Composable
fun Pill(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    filled: Boolean = false,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(50)
    Box(
        modifier
            .clip(shape)
            .background(color.copy(alpha = if (filled) 0.24f else 0.07f))
            .border(1.dp, color.copy(alpha = if (filled) 0.9f else 0.45f), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = color, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun Tag(text: String, color: Color, modifier: Modifier = Modifier) {
    Text(
        text,
        color = color,
        style = MaterialTheme.typography.labelMedium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

/** A star whose glow shows heat: dim white → cyan → pink. */
@Composable
fun StarDot(heat: Float, size: Dp = 18.dp) {
    val c = when {
        heat < 0.34f -> Color(0xFFB8BEDF)
        heat < 0.67f -> Space.cyan
        else -> Space.pink
    }
    Canvas(Modifier.size(size)) {
        val r = this.size.minDimension / 2
        drawCircle(Brush.radialGradient(listOf(c.copy(alpha = 0.2f + 0.5f * heat), Color.Transparent), center = center, radius = r), radius = r)
        drawCircle(c.copy(alpha = 0.45f + 0.55f * heat), radius = r * (0.18f + 0.16f * heat))
    }
}

@Composable
fun ThoughtRow(t: Thought, heat: Float, links: Int, subtitle: String? = null, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .glass(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StarDot(heat)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(t.text, style = MaterialTheme.typography.bodyMedium, color = Space.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) Text(subtitle, color = Space.dim, style = MaterialTheme.typography.labelMedium)
        }
        Spacer(Modifier.width(8.dp))
        Text(
            if (links == 0) "unlinked" else if (links == 1) "1 link" else "$links links",
            color = if (links == 0) Space.amber.copy(alpha = 0.8f) else Space.dim,
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

@Composable
fun SectionLabel(text: String) {
    Text(
        text.uppercase(),
        color = Space.dim,
        style = MaterialTheme.typography.labelMedium,
        modifier = Modifier.padding(start = 4.dp, top = 12.dp, bottom = 2.dp),
    )
}

@Composable
fun ScreenTitle(title: String, subtitle: String? = null) {
    Column(Modifier.padding(bottom = 4.dp)) {
        Text(title, style = MaterialTheme.typography.headlineLarge, color = Space.text)
        if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = Space.dim)
    }
}

@Composable
fun Card(title: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().glass().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (title != null) Text(title, style = MaterialTheme.typography.titleMedium, color = Space.text)
        content()
    }
}

fun ago(ms: Long): String {
    if (ms <= 0) return "a while ago"
    val days = ((System.currentTimeMillis() - ms) / Repo.DAY).toInt()
    return when {
        days <= 0 -> "today"
        days == 1 -> "yesterday"
        days < 60 -> "$days days ago"
        else -> "${days / 30} months ago"
    }
}

fun clock(h: Int, m: Int): String = LocalTime.of(h, m).format(DateTimeFormatter.ofPattern("h:mm a"))

fun toast(ctx: Context, msg: String) = Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()
