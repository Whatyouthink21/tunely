package com.tunely.app.ui.design

import android.content.Context
import android.graphics.drawable.BitmapDrawable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.palette.graphics.Palette
import coil.imageLoader
import coil.request.ImageRequest
import kotlin.math.cos
import kotlin.math.sin

/**
 * Tunely's signature backdrop: a slow, breathing aurora drawn from the current
 * accent (and the artwork of the track that is playing). Two counter-rotating
 * layers of soft radial blobs, no bitmaps, no blur passes — cheap enough for a
 * scrolling list.
 */
@Composable
fun AuroraBackdrop(
    seeds: List<Color>,
    modifier: Modifier = Modifier,
    animated: Boolean = true,
    intensity: Float = 1f
) {
    val motion = LocalMotionEnabled.current && animated
    val transition = rememberInfiniteTransition(label = "aurora")
    val primaryPhase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (motion) 28_000 else 1, easing = LinearEasing)
        ),
        label = "phaseA"
    )
    val secondaryPhase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (motion) 19_000 else 1, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "phaseB"
    )

    val palette = seeds.ifEmpty { listOf(Color(0xFF7C5CFF), Color(0xFFFF5EA8)) }
    val base = T.colors.background
    val strength = intensity * if (T.colors.isLight) 0.55f else 1f

    Canvas(modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        drawRect(base)

        fun blob(color: Color, cx: Float, cy: Float, radius: Float, alpha: Float) {
            if (alpha <= 0.01f || radius <= 0f) return
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(color.copy(alpha = alpha), color.copy(alpha = alpha * 0.35f), Color.Transparent),
                    center = Offset(cx, cy),
                    radius = radius
                ),
                radius = radius,
                center = Offset(cx, cy)
            )
        }

        val a = palette[0]
        val b = palette.getOrElse(1) { palette[0] }
        val c = palette.getOrElse(2) { palette[0] }

        val angleA = primaryPhase * 2f * Math.PI.toFloat()
        val angleB = secondaryPhase * 2f * Math.PI.toFloat()

        blob(
            a,
            cx = w * (0.18f + 0.22f * sin(angleA)),
            cy = h * (0.08f + 0.10f * cos(angleB)),
            radius = w * 1.05f,
            alpha = 0.42f * strength
        )
        blob(
            b,
            cx = w * (0.92f + 0.18f * cos(angleB)),
            cy = h * (0.26f + 0.12f * sin(angleA)),
            radius = w * 0.95f,
            alpha = 0.36f * strength
        )
        blob(
            c,
            cx = w * (0.35f + 0.28f * cos(angleA * 0.7f)),
            cy = h * (0.86f + 0.10f * sin(angleB * 0.8f)),
            radius = w * 1.1f,
            alpha = 0.30f * strength
        )
        // Vignette keeps text legible on top of all that colour.
        drawRect(
            Brush.verticalGradient(
                listOf(
                    base.copy(alpha = 0.55f),
                    Color.Transparent,
                    base.copy(alpha = 0.72f)
                )
            )
        )
    }
}

@Composable
fun AuroraSurfaceRoot(
    seeds: List<Color>,
    modifier: Modifier = Modifier,
    animated: Boolean = true,
    content: @Composable () -> Unit
) {
    Box(modifier.fillMaxSize().background(T.colors.background)) {
        AuroraBackdrop(seeds = seeds, animated = animated, modifier = Modifier.fillMaxSize())
        content()
    }
}

/**
 * Pulls a small palette out of an artwork image so the backdrop can match the
 * album instead of a static theme colour. Results are cached per url.
 */
object ArtworkColors {

    private val cache = LinkedHashMap<String, List<Color>>()
    private const val MAX_CACHE = 40

    suspend fun of(context: Context, url: String?): List<Color> {
        if (url.isNullOrBlank()) return emptyList()
        synchronized(cache) { cache[url] }?.let { return it }
        val colors = runCatching {
            val request = ImageRequest.Builder(context)
                .data(url)
                .allowHardware(false)
                .size(160)
                .build()
            when (val result = context.imageLoader.execute(request)) {
                is coil.request.SuccessResult -> {
                    val bitmap = (result.drawable as? BitmapDrawable)?.bitmap
                    bitmap?.let {
                        val palette = Palette.from(it).clearFilters().maximumColorCount(12).generate()
                        listOfNotNull(
                            palette.vibrantSwatch?.rgb,
                            palette.lightVibrantSwatch?.rgb,
                            palette.mutedSwatch?.rgb,
                            palette.darkVibrantSwatch?.rgb
                        ).map { argb -> Color(argb) }
                    }.orEmpty()
                }
                else -> emptyList()
            }
        }.getOrDefault(emptyList())
        if (colors.isNotEmpty()) {
            synchronized(cache) {
                if (cache.size >= MAX_CACHE) cache.keys.firstOrNull()?.let(cache::remove)
                cache[url] = colors
            }
        }
        return colors
    }

    /** Blends the artwork palette with the accent so nothing ever looks muddy. */
    fun merge(accent: Accent, artwork: List<Color>): List<Color> = when {
        artwork.isEmpty() -> listOf(accent.primary, accent.secondary)
        artwork.size == 1 -> listOf(accent.primary, artwork[0])
        else -> listOf(artwork[0], artwork[1], accent.secondary)
    }
}

/** Remembers [ArtworkColors.of] for a url. */
@Composable
fun rememberArtworkColors(url: String?, fallback: List<Color>): List<Color> {
    val context = androidx.compose.ui.platform.LocalContext.current
    val accent = T.colors.accent
    var colors by remember(url) { mutableStateOf(fallback) }
    LaunchedEffect(url, accent.id) {
        val fetched = ArtworkColors.of(context, url)
        if (fetched.isNotEmpty()) colors = ArtworkColors.merge(accent, fetched)
    }
    return colors
}
