package com.tunely.app.ui.design

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlin.math.sin

/**
 * Album art with a graceful fallback: a gradient plaque carrying the Tunely
 * waveform, so a missing cover never leaves a grey hole in the design.
 */
@Composable
fun ArtworkImage(
    url: String?,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(Dimens.radiusSm),
    contentScale: ContentScale = ContentScale.Crop,
    fallbackSeed: Int = 0
) {
    val colors = T.colors
    Box(modifier.clip(shape)) {
        // Fallback / loading plaque tinted from the accent.
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        listOf(
                            colors.accent.primary.copy(alpha = 0.55f),
                            colors.accent.secondary.copy(alpha = 0.35f),
                            colors.surfaceHigh
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.GraphicEq,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.65f),
                modifier = Modifier.fillMaxSize(0.42f)
            )
        }
        if (!url.isNullOrBlank()) {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = contentScale,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

/**
 * The "orbit" artwork treatment for the full player: a vinyl-style disc with
 * grooves, a sheen that sweeps as it turns, and rotation that eases up and down
 * with playback instead of snapping on and off.
 */
@Composable
fun SpinningDisc(
    url: String?,
    playing: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 280.dp,
    degreesPerSecond: Float = 24f,
    glow: Boolean = true
) {
    var rotation by remember { mutableFloatStateOf(0f) }
    var speed by remember { mutableFloatStateOf(0f) }
    val colors = T.colors
    val motion = LocalMotionEnabled.current

    LaunchedEffect(playing, motion) {
        if (!motion) return@LaunchedEffect
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                val dt = if (last == 0L) 0f else ((now - last) / 1_000_000_000f).coerceIn(0f, 0.05f)
                last = now
                val target = if (playing) 1f else 0f
                speed += (target - speed) * (dt * 2.4f).coerceIn(0f, 1f)
                rotation += degreesPerSecond * speed * dt
                if (rotation > 360f) rotation -= 360f
            }
            if (!playing && speed < 0.002f) break
        }
    }

    val glowAlpha by animateFloatAsState(
        targetValue = if (playing) 0.55f else 0.22f,
        animationSpec = Motion.gentle,
        label = "discGlow"
    )

    Box(
        modifier
            .size(size)
            .rotate(rotation)
            .then(
                if (glow) Modifier.glow(colors.accent.primary, 1.25f, glowAlpha * 0.5f) else Modifier
            )
            .drawBehind {
                val radius = this.size.minDimension / 2f
                // Grooves.
                val grooveColor = Color.White.copy(alpha = 0.06f)
                listOf(0.98f, 0.9f, 0.82f, 0.74f).forEach { factor ->
                    drawCircle(
                        color = grooveColor,
                        radius = radius * factor,
                        style = Stroke(width = 1.2f)
                    )
                }
                // Sweeping sheen.
                drawCircle(
                    brush = Brush.sweepGradient(
                        listOf(
                            Color.Transparent,
                            Color.White.copy(alpha = 0.10f),
                            Color.Transparent,
                            Color.Transparent
                        )
                    ),
                    radius = radius
                )
            }
            .padding(10.dp)
            .clip(CircleShape)
            .border(1.dp, Color.White.copy(alpha = 0.12f), CircleShape)
    ) {
        ArtworkImage(url = url, shape = CircleShape, modifier = Modifier.fillMaxSize())
        // Label ring in the middle, like a record.
        Box(
            Modifier
                .align(Alignment.Center)
                .size(size * 0.16f)
                .clip(CircleShape)
                .background(colors.background.copy(alpha = 0.85f))
                .border(1.dp, Color.White.copy(alpha = 0.14f), CircleShape)
        ) {
            WaveGlyph(
                modifier = Modifier.fillMaxSize().padding(size * 0.035f),
                playing = playing,
                color = colors.accent.primary
            )
        }
    }
}

/** The Tunely mark: five bars that breathe. */
@Composable
fun WaveGlyph(
    modifier: Modifier = Modifier,
    playing: Boolean = true,
    color: Color = T.colors.accent.primary,
    bars: Int = 5
) {
    val motion = LocalMotionEnabled.current
    var phase by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(playing, motion) {
        if (!motion) return@LaunchedEffect
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                val dt = if (last == 0L) 0f else ((now - last) / 1_000_000_000f).coerceIn(0f, 0.05f)
                last = now
                phase += dt * (if (playing) 2.4f else 0.6f)
            }
        }
    }

    androidx.compose.foundation.Canvas(modifier) {
        val count = bars.coerceAtLeast(3)
        val gap = size.width / (count * 2f)
        val barWidth = gap * 0.85f
        val mid = size.height / 2f
        for (i in 0 until count) {
            val t = phase + i * 0.7f
            val amplitude = if (playing) {
                0.35f + 0.65f * ((sin(t) * 0.5f + 0.5f) * 0.7f + 0.3f)
            } else {
                0.4f + 0.12f * (i % 2)
            }
            val height = size.height * amplitude.coerceIn(0.18f, 1f)
            val left = gap * (i * 2 + 0.5f)
            drawRoundRect(
                color = color,
                topLeft = Offset(left, mid - height / 2f),
                size = androidx.compose.ui.geometry.Size(barWidth, height),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidth / 2f)
            )
        }
    }
}

/** A tiny circular progress ring used by the hero card. */
@Composable
fun ProgressRing(
    progress: Float,
    modifier: Modifier = Modifier,
    trackColor: Color = Color.White.copy(alpha = 0.18f),
    brush: Brush = Brush.sweepGradient(T.colors.accentGradient)
) {
    androidx.compose.foundation.Canvas(modifier) {
        val stroke = size.minDimension * 0.09f
        drawCircle(
            color = trackColor,
            radius = size.minDimension / 2f - stroke / 2f,
            style = Stroke(width = stroke)
        )
        drawArc(
            brush = brush,
            startAngle = -90f,
            sweepAngle = 360f * progress.coerceIn(0f, 1f),
            useCenter = false,
            style = Stroke(width = stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round)
        )
    }
}
