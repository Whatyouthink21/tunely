package com.tunely.app.ui.design

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * Tunely's own bottom sheet: a rounded ink panel that springs up, keeps a drag
 * handle, can be flicked away and dims the app behind it. It replaces the stock
 * Material sheet so the corner radius, border and motion match the rest of the
 * design language.
 */
@Composable
fun SheetScaffold(
    visible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    maxHeightFraction: Float = 0.82f,
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = T.colors
    val scope = rememberCoroutineScope()
    val progress = remember { Animatable(0f) }
    var dragOffset by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(visible) {
        if (visible) {
            dragOffset = 0f
            progress.animateTo(1f, spring(dampingRatio = 0.78f, stiffness = 700f))
        } else {
            progress.animateTo(0f, spring(dampingRatio = 0.9f, stiffness = 900f))
        }
    }

    if (progress.value <= 0.001f && !visible) return

    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = progress.value }
                .background(colors.scrim)
                .tapable { onDismiss() }
        )
        Column(
            modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .graphicsLayer {
                    translationY = (1f - progress.value) * 900f + dragOffset
                }
                .clip(RoundedCornerShape(topStart = Dimens.radiusLg, topEnd = Dimens.radiusLg))
                .background(
                    Brush.verticalGradient(
                        listOf(colors.surfaceHigh, colors.surface)
                    )
                )
                .border(
                    width = 1.dp,
                    color = colors.outline,
                    shape = RoundedCornerShape(topStart = Dimens.radiusLg, topEnd = Dimens.radiusLg)
                )
                .heightIn(max = 720.dp)
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragEnd = {
                            if (dragOffset > 140f) {
                                scope.launch {
                                    progress.animateTo(0f, spring(dampingRatio = 0.9f, stiffness = 900f))
                                    dragOffset = 0f
                                    onDismiss()
                                }
                            } else {
                                scope.launch {
                                    val target = dragOffset
                                    dragOffset = 0f
                                    progress.snapTo(progress.value)
                                }
                            }
                        },
                        onVerticalDrag = { change, amount ->
                            change.consume()
                            dragOffset = (dragOffset + amount).coerceAtLeast(0f)
                        }
                    )
                }
        ) {
            // Drag handle
            Box(
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(top = 10.dp, bottom = 4.dp)
                    .width(44.dp)
                    .size(width = 44.dp, height = 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(colors.outlineStrong)
            )
            Column(
                Modifier
                    .fillMaxWidth()
                    .graphicsLayer { translationY = dragOffset }
            ) { content() }
        }
    }
}

@Composable
fun SheetTitle(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onClose: (() -> Unit)? = null
) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.xl, vertical = Dimens.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = T.type.headline, color = T.colors.textPrimary)
            if (subtitle != null) {
                Text(subtitle, style = T.type.caption, color = T.colors.textTertiary)
            }
        }
        if (onClose != null) {
            GhostIconButton(
                icon = Icons.Rounded.Close,
                onClick = onClose,
                size = 36.dp,
                iconSize = 18.dp
            )
        }
    }
}

/** A single tappable row inside a sheet (icon, label, optional value). */
@Composable
fun SheetRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    value: String? = null,
    tint: Color? = null,
    showCheck: Boolean = false,
    checked: Boolean = false,
    trailing: (@Composable () -> Unit)? = null
) {
    val colors = T.colors
    val accent = tint ?: colors.accent.primary
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.lg, vertical = 2.dp)
            .clip(RoundedCornerShape(Dimens.radiusSm))
            .tapable { onClick() }
            .padding(horizontal = Dimens.md, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(accent.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = accent, modifier = Modifier.size(17.dp))
        }
        Spacer(Modifier.width(Dimens.md))
        Text(
            label,
            style = T.type.subtitle,
            color = colors.textPrimary,
            modifier = Modifier.weight(1f)
        )
        if (value != null) {
            Text(value, style = T.type.caption, color = colors.textTertiary)
        }
        if (showCheck && checked) {
            Spacer(Modifier.width(Dimens.sm))
            Icon(
                Icons.Rounded.Check,
                null,
                tint = colors.accent.primary,
                modifier = Modifier.size(18.dp)
            )
        }
        trailing?.invoke()
    }
}
