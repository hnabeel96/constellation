package com.eko.constellation

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.sin
import kotlin.random.Random

object Space {
    val bgTop = Color(0xFF070A1A)
    val bgMid = Color(0xFF0D0A26)
    val bgBottom = Color(0xFF140B30)
    val panel = Color(0xFF14163A)
    val glass = Color(0x12FFFFFF)
    val glassBorder = Color(0x22FFFFFF)
    val text = Color(0xFFE8ECFF)
    val dim = Color(0xFF8F96B8)
    val cyan = Color(0xFF5EE7F2)
    val violet = Color(0xFF9B7BFF)
    val pink = Color(0xFFFF7AC6)
    val mint = Color(0xFF6EF2B0)
    val red = Color(0xFFFF6B8B)
    val amber = Color(0xFFFFC66E)
    val ink = Color(0xFF04131A)
}

fun LinkType.color(): Color = when (this) {
    LinkType.BUILDS_ON -> Space.cyan
    LinkType.CONTRADICTS -> Space.red
    LinkType.INSPIRED_BY -> Space.violet
    LinkType.RELATED -> Color(0xFFB8BEDF)
    LinkType.MERGED_INTO -> Space.mint
}

private val scheme = darkColorScheme(
    primary = Space.cyan,
    onPrimary = Space.ink,
    secondary = Space.violet,
    onSecondary = Color.White,
    background = Space.bgTop,
    onBackground = Space.text,
    surface = Space.panel,
    onSurface = Space.text,
    surfaceVariant = Color(0xFF1C1F3D),
    onSurfaceVariant = Space.dim,
    surfaceContainer = Space.panel,
    surfaceContainerHigh = Color(0xFF181A40),
    surfaceContainerHighest = Color(0xFF1C1F46),
    outline = Color(0x44FFFFFF),
    outlineVariant = Color(0x22FFFFFF),
    error = Space.red,
)

private val type = Typography(
    headlineLarge = TextStyle(fontWeight = FontWeight.Light, fontSize = 34.sp, letterSpacing = 0.5.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.Normal, fontSize = 20.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 16.sp),
    bodyLarge = TextStyle(fontWeight = FontWeight.Light, fontSize = 17.sp, lineHeight = 25.sp),
    bodyMedium = TextStyle(fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 12.sp, letterSpacing = 0.5.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.Medium, fontSize = 14.sp, letterSpacing = 0.3.sp),
)

@Composable
fun ConstellationTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, typography = type, content = content)
}

private class Star(val x: Float, val y: Float, val r: Float, val phase: Float, val speed: Float)

/** Deep-space gradient, two nebula glows and a slowly twinkling starfield. */
@Composable
fun SpaceBackground() {
    val stars = remember {
        val rnd = Random(7)
        List(90) { Star(rnd.nextFloat(), rnd.nextFloat(), rnd.nextFloat() * 1.4f + 0.4f, rnd.nextFloat() * 6.28f, rnd.nextFloat() * 0.8f + 0.4f) }
    }
    val t by rememberInfiniteTransition(label = "twinkle").animateFloat(
        initialValue = 0f,
        targetValue = 6.2832f,
        animationSpec = infiniteRepeatable(tween(9000, easing = LinearEasing)),
        label = "t",
    )
    Canvas(Modifier.fillMaxSize()) {
        drawRect(Brush.verticalGradient(listOf(Space.bgTop, Space.bgMid, Space.bgBottom)))
        val w = size.width
        val h = size.height
        val v = Offset(w * 0.85f, h * 0.12f)
        drawCircle(Brush.radialGradient(listOf(Space.violet.copy(alpha = 0.16f), Color.Transparent), center = v, radius = w * 0.8f), radius = w * 0.8f, center = v)
        val c = Offset(w * 0.1f, h * 0.85f)
        drawCircle(Brush.radialGradient(listOf(Space.cyan.copy(alpha = 0.09f), Color.Transparent), center = c, radius = w * 0.7f), radius = w * 0.7f, center = c)
        stars.forEach { s ->
            val a = 0.25f + 0.6f * ((sin(t * s.speed + s.phase) + 1f) / 2f)
            drawCircle(Color.White.copy(alpha = a), radius = s.r.dp.toPx(), center = Offset(s.x * w, s.y * h))
        }
    }
}
