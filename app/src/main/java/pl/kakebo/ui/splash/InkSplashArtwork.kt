package pl.kakebo.ui.splash

import android.annotation.SuppressLint
import android.content.res.Resources
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
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.xmlpull.v1.XmlPullParser
import pl.kakebo.R
import kotlin.math.sin
import kotlin.random.Random

/** Outlined lettering, feathered ink masks and a gold reflection, all rendered locally. */
@Composable
internal fun InkSplashArtwork(progress: () -> Float) {
    val colors = MaterialTheme.colorScheme
    val dark = colors.background.luminance() < 0.5f
    val paper = if (dark) Color(0xFF141B1D) else Color(0xFFF7F8F4)
    val ink = if (dark) Color(0xFFF0F3EE) else Color(0xFF247D68)
    val gold = if (dark) Color(0xFFD3B27C) else Color(0xFFBB945B)
    val shadow = Color(0xFF0B1214)
    val resources = LocalContext.current.resources
    val glyphs = remember(resources) { loadKanjiPaths(resources) }
    val flecks = remember {
        val random = Random(41)
        List(90) { Offset(random.nextFloat(), random.nextFloat()) }
    }
    val layerPaint = remember { Paint() }

    Box(Modifier.fillMaxSize().background(colors.background).testTag("startup_splash")) {
        Canvas(Modifier.fillMaxSize().clearAndSetSemantics {}) {
            val p = progress()
            val opacity = 1f - smooth((p - 0.91f) / 0.09f)
            val atmosphere = smooth(p / 0.18f) * opacity
            drawRect(paper, alpha = opacity)
            // Soft directional light and very fine paper fibres; no circular sun motif.
            drawRect(Brush.linearGradient(
                listOf(gold.copy(alpha = 0.07f * atmosphere), Color.Transparent, shadow.copy(alpha = 0.10f * atmosphere)),
                start = Offset.Zero, end = Offset(size.width, size.height),
            ))
            flecks.forEachIndexed { index, point ->
                val start = Offset(point.x * size.width, point.y * size.height)
                drawLine(ink, start, start + Offset((4 + index % 9).dp.toPx(), 0.4.dp.toPx()),
                    strokeWidth = 0.35.dp.toPx(), alpha = 0.045f * atmosphere)
            }
        }
        BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding(), contentAlignment = Alignment.Center) {
            val fit = minOf(1f, maxWidth.value / 320f, maxHeight.value / 510f)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.graphicsLayer {
                    val p = progress()
                    alpha = 1f - smooth((p - 0.91f) / 0.09f)
                    val settle = 1f + 0.035f * (1f - smooth(p / 0.8f))
                    scaleX = settle
                    scaleY = settle
                },
            ) {
                Canvas(Modifier.size(280.dp * fit, 344.dp * fit).clearAndSetSemantics {}) {
                    val p = progress()
                    val u = size.width / 280f
                    val goldPass = smooth((p - 0.48f) / 0.38f)
                    val lightY = -70f + 485f * goldPass
                    val presence = smooth((p - 0.02f) / 0.25f)

                    // Fine, broken rules frame the calligraphy like a print on handmade paper.
                    drawLine(gold, Offset(62f * u, 30f * u), Offset(62f * u, (30f + 85f * presence) * u),
                        strokeWidth = 0.5f * u, alpha = presence * 0.35f)
                    drawLine(gold, Offset(218f * u, 314f * u), Offset(218f * u, (314f - 85f * presence) * u),
                        strokeWidth = 0.5f * u, alpha = presence * 0.35f)

                    // Each glyph forms independently. The feathered diagonal mask suggests wet ink,
                    // rather than pretending the font outline describes Japanese stroke order.
                    glyphs.forEachIndexed { index, path ->
                        val reveal = smooth((p - 0.05f - index * 0.13f) / 0.30f)
                        if (reveal > 0f) {
                            drawIntoCanvas { it.saveLayer(Rect(Offset.Zero, size), layerPaint) }
                            translate(84f * u, (20f + (1f - reveal) * 9f) * u) {
                                scale(u, u, pivot = Offset.Zero) {
                                    translate(0.8f, 1.2f) {
                                        drawPath(path, shadow, alpha = if (dark) 0.55f else 0.16f)
                                    }
                                    drawPath(path, Brush.linearGradient(
                                        listOf(ink, if (dark) Color(0xFFB89059) else Color(0xFF174B3E), ink),
                                        start = Offset(0f, index * 104f), end = Offset(112f, index * 104f + 92f),
                                    ))
                                    drawPath(path, gold, alpha = if (dark) 0.42f else 0.24f, style = Stroke(0.35f))
                                }
                            }
                            // Reflection is clipped to the ink, leaving the paper untouched.
                            drawRect(Brush.linearGradient(
                                listOf(Color.Transparent, gold.copy(alpha = 0.95f), Color.Transparent),
                                start = Offset(0f, (lightY - 52f) * u),
                                end = Offset(size.width, (lightY + 30f) * u),
                            ), blendMode = BlendMode.SrcAtop)
                            if (reveal < 1f) {
                                val edge = (20f + index * 104f - 35f + 165f * reveal) * u
                                drawRect(Brush.linearGradient(
                                    listOf(Color.White, Color.Transparent),
                                    start = Offset(112f * u, edge - 16f * u),
                                    end = Offset(140f * u, edge + 16f * u),
                                ), blendMode = BlendMode.DstIn)
                            }
                            drawIntoCanvas { it.restore() }
                        }
                    }

                    // A few drifting gold motes trail the writing; their trajectories are deterministic.
                    flecks.take(18).forEachIndexed { index, point ->
                        val life = ((p - 0.09f - point.y * 0.37f) / 0.38f).coerceIn(0f, 1f)
                        val alpha = sin(life * Math.PI).toFloat() * 0.55f
                        val x = (80f + point.x * 120f + sin(life * 3f + index) * 9f) * u
                        val y = (35f + point.y * 275f - life * 26f) * u
                        drawCircle(gold, radius = (0.45f + point.x * 0.5f) * u, center = Offset(x, y), alpha = alpha)
                    }
                }
                Spacer(Modifier.height(10.dp * fit))
                Text(
                    stringResource(R.string.app_name), color = ink,
                    fontFamily = FontFamily.Serif, fontWeight = FontWeight.Normal,
                    fontSize = (38f * fit).sp, letterSpacing = (3f * fit).sp,
                    modifier = Modifier.graphicsLayer {
                        val reveal = smooth((progress() - 0.52f) / 0.20f)
                        alpha = reveal
                        translationY = 8.dp.toPx() * (1f - reveal)
                    },
                )
                Spacer(Modifier.height(12.dp * fit))
                Text(
                    stringResource(R.string.app_subtitle), color = colors.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 24.dp).graphicsLayer {
                        alpha = smooth((progress() - 0.64f) / 0.18f)
                    },
                )
            }
        }
    }
}

private fun smooth(value: Float): Float {
    val t = value.coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

// This specific drawable is compiled vector XML. Read its paths directly so both
// animations keep exactly the same lettering without a second copy of the asset.
@SuppressLint("ResourceType")
private fun loadKanjiPaths(resources: Resources): List<Path> = buildList {
    resources.getXml(R.drawable.kakebo_kanji).use { xml ->
        while (xml.eventType != XmlPullParser.END_DOCUMENT) {
            if (xml.eventType == XmlPullParser.START_TAG && xml.name == "path") {
                val data = xml.getAttributeValue("http://schemas.android.com/apk/res/android", "pathData")
                add(PathParser().parsePathString(data).toPath())
            }
            xml.next()
        }
    }
}
