package com.tunely.app.ui.design

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer

/** Whether the user wants animation at all (Settings → Appearance → Motion). */
val LocalMotionEnabled = compositionLocalOf { true }

/** Shared motion vocabulary so every screen feels like the same app. */
object Motion {
    val snappy = spring<Float>(dampingRatio = 0.78f, stiffness = Spring.StiffnessMediumLow)
    val bouncy = spring<Float>(dampingRatio = 0.45f, stiffness = Spring.StiffnessLow)
    val gentle = spring<Float>(dampingRatio = 0.9f, stiffness = Spring.StiffnessMediumLow)
    val quick = tween<Float>(durationMillis = 200, easing = FastOutSlowInEasing)
    /** Same timing, for [androidx.compose.animation.animateColorAsState]. */
    val colorQuick = tween<Color>(durationMillis = 200, easing = FastOutSlowInEasing)
    val smooth = tween<Float>(durationMillis = 420, easing = EaseOutCubic)
    val slowSweep = tween<Float>(durationMillis = 1_400, easing = LinearEasing)

    /** Staggered list entrance: capped so long lists do not crawl in. */
    fun enterDelay(index: Int): Int = (index.coerceAtMost(12)) * 35
}

/**
 * Press feedback for every tappable surface. Returns the modifier plus the
 * interaction source so callers can layer ripples or colours on top.
 */
@Composable
fun rememberPressScale(
    enabled: Boolean = true,
    pressedScale: Float = 0.965f
): Pair<Modifier, MutableInteractionSource> {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val motion = LocalMotionEnabled.current
    val scale = androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (pressed && enabled && motion) pressedScale else 1f,
        animationSpec = Motion.snappy,
        label = "pressScale"
    )
    val modifier = Modifier.graphicsLayer {
        scaleX = scale.value
        scaleY = scale.value
        // A touch of depth: pressing sinks the surface slightly.
        translationY = if (pressed && enabled && motion) 1.5f else 0f
    }
    return modifier to source
}

/** Fade + rise entrance used for rows, cards and shelves. */
@Composable
fun Modifier.enterAnimation(index: Int = 0, offsetY: Int = 28): Modifier {
    val motion = LocalMotionEnabled.current
    val progress = remember { Animatable(if (motion) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (!motion) return@LaunchedEffect
        kotlinx.coroutines.delay(Motion.enterDelay(index).toLong())
        progress.animateTo(1f, tween(durationMillis = 460, easing = EaseOutCubic))
    }
    return this
        .graphicsLayer {
            alpha = progress.value
            translationY = (1f - progress.value) * offsetY
        }
}

/** Shimmer used by skeleton loaders: a light sweeping over the surface. */
@Composable
fun shimmerBrush(base: Color, highlight: Color): Brush {
    val motion = LocalMotionEnabled.current
    val transition = androidx.compose.animation.core.rememberInfiniteTransition(label = "shimmer")
    val x by transition.animateFloat(
        initialValue = -400f,
        targetValue = 1_200f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = tween<Float>(durationMillis = if (motion) 1_400 else 1, easing = LinearEasing)
        ),
        label = "shimmerX"
    )
    return Brush.linearGradient(
        colors = listOf(base, highlight, base),
        start = Offset(x - 280f, 0f),
        end = Offset(x, 280f)
    )
}

/** A soft radial bloom, drawn behind artwork or the active lyric line. */
fun Modifier.glow(color: Color, radiusFactor: Float = 1.35f, alpha: Float = 0.45f): Modifier =
    drawWithContent {
        drawContent()
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = maxOf(size.width, size.height) / 2f * radiusFactor
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(color.copy(alpha = alpha), Color.Transparent),
                center = center,
                radius = radius
            ),
            radius = radius,
            center = center
        )
    }

/** Convenience for a subtle clickable card without the Material ripple box. */
@Composable
fun Modifier.tapSurface(
    onClick: () -> Unit,
    enabled: Boolean = true
): Modifier {
    val source = remember { MutableInteractionSource() }
    return this.clickable(
        interactionSource = source,
        indication = null,
        enabled = enabled,
        onClick = onClick
    )
}
