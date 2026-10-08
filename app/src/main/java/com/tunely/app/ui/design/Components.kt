package com.tunely.app.ui.design

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tunely.app.data.PlaybackKind
import com.tunely.app.data.SourceInfo
import com.tunely.app.data.Sources
import com.tunely.app.data.Track

// ─── Surfaces ───────────────────────────────────────────────────────────

/**
 * The workhorse panel: a slightly translucent gradient card with a hairline
 * border and a top highlight, so cards read as glass without a blur pass.
 */
@Composable
fun AuroraCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(Dimens.radiusMd),
    tint: Color? = null,
    borderColor: Color? = null,
    elevationGlow: Boolean = false,
    content: @Composable () -> Unit
) {
    val colors = T.colors
    val surface = tint ?: colors.surface
    Box(
        modifier
            .then(
                if (elevationGlow) Modifier.glow(colors.accent.primary, 1.4f, 0.16f) else Modifier
            )
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    listOf(
                        surface.copy(alpha = if (colors.isLight) 0.96f else 0.92f),
                        colors.surfaceHigh.copy(alpha = if (colors.isLight) 0.92f else 0.82f)
                    )
                )
            )
            .border(1.dp, borderColor ?: colors.outline, shape)
    ) {
        content()
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Modifier.tapable(
    enabled: Boolean = true,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit
): Modifier {
    val source = remember { MutableInteractionSource() }
    return this.combinedClickable(
        interactionSource = source,
        indication = null,
        enabled = enabled,
        onClick = onClick,
        onLongClick = onLongClick
    )
}

/** Clickable AuroraCard with press feedback. */
@Composable
fun TunelyCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(Dimens.radiusMd),
    tint: Color? = null,
    enabled: Boolean = true,
    onLongClick: (() -> Unit)? = null,
    glow: Boolean = false,
    content: @Composable () -> Unit
) {
    val colors = T.colors
    val (press, source) = rememberPressScale(enabled)
    val surface = tint ?: colors.surface
    Box(
        modifier
            .then(press)
            .then(if (glow) Modifier.glow(colors.accent.primary, 1.5f, 0.16f) else Modifier)
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    listOf(
                        surface.copy(alpha = if (colors.isLight) 0.96f else 0.92f),
                        colors.surfaceHigh.copy(alpha = if (colors.isLight) 0.92f else 0.82f)
                    )
                )
            )
            .border(1.dp, colors.outline, shape)
            .tapGesture(source, enabled, onClick, onLongClick)
    ) { content() }
}

@OptIn(ExperimentalFoundationApi::class)
private fun Modifier.tapGesture(
    source: MutableInteractionSource,
    enabled: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?
): Modifier = this.combinedClickable(
    interactionSource = source,
    indication = null,
    enabled = enabled,
    onClick = onClick,
    onLongClick = onLongClick
)

// ─── Buttons ────────────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    primary: Boolean = true,
    enabled: Boolean = true
) {
    val colors = T.colors
    val (press, source) = rememberPressScale(enabled)
    Box(
        modifier
            .then(press)
            .clip(CircleShape)
            .then(
                if (primary) {
                    Modifier.background(Brush.horizontalGradient(colors.accentGradient))
                } else {
                    Modifier
                        .background(colors.surfaceHigh.copy(alpha = 0.6f))
                        .border(1.dp, colors.outline, CircleShape)
                }
            )
            .combinedClickable(
                interactionSource = source,
                indication = null,
                enabled = enabled,
                onClick = onClick
            )
            .padding(horizontal = 20.dp, vertical = 11.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(
                    icon, null,
                    tint = if (primary) Color.White else colors.textPrimary,
                    modifier = Modifier.size(17.dp)
                )
                Spacer(Modifier.width(8.dp))
            }
            Text(
                text,
                style = T.type.subtitle.copy(fontWeight = FontWeight.W600),
                color = if (primary) Color.White else colors.textPrimary
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GhostIconButton(
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    active: Boolean = false,
    size: Dp = 42.dp,
    iconSize: Dp = 21.dp,
    enabled: Boolean = true,
    tint: Color? = null
) {
    val colors = T.colors
    val (press, source) = rememberPressScale(enabled, 0.9f)
    val background = if (active) {
        colors.accent.primary.copy(alpha = 0.16f)
    } else {
        colors.surfaceHigh.copy(alpha = 0.45f)
    }
    val animated by animateColorAsState(background, Motion.colorQuick, label = "ghostBg")
    Box(
        modifier
            .then(press)
            .size(size)
            .clip(CircleShape)
            .background(animated)
            .border(
                1.dp,
                if (active) colors.accent.primary.copy(alpha = 0.35f) else colors.outline,
                CircleShape
            )
            .combinedClickable(
                interactionSource = source,
                indication = null,
                enabled = enabled,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            icon,
            contentDescription,
            tint = tint ?: if (active) colors.accent.primary else colors.textPrimary,
            modifier = Modifier.size(iconSize)
        )
    }
}

// ─── Controls ───────────────────────────────────────────────────────────

@Composable
fun TunelySwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val colors = T.colors
    val thumbOffset by animateDpAsState(
        targetValue = if (checked) 22.dp else 2.dp,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium),
        label = "switchThumb"
    )
    val trackColor by animateColorAsState(
        targetValue = if (checked) colors.accent.primary else colors.surfaceHigh,
        animationSpec = Motion.colorQuick,
        label = "switchTrack"
    )
    Box(
        modifier
            .size(width = 50.dp, height = 30.dp)
            .clip(CircleShape)
            .background(
                if (checked) {
                    Brush.horizontalGradient(colors.accentGradient)
                } else {
                    Brush.horizontalGradient(listOf(trackColor, trackColor))
                }
            )
            .border(1.dp, if (checked) Color.Transparent else colors.outlineStrong, CircleShape)
            .tapable(enabled = enabled) { onCheckedChange(!checked) }
    ) {
        Box(
            Modifier
                .offset(x = thumbOffset, y = 2.dp)
                .size(26.dp)
                .clip(CircleShape)
                .background(Color.White)
                .graphicsLayer { shadowElevation = 6f; shape = CircleShape; clip = false }
        )
    }
}

/**
 * Slim gradient slider used for both seeking and settings. The thumb grows
 * while dragging and the fill is a gradient, never a flat bar.
 */
@Composable
fun TunelySlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    onValueChangeFinished: (() -> Unit)? = null,
    enabled: Boolean = true,
    height: Dp = 30.dp,
    trackHeight: Dp = 5.dp,
    brush: Brush? = null,
    trackColor: Color? = null
) {
    val colors = T.colors
    var dragging by remember { mutableStateOf(false) }
    val activeBrush = brush ?: Brush.horizontalGradient(colors.accentGradient)
    val inactive = trackColor ?: colors.outlineStrong
    val thumbScale by animateFloatAsState(
        targetValue = if (dragging) 1.4f else 1f,
        animationSpec = Motion.snappy,
        label = "thumbScale"
    )

    Box(
        modifier
            .height(height)
            .fillMaxWidth()
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures { offset ->
                    onValueChange((offset.x / size.width.toFloat()).coerceIn(0f, 1f))
                    onValueChangeFinished?.invoke()
                }
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectHorizontalDragGestures(
                    onDragStart = { dragging = true },
                    onDragEnd = {
                        dragging = false
                        onValueChangeFinished?.invoke()
                    },
                    onDragCancel = { dragging = false },
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        onValueChange((change.position.x / size.width.toFloat()).coerceIn(0f, 1f))
                    }
                )
            }
            .drawBehind {
                val mid = this.size.height / 2f
                val trackPx = trackHeight.toPx()
                val radius = CornerRadius(trackPx / 2f)
                drawRoundRect(
                    color = inactive,
                    topLeft = Offset(0f, mid - trackPx / 2f),
                    size = Size(this.size.width, trackPx),
                    cornerRadius = radius
                )
                val filled = this.size.width * value.coerceIn(0f, 1f)
                if (filled > 0f) {
                    drawRoundRect(
                        brush = activeBrush,
                        topLeft = Offset(0f, mid - trackPx / 2f),
                        size = Size(filled.coerceAtLeast(trackPx), trackPx),
                        cornerRadius = radius
                    )
                }
                val thumbRadius = trackPx * 1.45f * thumbScale
                val center = Offset(
                    filled.coerceIn(thumbRadius, this.size.width - thumbRadius),
                    mid
                )
                drawCircle(
                    color = Color.White.copy(alpha = 0.35f),
                    radius = thumbRadius * 1.6f,
                    center = center
                )
                drawCircle(
                    brush = Brush.radialGradient(listOf(Color.White, colors.accent.primary), center = center, radius = thumbRadius),
                    radius = thumbRadius,
                    center = center
                )
            }
    )
}

// ─── Chips & badges ─────────────────────────────────────────────────────

@Composable
fun SourceChip(
    info: SourceInfo,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = T.colors
    val sourceColor = Color(info.color)
    val bg by animateColorAsState(
        targetValue = if (selected) sourceColor.copy(alpha = 0.18f) else colors.surfaceHigh.copy(alpha = 0.5f),
        animationSpec = Motion.colorQuick,
        label = "chipBg"
    )
    val (press, source) = rememberPressScale()
    Row(
        modifier
            .then(press)
            .clip(CircleShape)
            .background(bg)
            .border(
                1.dp,
                if (selected) sourceColor.copy(alpha = 0.55f) else colors.outline,
                CircleShape
            )
            .tapable { onClick() }
            .padding(horizontal = 13.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(sourceColor))
        Spacer(Modifier.width(8.dp))
        Text(
            info.name,
            style = T.type.caption.copy(
                fontWeight = if (selected) FontWeight.W600 else FontWeight.W500
            ),
            color = if (selected) colors.textPrimary else colors.textSecondary
        )
    }
}

/** Generic filter pill: an accent dot, a label, spring-animated selection. */
@Composable
fun FilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    dotColor: Color? = null
) {
    val colors = T.colors
    val accent = dotColor ?: colors.accent.primary
    val bg by animateColorAsState(
        targetValue = if (selected) accent.copy(alpha = 0.18f) else colors.surfaceHigh.copy(alpha = 0.5f),
        animationSpec = Motion.colorQuick,
        label = "filterBg"
    )
    val (press, source) = rememberPressScale()
    Row(
        modifier
            .then(press)
            .clip(CircleShape)
            .background(bg)
            .border(
                1.dp,
                if (selected) accent.copy(alpha = 0.55f) else colors.outline,
                CircleShape
            )
            .tapable { onClick() }
            .padding(horizontal = 13.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(accent))
        Spacer(Modifier.width(8.dp))
        Text(
            label,
            style = T.type.caption.copy(
                fontWeight = if (selected) FontWeight.W600 else FontWeight.W500
            ),
            color = if (selected) colors.textPrimary else colors.textSecondary
        )
    }
}

@Composable
fun SourceBadge(sourceId: String, modifier: Modifier = Modifier) {
    val info = Sources.byId(sourceId) ?: return
    val color = Color(info.color)
    Row(
        modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(5.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(5.dp))
        Text(
            info.short,
            style = T.type.micro.copy(letterSpacing = 0.6.sp),
            color = color,
            fontWeight = FontWeight.W700
        )
    }
}

@Composable
fun KindChip(kind: PlaybackKind, modifier: Modifier = Modifier) {
    if (kind == PlaybackKind.FULL) return
    val colors = T.colors
    val label = if (kind == PlaybackKind.LIVE) "LIVE" else "PREVIEW"
    val tint = if (kind == PlaybackKind.LIVE) Color(0xFF00E0A4) else colors.textSecondary
    Row(
        modifier
            .clip(RoundedCornerShape(6.dp))
            .background(tint.copy(alpha = 0.16f))
            .padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (kind == PlaybackKind.LIVE) {
            Box(Modifier.size(5.dp).clip(CircleShape).background(tint))
            Spacer(Modifier.width(4.dp))
        }
        Text(
            label,
            style = T.type.micro.copy(letterSpacing = 0.6.sp),
            color = tint,
            fontWeight = FontWeight.W700
        )
    }
}

// ─── Structure ──────────────────────────────────────────────────────────

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    action: (@Composable () -> Unit)? = null
) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(start = Dimens.xl, end = Dimens.lg, top = Dimens.xl, bottom = Dimens.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = T.type.title, color = T.colors.textPrimary)
            if (subtitle != null) {
                Text(subtitle, style = T.type.caption, color = T.colors.textTertiary)
            }
        }
        action?.invoke()
    }
}

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    val colors = T.colors
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.xxl, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(colors.surfaceHigh.copy(alpha = 0.7f))
                .border(1.dp, colors.outline, CircleShape)
                .glow(colors.accent.primary, 1.6f, 0.18f),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = colors.accent.primary, modifier = Modifier.size(30.dp))
        }
        Spacer(Modifier.height(Dimens.lg))
        Text(title, style = T.type.title, color = colors.textPrimary)
        Spacer(Modifier.height(6.dp))
        Text(
            message,
            style = T.type.caption,
            color = colors.textTertiary,
            textAlign = TextAlign.Center
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(Dimens.lg))
            PillButton(actionLabel, onAction)
        }
    }
}

@Composable
fun ShimmerBlock(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(Dimens.radiusSm)
) {
    val colors = T.colors
    Box(
        modifier
            .clip(shape)
            .background(
                shimmerBrush(
                    base = colors.surfaceHigh.copy(alpha = 0.55f),
                    highlight = colors.accent.primary.copy(alpha = 0.16f)
                )
            )
    )
}

@Composable
fun SkeletonTrackRow(modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.lg, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ShimmerBlock(Modifier.size(50.dp))
        Spacer(Modifier.width(Dimens.md))
        Column(Modifier.weight(1f)) {
            ShimmerBlock(Modifier.fillMaxWidth(0.62f).height(13.dp))
            Spacer(Modifier.height(7.dp))
            ShimmerBlock(Modifier.fillMaxWidth(0.4f).height(11.dp))
        }
    }
}

// ─── Track presentation ─────────────────────────────────────────────────

@Composable
fun TrackRow(
    track: Track,
    index: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    isCurrent: Boolean = false,
    isPlaying: Boolean = false,
    showSource: Boolean = true,
    trailing: (@Composable () -> Unit)? = null
) {
    val colors = T.colors
    val enter = Modifier.enterAnimation(index)

    Row(
        modifier
            .fillMaxWidth()
            .then(enter)
            .clip(RoundedCornerShape(Dimens.radiusSm))
            .tapable(onClick = onClick, onLongClick = onLongClick)
            .background(
                if (isCurrent) colors.accent.primary.copy(alpha = 0.10f) else Color.Transparent
            )
            .padding(horizontal = Dimens.md, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(contentAlignment = Alignment.Center) {
            ArtworkImage(
                url = track.artworkUrl,
                modifier = Modifier.size(50.dp),
                shape = RoundedCornerShape(12.dp)
            )
            if (isCurrent) {
                Box(
                    Modifier
                        .size(50.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.background.copy(alpha = 0.55f)),
                    contentAlignment = Alignment.Center
                ) {
                    WaveGlyph(
                        modifier = Modifier.size(22.dp, 20.dp),
                        playing = isPlaying,
                        color = colors.accent.primary
                    )
                }
            }
        }

        Spacer(Modifier.width(Dimens.md))

        Column(Modifier.weight(1f)) {
            Text(
                track.title,
                style = T.type.subtitle,
                color = if (isCurrent) colors.accent.primary else colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(3.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (track.explicit) {
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(3.dp))
                            .background(colors.textTertiary.copy(alpha = 0.35f))
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    ) {
                        Text(
                            "E",
                            style = T.type.micro.copy(letterSpacing = 0.sp),
                            color = colors.textPrimary
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    track.artist,
                    style = T.type.caption,
                    color = colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (showSource) {
                    Spacer(Modifier.width(6.dp))
                    SourceBadge(track.source)
                }
                if (track.kind != PlaybackKind.FULL) {
                    Spacer(Modifier.width(6.dp))
                    KindChip(track.kind)
                }
            }
        }

        if (trailing != null) {
            Spacer(Modifier.width(Dimens.sm))
            trailing()
        }
    }
}

/** Vertical artwork tile used by the home shelves. */
@Composable
fun TrackTile(
    track: Track,
    index: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = 148.dp,
    isCurrent: Boolean = false
) {
    val colors = T.colors
    val (press, source) = rememberPressScale()
    Column(
        modifier
            .width(width)
            .then(Modifier.enterAnimation(index))
            .then(press)
            .clip(RoundedCornerShape(Dimens.radiusSm))
            .tapable { onClick() }
    ) {
        Box {
            ArtworkImage(
                url = track.artworkUrl,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(width)
                    .then(
                        if (isCurrent) Modifier.glow(colors.accent.primary, 1.05f, 0.45f) else Modifier
                    ),
                shape = RoundedCornerShape(Dimens.radiusSm)
            )
            if (isCurrent) {
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(colors.accent.primary),
                    contentAlignment = Alignment.Center
                ) {
                    WaveGlyph(Modifier.size(14.dp), playing = true, color = Color.White)
                }
            }
            if (track.kind != PlaybackKind.FULL) {
                Box(Modifier.align(Alignment.TopStart).padding(8.dp)) { KindChip(track.kind) }
            }
        }
        Spacer(Modifier.height(9.dp))
        Text(
            track.title,
            style = T.type.subtitle,
            color = colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(2.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                track.artist,
                style = T.type.caption,
                color = colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            Spacer(Modifier.width(6.dp))
            SourceBadge(track.source)
        }
    }
}

// ─── Navigation dock ────────────────────────────────────────────────────

data class DockItem(val id: Int, val label: String, val icon: ImageVector)

@Composable
fun DockBar(
    items: List<DockItem>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = T.colors
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.lg)
            .clip(CircleShape)
            .background(colors.surfaceGlass)
            .border(1.dp, colors.outline, CircleShape)
            .height(Dimens.dockHeight)
    ) {
        val itemWidth = maxWidth / items.size
        val indicatorOffset by animateDpAsState(
            targetValue = itemWidth * items.indexOfFirst { it.id == selected }.coerceAtLeast(0),
            animationSpec = spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow),
            label = "dockIndicator"
        )

        Box(
            Modifier
                .offset(x = indicatorOffset)
                .width(itemWidth)
                .fillMaxSize()
                .padding(6.dp)
                .clip(CircleShape)
                .background(
                    Brush.horizontalGradient(colors.accentGradient.map { it.copy(alpha = 0.24f) })
                )
        )

        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            items.forEach { item ->
                val active = item.id == selected
                val tint by animateColorAsState(
                    targetValue = if (active) colors.accent.primary else colors.textTertiary,
                    animationSpec = Motion.colorQuick,
                    label = "dockTint"
                )
                val scale by animateFloatAsState(
                    targetValue = if (active) 1.06f else 1f,
                    animationSpec = Motion.snappy,
                    label = "dockScale"
                )
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .tapable { onSelect(item.id) },
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                        }
                    ) {
                        Icon(item.icon, item.label, tint = tint, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.height(3.dp))
                        Text(
                            item.label,
                            style = T.type.micro.copy(letterSpacing = 0.2.sp),
                            color = tint,
                            fontWeight = if (active) FontWeight.W700 else FontWeight.W500
                        )
                    }
                }
            }
        }
    }
}

// ─── Text input ─────────────────────────────────────────────────────────

/**
 * The app's one text field: a rounded ink well with a gradient focus ring,
 * used for search and for naming playlists.
 */
@Composable
fun TunelyTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    leadingIcon: ImageVector? = null,
    trailing: (@Composable () -> Unit)? = null,
    imeAction: androidx.compose.ui.text.input.ImeAction = androidx.compose.ui.text.input.ImeAction.Search,
    onImeAction: () -> Unit = {},
    singleLine: Boolean = true
) {
    val colors = T.colors
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    var focused by remember { mutableStateOf(false) }
    val borderColor by animateColorAsState(
        targetValue = if (focused) colors.accent.primary.copy(alpha = 0.65f) else colors.outline,
        animationSpec = Motion.colorQuick,
        label = "fieldBorder"
    )
    Box(
        modifier
            .clip(CircleShape)
            .background(colors.surfaceHigh.copy(alpha = 0.55f))
            .border(1.dp, borderColor, CircleShape)
            .padding(horizontal = Dimens.lg, vertical = 3.dp)
    ) {
        androidx.compose.foundation.text.BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = singleLine,
            textStyle = T.type.subtitle.copy(color = colors.textPrimary),
            cursorBrush = Brush.horizontalGradient(colors.accentGradient),
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = imeAction),
            keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                onSearch = {
                    focusManager.clearFocus()
                    onImeAction()
                },
                onDone = {
                    focusManager.clearFocus()
                    onImeAction()
                }
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 11.dp)
                .onFocusChangedCompat { focused = it },
            decorationBox = { inner ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (leadingIcon != null) {
                        Icon(
                            leadingIcon,
                            null,
                            tint = if (focused) colors.accent.primary else colors.textTertiary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(Dimens.sm))
                    }
                    Box(Modifier.weight(1f)) {
                        if (value.isEmpty()) {
                            Text(
                                placeholder,
                                style = T.type.subtitle,
                                color = colors.textTertiary
                            )
                        }
                        inner()
                    }
                    if (trailing != null) trailing()
                }
            }
        )
    }
}

private fun Modifier.onFocusChangedCompat(onChange: (Boolean) -> Unit): Modifier =
    this.onFocusChanged { onChange(it.isFocused) }
