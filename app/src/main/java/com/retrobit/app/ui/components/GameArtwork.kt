package com.retrobit.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.retrobit.app.R
import com.retrobit.app.data.ConsoleInfo
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** How far the box art rises over the screenshot's lower edge; the two stay visually attached. */
private val CoverOverlap = 42.dp

/**
 * Screenshot with the box art rising over its lower edge. A start-aligned cover follows the
 * picture's real left edge, and [beside] fills the strip to the right of that cover.
 */
@Composable
fun PreviewWithCover(
    snapUrl: Any?,
    console: ConsoleInfo?,
    scanlines: Boolean,
    coverUrl: String?,
    title: String,
    gameId: String,
    framed: Boolean,
    coverHeight: Dp,
    modifier: Modifier = Modifier,
    maxBlockHeight: Dp = Dp.Infinity,
    coverAtStart: Boolean = false,
    shareCover: Boolean = true,
    beside: (@Composable () -> Unit)? = null
) {
    BoxWithConstraints(modifier) {
        // The screenshot defines the block height so a column can top-align it. Short screens
        // shrink screenshot and cover together so the cover never outgrows the screenshot.
        val cap = listOf(maxHeight, maxBlockHeight).filter { it.value.isFinite() }.minOrNull() ?: Dp.Infinity
        val natural = maxWidth * 9f / 16f + coverHeight - if (beside == null) CoverOverlap else 0.dp
        val block = natural.coerceAtMost(cap)
        val scale = block / natural
        val cover = if (beside == null) coverHeight * scale else coverHeight.coerceAtMost(block * 0.65f)
        val overlap = if (beside == null) CoverOverlap * scale else 0.dp
        val coverWidth = cover * coverAspectRatio(console)
        val coverStart = if (coverAtStart) {
            4.dp
        } else {
            (maxWidth - coverWidth) / 2
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(block)
                .align(Alignment.TopCenter)
        ) {
            CrtSnap(
                snapUrl = snapUrl,
                console = console,
                scanlines = scanlines,
                modifier = Modifier.fillMaxSize().padding(bottom = cover - overlap)
            )
            DetailCover(
                url = coverUrl,
                title = title,
                console = console,
                framed = framed,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = coverStart.coerceAtLeast(0.dp))
                    .then(if (shareCover) Modifier.sharedCover(gameId) else Modifier)
                    .height(cover)
                    .aspectRatio(coverAspectRatio(console))
            )
            if (beside != null) {
                Box(
                    Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = (coverStart + coverWidth + 14.dp).coerceAtLeast(0.dp))
                        .height(cover - overlap)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.CenterStart
                ) { beside() }
            }
        }
    }
}

@Composable
private fun DetailCover(
    url: String?,
    title: String,
    console: ConsoleInfo?,
    framed: Boolean,
    modifier: Modifier = Modifier
) {
    val accent = consoleColor(console)
    val shape = RoundedCornerShape(8.dp)
    Box(modifier, contentAlignment = Alignment.Center) {
        // Console-tinted halo bleeds out behind the cover so it reads as a lit object on the shelf.
        Box(
            Modifier
                .matchParentSize()
                .graphicsLayer { scaleX = 1.32f; scaleY = 1.26f }
                .drawBehind {
                    drawCircle(
                        brush = Brush.radialGradient(
                            listOf(accent.copy(alpha = 0.30f), Color.Transparent),
                            radius = size.minDimension * 0.72f
                        )
                    )
                }
        )
        Box(
            Modifier
                .matchParentSize()
                .shadow(10.dp, shape, ambientColor = accent.copy(alpha = 0.55f), spotColor = accent.copy(alpha = 0.65f))
                .clip(shape)
                .background(
                    Brush.linearGradient(
                        listOf(accent.copy(alpha = 0.85f), Color(0xFF23232B), accent.copy(alpha = 0.45f))
                    )
                )
                .padding(2.dp)
        ) {
            GameCover(
                url = url,
                title = title,
                console = console,
                modifier = Modifier.fillMaxSize(),
                corner = 6.dp,
                framed = framed
            )
            Box(
                Modifier
                    .matchParentSize()
                    .clip(RoundedCornerShape(6.dp))
                    .background(
                        Brush.linearGradient(
                            0f to Color.White.copy(alpha = 0.22f),
                            0.38f to Color.Transparent,
                            1f to Color.Black.copy(alpha = 0.18f)
                        )
                    )
            )
        }
    }
}

private val PerformanceConsoles = setOf("n64", "ps1", "nds", "psp")

@Composable
fun PerformanceNote(consoleId: String, modifier: Modifier = Modifier.padding(top = 10.dp)) {
    if (consoleId !in PerformanceConsoles) return
    val isTv = LocalIsTv.current
    Column(modifier) {
        Row(verticalAlignment = Alignment.Top) {
            Icon(Icons.Rounded.Info, null, tint = HintBlue, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(7.dp))
            Text(
                stringResource(R.string.performance_note),
                style = MaterialTheme.typography.labelSmall,
                color = retroOnSurface(0.75f)
            )
        }
        if (consoleId == "nds" && isTv) {
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.Top) {
                Icon(Icons.Rounded.Info, null, tint = HintYellow, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(7.dp))
                Text(
                    stringResource(R.string.performance_note_nds_tv),
                    style = MaterialTheme.typography.labelSmall,
                    color = retroOnSurface(0.75f)
                )
            }
        }
    }
}

private val FlagRed = Color(0xFFE30A17)

/** Turkish flag drawn from its construction ratios, so no bitmap or emoji font is needed. */
@Composable
fun TurkishFlag(height: Dp, modifier: Modifier = Modifier) {
    val label = stringResource(R.string.turkish_release)
    Canvas(
        modifier
            .semantics { contentDescription = label }
            .height(height)
            .aspectRatio(1.5f)
            .clip(RoundedCornerShape(2.dp))
    ) {
        val g = size.height
        drawRect(FlagRed)
        drawCircle(Color.White, radius = g * 0.25f, center = Offset(g * 0.5f, g * 0.5f))
        drawCircle(FlagRed, radius = g * 0.2f, center = Offset(g * 0.5625f, g * 0.5f))
        val star = Path()
        val cx = g * 0.9f
        val cy = g * 0.5f
        val outer = g * 0.125f
        val inner = outer * 0.382f
        for (i in 0 until 10) {
            val angle = PI + i * PI / 5
            val r = if (i % 2 == 0) outer else inner
            val x = cx + (r * cos(angle)).toFloat()
            val y = cy + (r * sin(angle)).toFloat()
            if (i == 0) star.moveTo(x, y) else star.lineTo(x, y)
        }
        star.close()
        drawPath(star, Color.White)
    }
}
