package pl.kakebo.ui.splash

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pl.kakebo.R
import pl.kakebo.SplashStyle
import kotlin.math.sin

/** One finite animation; Animatable also respects Android's animation duration scale. */
@Composable
fun KakeboSplash(style: SplashStyle = SplashStyle.SUN, onFinished: () -> Unit) {
    val progress = remember { Animatable(0f) }
    val finish by rememberUpdatedState(onFinished)
    LaunchedEffect(style) {
        progress.snapTo(0f)
        progress.animateTo(1f, tween(durationMillis = if (style == SplashStyle.INK) 3400 else 2400, easing = LinearEasing))
        finish()
    }
    when (style) {
        SplashStyle.SUN -> KakeboSplashArtwork(progress.value)
        SplashStyle.INK -> InkSplashArtwork { progress.value }
    }
}

@Composable
internal fun KakeboSplashArtwork(progress: Float) {
    val colors = MaterialTheme.colorScheme
    val dark = colors.background.luminance() < 0.5f
    val ink = if (dark) Color(0xFFF0F3EE) else Color(0xFF247D68)
    val sun = if (dark) Color(0xFFF09C91) else Color(0xFFBA514C)
    val gold = if (dark) Color(0xFFD3B27C) else Color(0xFFBB945B)
    val kanji = painterResource(R.drawable.kakebo_kanji)
    val layerPaint = remember { Paint() }
    val reveal = (progress / 0.18f).coerceIn(0f, 1f)
    val travel = ((progress - 0.08f) / 0.76f).coerceIn(0f, 1f)
    val fade = ((1f - progress) / 0.12f).coerceIn(0f, 1f)

    Box(
        Modifier.fillMaxSize().background(colors.background).testTag("startup_splash"),
        contentAlignment = Alignment.Center,
    ) {
        // Scale the illustration as one piece so it also fits landscape and large text settings.
        BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding(), contentAlignment = Alignment.Center) {
            val scale = minOf(1f, maxWidth.value / 320f, maxHeight.value / 500f)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.graphicsLayer { alpha = fade },
            ) {
                Canvas(Modifier.size(280.dp * scale, 340.dp * scale).clearAndSetSemantics {}) {
                    val unit = size.width / 280f
                    val center = Offset(
                        (60f + 165f * travel + 16f * sin(travel * Math.PI).toFloat()) * unit,
                        (298f - 260f * travel) * unit,
                    )
                    drawCircle(
                        Brush.radialGradient(listOf(sun.copy(alpha = 0.18f * reveal), Color.Transparent),
                            center = center, radius = 100f * unit),
                        radius = 100f * unit, center = center,
                    )
                    drawCircle(sun, radius = 43f * unit, center = center, alpha = reveal * 0.9f)
                    drawCircle(sun, radius = 51f * unit, center = center,
                        alpha = reveal * 0.22f, style = Stroke(unit))

                    // A separate layer confines the moving golden light to the actual letter shapes.
                    drawIntoCanvas { it.saveLayer(Rect(Offset.Zero, size), layerPaint) }
                    translate(left = 90f * unit, top = (24f + 10f * (1f - reveal)) * unit) {
                        with(kanji) {
                            draw(Size(100f * unit, 280f * unit), alpha = reveal, colorFilter = ColorFilter.tint(ink))
                        }
                    }
                    drawCircle(
                        Brush.radialGradient(listOf(gold, gold.copy(alpha = 0.65f), Color.Transparent),
                            center = center, radius = 92f * unit),
                        radius = 92f * unit, center = center, blendMode = BlendMode.SrcAtop,
                    )
                    drawIntoCanvas { it.restore() }
                }
                Spacer(Modifier.height(12.dp * scale))
                Text(
                    stringResource(R.string.app_name), color = ink,
                    fontFamily = FontFamily.Serif, fontWeight = FontWeight.Normal,
                    fontSize = (40f * scale).sp, letterSpacing = (2f * scale).sp,
                    modifier = Modifier.graphicsLayer { alpha = reveal },
                )
                Spacer(Modifier.height(8.dp * scale))
                Text(
                    stringResource(R.string.app_subtitle), color = colors.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 24.dp).graphicsLayer { alpha = reveal },
                )
            }
        }
    }
}
