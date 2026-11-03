package com.intraprenuers.noticepilot.ui.components

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import kotlin.math.max
import kotlin.random.Random

/**
 * The dark theme's atmosphere, drawn once behind every screen:
 *  1. one diffuse forest-green light behind the header/logo corner (top-left), fading to nothing;
 *  2. an extremely faint static grain, so large near-black areas don't look flat.
 * Light theme: draws nothing (paper stays flat, as designed). Sits on its own layer, so scrolling the
 * screens on top never redraws it. Purely decorative: no semantics, no touch handling.
 */
@Composable
fun Backdrop(modifier: Modifier = Modifier) {
    if (!isSystemInDarkTheme()) return
    val grain = remember { ShaderBrush(ImageShader(grainTile(), TileMode.Repeated, TileMode.Repeated)) }
    Spacer(
        modifier = modifier
            .graphicsLayer()
            .drawBehind {
                // Ambient light: forest green at low alpha, centred just above the wordmark, very wide falloff.
                val radius = max(size.width, size.height) * 0.75f
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(AMBIENT.copy(alpha = 0.20f), AMBIENT.copy(alpha = 0.07f), Color.Transparent),
                        center = Offset(size.width * 0.15f, -size.height * 0.02f),
                        radius = radius
                    )
                )
                drawRect(brush = grain, alpha = GRAIN_ALPHA)
            }
    )
}

/** NoticePilot forest green (Palette.Forest): the light's colour, never used as a bright glow. */
private val AMBIENT = Color(0xFF245B49)

/** Overall grain strength. Individual dots are at most 60% white, so the effective peak is ~2.4%. */
private const val GRAIN_ALPHA = 0.04f

/** A 128x128 tile of sparse single-pixel dots in four strengths. Deterministic, so it never shimmers. */
private fun grainTile(): ImageBitmap {
    val size = 128
    val bitmap = ImageBitmap(size, size)
    val canvas = Canvas(bitmap)
    val random = Random(20261103)
    val buckets = listOf(0.15f, 0.3f, 0.45f, 0.6f)
    for (alpha in buckets) {
        val points = List(900) { Offset(random.nextInt(size).toFloat() + 0.5f, random.nextInt(size).toFloat() + 0.5f) }
        val paint = Paint().apply {
            color = Color.White.copy(alpha = alpha)
            strokeWidth = 1f
        }
        canvas.drawPoints(PointMode.Points, points, paint)
    }
    return bitmap
}
