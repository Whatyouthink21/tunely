package com.tunely.app.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tunely.app.data.LyricLine
import com.tunely.app.data.Lyrics

/**
 * Premium lyrics view: Apple Music-style with neon glow on the active line,
 * shimmering word-by-word highlight, and soft blur on inactive lines.
 * Glow intensity and font size come from settings.
 */
@Composable
fun LyricsView(
    lyrics: Lyrics?,
    positionMs: Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
    fontSize: Int = 30,
    glowEnabled: Boolean = true,
    accentColor: Color = AccentRed
) {
    if (lyrics == null) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("♪", fontSize = 60.sp, color = Color.White.copy(alpha = 0.3f))
                Spacer(Modifier.height(12.dp))
                Text("No lyrics available", color = Color.White.copy(alpha = 0.5f), fontSize = 16.sp)
                Text("Try a different source in Settings", color = Color.White.copy(alpha = 0.3f), fontSize = 13.sp)
            }
        }
        return
    }

    val activeIndex = lyrics.lines.indexOfLast { it.startMs <= positionMs }.coerceAtLeast(0)
    val listState = rememberLazyListState()

    LaunchedEffect(activeIndex) {
        if (lyrics.synced) listState.animateScrollToItem((activeIndex - 2).coerceAtLeast(0))
    }

    Box(modifier.fillMaxSize()) {
        // Fade overlays at top/bottom for the Apple Music "curtain" look
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 140.dp, horizontal = 24.dp)
        ) {
            itemsIndexed(lyrics.lines) { i, line ->
                val active = lyrics.synced && i == activeIndex
                val dist = kotlin.math.abs(i - activeIndex)
                val alpha by animateFloatAsState(
                    if (!lyrics.synced || active) 1f else (0.4f - dist * 0.04f).coerceAtLeast(0.12f),
                    tween(350), label = "a"
                )
                val scale by animateFloatAsState(
                    if (active) 1.02f else 0.93f,
                    spring(dampingRatio = 0.7f, stiffness = 180f), label = "s"
                )
                val blur by animateDpAsState(
                    if (!lyrics.synced || active) 0.dp else (dist.coerceAtMost(4) * 1.2f).dp,
                    tween(350), label = "b"
                )

                LyricLineText(
                    line = line,
                    positionMs = positionMs,
                    active = active,
                    fontSize = fontSize,
                    glowEnabled = glowEnabled,
                    accentColor = accentColor,
                    modifier = Modifier
                        .padding(vertical = 8.dp)
                        .scale(scale)
                        .alpha(alpha)
                        .blur(blur)
                        .fillMaxWidth()
                        .clickable(enabled = lyrics.synced) { onSeek(line.startMs) }
                )
            }
        }

        // Top/bottom gradient curtains
        Box(
            Modifier
                .fillMaxWidth()
                .height(100.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Black.copy(alpha = 0.8f), Color.Transparent)
                    )
                )
        )
        Box(
            Modifier
                .fillMaxWidth()
                .height(120.dp)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.9f))
                    )
                )
        )
    }
}

@Composable
private fun LyricLineText(
    line: LyricLine,
    positionMs: Long,
    active: Boolean,
    fontSize: Int,
    glowEnabled: Boolean,
    accentColor: Color,
    modifier: Modifier
) {
    val text = line.text.ifBlank { "♪" }
    val glowAlpha = pulsingGlowAlpha(min = 0.3f, max = 0.75f)
    val lineColor = if (line.isBackground) Color.White.copy(alpha = 0.6f) else Color.White

    if (!active) {
        Text(
            text,
            color = lineColor,
            fontSize = (fontSize - 2).sp,
            fontWeight = FontWeight.Bold,
            lineHeight = (fontSize + 8).sp,
            modifier = modifier
        )
        return
    }

    // Active line with glow halo behind it
    Box(modifier = modifier) {
        // Glow halo
        if (glowEnabled) {
            Box(
                Modifier
                    .matchParentSize()
                    .padding(vertical = 6.dp, horizontal = 4.dp)
                    .background(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                Color.Transparent,
                                accentColor.copy(alpha = 0.18f * glowAlpha),
                                accentColor.copy(alpha = 0.28f * glowAlpha),
                                accentColor.copy(alpha = 0.18f * glowAlpha),
                                Color.Transparent
                            )
                        ),
                        shape = RoundedCornerShape(14.dp)
                    )
                    .blur(18.dp)
            )
        }

        // The annotated text itself
        val annotated = buildAnnotatedString {
            if (line.words.isNotEmpty()) {
                line.words.forEachIndexed { idx, w ->
                    val done = positionMs >= w.endMs
                    val inProgress = positionMs in w.startMs until w.endMs
                    val progress = when {
                        done -> 1f
                        inProgress -> ((positionMs - w.startMs).toFloat() /
                            (w.endMs - w.startMs).coerceAtLeast(1)).coerceIn(0f, 1f)
                        else -> 0f
                    }
                    val color = when {
                        done -> lineColor
                        inProgress -> lerpColor(Color.White.copy(alpha = 0.4f), lineColor, progress)
                        else -> Color.White.copy(alpha = 0.4f)
                    }
                    // Scale the active word slightly for punch
                    val style = SpanStyle(
                        color = color,
                        fontSize = if (inProgress) (fontSize + 1).sp else fontSize.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    withStyle(style) { append(w.text) }
                    if (idx < line.words.lastIndex) append(" ")
                }
            } else {
                // Fallback: character-level sweep
                val total = (line.endMs - line.startMs).coerceAtLeast(1)
                val progress = ((positionMs - line.startMs).toFloat() / total).coerceIn(0f, 1f)
                val cut = (text.length * progress).toInt()
                withStyle(SpanStyle(color = lineColor, fontWeight = FontWeight.ExtraBold)) {
                    append(text.take(cut))
                }
                withStyle(SpanStyle(color = Color.White.copy(alpha = 0.35f))) {
                    append(text.drop(cut))
                }
            }
        }
        Text(
            annotated,
            fontSize = fontSize.sp,
            fontWeight = FontWeight.Bold,
            lineHeight = (fontSize + 8).sp,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
        )
    }
}

/** Linearly interpolate between two colors. */
private fun lerpColor(a: Color, b: Color, t: Float): Color {
    val tt = t.coerceIn(0f, 1f)
    return Color(
        red = a.red + (b.red - a.red) * tt,
        green = a.green + (b.green - a.green) * tt,
        blue = a.blue + (b.blue - a.blue) * tt,
        alpha = a.alpha + (b.alpha - a.alpha) * tt
    )
}
