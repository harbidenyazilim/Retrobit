package com.retrobit.app.ui.minigames

import android.graphics.Paint
import android.graphics.Typeface
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Flight
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.ViewAgenda
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.retrobit.app.R
import com.retrobit.app.data.ImportProgress
import com.retrobit.app.ui.components.GamepadHint
import com.retrobit.app.ui.components.GamepadHintBar
import com.retrobit.app.ui.components.HintBlue
import com.retrobit.app.ui.components.HintGreen
import com.retrobit.app.ui.components.LocalSafeArea
import com.retrobit.app.ui.components.RequestFocusOnWindowFocus
import com.retrobit.app.ui.components.RetroBackdrop
import com.retrobit.app.ui.components.focusLift
import com.retrobit.app.ui.components.retroOnSurface
import com.retrobit.app.ui.theme.TechFont
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.min
import kotlin.random.Random

private val BoardColor = Color(0xFF0B0B12)

private enum class GameId(
    val nameRes: Int,
    val descRes: Int,
    val controlsRes: Int,
    val icon: ImageVector,
    val accent: Color
) {
    SNAKE(
        R.string.arcade_snake_name,
        R.string.arcade_snake_desc,
        R.string.arcade_snake_controls,
        Icons.Rounded.SportsEsports,
        Color(0xFF4ADE80)
    ),
    G2048(
        R.string.arcade_2048_name,
        R.string.arcade_2048_desc,
        R.string.arcade_2048_controls,
        Icons.Rounded.GridView,
        Color(0xFFF5C518)
    ),
    BRICKS(
        R.string.arcade_bricks_name,
        R.string.arcade_bricks_desc,
        R.string.arcade_bricks_controls,
        Icons.Rounded.ViewAgenda,
        Color(0xFFF87171)
    ),
    FLAPPY(
        R.string.arcade_flappy_name,
        R.string.arcade_flappy_desc,
        R.string.arcade_flappy_controls,
        Icons.Rounded.Flight,
        Color(0xFF38BDF8)
    )
}

/**
 * The wait lounge: four small games drawn on a Canvas, so they cost no assets and carry no
 * third-party licences. B always drops back one level (game → hub → home).
 */
@Composable
fun MiniGamesScreen(
    isTr: Boolean,
    importing: Boolean,
    progress: ImportProgress?,
    onExit: () -> Unit
) {
    var active by rememberSaveable { mutableStateOf<String?>(null) }
    var lastIndex by rememberSaveable { mutableStateOf(0) }
    val bests = remember { mutableStateMapOf<String, Int>() }
    val game = GameId.entries.firstOrNull { it.name == active }

    BackHandler {
        if (game != null) active = null else onExit()
    }

    if (game == null) {
        ArcadeHub(
            importing = importing,
            progress = progress,
            bests = bests,
            initialFocus = lastIndex.coerceIn(0, GameId.entries.lastIndex),
            onPlay = { id ->
                lastIndex = id.ordinal
                active = id.name
            },
            onExit = onExit
        )
        return
    }

    val onBest: (Int) -> Unit = { score ->
        if (score > (bests[game.name] ?: 0)) bests[game.name] = score
    }
    val best = bests[game.name] ?: 0
    val back = { active = null }
    when (game) {
        GameId.SNAKE -> SnakeGame(best, importing, progress, onBest, back)
        GameId.G2048 -> TilesGame(best, importing, progress, onBest, back)
        GameId.BRICKS -> BricksGame(best, importing, progress, onBest, back)
        GameId.FLAPPY -> FlappyGame(best, importing, progress, onBest, back)
    }
}

@Composable
private fun ArcadeHub(
    importing: Boolean,
    progress: ImportProgress?,
    bests: Map<String, Int>,
    initialFocus: Int,
    onPlay: (GameId) -> Unit,
    onExit: () -> Unit
) {
    val games = GameId.entries
    val focuses = remember { List(games.size) { FocusRequester() } }
    val startFocus = remember { focuses[initialFocus] }
    var focusedIndex by remember { mutableStateOf(initialFocus) }
    LaunchedEffect(Unit) {
        delay(80)
        runCatching { startFocus.requestFocus() }
    }
    RequestFocusOnWindowFocus(startFocus)

    Box(Modifier.fillMaxSize()) {
        RetroBackdrop(null, scanlines = true, dim = 0.55f)
        Column(Modifier.fillMaxSize().padding(LocalSafeArea.current)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.arcade_title),
                        fontFamily = TechFont,
                        fontWeight = FontWeight.Black,
                        fontSize = 32.sp,
                        color = retroOnSurface()
                    )
                    Text(
                        stringResource(R.string.arcade_subtitle),
                        style = MaterialTheme.typography.labelLarge,
                        color = GameId.G2048.accent,
                        letterSpacing = 2.sp
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(if (importing) R.string.arcade_hint else R.string.arcade_hint_idle),
                        style = MaterialTheme.typography.bodySmall,
                        color = retroOnSurface(0.62f)
                    )
                }
                ImportNote(importing, progress, GameId.G2048.accent)
            }
            Spacer(Modifier.height(16.dp))
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Row(
                Modifier.fillMaxWidth().height(240.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                games.forEachIndexed { i, game ->
                    ArcadeCard(
                        game = game,
                        best = bests[game.name] ?: 0,
                        onPlay = { onPlay(game) },
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .focusRequester(focuses[i])
                            .onFocusChanged { if (it.isFocused) focusedIndex = i }
                            .onPreviewKeyEvent { event ->
                                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                                when (event.key) {
                                    Key.DirectionLeft -> {
                                        runCatching {
                                            focuses[(i + games.size - 1) % games.size].requestFocus()
                                        }
                                        true
                                    }
                                    Key.DirectionRight -> {
                                        runCatching { focuses[(i + 1) % games.size].requestFocus() }
                                        true
                                    }
                                    Key.DirectionUp, Key.DirectionDown -> true
                                    else -> false
                                }
                            }
                    )
                }
            }
            }
            Spacer(Modifier.height(10.dp))
            GamepadHintBar(
                hints = listOf(
                    GamepadHint("A", stringResource(R.string.arcade_start), HintGreen) { onPlay(games[focusedIndex]) },
                    GamepadHint("B", stringResource(R.string.arcade_back_hint), HintBlue, onExit)
                )
            )
        }
    }
}

@Composable
private fun ArcadeCard(
    game: GameId,
    best: Int,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interaction = remember { MutableInteractionSource() }
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(20.dp)
    Surface(
        modifier = modifier
            .focusLift(focused, game.accent, shape, lift = 1.04f, spread = 10.dp)
            .clip(shape)
            .onFocusChanged { focused = it.isFocused }
            .clickable(interactionSource = interaction, indication = null, onClick = onPlay),
        shape = shape,
        color = Color.White.copy(alpha = if (focused) 0.11f else 0.055f),
        border = BorderStroke(
            if (focused) 2.dp else 1.dp,
            if (focused) game.accent else Color.White.copy(alpha = 0.13f)
        )
    ) {
        Column(
            Modifier.fillMaxSize().padding(20.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Box(
                    Modifier
                        .size(58.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(game.accent.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        game.icon,
                        contentDescription = null,
                        tint = game.accent,
                        modifier = Modifier.size(30.dp)
                    )
                }
                Spacer(Modifier.height(18.dp))
                Text(
                    stringResource(game.nameRes),
                    fontFamily = TechFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 23.sp,
                    color = retroOnSurface()
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(game.descRes),
                    style = MaterialTheme.typography.bodyMedium,
                    color = retroOnSurface(0.72f),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Column {
                Text(
                    stringResource(game.controlsRes),
                    style = MaterialTheme.typography.labelMedium,
                    color = game.accent
                )
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.arcade_start),
                        style = MaterialTheme.typography.labelSmall,
                        color = retroOnSurface(0.72f)
                    )
                    Spacer(Modifier.weight(1f))
                    if (best > 0) {
                        Text(
                            "${stringResource(R.string.arcade_best)} $best",
                            fontFamily = TechFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = retroOnSurface(0.8f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ImportNote(importing: Boolean, progress: ImportProgress?, accent: Color) {
    if (!importing) return
    val scanning = progress == null || progress.scanning
    val fraction = if (scanning) null else progress?.fraction
    Column(horizontalAlignment = Alignment.End) {
        Text(
            if (fraction != null) {
                stringResource(R.string.arcade_importing, (fraction * 100).toInt())
            } else {
                stringResource(R.string.arcade_importing_scan)
            },
            style = MaterialTheme.typography.labelMedium,
            color = accent
        )
        if (fraction != null) {
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier.width(120.dp).padding(top = 6.dp)
            )
        }
    }
}

@Composable
private fun GameScaffold(
    game: GameId,
    score: Int,
    best: Int,
    gameOver: Boolean,
    newBest: Boolean,
    importing: Boolean,
    progress: ImportProgress?,
    onConfirm: () -> Unit,
    onDirection: (Key) -> Unit,
    onBack: () -> Unit,
    touch: TouchMode = TouchMode.Swipe,
    onAim: (Float) -> Unit = {},
    content: @Composable (Modifier) -> Unit
) {
    val currentConfirm by androidx.compose.runtime.rememberUpdatedState(onConfirm)
    val currentDirection by androidx.compose.runtime.rememberUpdatedState(onDirection)
    val currentAim by androidx.compose.runtime.rememberUpdatedState(onAim)
    val overState = androidx.compose.runtime.rememberUpdatedState(gameOver)
    val overSince = remember { androidx.compose.runtime.mutableLongStateOf(0L) }
    val sounds = com.retrobit.app.ui.components.LocalUiSounds.current
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    androidx.compose.runtime.DisposableEffect(sounds) {
        val endGameplay = sounds.beginGameplay()
        onDispose { endGameplay() }
    }
    LaunchedEffect(gameOver) {
        if (gameOver) {
            overSince.longValue = android.os.SystemClock.uptimeMillis()
            delay(65) // Leave a little room after the collision transient.
            sounds.play(GameSound.GameOver)
        }
    }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(40)
        runCatching { focus.requestFocus() }
    }
    RequestFocusOnWindowFocus(focus)
    val name = stringResource(game.nameRes)

    Box(Modifier.fillMaxSize()) {
        RetroBackdrop(null, scanlines = true, dim = 0.55f)
        Column(Modifier.fillMaxSize().padding(LocalSafeArea.current)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        name,
                        fontFamily = TechFont,
                        fontWeight = FontWeight.Black,
                        fontSize = 30.sp,
                        color = retroOnSurface()
                    )
                    Text(
                        stringResource(game.controlsRes),
                        style = MaterialTheme.typography.labelMedium,
                        color = game.accent
                    )
                }
                ImportNote(importing, progress, game.accent)
                Spacer(Modifier.width(22.dp))
                ScoreBlock(stringResource(R.string.arcade_score), score, game.accent)
                Spacer(Modifier.width(22.dp))
                ScoreBlock(stringResource(R.string.arcade_best), best, retroOnSurface(0.85f))
            }
            Spacer(Modifier.height(10.dp))
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .focusRequester(focus)
                    .onPreviewKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        if (lifecycle.currentState != androidx.lifecycle.Lifecycle.State.RESUMED) {
                            return@onPreviewKeyEvent true
                        }
                        if (event.nativeKeyEvent.repeatCount > 0 &&
                            (game != GameId.BRICKS || event.key !in ARROW_KEYS)
                        ) {
                            return@onPreviewKeyEvent event.key in ARROW_KEYS
                        }
                        when (event.key) {
                            Key.DirectionLeft, Key.DirectionRight,
                            Key.DirectionUp, Key.DirectionDown -> {
                                onDirection(event.key)
                                true
                            }
                            Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                                onConfirm()
                                true
                            }
                            else -> false
                        }
                    }
                    .focusable(),
                contentAlignment = Alignment.Center
            ) {
                BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    val aspect = 16f / 9f
                    val boardWidth = minOf(maxWidth, maxHeight * aspect)
                    val boardHeight = boardWidth / aspect
                    val boardShape = RoundedCornerShape(18.dp)
                    Box(
                        Modifier
                            .size(boardWidth, boardHeight)
                            .pointerInput(touch) {
                                touchControls(
                                    touch,
                                    onTap = {
                                        // A finger still on the glass at the crash must not restart at once.
                                        val settled = android.os.SystemClock.uptimeMillis() - overSince.longValue > 450
                                        if (!overState.value || settled) currentConfirm()
                                    },
                                    onSwipe = { if (!overState.value) currentDirection(it) },
                                    onAim = { if (!overState.value) currentAim(it) }
                                )
                            }
                    ) {
                    content(
                        Modifier
                            .fillMaxSize()
                            .clip(boardShape)
                            .background(BoardColor)
                            .border(1.dp, Color.White.copy(alpha = 0.10f), boardShape)
                    )
                    if (gameOver) {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .clip(boardShape)
                                .background(Color.Black.copy(alpha = 0.58f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Surface(
                                shape = RoundedCornerShape(18.dp),
                                color = Color(0xFF14141D),
                                border = BorderStroke(1.dp, game.accent.copy(alpha = 0.5f))
                            ) {
                                Column(
                                    Modifier.padding(horizontal = 32.dp, vertical = 24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        stringResource(R.string.arcade_game_over),
                                        fontFamily = TechFont,
                                        fontWeight = FontWeight.Black,
                                        fontSize = 28.sp,
                                        color = game.accent
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        "${stringResource(R.string.arcade_score)}: $score",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = retroOnSurface()
                                    )
                                    if (newBest) {
                                        Text(
                                            stringResource(R.string.arcade_new_best),
                                            style = MaterialTheme.typography.labelLarge,
                                            color = HintGreen
                                        )
                                    }
                                    Spacer(Modifier.height(14.dp))
                                    Text(
                                        stringResource(R.string.arcade_restart),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = retroOnSurface(0.8f)
                                    )
                                }
                            }
                        }
                    }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            val hints = buildList {
                if (gameOver) {
                    add(GamepadHint("A", stringResource(R.string.arcade_restart), HintGreen, onConfirm))
                }
                add(GamepadHint("B", stringResource(R.string.arcade_back_hint), HintBlue, onBack))
            }
            GamepadHintBar(hints = hints)
        }
    }
}

private val ARROW_KEYS = setOf(Key.DirectionLeft, Key.DirectionRight, Key.DirectionUp, Key.DirectionDown)

internal enum class TouchMode {
    /** One direction per swipe, a short tap confirms. */
    Swipe,
    /** Every stretch of a drag can turn again without lifting the finger. */
    Steer,
    /** The finger's horizontal position steers; a short tap confirms. */
    Aim,
    /** Confirms the moment the finger lands. */
    Press
}

private suspend fun PointerInputScope.touchControls(
    mode: TouchMode,
    onTap: () -> Unit,
    onSwipe: (Key) -> Unit,
    onAim: (Float) -> Unit
) {
    val swipeDistance = 28.dp.toPx()
    val tapSlop = viewConfiguration.touchSlop
    awaitEachGesture {
        val down = awaitFirstDown()
        when (mode) {
            TouchMode.Press -> onTap()
            TouchMode.Aim -> onAim(down.position.x / size.width)
            else -> Unit
        }
        var anchor = down.position
        var travelled = 0f
        var swiped = false
        while (true) {
            val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
            if (!change.pressed) break
            travelled = maxOf(travelled, (change.position - down.position).getDistance())
            if (mode == TouchMode.Aim) {
                onAim(change.position.x / size.width)
                change.consume()
                continue
            }
            if (mode == TouchMode.Press || swiped && mode == TouchMode.Swipe) continue
            val delta = change.position - anchor
            if (delta.getDistance() >= swipeDistance) {
                onSwipe(
                    if (abs(delta.x) > abs(delta.y)) {
                        if (delta.x > 0) Key.DirectionRight else Key.DirectionLeft
                    } else {
                        if (delta.y > 0) Key.DirectionDown else Key.DirectionUp
                    }
                )
                swiped = true
                anchor = change.position
                change.consume()
            }
        }
        if (mode != TouchMode.Press && !swiped && travelled <= tapSlop) onTap()
    }
}

@Composable
private fun ScoreBlock(label: String, value: Int, color: Color) {
    Column(horizontalAlignment = Alignment.End) {
        Text(
            label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = retroOnSurface(0.55f),
            letterSpacing = 1.sp
        )
        Text(
            value.toString(),
            fontFamily = TechFont,
            fontWeight = FontWeight.Black,
            fontSize = 26.sp,
            color = color
        )
    }
}

/* ---------------------------------------------------------------------------------------------
 * Snake
 * ------------------------------------------------------------------------------------------- */

private const val SNAKE_COLS = 24
private const val SNAKE_ROWS = 15
private const val SNAKE_TICK = 0.11f

internal data class SnakeState(
    val body: List<Int> = listOf(75, 74, 73),
    val pending: Int = 1,
    val food: Int = 5 * SNAKE_COLS + 17,
    val score: Int = 0,
    val alive: Boolean = true
) {
    fun turn(next: Int): SnakeState {
        if (!alive || body.size > 1 && next == -(body[0] - body[1])) return this
        return copy(pending = next)
    }

    fun tick(): SnakeState {
        if (!alive) return this
        val step = pending
        val head = body.first()
        val col = head % SNAKE_COLS + when (step) {
            -1 -> -1
            1 -> 1
            else -> 0
        }
        val row = head / SNAKE_COLS + when (step) {
            -SNAKE_COLS -> -1
            SNAKE_COLS -> 1
            else -> 0
        }
        if (col !in 0 until SNAKE_COLS || row !in 0 until SNAKE_ROWS) return copy(alive = false)
        val next = row * SNAKE_COLS + col
        // The tail frees up in the same tick, so chasing it is legal.
        val blocking = if (next == food) body else body.dropLast(1)
        if (next in blocking) return copy(alive = false)
        val grown = listOf(next) + body
        return if (next == food) {
            val free = (0 until SNAKE_COLS * SNAKE_ROWS).filter { it !in grown }
            copy(
                body = grown,
                pending = step,
                food = free.randomOrNull() ?: -1,
                score = score + 1
            )
        } else {
            copy(body = grown.dropLast(1), pending = step)
        }
    }
}

@Composable
private fun SnakeGame(
    best: Int,
    importing: Boolean,
    progress: ImportProgress?,
    onBest: (Int) -> Unit,
    onBack: () -> Unit
) {
    var state by remember { mutableStateOf(SnakeState()) }
    var newBest by remember { mutableStateOf(false) }
    val reducedMotion = com.retrobit.app.ui.components.LocalReducedMotion.current
    val sounds = com.retrobit.app.ui.components.LocalUiSounds.current
    val effects = remember { MiniEffects() }
    var previousBody by remember { mutableStateOf(state.body) }
    val tickPhase = remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    GameFrames(state.alive || (!reducedMotion && effects.active)) { dt ->
        if (reducedMotion) effects.clear() else effects.step(dt)
        if (state.alive) {
            tickPhase.floatValue += dt
            if (tickPhase.floatValue >= SNAKE_TICK) {
                tickPhase.floatValue -= SNAKE_TICK
                val before = state
                val next = before.tick()
                previousBody = before.body
                state = next
                if (next.score > before.score) {
                    sounds.play(GameSound.Score)
                    if (!reducedMotion) {
                        effects.burst(
                            (before.food % SNAKE_COLS + 0.5f) / SNAKE_COLS,
                            (before.food / SNAKE_COLS + 0.5f) / SNAKE_ROWS,
                            Color(0xFFFF6B6B)
                        )
                    }
                } else if (!next.alive) {
                    previousBody = next.body
                    sounds.play(GameSound.Collision)
                }
            }
        }
    }
    LaunchedEffect(state.alive) {
        if (!state.alive) {
            newBest = state.score > best
            onBest(state.score)
        } else {
            newBest = false
        }
    }

    GameScaffold(
        game = GameId.SNAKE,
        score = state.score,
        best = maxOf(best, state.score),
        gameOver = !state.alive,
        newBest = newBest,
        importing = importing,
        progress = progress,
        onConfirm = {
            if (!state.alive) {
                state = SnakeState()
                previousBody = state.body
                tickPhase.floatValue = 0f
                effects.clear()
                sounds.play(GameSound.Confirm)
            }
        },
        onDirection = { key ->
            val turn = when (key) {
                Key.DirectionLeft -> -1
                Key.DirectionRight -> 1
                Key.DirectionUp -> -SNAKE_COLS
                Key.DirectionDown -> SNAKE_COLS
                else -> 0
            }
            if (turn != 0) {
                val next = state.turn(turn)
                if (next != state) sounds.play(GameSound.Move)
                state = next
            }
        },
        onBack = onBack,
        touch = TouchMode.Steer
    ) { modifier ->
        Canvas(modifier) {
            val cw = size.width / SNAKE_COLS
            val ch = size.height / SNAKE_ROWS
            for (row in 0 until SNAKE_ROWS) {
                for (col in 0 until SNAKE_COLS) {
                    if ((row + col) % 2 == 0) {
                        drawRect(
                            Color.White.copy(alpha = 0.018f),
                            Offset(col * cw, row * ch),
                            Size(cw, ch)
                        )
                    }
                }
            }
            if (state.food >= 0) {
                val fCol = state.food % SNAKE_COLS
                val fRow = state.food / SNAKE_COLS
                drawCircle(
                    Color(0xFFFF6B6B),
                    radius = min(cw, ch) * 0.31f * if (reducedMotion) 1f else
                        (1f + 0.12f * kotlin.math.sin(effects.time * 5f)),
                    center = Offset(fCol * cw + cw / 2, fRow * ch + ch / 2)
                )
            }
            if (!reducedMotion) effects.draw(this)
            val blend = if (reducedMotion) 1f else (tickPhase.floatValue / SNAKE_TICK).coerceIn(0f, 1f)
            state.body.forEachIndexed { i, cell ->
                val previous = previousBody.getOrElse(i) { previousBody.last() }
                val col = previous % SNAKE_COLS + (cell % SNAKE_COLS - previous % SNAKE_COLS) * blend
                val row = previous / SNAKE_COLS + (cell / SNAKE_COLS - previous / SNAKE_COLS) * blend
                val fade = 1f - 0.45f * (i.toFloat() / state.body.size)
                drawRoundRect(
                    if (i == 0) GameId.SNAKE.accent else GameId.SNAKE.accent.copy(alpha = 0.5f + 0.5f * fade),
                    Offset(col * cw + cw * 0.07f, row * ch + ch * 0.07f),
                    Size(cw * 0.86f, ch * 0.86f),
                    CornerRadius(cw * 0.24f)
                )
            }
        }
    }
}

/* ---------------------------------------------------------------------------------------------
 * 2048
 * ------------------------------------------------------------------------------------------- */

private data class TileSlide(val from: Int, val to: Int, val value: Int)

private data class Tiles2048(
    val cells: IntArray,
    val score: Int = 0,
    val over: Boolean = false,
    val slides: List<TileSlide> = emptyList(),
    val merges: Set<Int> = emptySet(),
    val spawned: Int = -1
) {
    companion object {
        fun newGame(): Tiles2048 {
            var cells = IntArray(16)
            cells = spawn(cells)
            cells = spawn(cells)
            return Tiles2048(cells)
        }

        private fun spawn(cells: IntArray): IntArray {
            val free = cells.indices.filter { cells[it] == 0 }
            if (free.isEmpty()) return cells
            val next = cells.copyOf()
            next[free.random()] = if (Random.nextInt(10) == 0) 4 else 2
            return next
        }

        private fun isOver(cells: IntArray): Boolean {
            for (i in cells.indices) {
                if (cells[i] == 0) return false
                val row = i / 4
                val col = i % 4
                if (col < 3 && cells[i] == cells[i + 1]) return false
                if (row < 3 && cells[i] == cells[i + 4]) return false
            }
            return true
        }
    }

    fun move(dx: Int, dy: Int): Tiles2048 {
        if (over || dx == 0 && dy == 0) return this
        val next = IntArray(16)
        val travel = ArrayList<TileSlide>(16)
        val mergedCells = HashSet<Int>(8)
        var gained = 0
        for (line in 0 until 4) {
            val order = IntArray(4) { position ->
                val p = if (dx < 0 || dx == 0 && dy < 0) position else 3 - position
                if (dx != 0) line * 4 + p else p * 4 + line
            }
            val occupied = order.filter { cells[it] != 0 }
            var source = 0
            var destination = 0
            while (source < occupied.size) {
                val from = occupied[source]
                val to = order[destination++]
                val value = cells[from]
                travel.add(TileSlide(from, to, value))
                if (source + 1 < occupied.size && value == cells[occupied[source + 1]]) {
                    travel.add(TileSlide(occupied[source + 1], to, value))
                    next[to] = value * 2
                    gained += value * 2
                    mergedCells.add(to)
                    source += 2 // Each input tile merges once; never chain a newly merged tile.
                } else {
                    next[to] = value
                    source++
                }
            }
        }
        if (next.contentEquals(cells)) return this
        val spawnedCells = spawn(next)
        val spawnIndex = next.indices.firstOrNull { next[it] == 0 && spawnedCells[it] != 0 } ?: -1
        return Tiles2048(spawnedCells, score + gained, isOver(spawnedCells), travel, mergedCells, spawnIndex)
    }
}

private fun tileColor(value: Int): Color = when (value) {
    2 -> Color(0xFFEEE4DA)
    4 -> Color(0xFFEDE0C8)
    8 -> Color(0xFFF2B179)
    16 -> Color(0xFFF59563)
    32 -> Color(0xFFF67C5F)
    64 -> Color(0xFFF65E3B)
    128 -> Color(0xFFEDCF72)
    256 -> Color(0xFFEDCC61)
    512 -> Color(0xFFEDC850)
    1024 -> Color(0xFFEDC53F)
    2048 -> Color(0xFFEDC22E)
    else -> Color(0xFF3C3A32)
}

private fun tileInk(value: Int): Color =
    if (value <= 4) Color(0xFF776E65) else Color(0xFFF9F6F2)

@Composable
private fun TilesGame(
    best: Int,
    importing: Boolean,
    progress: ImportProgress?,
    onBest: (Int) -> Unit,
    onBack: () -> Unit
) {
    var state by remember { mutableStateOf(Tiles2048.newGame()) }
    var newBest by remember { mutableStateOf(false) }
    val reducedMotion = com.retrobit.app.ui.components.LocalReducedMotion.current
    val sounds = com.retrobit.app.ui.components.LocalUiSounds.current
    val motion = remember { androidx.compose.runtime.mutableFloatStateOf(0.24f) }
    var queued by remember { mutableStateOf<Key?>(null) }
    fun move(key: Key) {
        val moved = when (key) {
            Key.DirectionLeft -> state.move(-1, 0)
            Key.DirectionRight -> state.move(1, 0)
            Key.DirectionUp -> state.move(0, -1)
            Key.DirectionDown -> state.move(0, 1)
            else -> state
        }
        if (moved !== state) {
            sounds.play(if (moved.score > state.score) GameSound.Score else GameSound.Move)
            state = moved
            motion.floatValue = if (reducedMotion) 0.24f else 0f
        }
    }
    GameFrames(!reducedMotion && motion.floatValue < 0.24f) { dt ->
        motion.floatValue = (motion.floatValue + dt).coerceAtMost(0.24f)
        if (motion.floatValue >= 0.24f) {
            queued?.let { key -> queued = null; move(key) }
        }
    }
    LaunchedEffect(reducedMotion) {
        if (reducedMotion) {
            motion.floatValue = 0.24f
            queued?.let { key -> queued = null; move(key) }
        }
    }
    LaunchedEffect(state.over) {
        if (state.over) {
            newBest = state.score > best
            onBest(state.score)
        } else {
            newBest = false
        }
    }
    val paint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD
        }
    }

    GameScaffold(
        game = GameId.G2048,
        score = state.score,
        best = maxOf(best, state.score),
        gameOver = state.over && (reducedMotion || motion.floatValue >= 0.24f),
        newBest = newBest,
        importing = importing,
        progress = progress,
        onConfirm = {
            if (state.over) {
                state = Tiles2048.newGame()
                queued = null
                motion.floatValue = 0.24f
                sounds.play(GameSound.Confirm)
            }
        },
        onDirection = { key ->
            // One buffered direction prevents overlapping slides without an unbounded input queue.
            if (!reducedMotion && motion.floatValue < 0.24f) queued = key else move(key)
        },
        onBack = onBack
    ) { modifier ->
        Canvas(modifier.padding(10.dp)) {
            val side = min(size.width, size.height)
            val gap = side * 0.022f
            val tile = (side - gap * 5f) / 4f
            val left = (size.width - side) / 2f
            val top = (size.height - side) / 2f
            for (i in 0 until 16) {
                drawRoundRect(
                    Color.White.copy(alpha = 0.055f),
                    Offset(left + gap + (i % 4) * (tile + gap), top + gap + (i / 4) * (tile + gap)),
                    Size(tile, tile), CornerRadius(tile * 0.14f)
                )
            }
            fun drawTile(value: Int, col: Float, row: Float, scale: Float = 1f) {
                val x = left + gap + col * (tile + gap)
                val y = top + gap + row * (tile + gap)
                val inset = tile * (1f - scale) / 2f
                drawRoundRect(tileColor(value), Offset(x + inset, y + inset),
                    Size(tile * scale, tile * scale), CornerRadius(tile * 0.14f * scale))
                paint.textSize = tile * scale * when {
                    value < 100 -> 0.40f
                    value < 1000 -> 0.32f
                    else -> 0.25f
                }
                paint.color = tileInk(value).toArgb()
                drawContext.canvas.nativeCanvas.drawText(value.toString(), x + tile / 2,
                    y + tile / 2 - (paint.ascent() + paint.descent()) / 2, paint)
            }
            val elapsed = if (reducedMotion) 0.24f else motion.floatValue
            if (elapsed < 0.105f && state.slides.isNotEmpty()) {
                val t = (elapsed / 0.105f).coerceIn(0f, 1f)
                val ease = 1f - (1f - t) * (1f - t) * (1f - t)
                // Both original tiles converge on their merge target before showing the new value.
                state.slides.forEach { slide ->
                    drawTile(slide.value,
                        slide.from % 4 + (slide.to % 4 - slide.from % 4) * ease,
                        slide.from / 4 + (slide.to / 4 - slide.from / 4) * ease)
                }
            } else {
                val pulse = ((elapsed - 0.105f) / 0.135f).coerceIn(0f, 1f)
                for (i in state.cells.indices) {
                    val value = state.cells[i]
                    if (value == 0) continue
                    val scale = when {
                        reducedMotion -> 1f
                        i == state.spawned -> 0.72f + 0.28f * pulse + 0.08f * kotlin.math.sin(pulse * kotlin.math.PI).toFloat()
                        i in state.merges -> 1f + 0.15f * kotlin.math.sin(pulse * kotlin.math.PI).toFloat()
                        else -> 1f
                    }
                    drawTile(value, (i % 4).toFloat(), (i / 4).toFloat(), scale)
                }
            }
        }
    }
}

/* ---------------------------------------------------------------------------------------------
 * Bricks
 * ------------------------------------------------------------------------------------------- */

private const val BRICK_COLS = 8
private const val BRICK_ROWS = 4
private const val BRICK_LEFT = 0.05f
private const val BRICK_RIGHT = 0.95f
private const val BRICK_GAP = 0.008f
private const val BRICK_TOP = 0.07f
private const val BRICK_HEIGHT = 0.042f
private const val PADDLE_Y = 0.88f
private const val PADDLE_HALF = 0.09f
private const val PADDLE_HEIGHT = 0.024f
private const val BALL_RADIUS = 0.017f

private fun brickWidth() =
    (BRICK_RIGHT - BRICK_LEFT - BRICK_GAP * (BRICK_COLS - 1)) / BRICK_COLS

private data class BricksState(
    val paddleX: Float = 0.5f,
    val paddleTarget: Float = 0.5f,
    val ballX: Float = 0.5f,
    val ballY: Float = PADDLE_Y - PADDLE_HEIGHT / 2 - BALL_RADIUS - 0.004f,
    val vx: Float = 0f,
    val vy: Float = 0f,
    val bricks: BooleanArray = BooleanArray(BRICK_COLS * BRICK_ROWS) { true },
    val score: Int = 0,
    val lives: Int = 3,
    val started: Boolean = false,
    val over: Boolean = false,
    val hitBricks: List<Int> = emptyList(),
    val collisionCount: Int = 0
) {
    fun movePaddle(delta: Float): BricksState {
        if (over) return this
        val x = (paddleTarget + delta).coerceIn(PADDLE_HALF + 0.01f, 1f - PADDLE_HALF - 0.01f)
        return copy(paddleTarget = x)
    }

    fun launch(): BricksState {
        if (over) return BricksState()
        if (started) return this
        return copy(
            started = true,
            vx = if (Random.nextBoolean()) 0.30f else -0.30f,
            vy = -0.52f,
            ballX = paddleX,
            ballY = PADDLE_Y - PADDLE_HEIGHT / 2 - BALL_RADIUS - 0.004f
        )
    }

    fun step(dt: Float, smoothPaddle: Boolean = true): BricksState {
        if (over) return this
        val time = dt.coerceIn(0f, 0.05f)
        val x = if (!smoothPaddle || abs(paddleTarget - paddleX) < 0.0005f) paddleTarget else
            paddleX + (paddleTarget - paddleX) * min(1f, time * 20f)
        var state = copy(paddleX = x, ballX = if (started) ballX else x, hitBricks = emptyList())
        if (!started) return state
        var left = time
        while (left > 0f && state.started && !state.over) {
            val slice = min(left, 0.008f)
            left -= slice
            state = state.integrate(slice)
        }
        return state
    }

    private fun integrate(dt: Float): BricksState {
        var x = ballX + vx * dt
        var y = ballY + vy * dt
        var dx = vx
        var dy = vy
        var collisions = collisionCount
        var hits = hitBricks
        if (x - BALL_RADIUS < 0f) {
            x = BALL_RADIUS
            dx = abs(dx)
            collisions++
        }
        if (x + BALL_RADIUS > 1f) {
            x = 1f - BALL_RADIUS
            dx = -abs(dx)
            collisions++
        }
        if (y - BALL_RADIUS < 0f) {
            y = BALL_RADIUS
            dy = abs(dy)
            collisions++
        }
        var rack = bricks
        var gained = 0
        val width = brickWidth()
        for (i in bricks.indices) {
            if (!bricks[i]) continue
            val col = i % BRICK_COLS
            val row = i / BRICK_COLS
            val bx = BRICK_LEFT + col * (width + BRICK_GAP)
            val by = BRICK_TOP + row * (BRICK_HEIGHT + BRICK_GAP)
            if (x + BALL_RADIUS > bx && x - BALL_RADIUS < bx + width &&
                y + BALL_RADIUS > by && y - BALL_RADIUS < by + BRICK_HEIGHT
            ) {
                if (rack === bricks) rack = bricks.copyOf()
                rack[i] = false
                gained += (BRICK_ROWS - row) * 10
                if (hits.size < 8) hits = hits + i
                collisions++
                dy = -dy
                y = if (dy < 0) by - BALL_RADIUS else by + BRICK_HEIGHT + BALL_RADIUS
                break
            }
        }
        if (dy > 0f && y + BALL_RADIUS >= PADDLE_Y &&
            y - BALL_RADIUS <= PADDLE_Y + PADDLE_HEIGHT &&
            abs(x - paddleX) <= PADDLE_HALF + BALL_RADIUS
        ) {
            y = PADDLE_Y - BALL_RADIUS
            dx = ((x - paddleX) / PADDLE_HALF).coerceIn(-1f, 1f) * 0.5f
            dy = -abs(dy)
            collisions++
        }
        var scoreNow = score + gained
        if (rack.all { !it }) {
            rack = BooleanArray(BRICK_COLS * BRICK_ROWS) { true }
            scoreNow += 50
        }
        if (y - BALL_RADIUS > 1f) {
            val leftLives = lives - 1
            return if (leftLives <= 0) {
                copy(ballX = x, ballY = y, bricks = rack, score = scoreNow, lives = 0, over = true,
                    hitBricks = hits, collisionCount = collisions)
            } else {
                copy(
                    ballX = paddleX,
                    ballY = PADDLE_Y - PADDLE_HEIGHT / 2 - BALL_RADIUS - 0.004f,
                    vx = 0f,
                    vy = 0f,
                    bricks = rack,
                    score = scoreNow,
                    lives = leftLives,
                    started = false,
                    hitBricks = hits,
                    collisionCount = collisions
                )
            }
        }
        return copy(ballX = x, ballY = y, vx = dx, vy = dy, bricks = rack, score = scoreNow,
            hitBricks = hits, collisionCount = collisions)
    }
}

private val BRICK_COLORS = listOf(
    Color(0xFFF87171),
    Color(0xFFFB923C),
    Color(0xFFFACC15),
    Color(0xFF4ADE80)
)

@Composable
private fun BricksGame(
    best: Int,
    importing: Boolean,
    progress: ImportProgress?,
    onBest: (Int) -> Unit,
    onBack: () -> Unit
) {
    var state by remember { mutableStateOf(BricksState()) }
    var newBest by remember { mutableStateOf(false) }
    val reducedMotion = com.retrobit.app.ui.components.LocalReducedMotion.current
    val sounds = com.retrobit.app.ui.components.LocalUiSounds.current
    val effects = remember { MiniEffects() }
    GameFrames(
        !state.over && (state.started || state.paddleTarget != state.paddleX) ||
            (!reducedMotion && effects.active)
    ) { dt ->
        if (reducedMotion) effects.clear() else effects.step(dt)
        val before = state
        val next = before.step(dt, smoothPaddle = !reducedMotion)
        state = next
        if (next.lives < before.lives) {
            effects.clear() // Never draw a trail connecting the lost ball to its respawn.
            sounds.play(GameSound.Collision)
        } else if (next.score > before.score) {
            sounds.play(GameSound.Score)
        } else if (next.collisionCount != before.collisionCount) {
            sounds.play(GameSound.Collision)
        }
        if (!reducedMotion && !before.over) {
            next.hitBricks.forEach { i ->
                val row = i / BRICK_COLS
                effects.burst(
                    BRICK_LEFT + (i % BRICK_COLS) * (brickWidth() + BRICK_GAP) + brickWidth() / 2f,
                    BRICK_TOP + row * (BRICK_HEIGHT + BRICK_GAP) + BRICK_HEIGHT / 2f,
                    BRICK_COLORS[row], count = 6
                )
            }
            if (next.started && !next.over) effects.trail(next.ballX, next.ballY)
        }
    }
    LaunchedEffect(state.over) {
        if (state.over) {
            newBest = state.score > best
            onBest(state.score)
        } else {
            newBest = false
        }
    }

    GameScaffold(
        game = GameId.BRICKS,
        score = state.score,
        best = maxOf(best, state.score),
        gameOver = state.over,
        newBest = newBest,
        importing = importing,
        progress = progress,
        onConfirm = {
            val next = state.launch()
            if (next !== state) {
                effects.clear()
                sounds.play(GameSound.Confirm)
                state = next
            }
        },
        onDirection = { key ->
            val delta = when (key) {
                Key.DirectionLeft -> -0.09f
                Key.DirectionRight -> 0.09f
                else -> 0f
            }
            if (delta != 0f) {
                val next = state.movePaddle(delta)
                if (next.paddleTarget != state.paddleTarget) sounds.play(GameSound.Move)
                state = next
            }
        },
        onBack = onBack,
        touch = TouchMode.Aim,
        onAim = { x -> state = state.movePaddle(x - state.paddleTarget) }
    ) { modifier ->
        Canvas(modifier) {
            val width = brickWidth()
            for (i in state.bricks.indices) {
                val col = i % BRICK_COLS
                val row = i / BRICK_COLS
                val x = BRICK_LEFT + col * (width + BRICK_GAP)
                val y = BRICK_TOP + row * (BRICK_HEIGHT + BRICK_GAP)
                val color = BRICK_COLORS[row % BRICK_COLORS.size]
                drawRoundRect(
                    if (state.bricks[i]) color else color.copy(alpha = 0.12f),
                    Offset(x * size.width, y * size.height),
                    Size(width * size.width, BRICK_HEIGHT * size.height),
                    CornerRadius(size.minDimension * 0.008f)
                )
            }
            // Lives as small pips in the bottom-left corner.
            repeat(3) { i ->
                drawCircle(
                    if (i < state.lives) Color.White.copy(alpha = 0.75f) else Color.White.copy(alpha = 0.15f),
                    radius = size.minDimension * 0.012f,
                    center = Offset(size.width * 0.035f + i * size.minDimension * 0.045f, size.height * 0.955f)
                )
            }
            val paddleLeft = (state.paddleX - PADDLE_HALF) * size.width
            drawRoundRect(
                Color(0xFFE2E8F0),
                Offset(paddleLeft, PADDLE_Y * size.height),
                Size(PADDLE_HALF * 2 * size.width, PADDLE_HEIGHT * size.height),
                CornerRadius(size.minDimension * 0.012f)
            )
            if (!reducedMotion) effects.draw(this, GameId.BRICKS.accent, BALL_RADIUS)
            drawCircle(
                GameId.BRICKS.accent,
                radius = BALL_RADIUS * size.width,
                center = Offset(state.ballX * size.width, state.ballY * size.height)
            )
        }
    }
}

/* ---------------------------------------------------------------------------------------------
 * Flappy
 * ------------------------------------------------------------------------------------------- */

private const val BIRD_X = 0.26f
private const val BIRD_RADIUS = 0.030f
private const val FLAPPY_GRAVITY = 1.85f
private const val FLAPPY_IMPULSE = -0.62f
private const val PIPE_SPEED = 0.30f
private const val PIPE_WIDTH = 0.11f
private const val PIPE_GAP_HALF = 0.15f

internal data class Pipe(val x: Float, val gapY: Float, val scored: Boolean = false)

internal data class FlappyState(
    val birdY: Float = 0.46f,
    val vy: Float = 0f,
    val pipes: List<Pipe> = emptyList(),
    val score: Int = 0,
    val started: Boolean = false,
    val over: Boolean = false
) {
    fun flap(): FlappyState =
        if (over) FlappyState(started = true, vy = FLAPPY_IMPULSE) else copy(vy = FLAPPY_IMPULSE, started = true)

    fun step(dt: Float): FlappyState {
        if (over || !started) return this
        val velocity = (vy + FLAPPY_GRAVITY * dt).coerceAtMost(1.5f)
        val y = birdY + velocity * dt
        var gained = 0
        var moved = pipes.map { pipe ->
            val nextX = pipe.x - PIPE_SPEED * dt
            if (!pipe.scored && nextX + PIPE_WIDTH < BIRD_X) {
                gained++
                Pipe(nextX, pipe.gapY, scored = true)
            } else {
                pipe.copy(x = nextX)
            }
        }.filter { it.x > -0.25f }
        if (moved.isEmpty() || moved.last().x < 0.68f) {
            moved = moved + Pipe(1.06f, 0.29f + Random.nextFloat() * 0.42f)
        }
        val verticalRadius = BIRD_RADIUS * (16f / 9f)
        val hitTop = y - verticalRadius < 0f
        val hitBottom = y + verticalRadius > 1f
        val hitPipe = moved.any { pipe ->
            BIRD_X + BIRD_RADIUS > pipe.x &&
                BIRD_X - BIRD_RADIUS < pipe.x + PIPE_WIDTH &&
                (y - verticalRadius < pipe.gapY - PIPE_GAP_HALF || y + verticalRadius > pipe.gapY + PIPE_GAP_HALF)
        }
        return copy(
            birdY = y,
            vy = velocity,
            pipes = moved,
            score = score + gained,
            over = hitTop || hitBottom || hitPipe
        )
    }
}

@Composable
private fun FlappyGame(
    best: Int,
    importing: Boolean,
    progress: ImportProgress?,
    onBest: (Int) -> Unit,
    onBack: () -> Unit
) {
    var state by remember { mutableStateOf(FlappyState()) }
    var newBest by remember { mutableStateOf(false) }
    val reducedMotion = com.retrobit.app.ui.components.LocalReducedMotion.current
    val sounds = com.retrobit.app.ui.components.LocalUiSounds.current
    val effects = remember { MiniEffects() }
    val flapPhase = remember { androidx.compose.runtime.mutableFloatStateOf(0.22f) }
    val tilt = remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    fun flap() {
        if (state.over) {
            effects.clear()
            tilt.floatValue = 0f
        }
        sounds.play(if (state.over || !state.started) GameSound.Confirm else GameSound.Move)
        state = state.flap()
        flapPhase.floatValue = if (reducedMotion) 0.22f else 0f
    }
    GameFrames(state.started && !state.over || (!reducedMotion && effects.active)) { dt ->
        if (reducedMotion) {
            effects.clear()
            tilt.floatValue = 0f
            flapPhase.floatValue = 0.22f
        } else {
            effects.step(dt, driftX = PIPE_SPEED)
            flapPhase.floatValue = (flapPhase.floatValue + dt).coerceAtMost(0.22f)
            val target = (state.vy * 65f).coerceIn(-24f, 65f)
            tilt.floatValue += (target - tilt.floatValue) * min(1f, dt * 14f)
        }
        val before = state
        val next = before.step(dt)
        state = next
        if (next.over && !before.over) {
            sounds.play(GameSound.Collision)
            if (!reducedMotion) effects.burst(BIRD_X, next.birdY, Color(0xFFF87171))
        } else if (next.score > before.score) {
            sounds.play(GameSound.Score)
            if (!reducedMotion) effects.burst(BIRD_X + 0.08f, next.birdY, GameId.FLAPPY.accent, count = 10)
        }
        if (!reducedMotion && next.started && !next.over) effects.trail(BIRD_X - 0.02f, next.birdY)
    }
    LaunchedEffect(state.over) {
        if (state.over) {
            newBest = state.score > best
            onBest(state.score)
        } else {
            newBest = false
        }
    }

    GameScaffold(
        game = GameId.FLAPPY,
        score = state.score,
        best = maxOf(best, state.score),
        gameOver = state.over,
        newBest = newBest,
        importing = importing,
        progress = progress,
        onConfirm = ::flap,
        onDirection = { key -> if (key == Key.DirectionUp) flap() },
        onBack = onBack,
        touch = TouchMode.Press
    ) { modifier ->
        Canvas(modifier) {
            drawRect(
                brush = Brush.verticalGradient(
                    listOf(Color(0xFF0E2233), Color(0xFF13324A))
                )
            )
            val pipeWidth = PIPE_WIDTH * size.width
            state.pipes.forEach { pipe ->
                val x = pipe.x * size.width
                val gapTop = (pipe.gapY - PIPE_GAP_HALF) * size.height
                val gapBottom = (pipe.gapY + PIPE_GAP_HALF) * size.height
                val pipeColor = Color(0xFF3FA34D)
                drawRoundRect(
                    pipeColor,
                    Offset(x, 0f),
                    Size(pipeWidth, gapTop),
                    CornerRadius(size.minDimension * 0.012f)
                )
                drawRoundRect(
                    pipeColor,
                    Offset(x, gapBottom),
                    Size(pipeWidth, size.height - gapBottom),
                    CornerRadius(size.minDimension * 0.012f)
                )
                drawRect(
                    Color(0xFF2F7D3A),
                    Offset(x - pipeWidth * 0.06f, gapTop - size.height * 0.018f),
                    Size(pipeWidth * 1.12f, size.height * 0.018f)
                )
                drawRect(
                    Color(0xFF2F7D3A),
                    Offset(x - pipeWidth * 0.06f, gapBottom),
                    Size(pipeWidth * 1.12f, size.height * 0.018f)
                )
            }
            // Ground strip.
            drawRect(
                Color(0xFF1B2B1D),
                Offset(0f, size.height * 0.965f),
                Size(size.width, size.height * 0.035f)
            )
            val cx = BIRD_X * size.width
            val cy = state.birdY * size.height
            val radius = BIRD_RADIUS * size.width
            if (!reducedMotion) effects.draw(this, Color(0xFFFFD54F), BIRD_RADIUS * 0.65f)
            val squash = if (reducedMotion) 1f else
                1f + 0.18f * kotlin.math.sin((flapPhase.floatValue / 0.22f) * kotlin.math.PI).toFloat()
            flapTransform(cx, cy, if (reducedMotion) 0f else tilt.floatValue, squash) {
                val r = radius
                val wingLift = kotlin.math.sin((flapPhase.floatValue / 0.22f) * kotlin.math.PI).toFloat()
                // Tuft of head feathers.
                drawCircle(Color(0xFFFFC233), radius = r * 0.2f, center = Offset(cx - r * 0.12f, cy - r * 0.98f))
                drawCircle(Color(0xFFFFC233), radius = r * 0.15f, center = Offset(cx + r * 0.14f, cy - r * 1.02f))
                drawCircle(Color(0xFFFFD54F), radius = r, center = Offset(cx, cy))
                drawCircle(
                    Color(0xFFE0A100),
                    radius = r,
                    center = Offset(cx, cy),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(r * 0.09f)
                )
                drawOval(
                    Color(0xFFFFF4CC),
                    topLeft = Offset(cx - r * 0.55f, cy + r * 0.05f),
                    size = Size(r * 1.2f, r * 0.8f)
                )
                // Little wing that lifts on every flap.
                drawOval(
                    Color(0xFFFFB300),
                    topLeft = Offset(cx - r * 0.95f, cy - r * (0.05f + 0.35f * wingLift)),
                    size = Size(r * 0.75f, r * (0.5f - 0.12f * wingLift))
                )
                val eye = Offset(cx + r * 0.32f, cy - r * 0.22f)
                drawCircle(Color.White, radius = r * 0.36f, center = eye)
                drawCircle(Color(0xFF1F2937), radius = r * 0.22f, center = eye + Offset(r * 0.07f, r * 0.02f))
                drawCircle(Color.White, radius = r * 0.08f, center = eye + Offset(r * 0.13f, -r * 0.08f))
                drawCircle(Color(0xFFFF8FA3).copy(alpha = 0.75f), radius = r * 0.15f,
                    center = Offset(cx + r * 0.2f, cy + r * 0.3f))
                val beakTop = Path().apply {
                    moveTo(cx + r * 0.78f, cy - r * 0.02f)
                    quadraticTo(cx + r * 1.32f, cy + r * 0.04f, cx + r * 0.82f, cy + r * 0.22f)
                    close()
                }
                drawPath(beakTop, Color(0xFFFF9F1C))
                drawCircle(Color(0xFFFF9F1C), radius = r * 0.13f, center = Offset(cx + r * 0.86f, cy + r * 0.1f))
            }
            if (!state.started) {
                drawCircle(Color.White.copy(alpha = 0.08f), radius = size.minDimension * 0.14f, center = Offset(cx, cy))
            }
        }
    }
}
