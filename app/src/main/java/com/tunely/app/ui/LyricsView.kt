package com.tunely.app.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tunely.app.data.LyricLine
import com.tunely.app.data.Lyrics

/**
 * Apple Music-style lyrics: the active line is large and bright, other lines are
 * dimmed and softly blurred, the list auto-centers on the active line, and the
 * active line fills word by word (real word timing when the source has it,
 * otherwise interpolated across the line by character count).
 */
@Composable
fun LyricsView(lyrics: Lyrics?, positionMs: Long, onSeek: (Long) -> Unit, modifier: Modifier = Modifier) {
    if (lyrics == null) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Lyrics not available", color = Color.White.copy(alpha = 0.6f))
        }
        return
    }

    val activeIndex = lyrics.lines.indexOfLast { it.startMs <= positionMs }.coerceAtLeast(0)
    val listState = rememberLazyListState()

    LaunchedEffect(activeIndex) {
        if (lyrics.synced) listState.animateScrollToItem((activeIndex - 2).coerceAtLeast(0))
    }

    LazyColumn(state = listState, modifier = modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 120.dp, horizontal = 28.dp)) {
        itemsIndexed(lyrics.lines) { i, line ->
            val active = lyrics.synced && i == activeIndex
            val dist = kotlin.math.abs(i - activeIndex)
            val alpha by animateFloatAsState(if (!lyrics.synced || active) 1f else 0.45f, tween(350), label = "a")
            val scale by animateFloatAsState(if (active) 1f else 0.92f, tween(350), label = "s")
            val blur by animateDpAsState(if (!lyrics.synced || active) 0.dp else (dist.coerceAtMost(3) * 0.8f).dp, tween(350), label = "b")

            LyricLineText(
                line = line,
                positionMs = positionMs,
                active = active,
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .scale(scale)
                    .alpha(alpha)
                    .blur(blur)
                    .fillMaxWidth()
            )
        }
    }
}

@Composable
private fun LyricLineText(line: LyricLine, positionMs: Long, active: Boolean, modifier: Modifier) {
    val text = line.text.ifBlank { "♪" }
    if (!active) {
        Text(text, color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold, lineHeight = 36.sp, modifier = modifier)
        return
    }
    // Fraction of each word that is "sung" so far.
    val annotated = buildAnnotatedString {
        if (line.words.isNotEmpty()) {
            line.words.forEachIndexed { idx, w ->
                val done = positionMs >= w.endMs
                val inProgress = positionMs in w.startMs until w.endMs
                val c = when { done -> 1f; inProgress -> 0.55f + 0.45f * ((positionMs - w.startMs).toFloat() / (w.endMs - w.startMs).coerceAtLeast(1)); else -> 0.4f }
                withStyle(SpanStyle(color = Color.White.copy(alpha = c))) { append(w.text) }
                if (idx < line.words.lastIndex) append(" ")
            }
        } else {
            val progress = ((positionMs - line.startMs).toFloat() / (line.endMs - line.startMs).coerceAtLeast(1)).coerceIn(0f, 1f)
            val cut = (text.length * progress).toInt()
            withStyle(SpanStyle(color = Color.White)) { append(text.take(cut)) }
            withStyle(SpanStyle(color = Color.White.copy(alpha = 0.4f))) { append(text.drop(cut)) }
        }
    }
    Text(annotated, fontSize = 30.sp, fontWeight = FontWeight.Bold, lineHeight = 36.sp, modifier = modifier)
}

