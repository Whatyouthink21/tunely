package com.tunely.app.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ─── Colors ─────────────────────────────────────────────────────────────
object GlassColors {
    val glassWhite = Color.White.copy(alpha = 0.08f)
    val glassWhiteLight = Color.White.copy(alpha = 0.14f)
    val glassWhiteBorder = Color.White.copy(alpha = 0.18f)
    val glassBlack = Color.Black.copy(alpha = 0.35f)
    val glassBlackLight = Color.Black.copy(alpha = 0.20f)
    val glowWhite = Color.White.copy(alpha = 0.5f)
    val cardDark = Color(0xFF1C1C1E).copy(alpha = 0.70f)
    val cardDarkSolid = Color(0xFF1C1C1E)
    val surfaceGlass = Color(0xFF141416).copy(alpha = 0.85f)
    val navGlass = Color(0xFF0E0E10).copy(alpha = 0.75f)
    val highlightGlow = Color(0xFFFA2D48).copy(alpha = 0.5f)
}

// ─── Glass Modifier Helpers ─────────────────────────────────────────────

/**
 * Frosted-glass panel: translucent fill + subtle highlight border.
 * Looks best stacked on top of a vibrant background.
 */
fun Modifier.glassPanel(
    shape: Shape = RoundedCornerShape(22.dp),
    tint: Color = GlassColors.glassWhite,
    borderColor: Color = GlassColors.glassWhiteBorder,
    elevation: Dp = 0.dp
): Modifier = this
    .then(if (elevation > 0.dp) Modifier.shadow(elevation, shape, ambientColor = Color.Black.copy(0.4f)) else Modifier)
    .background(
        brush = Brush.linearGradient(
            colors = listOf(tint, tint.copy(alpha = tint.alpha * 0.4f)),
            start = Offset(0f, 0f),
            end = Offset(1f, 1f)
        ),
        shape = shape
    )
    .border(width = 0.8.dp, color = borderColor, shape = shape)

/**
 * Solid glass card with dark tint for library/list items.
 */
fun Modifier.glassCard(
    shape: Shape = RoundedCornerShape(18.dp),
    tint: Color = GlassColors.cardDark,
    borderColor: Color = Color.White.copy(alpha = 0.06f)
): Modifier = this
    .background(
        brush = Brush.verticalGradient(
            colors = listOf(tint, tint.copy(alpha = tint.alpha * 0.7f))
        ),
        shape = shape
    )
    .border(width = 0.6.dp, color = borderColor, shape = shape)

/**
 * Soft outer glow — uses drawBehind to paint a blurred color halo.
 * Pair with a small .blur() for the actual softness.
 */
fun Modifier.glow(
    color: Color,
    radius: Dp = 24.dp,
    alpha: Float = 0.55f
): Modifier = this.drawBehind {
    drawGlow(color.copy(alpha = alpha), radius.toPx())
}

private fun DrawScope.drawGlow(color: Color, radius: Float) {
    val cx = size.width / 2
    val cy = size.height / 2
    val r = kotlin.math.max(size.width, size.height) / 2 + radius
    val brush = Brush.radialGradient(
        colors = listOf(color, color.copy(alpha = 0f)),
        center = Offset(cx, cy),
        radius = r
    )
    drawCircle(brush, radius = r, center = Offset(cx, cy))
}

/**
 * Neon text glow effect — paints a halo behind the composable.
 */
fun Modifier.textGlow(color: Color, radius: Dp = 14.dp): Modifier = this
    .drawBehind {
        val cx = size.width / 2
        val cy = size.height / 2
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(color.copy(alpha = 0.7f), color.copy(alpha = 0f)),
                center = Offset(cx, cy),
                radius = kotlin.math.max(size.width, size.height) / 2 + radius.toPx()
            ),
            radius = kotlin.math.max(size.width, size.height) / 2 + radius.toPx(),
            center = Offset(cx, cy)
        )
    }

/**
 * Pulsing glow animation (used behind artwork / active elements).
 */
@Composable
fun pulsingGlowAlpha(min: Float = 0.35f, max: Float = 0.8f, durationMs: Int = 2400): Float {
    val t = rememberInfiniteTransition(label = "pulse")
    val a = t.animateFloat(
        initialValue = min,
        targetValue = max,
        animationSpec = infiniteRepeatable(tween(durationMs, easing = EaseInOutSine), RepeatMode.Reverse),
        label = "pulseAlpha"
    )
    return a.value
}

// ─── Glass Components ───────────────────────────────────────────────────

/** A glassy toggle pill for settings rows. */
@Composable
fun GlassSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val color by animateColorAsState(
        if (checked) AccentRed else Color.White.copy(alpha = 0.25f),
        tween(280), label = "sw"
    )
    Box(
        modifier = modifier
            .size(50.dp, 30.dp)
            .clip(RoundedCornerShape(50))
            .background(color)
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(3.dp),
        contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Box(
            Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(Color.White)
                .shadow(4.dp, CircleShape)
        )
    }
}

/** A glass row with icon + label + trailing content. */
@Composable
fun GlassSettingsRow(
    icon: ImageVector,
    label: String,
    iconTint: Color = AccentRed,
    onClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(GlassColors.glassWhite)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(iconTint.copy(alpha = 0.22f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = iconTint, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(12.dp))
        Text(label, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
        trailing?.invoke()
    }
}

/** Section header styled like Apple Music settings. */
@Composable
fun GlassSectionHeader(title: String, subtitle: String? = null) {
    Column(Modifier.padding(start = 18.dp, end = 18.dp, top = 22.dp, bottom = 8.dp)) {
        Text(
            title.uppercase(),
            color = Color.White.copy(alpha = 0.6f),
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.sp
        )
        subtitle?.let {
            Text(it, color = Color.White.copy(alpha = 0.4f), fontSize = 12.sp)
        }
    }
}
