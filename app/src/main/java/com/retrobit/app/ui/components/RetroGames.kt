package com.retrobit.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.retrobit.app.R
import com.retrobit.app.data.ConsoleCatalog
import com.retrobit.app.data.ConsoleInfo
import com.retrobit.app.data.LibraryGame
import com.retrobit.app.data.GameMetadata
import com.retrobit.app.ui.theme.TechFont
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

fun onAccent(accent: Color): Color = if (accent.luminance() > 0.45f) RetroInk else Color.White

/** Newest save-slot capture the player wrote for [gameId]; [refreshKey] re-reads it after a session. */
@Composable
fun rememberLocalSnap(gameId: String, refreshKey: Any?): File? {
    val context = LocalContext.current
    var snap by remember(gameId) { mutableStateOf<File?>(null) }
    LaunchedEffect(gameId, refreshKey) {
        snap = withContext(Dispatchers.IO) {
            File(context.filesDir, "saves")
                .listFiles { f ->
                    f.isFile && (f.name == "${gameId}_auto.png" ||
                        (f.name.startsWith("${gameId}_slot") && f.name.endsWith(".png")))
                }
                ?.maxByOrNull { it.lastModified() }
        }
    }
    return snap
}

/** Staggered slide-in for the first rows of a list; a negative delay draws the item in place. */
@Composable
fun Modifier.entryMotion(delayMs: Int): Modifier {
    if (delayMs < 0 || LocalReducedMotion.current) return this
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(delayMs.toLong())
        progress.animateTo(1f, tween(280, easing = FastOutSlowInEasing))
    }
    return graphicsLayer {
        alpha = progress.value
        translationX = (1f - progress.value) * 28.dp.toPx()
    }
}

/** Delay for the item at [index] while its screen is still opening, otherwise -1. */
@Composable
fun rememberEntryDelay(screenOpenedAt: Long, index: Int, firstVisible: () -> Int = { 0 }): Int = remember {
    val slot = index - androidx.compose.runtime.snapshots.Snapshot.withoutReadObservation(firstVisible)
    if (slot in 0..13 && android.os.SystemClock.uptimeMillis() - screenOpenedAt < 450) slot * 32 else -1
}

/** File extension, or the archive entry's when the ROM still sits inside a zip. */
fun LibraryGame.formatLabel(): String {
    val name = pendingEntry ?: fileName.ifBlank { filePath }
    return name.substringAfterLast('.', "").uppercase().take(5).ifBlank { "—" }
}

@Composable
fun playTimeLabel(seconds: Long): String {
    val totalMinutes = seconds / 60
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return when {
        seconds <= 0L -> "—"
        hours > 0 -> stringResource(R.string.stats_time_hm, hours.toInt(), minutes.toInt())
        minutes > 0 -> stringResource(R.string.stats_time_m, minutes.toInt())
        else -> stringResource(R.string.stats_time_lt)
    }
}

/**
 * One line of a gamelist. Short confirm plays, holding confirm opens the details; on a touch
 * screen the first tap selects the row so the preview can be read, the second one plays.
 */
@Composable
fun GameListRow(
    game: LibraryGame,
    index: Int,
    style: ConsoleStyle,
    favorite: Boolean,
    selected: Boolean,
    focusRequester: FocusRequester,
    onFocused: () -> Unit,
    onPlay: () -> Unit,
    onDetails: () -> Unit,
    onSelectTap: () -> Unit,
    modifier: Modifier = Modifier,
    entryDelayMs: Int = -1
) {
    var focused by remember { mutableStateOf(false) }
    val accent = style.accent
    val retro = LocalRetroTheme.current
    val textColor = if (focused) onAccent(accent) else retroOnSurface()
    val caret by animateFloatAsState(
        if (focused) 1f else 0f,
        tween(if (LocalReducedMotion.current) 0 else 180),
        label = "rowCaret"
    )
    Row(
        modifier
            .entryMotion(entryDelayMs)
            .then(
                if (LocalReducedMotion.current) Modifier
                else Modifier.focusLift(focused, accent, style.shape, lift = 1.012f, spread = 6.dp)
            )
            .fillMaxWidth()
            .height(44.dp)
            .clip(style.shape)
            .background(
                when {
                    focused -> Brush.horizontalGradient(listOf(accent, accent.copy(alpha = 0.78f)))
                    selected && retro -> Brush.horizontalGradient(listOf(accent.copy(alpha = 0.32f), Color.Transparent))
                    selected -> SolidColor(accent.copy(alpha = 0.14f))
                    retro -> Brush.horizontalGradient(listOf(Color.Black.copy(alpha = 0.32f), Color.Black.copy(alpha = 0.12f)))
                    else -> SolidColor(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                }
            )
            .focusRequester(focusRequester)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused()
            }
            .gamepadClickable(
                onClick = onPlay,
                onLongClick = onDetails,
                onTap = { if (selected) onPlay() else onSelectTap() }
            )
            .padding(start = 8.dp, end = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Pixel cursor in the style of ES-DE/XMB selectors; it slides in as the row takes focus.
        Box(
            Modifier
                .width(10.dp)
                .height(12.dp)
                .graphicsLayer {
                    alpha = caret
                    translationX = (caret - 1f) * 6.dp.toPx()
                }
                .drawBehind {
                    val path = Path().apply {
                        moveTo(0f, 0f)
                        lineTo(size.width * 0.7f, size.height / 2f)
                        lineTo(0f, size.height)
                        close()
                    }
                    drawPath(path, textColor)
                }
        )
        Text(
            (index + 1).toString().padStart(3, '0'),
            fontFamily = TechFont,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
            color = if (focused) textColor.copy(alpha = 0.7f) else accent.copy(alpha = 0.85f),
            modifier = Modifier.width(36.dp)
        )
        Text(
            game.title,
            style = MaterialTheme.typography.titleSmall,
            color = textColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).graphicsLayer { translationX = caret * 4.dp.toPx() }
        )
        if (game.isTurkishRelease) {
            TurkishFlag(12.dp, Modifier.padding(start = 8.dp))
        }
        if (favorite) {
            Icon(
                Icons.Rounded.Favorite,
                contentDescription = stringResource(R.string.favorites),
                tint = if (focused) textColor else Color(0xFFFF5CA8),
                modifier = Modifier.padding(start = 8.dp).size(15.dp)
            )
        }
    }
}

/** Box-art tile themed after its console; used by shelf mode, favourites and search. */
@Composable
fun RetroGameCard(
    game: LibraryGame,
    coverUrl: String?,
    favorite: Boolean,
    showConsole: Boolean,
    onPlay: () -> Unit,
    onDetails: () -> Unit,
    onFocused: () -> Unit = {},
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    entryDelayMs: Int = -1,
    framed: Boolean = LocalRetroTheme.current
) {
    val console = ConsoleCatalog.get(game.consoleId)
    val style = consoleStyle(console)
    val retro = LocalRetroTheme.current
    val coverShape = RoundedCornerShape(4.dp)
    var focused by remember { mutableStateOf(false) }
    Column(
        modifier
            .entryMotion(entryDelayMs)
            .then(
                if (LocalReducedMotion.current) Modifier
                else Modifier.focusLift(focused, style.accent, coverShape, lift = 1.025f, spread = 5.dp)
            )
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused()
            }
            .gamepadClickable(onClick = onPlay, onLongClick = onDetails)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(coverAspectRatio(console))
                .background(
                    if (retro) Color.Black.copy(alpha = 0.5f)
                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    coverShape
                )
                .then(if (focused) Modifier.border(2.dp, style.accent, coverShape) else Modifier)
        ) {
            GameCover(
                url = coverUrl,
                title = game.title,
                console = console,
                modifier = Modifier.fillMaxSize().padding(3.dp),
                corner = 4.dp,
                framed = framed && !focused
            )
        }
        if (showConsole) {
            Text(
                console?.shortName ?: game.consoleId.uppercase(),
                fontFamily = TechFont,
                fontWeight = FontWeight.Bold,
                fontSize = 9.sp,
                color = style.accent,
                modifier = Modifier.padding(top = 5.dp, start = 2.dp)
            )
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 7.dp, start = 2.dp, end = 2.dp, bottom = 4.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                game.title,
                style = MaterialTheme.typography.labelLarge,
                color = retroOnSurface(if (focused) 1f else 0.82f),
                minLines = 2,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (game.isTurkishRelease) {
                TurkishFlag(11.dp, Modifier.padding(top = 3.dp))
            }
            if (favorite) {
                Icon(
                    Icons.Rounded.Favorite,
                    contentDescription = stringResource(R.string.favorites),
                    tint = Color(0xFFFF5CA8),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

/** In-game capture inside a tube-shaped bezel; falls back to the system mark when none exists. */
@Composable
fun CrtSnap(
    snapUrl: Any?,
    console: ConsoleInfo?,
    scanlines: Boolean,
    modifier: Modifier = Modifier
) {
    val style = consoleStyle(console)
    val retro = LocalRetroTheme.current
    val shape = RoundedCornerShape(if (retro) 12.dp else 8.dp)
    val imageRatio = 4f / 3f
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
    val imageWidth = minOf(maxWidth, maxHeight * imageRatio)
    Box(
        Modifier
            .width(imageWidth)
            .height(imageWidth / imageRatio)
            .clip(shape)
            .background(if (retro) Color.Black else MaterialTheme.colorScheme.surfaceVariant)
            .border(
                1.dp,
                if (retro) style.accent.copy(alpha = 0.75f) else MaterialTheme.colorScheme.outlineVariant,
                shape
            )
    ) {
        Column(
            Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ConsoleGlyph(
                console?.glyph,
                Modifier.size(34.dp),
                tint = if (retro) style.accent.copy(alpha = 0.6f)
                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
            Text(
                stringResource(R.string.no_snap),
                style = MaterialTheme.typography.labelSmall,
                color = retroOnSurface(0.45f)
            )
        }
        if (snapUrl != null) {
            // One short image fade, no second crossfade or continuously zooming screenshot.
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(snapUrl)
                    .crossfade(if (LocalReducedMotion.current) 0 else 100)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
        }
        if (retro) {
            Box(Modifier.fillMaxSize().crtOverlay(scanlines))
            Box(
                Modifier.fillMaxSize().background(
                    Brush.linearGradient(
                        0f to Color.White.copy(alpha = 0.08f),
                        0.35f to Color.Transparent,
                        1f to Color.Transparent
                    )
                )
            )
        }
    }
    }
}

@Composable
fun genreLabel(genre: String?): String {
    val codes = remember(genre) { GenreLabels.resolve(genre) }
    return codes.map { code ->
        stringResource(
            when (code) {
                "platform" -> R.string.game_genre_platform
                "adventure" -> R.string.game_genre_adventure
                "rpg" -> R.string.game_genre_rpg
                "racing" -> R.string.game_genre_racing
                "fighting" -> R.string.game_genre_fighting
                "shooter" -> R.string.game_genre_shooter
                "puzzle" -> R.string.game_genre_puzzle
                "strategy" -> R.string.game_genre_strategy
                "sim" -> R.string.game_genre_sim
                "run" -> R.string.game_genre_run
                "beat" -> R.string.game_genre_beat
                "action" -> R.string.game_genre_action
                "horror" -> R.string.game_genre_horror
                "rhythm" -> R.string.game_genre_rhythm
                "arcade" -> R.string.game_genre_arcade
                "sports" -> R.string.game_genre_sports
                "action_adventure" -> R.string.game_genre_action_adventure
                "jrpg" -> R.string.game_genre_jrpg
                "shoot_em_up" -> R.string.game_genre_shoot_em_up
                else -> error("Unmapped canonical genre: $code")
            }
        )
    }.joinToString(" / ").ifEmpty { "—" }
}

/** Stable slots: missing or unrecognised metadata is a dash, never an inferred value. */
@Composable
fun GameMetadataCells(metadata: GameMetadata?, accent: Color, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().height(50.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        MetaCell(
            stringResource(R.string.game_meta_genre),
            genreLabel(metadata?.genre),
            accent,
            Modifier.weight(1f).fillMaxHeight()
        )
        MetaCell(
            stringResource(R.string.game_meta_year),
            metadata?.year?.toString() ?: "—",
            accent,
            Modifier.weight(1f).fillMaxHeight()
        )
    }
}

@Composable
fun MetaCell(label: String, value: String, accent: Color, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (LocalRetroTheme.current) Color.Black.copy(alpha = 0.42f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
            .padding(horizontal = 10.dp, vertical = 7.dp)
    ) {
        Text(
            label.uppercase(LocalConfiguration.current.locales[0]),
            style = MaterialTheme.typography.labelSmall,
            fontSize = 9.sp,
            letterSpacing = 1.sp,
            color = accent,
            maxLines = 1
        )
        Text(
            value,
            style = MaterialTheme.typography.labelLarge,
            color = retroOnSurface(),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
