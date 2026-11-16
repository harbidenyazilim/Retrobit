package com.retrobit.app.ui.components

import android.graphics.Bitmap
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.retrobit.app.R
import com.retrobit.app.data.ConsoleInfo
import com.retrobit.app.ui.theme.Display
import com.retrobit.app.ui.theme.PixelFont
import com.retrobit.app.ui.theme.TechFont
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Overscan-safe padding for content drawn on top of full-bleed backdrops. */
val LocalSafeArea = compositionLocalOf { PaddingValues(0.dp) }

@OptIn(ExperimentalSharedTransitionApi::class)
val LocalSharedScope = compositionLocalOf<SharedTransitionScope?> { null }
val LocalNavAnimScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

/**
 * Lets the gamelist cover fly into the detail cover (and back). Outside a nav destination that
 * provides both scopes it is a no-op, so the same composable works on every screen.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.sharedCover(key: String): Modifier {
    val shared = LocalSharedScope.current ?: return this
    val anim = LocalNavAnimScope.current ?: return this
    return with(shared) {
        this@sharedCover.sharedElement(
            rememberSharedContentState("cover-$key"),
            anim,
            boundsTransform = { _, _ -> tween(360, easing = FastOutSlowInEasing) }
        )
    }
}

/**
 * The console-themed look (backdrops, wordmarks, CRT) is a supporter perk. Free mode keeps the
 * same layout but draws flat Material surfaces so light themes stay readable.
 */
val LocalRetroTheme = compositionLocalOf { false }

/** Turns off continuous parallax and Ken Burns motion while keeping the retro art and focus cues. */
val LocalReducedMotion = compositionLocalOf { false }

/** Ink that reads on the dark retro backdrop, or on the plain surface in free mode. */
@Composable
fun retroOnSurface(alpha: Float = 1f): Color =
    if (LocalRetroTheme.current) Color.White.copy(alpha = alpha)
    else MaterialTheme.colorScheme.onBackground.copy(alpha = alpha)

val RetroInk = Color(0xFF07070C)
val HintGreen = Color(0xFF34C759)
val HintRed = Color(0xFFFF453A)
val HintBlue = Color(0xFF0A84FF)
val HintYellow = Color(0xFFFFD60A)

@DrawableRes
fun consoleBackdropRes(consoleId: String?): Int? = when (consoleId) {
    "nes" -> R.drawable.bg_nes
    "snes" -> R.drawable.bg_snes
    "n64" -> R.drawable.bg_n64
    "gb" -> R.drawable.bg_gb_v112
    "gbc" -> R.drawable.bg_gbc_v112
    "gba" -> R.drawable.bg_gba
    "nds" -> R.drawable.bg_nds
    "genesis" -> R.drawable.bg_genesis
    "ps1" -> R.drawable.bg_ps1
    "psp" -> R.drawable.bg_psp
    "atari2600" -> R.drawable.bg_atari2600
    else -> null
}

data class ConsoleStyle(
    val accent: Color,
    val palette: List<Color>,
    val wordmark: String,
    /** Shape used by list rows and cards: hard cut corners for the 8/16-bit era, soft for later. */
    val shape: Shape,
    val cardShape: Shape,
    val headingFont: FontFamily
)

private val cut = CutCornerShape(topStart = 10.dp, bottomEnd = 10.dp)
private val cutCard = CutCornerShape(topStart = 14.dp, bottomEnd = 14.dp)
private val pixelRow = RoundedCornerShape(2.dp)
private val soft = RoundedCornerShape(14.dp)
private val softCard = RoundedCornerShape(18.dp)
private val tech = CutCornerShape(topEnd = 12.dp, bottomStart = 12.dp)

fun consoleStyle(console: ConsoleInfo?): ConsoleStyle {
    val accent = Color(console?.color ?: 0xFF8B7CFF)
    fun c(v: Long) = Color(v)
    return when (console?.id) {
        "nes" -> ConsoleStyle(accent, listOf(c(0xFFE4572E), c(0xFF2B2B2B), c(0xFFBDBDBD)),
            "NES", pixelRow, pixelRow, PixelFont)
        "snes" -> ConsoleStyle(accent, listOf(c(0xFFE5484D), c(0xFFF5C518), c(0xFF3B82F6), c(0xFF22C55E)),
            "SNES", cut, cutCard, PixelFont)
        "n64" -> ConsoleStyle(accent, listOf(c(0xFFEF4444), c(0xFF22C55E), c(0xFF3B82F6), c(0xFFEAB308)),
            "N64", tech, tech, TechFont)
        "gb" -> ConsoleStyle(accent, listOf(c(0xFF9BBC0F), c(0xFF8BAC0F), c(0xFF306230), c(0xFF0F380F)),
            "GAME BOY", pixelRow, cutCard, PixelFont)
        "gbc" -> ConsoleStyle(accent, listOf(c(0xFF2563EB), c(0xFF2DD4BF), c(0xFFA855F7)),
            "GAME BOY COLOR", soft, softCard, Display)
        "gba" -> ConsoleStyle(accent, listOf(c(0xFFA855F7), c(0xFF6366F1), c(0xFFE879F9)),
            "GBA", tech, tech, TechFont)
        "nds" -> ConsoleStyle(accent, listOf(c(0xFF0EA5E9), c(0xFFE2E8F0), c(0xFF0EA5E9)),
            "DS", soft, softCard, Display)
        "genesis" -> ConsoleStyle(accent, listOf(c(0xFF2563EB), c(0xFFE5E7EB), c(0xFFDC2626)),
            "SEGA", cut, cutCard, TechFont)
        "ps1" -> ConsoleStyle(c(0xFFCBD5E1), listOf(c(0xFF94A3B8), c(0xFFEF4444), c(0xFF22C55E), c(0xFF3B82F6)),
            "PS1", tech, tech, TechFont)
        "psp" -> ConsoleStyle(c(0xFF2DD4BF), listOf(c(0xFF0F766E), c(0xFF2DD4BF), c(0xFFE2E8F0)),
            "PSP", soft, softCard, Display)
        "atari2600" -> ConsoleStyle(accent, listOf(c(0xFFD58A43), c(0xFF704A30), c(0xFFE8D6B5)),
            "ATARI 2600", pixelRow, cutCard, TechFont)
        else -> ConsoleStyle(accent, listOf(accent, Color.White.copy(alpha = 0.7f)),
            (console?.shortName ?: "RETRO").uppercase(Locale.ROOT), tech, tech, TechFont)
    }
}

/**
 * Original type-only system marks (see ConsoleMarks.kt). The free theme keeps plain text.
 */
@Composable
fun ConsoleWordmark(
    console: ConsoleInfo?,
    modifier: Modifier = Modifier,
    size: TextUnit = 56.sp,
    showStrip: Boolean = true,
    alignEnd: Boolean = false
) {
    val style = consoleStyle(console)
    val text = style.wordmark
    if (!LocalRetroTheme.current) {
        Column(modifier, horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start) {
            Text(
                text,
                fontWeight = FontWeight.Bold,
                fontSize = size * 0.72f,
                lineHeight = size * 0.9f,
                letterSpacing = size * 0.05f,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                softWrap = false
            )
        }
        return
    }
    RetroConsoleMark(console?.id, text, style.accent, size, showStrip, alignEnd, modifier)
}

@Composable
fun BackdropImage(consoleId: String?, modifier: Modifier = Modifier, widthPx: Int = 1280) {
    if (!LocalRetroTheme.current) {
        Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant))
        return
    }
    val res = consoleBackdropRes(consoleId)
    val accent = consoleStyle(com.retrobit.app.data.ConsoleCatalog.get(consoleId)).accent
    if (res == null) {
        Box(
            modifier.background(
                Brush.linearGradient(
                    listOf(accent.copy(alpha = 0.55f), RetroInk, accent.copy(alpha = 0.25f))
                )
            )
        )
    } else {
        val context = LocalContext.current
        AsyncImage(
            model = remember(res, widthPx) {
                ImageRequest.Builder(context)
                    .data(res)
                    .size(widthPx, widthPx * 731 / 1280)
                    .bitmapConfig(Bitmap.Config.RGB_565)
                    .build()
            },
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier
        )
    }
}

/** +1 / -1 for the short way round a wrapping carousel, 0 when either id is unknown. */
fun carouselDirection(order: List<String>?, from: String?, to: String?): Int {
    if (order == null) return 0
    val a = order.indexOf(from)
    val b = order.indexOf(to)
    if (a < 0 || b < 0 || a == b) return 0
    val ahead = (b - a + order.size) % order.size
    return if (ahead <= order.size / 2) 1 else -1
}

/**
 * Darker on the left and bottom, where text sits, and lightly vignetted like a tube. [hero] is
 * the focused game's snap, drawn blurred over the console art; [order] makes a system change
 * slide the art the way the carousel moved, slower than the foreground (parallax).
 */
@Composable
fun RetroBackdrop(
    consoleId: String?,
    modifier: Modifier = Modifier,
    scanlines: Boolean = true,
    dim: Float = 0f,
    hero: Any? = null,
    order: List<String>? = null
) {
    if (!LocalRetroTheme.current) {
        val base = MaterialTheme.colorScheme.background
        val accent = MaterialTheme.colorScheme.primary
        Box(modifier.fillMaxSize().background(base).then(
            if (order == null) Modifier else Modifier.drawBehind {
                drawRect(Brush.radialGradient(
                    listOf(accent.copy(alpha = 0.14f), Color.Transparent),
                    center = Offset(size.width * 0.8f, size.height * 0.3f),
                    radius = size.maxDimension * 0.85f
                ))
                val step = 48.dp.toPx()
                var x = 0f
                while (x < size.width) {
                    var y = 0f
                    while (y < size.height) {
                        drawCircle(accent.copy(alpha = 0.12f), 1.dp.toPx(), Offset(x, y))
                        y += step
                    }
                    x += step
                }
            }
        ))
        return
    }
    val accent by animateColorAsState(
        consoleStyle(com.retrobit.app.data.ConsoleCatalog.get(consoleId)).accent,
        tween(520),
        label = "backdropAccent"
    )
    // Slow Ken Burns drift, as in ES-DE/Pegasus themes. Read only inside graphicsLayer so the
    // backdrop never recomposes; the frame clock pauses on its own when the activity stops.
    val drift = if (LocalReducedMotion.current) null else rememberInfiniteTransition(label = "drift")
    val t = if (drift == null) {
        0.5f
    } else {
        val value by drift.animateFloat(
            0f, 1f,
            infiniteRepeatable(tween(26_000, easing = LinearEasing), RepeatMode.Reverse),
            label = "driftT"
        )
        value
    }
    Box(modifier.fillMaxSize().background(RetroInk)) {
        AnimatedContent(
            targetState = consoleId,
            transitionSpec = {
                val dir = carouselDirection(order, initialState, targetState)
                (fadeIn(tween(520)) + slideInHorizontally(tween(620, easing = FastOutSlowInEasing)) { dir * it / 18 }) togetherWith
                    (fadeOut(tween(520)) + slideOutHorizontally(tween(620, easing = FastOutSlowInEasing)) { -dir * it / 18 })
            },
            label = "backdrop",
            modifier = Modifier.fillMaxSize().graphicsLayer {
                val s = 1.06f + 0.05f * t
                scaleX = s
                scaleY = s
                translationX = (t - 0.5f) * size.width * 0.03f
                translationY = (0.5f - t) * size.height * 0.015f
            }
        ) { id ->
            BackdropImage(id, Modifier.fillMaxSize())
        }
        Crossfade(targetState = hero, animationSpec = tween(480), label = "hero") { h ->
            if (h != null) HeroBlur(h)
        }
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(
                    0f to Color.Black.copy(alpha = 0.82f),
                    0.45f to Color.Black.copy(alpha = 0.35f),
                    1f to Color.Black.copy(alpha = 0.1f)
                )
            )
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to Color.Black.copy(alpha = 0.45f),
                    0.25f to Color.Transparent,
                    0.62f to Color.Transparent,
                    1f to Color.Black.copy(alpha = 0.92f)
                )
            )
        )
        if (dim > 0f) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = dim)))
        Box(
            Modifier.fillMaxSize().drawBehind {
                drawRect(
                    Brush.radialGradient(
                        0f to accent.copy(alpha = 0.26f),
                        1f to Color.Transparent,
                        center = Offset(size.width * 0.12f, size.height),
                        radius = size.maxDimension * 0.6f
                    )
                )
            }
        )
        Box(Modifier.fillMaxSize().crtOverlay(scanlines))
    }
}

/**
 * The snap decoded at thumbnail size and stretched: upscaling alone already reads as a soft blur
 * on old boxes, and API 31+ adds a real one on top. Costs one tiny bitmap per game.
 */
@Composable
private fun HeroBlur(snap: Any) {
    val context = LocalContext.current
    AsyncImage(
        model = remember(snap) {
            ImageRequest.Builder(context)
                .data(snap)
                .size(64, 48)
                .bitmapConfig(Bitmap.Config.RGB_565)
                .build()
        },
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .fillMaxSize()
            .then(if (android.os.Build.VERSION.SDK_INT >= 31) Modifier.blur(18.dp) else Modifier)
            .graphicsLayer { alpha = 0.78f }
    )
}

/**
 * Focus lift shared by the retro screens: a small scale-up plus a soft accent halo drawn as
 * stacked outlines, so it needs no blur and works below API 28 where shadow colours are ignored.
 */
@Composable
fun Modifier.focusLift(
    focused: Boolean,
    accent: Color,
    shape: Shape,
    lift: Float = 1.05f,
    spread: Dp = 10.dp
): Modifier {
    val p by animateFloatAsState(
        if (focused) 1f else 0f,
        tween(200, easing = FastOutSlowInEasing),
        label = "focusLift"
    )
    val glow = LocalRetroTheme.current
    return this
        .graphicsLayer {
            val s = 1f + (lift - 1f) * p
            scaleX = s
            scaleY = s
        }
        .then(if (glow) Modifier.focusGlow({ p }, accent, shape, spread) else Modifier)
}

fun Modifier.focusGlow(progress: () -> Float, color: Color, shape: Shape, spread: Dp): Modifier = drawBehind {
    val p = progress()
    if (p <= 0.01f) return@drawBehind
    val total = spread.toPx()
    val steps = 4
    for (i in steps downTo 1) {
        val grow = total * i / steps
        val outline = shape.createOutline(
            Size(size.width + grow * 2, size.height + grow * 2),
            layoutDirection,
            this
        )
        translate(-grow, -grow) { drawOutline(outline, color.copy(alpha = 0.11f * p)) }
    }
}

// One dark row out of three; built once and shared, so every overlay is a single bitmap-shader rect.
private val scanlineTile: ImageBitmap by lazy {
    val bmp = Bitmap.createBitmap(1, 3, Bitmap.Config.ARGB_8888)
    bmp.setPixel(0, 0, android.graphics.Color.argb(66, 0, 0, 0))
    bmp.asImageBitmap()
}

/** Scanlines every 3 px plus a soft vignette. */
fun Modifier.crtOverlay(scanlines: Boolean = true): Modifier = drawWithCache {
    val lines = ShaderBrush(ImageShader(scanlineTile, TileMode.Repeated, TileMode.Repeated))
    val vignette = Brush.radialGradient(
        0.55f to Color.Transparent,
        1f to Color.Black.copy(alpha = 0.55f),
        center = Offset(size.width / 2f, size.height / 2f),
        radius = size.maxDimension * 0.72f
    )
    onDrawWithContent {
        drawContent()
        if (scanlines) drawRect(lines)
        drawRect(vignette)
    }
}

/** A small "PRESS"-style button cap: the controller face button letter in its usual colour. */
@Composable
fun ButtonCap(label: String, color: Color, modifier: Modifier = Modifier) {
    val wide = label.length > 1
    Box(
        modifier
            .height(22.dp)
            .widthIn(min = 22.dp)
            .clip(if (wide) RoundedCornerShape(11.dp) else CircleShape)
            .background(Color.Black.copy(alpha = 0.55f))
            .border(1.5.dp, color, if (wide) RoundedCornerShape(11.dp) else CircleShape)
            .padding(horizontal = if (wide) 7.dp else 0.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            fontFamily = TechFont,
            fontWeight = FontWeight.Black,
            fontSize = if (wide) 9.sp else 11.sp,
            color = color,
            maxLines = 1
        )
    }
}

data class GamepadHint(
    val button: String,
    val label: String,
    val color: Color,
    val onClick: (() -> Unit)? = null
)

/** Batocera-style legend. Hints are tappable but never focusable, so the D-pad skips them. */
@Composable
fun GamepadHintBar(hints: List<GamepadHint>, modifier: Modifier = Modifier, trailing: @Composable () -> Unit = {}) {
    Row(
        modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            Modifier.weight(1f).horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            hints.forEach { hint ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .then(
                            if (hint.onClick != null) {
                                Modifier.pointerInput(hint.onClick) { detectTapGestures { hint.onClick.invoke() } }
                            } else Modifier
                        )
                        .padding(horizontal = 2.dp, vertical = 3.dp)
                ) {
                    ButtonCap(hint.button, hint.color)
                    Spacer(Modifier.width(7.dp))
                    Text(
                        hint.label,
                        style = MaterialTheme.typography.labelMedium,
                        color = retroOnSurface(0.88f),
                        maxLines = 1
                    )
                }
            }
        }
        trailing()
    }
}

@Composable
fun RetroClock(modifier: Modifier = Modifier) {
    var now by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = Date()
            delay(15_000)
        }
    }
    val format = remember { SimpleDateFormat("HH:mm", Locale.ROOT) }
    Text(
        format.format(now),
        modifier = modifier,
        fontFamily = TechFont,
        fontWeight = FontWeight.Bold,
        fontSize = 16.sp,
        letterSpacing = 2.sp,
        color = retroOnSurface(0.9f)
    )
}

/** Pill button for the top bar. Focus fills it with the accent so it reads from the couch. */
@Composable
fun RetroNavButton(
    icon: ImageVector,
    label: String?,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    badge: String? = null
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val retro = LocalRetroTheme.current
    val shape = RoundedCornerShape(20.dp)
    val fill by animateColorAsState(
        if (focused) accent
        else if (retro) Color.Black.copy(alpha = 0.42f)
        else MaterialTheme.colorScheme.surfaceVariant,
        tween(160),
        label = "navFill"
    )
    Row(
        modifier
            .focusLift(focused, accent, shape, lift = 1.07f, spread = 8.dp)
            .height(40.dp)
            .clip(shape)
            .background(fill)
            .border(
                1.dp,
                if (focused) retroOnSurface()
                else if (retro) Color.White.copy(alpha = 0.16f)
                else MaterialTheme.colorScheme.outlineVariant,
                shape
            )
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = if (label == null) 10.dp else 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = label, tint = if (focused) RetroInk else retroOnSurface(), modifier = Modifier.size(19.dp))
        if (label != null) {
            Spacer(Modifier.width(7.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                color = if (focused) RetroInk else retroOnSurface(),
                maxLines = 1
            )
        }
        if (badge != null) {
            Spacer(Modifier.width(7.dp))
            Text(
                badge,
                fontFamily = TechFont,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                color = if (focused) RetroInk else accent
            )
        }
    }
}

/** Chip used for filters and sort orders on the retro screens. */
@Composable
fun RetroChip(
    label: String,
    selected: Boolean,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val retro = LocalRetroTheme.current
    val shape = RoundedCornerShape(10.dp)
    val fill by animateColorAsState(
        when {
            focused -> accent
            selected -> accent.copy(alpha = 0.28f)
            retro -> Color.Black.copy(alpha = 0.4f)
            else -> MaterialTheme.colorScheme.surfaceVariant
        },
        tween(160),
        label = "chipFill"
    )
    Box(
        modifier
            .focusLift(focused, accent, shape, lift = 1.06f, spread = 7.dp)
            .heightIn(min = 34.dp)
            .clip(shape)
            .background(fill)
            .border(
                if (focused) 2.dp else 1.dp,
                when {
                    focused -> retroOnSurface()
                    selected -> accent
                    retro -> Color.White.copy(alpha = 0.14f)
                    else -> MaterialTheme.colorScheme.outlineVariant
                },
                shape
            )
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = if (focused) RetroInk else retroOnSurface(),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Face-button shortcuts for a whole screen. Only fresh presses count, so holding a button does
 * not fire a stream of random games or favourite toggles.
 */
fun Modifier.retroShortcuts(
    onX: (() -> Unit)? = null,
    onY: (() -> Unit)? = null,
    onStart: (() -> Unit)? = null,
    onSelect: (() -> Unit)? = null,
    onL1: (() -> Unit)? = null,
    onR1: (() -> Unit)? = null
): Modifier = onKeyEvent { event ->
    if (event.type != KeyEventType.KeyDown || event.nativeKeyEvent.repeatCount > 0) return@onKeyEvent false
    val action = when (event.key) {
        Key.ButtonX -> onX
        Key.ButtonY -> onY
        Key.ButtonStart, Key.Menu -> onStart
        Key.ButtonSelect -> onSelect
        Key.ButtonL1 -> onL1
        Key.ButtonR1 -> onR1
        else -> null
    } ?: return@onKeyEvent false
    action()
    true
}

/** libretro-thumbnails keeps in-game captures next to the box art under the same file name. */
fun snapUrlFor(coverUrl: String?): String? {
    if (coverUrl.isNullOrBlank() || !coverUrl.contains("libretro-thumbnails")) return null
    return when {
        "/Named_Boxarts/" in coverUrl -> coverUrl.replace("/Named_Boxarts/", "/Named_Snaps/")
        "/Named_Titles/" in coverUrl -> coverUrl.replace("/Named_Titles/", "/Named_Snaps/")
        "/Named_Snaps/" in coverUrl -> coverUrl
        else -> null
    }
}

/** The Retrobit mark used in the top-left of every frontend screen. */
@Composable
fun RetrobitMark(modifier: Modifier = Modifier, accent: Color = HintBlue, compact: Boolean = false) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        androidx.compose.foundation.Image(
            painter = androidx.compose.ui.res.painterResource(com.retrobit.app.R.drawable.ic_launcher_foreground),
            contentDescription = null,
            modifier = Modifier.size(36.dp)
        )
        if (!compact) Spacer(Modifier.width(9.dp))
        if (!compact) Text(
            "RETROBIT",
            fontFamily = TechFont,
            fontWeight = FontWeight.Black,
            fontSize = 17.sp,
            letterSpacing = 4.sp,
            color = if (LocalRetroTheme.current) retroOnSurface() else accent
        )
    }
}

@Composable
fun RetroTopBar(
    title: String,
    subtitle: String?,
    accent: Color,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    backFocus: androidx.compose.ui.focus.FocusRequester? = null,
    actions: @Composable () -> Unit = {}
) {
    Row(
        modifier.fillMaxWidth().padding(top = 6.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        RetroNavButton(
            androidx.compose.material.icons.Icons.Rounded.ArrowBack,
            null,
            accent,
            onBack,
            modifier = if (backFocus != null) Modifier.focusRequester(backFocus) else Modifier
        )
        Column(Modifier.weight(1f).padding(start = 6.dp)) {
            val retro = LocalRetroTheme.current
            Text(
                title.uppercase(androidx.compose.ui.platform.LocalConfiguration.current.locales[0]),
                fontFamily = if (retro) PixelFont else FontFamily.Default,
                fontWeight = if (retro) FontWeight.Normal else FontWeight.Bold,
                fontSize = 17.sp,
                lineHeight = 22.sp,
                color = retroOnSurface(),
                style = if (retro) TextStyle(shadow = Shadow(accent, Offset(4f, 4f), 0f)) else TextStyle(),
                maxLines = 1
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = accent,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
        actions()
        RetroClock(Modifier.padding(start = 4.dp))
    }
}

@Composable
fun RetroPanel(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(16.dp),
    content: @Composable () -> Unit
) {
    val retro = LocalRetroTheme.current
    Box(
        modifier
            .clip(shape)
            .background(
                if (retro) Color.Black.copy(alpha = 0.52f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
            .border(
                1.dp,
                if (retro) Color.White.copy(alpha = 0.1f) else MaterialTheme.colorScheme.outlineVariant,
                shape
            )
    ) { content() }
}
