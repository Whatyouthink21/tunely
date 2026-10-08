package com.tunely.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FormatQuote
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tunely.app.data.LyricLine
import com.tunely.app.data.Lyrics
import com.tunely.app.ui.design.Dimens
import com.tunely.app.ui.design.EmptyState
import com.tunely.app.ui.design.ShimmerBlock
import com.tunely.app.ui.design.T
import com.tunely.app.ui.design.glow
import com.tunely.app.ui.design.tapable

/**
 * Karaoke-style lyrics: the active line fills with an accent gradient as the
 * track plays, word by word when the provider supplies word timings. Inactive
 * lines shrink and fade so the current line always reads first.
 */
@OptIn(ExperimentalTextApi::class)
@Composable
fun LyricsPane(
    lyrics: Lyrics?,
    loading: Boolean,
    positionMs: Long,
    fontSize: Int,
    glowEnabled: Boolean,
    centre: Boolean,
    onSeek: (Long) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = T.colors
    val listState = rememberLazyListState()

    when {
        loading && lyrics == null -> {
            Column(
                modifier
                    .fillMaxSize()
                    .padding(horizontal = Dimens.xxl, vertical = Dimens.lg),
                verticalArrangement = Arrangement.Center
            ) {
                repeat(6) { index ->
                    ShimmerBlock(
                        Modifier
                            .fillMaxWidth(if (index % 2 == 0) 0.85f else 0.6f)
                            .height(18.dp)
                    )
                    Spacer(Modifier.height(14.dp))
                }
            }
        }

        lyrics == null || lyrics.lines.isEmpty() -> {
            Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                EmptyState(
                    icon = Icons.Rounded.FormatQuote,
                    title = "No lyrics for this one",
                    message = "Tunely asked LRCLIB and Lyrics.ovh. Live radio and previews " +
                        "usually have none.",
                    actionLabel = "Try again",
                    onAction = onRetry
                )
            }
        }

        else -> {
            val activeIndex = remember(lyrics, positionMs) {
                lyrics.lines.indexOfLast { it.startMs <= positionMs }.coerceAtLeast(0)
            }
            val activeLine = lyrics.lines.getOrNull(activeIndex)

            LaunchedEffect(activeIndex, lyrics) {
                val target = (activeIndex - if (centre) 3 else 1).coerceAtLeast(0)
                runCatching { listState.animateScrollToItem(target) }
            }

            LazyColumn(
                state = listState,
                modifier = modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = Dimens.xl,
                    end = Dimens.xl,
                    top = 40.dp,
                    bottom = 120.dp
                ),
                verticalArrangement = Arrangement.spacedBy(Dimens.md)
            ) {
                itemsIndexed(lyrics.lines) { index, line ->
                    LyricLineView(
                        line = line,
                        isActive = index == activeIndex,
                        isPast = index < activeIndex,
                        progress = if (index == activeIndex && activeLine != null) {
                            lineProgress(activeLine, positionMs)
                        } else 0f,
                        currentWordIndex = if (index == activeIndex) {
                            wordIndex(line, positionMs)
                        } else -1,
                        fontSize = fontSize,
                        glowEnabled = glowEnabled && index == activeIndex,
                        onClick = { onSeek(line.startMs) }
                    )
                }
                item {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = Dimens.lg),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            if (lyrics.synced) "Synced · ${lyrics.source}" else "Timing estimated · ${lyrics.source}",
                            style = T.type.micro,
                            color = colors.textTertiary
                        )
                        Spacer(Modifier.width(Dimens.sm))
                        Box(
                            Modifier
                                .size(26.dp)
                                .clip(CircleShape)
                                .background(colors.surfaceHigh.copy(alpha = 0.6f))
                                .padding(6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Rounded.Refresh,
                                contentDescription = "Reload lyrics",
                                tint = colors.textTertiary,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTextApi::class)
@Composable
private fun LyricLineView(
    line: LyricLine,
    isActive: Boolean,
    isPast: Boolean,
    progress: Float,
    currentWordIndex: Int,
    fontSize: Int,
    glowEnabled: Boolean,
    onClick: () -> Unit
) {
    val colors = T.colors
    val baseStyle = TextStyle(
        fontFamily = T.type.title.fontFamily,
        fontWeight = if (isActive) FontWeight.W700 else FontWeight.W500,
        fontSize = fontSize.sp,
        lineHeight = (fontSize * 1.35f).sp
    )
    val textAlign = if (line.isBackground) TextAlign.Center else TextAlign.Start
    val inactiveAlpha = when {
        isActive -> 1f
        isPast -> 0.32f
        else -> 0.45f
    }

    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dimens.radiusSm))
            .graphicsLayer {
                alpha = inactiveAlpha
                val scale = if (isActive) 1f else 0.97f
                scaleX = scale
                scaleY = scale
            }
            .then(
                if (glowEnabled) Modifier.glow(colors.accent.primary, 1.3f, 0.16f) else Modifier
            )
            .tapable { onClick() }
            .padding(vertical = 2.dp)
    ) {
        // Base (unfilled) line.
        Text(
            text = line.text,
            style = baseStyle,
            color = if (isActive) colors.textPrimary.copy(alpha = 0.35f) else colors.textSecondary,
            textAlign = textAlign,
            modifier = Modifier.fillMaxWidth()
        )
        // Gradient fill that sweeps across the line as it plays.
        if (isActive && progress > 0f) {
            Text(
                text = line.text,
                style = TextStyle(
                    fontFamily = baseStyle.fontFamily,
                    fontWeight = baseStyle.fontWeight,
                    fontSize = baseStyle.fontSize,
                    lineHeight = baseStyle.lineHeight,
                    brush = Brush.horizontalGradient(colors.accentGradient)
                ),
                textAlign = textAlign,
                modifier = Modifier
                    .fillMaxWidth()
                    .drawWithContent {
                        clipRect(right = size.width * progress.coerceIn(0f, 1f)) {
                            this@drawWithContent.drawContent()
                        }
                    }
            )
        }
    }
}

private fun lineProgress(line: LyricLine, positionMs: Long): Float {
    val span = (line.endMs - line.startMs).coerceAtLeast(1L)
    return ((positionMs - line.startMs).toFloat() / span.toFloat()).coerceIn(0f, 1f)
}

private fun wordIndex(line: LyricLine, positionMs: Long): Int =
    line.words.indexOfLast { it.startMs <= positionMs }
