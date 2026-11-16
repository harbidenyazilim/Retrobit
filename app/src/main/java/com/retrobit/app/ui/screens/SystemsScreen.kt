package com.retrobit.app.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.retrobit.app.AppViewModel
import com.retrobit.app.R
import com.retrobit.app.data.ConsoleCatalog
import com.retrobit.app.data.ConsoleInfo
import com.retrobit.app.data.ImportProgress
import com.retrobit.app.ui.components.BackdropImage
import com.retrobit.app.ui.components.ConsoleWordmark
import com.retrobit.app.ui.components.GamepadHint
import com.retrobit.app.ui.components.GamepadHintBar
import com.retrobit.app.ui.components.HintBlue
import com.retrobit.app.ui.components.HintGreen
import com.retrobit.app.ui.components.HintYellow
import com.retrobit.app.ui.components.LocalRetroTheme
import com.retrobit.app.ui.components.LocalReducedMotion
import com.retrobit.app.ui.components.LocalSafeArea
import com.retrobit.app.ui.components.RequestFocusOnWindowFocus
import com.retrobit.app.ui.components.RetroBackdrop
import com.retrobit.app.ui.components.carouselDirection
import com.retrobit.app.ui.components.RetroClock
import com.retrobit.app.ui.components.RetroNavButton
import com.retrobit.app.ui.components.RetroPanel
import com.retrobit.app.ui.components.RetrobitMark
import com.retrobit.app.ui.components.consoleStyle
import com.retrobit.app.ui.components.focusGlow
import com.retrobit.app.ui.components.focusLift
import com.retrobit.app.ui.components.onAccent
import com.retrobit.app.ui.components.retroOnSurface
import com.retrobit.app.ui.components.retroShortcuts
import com.retrobit.app.ui.theme.TechFont
import kotlinx.coroutines.delay
import kotlin.math.abs

@Composable
fun SystemsScreen(
    vm: AppViewModel,
    isTr: Boolean,
    onOpenSystem: (String) -> Unit,
    onFavorites: () -> Unit,
    onSearch: () -> Unit,
    onSettings: () -> Unit,
    onArcade: () -> Unit
) {
    val library by vm.library.collectAsStateWithLifecycle()
    val favorites by vm.favorites.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val importing by vm.importing.collectAsStateWithLifecycle()
    val progress by vm.importProgress.collectAsStateWithLifecycle()
    val safe = LocalSafeArea.current
    val reducedMotion = LocalReducedMotion.current

    val counts = remember(library, importing) {
        if (importing) emptyMap() else library.groupingBy { it.consoleId }.eachCount()
    }
    val systems = remember(counts, settings.consoleSort, settings.consoleCustomOrder, settings.hideEmptyConsoles, isTr) {
        val stocked = ConsoleCatalog.consoles.filter { (counts[it.id] ?: 0) > 0 }
        // An empty library (or a running import) keeps every console so the carousel is never blank.
        val visible = if (settings.hideEmptyConsoles && stocked.isNotEmpty()) stocked else ConsoleCatalog.consoles
        ConsoleCatalog.sortSystems(
            visible,
            settings.consoleSort,
            settings.consoleCustomOrder,
            counts,
            isTr
        )
    }
    val systemIds = remember(systems) { systems.map { it.id } }
    val visibleGameCount = if (importing) 0 else library.size
    val favoriteCount = remember(library, favorites, importing) {
        if (importing) 0 else library.count { it.isFavorite(favorites) }
    }
    var selectedId by rememberSaveable {
        mutableStateOf(
            library.maxByOrNull { it.lastPlayedAt }?.takeIf { it.lastPlayedAt > 0 }?.consoleId
                ?: systems.firstOrNull { (counts[it.id] ?: 0) > 0 }?.id
                ?: systems.first().id
        )
    }
    val index = systems.indexOfFirst { it.id == selectedId }.coerceAtLeast(0)
    val selected = systems[index]
    val style = consoleStyle(selected)

    val carouselFocus = remember { FocusRequester() }
    val arcadeFocus = remember { FocusRequester() }
    val bannerPlayFocus = remember { FocusRequester() }
    var carouselFocused by remember { mutableStateOf(false) }
    var confirmDown by remember { mutableStateOf(false) }

    fun select(i: Int) {
        selectedId = systems[(i + systems.size) % systems.size].id
    }

    fun randomGame() {
        val pool = library.filter { it.consoleId == selected.id }.ifEmpty { library }
        pool.randomOrNull()?.let { vm.playLibrary(it) }
    }

    LaunchedEffect(Unit) {
        delay(60)
        runCatching { carouselFocus.requestFocus() }
    }
    // Sorting, filtering or a deleted last game can drop the selected system from the carousel.
    LaunchedEffect(systems) {
        if (systems.isNotEmpty() && systems.none { it.id == selectedId }) selectedId = systems.first().id
    }
    RequestFocusOnWindowFocus(carouselFocus)

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .retroShortcuts(
                onX = onFavorites,
                onY = ::randomGame,
                onStart = onSettings,
                onSelect = onSearch,
                onL1 = { select(index - 1) },
                onR1 = { select(index + 1) }
            )
    ) {
        val narrow = maxWidth < 600.dp
        val compactHeader = maxWidth < 1000.dp
        RetroBackdrop(selected.id, scanlines = settings.uiScanlines, order = systemIds)

        Column(Modifier.fillMaxSize().padding(safe)) {
            Row(
                Modifier.fillMaxWidth().padding(top = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                RetrobitMark(accent = style.accent, compact = narrow)
                if (!compactHeader) Spacer(Modifier.width(6.dp))
                if (!compactHeader) Text(
                    pluralStringResource(R.plurals.games_plural, visibleGameCount, visibleGameCount) +
                        "  ·  " +
                        pluralStringResource(R.plurals.systems_plural, counts.size, counts.size),
                    style = MaterialTheme.typography.labelMedium,
                    color = retroOnSurface(0.6f)
                )
                Spacer(Modifier.weight(1f))
                RetroNavButton(
                    Icons.Rounded.SportsEsports,
                    if (compactHeader) null else stringResource(R.string.arcade_title),
                    style.accent,
                    onArcade,
                    modifier = Modifier
                        .focusRequester(arcadeFocus)
                        .onPreviewKeyEvent { event ->
                            if (event.key == Key.DirectionDown && event.type == KeyEventType.KeyDown) {
                                runCatching { carouselFocus.requestFocus() }
                                true
                            } else {
                                false
                            }
                        }
                )
                RetroNavButton(
                    Icons.Rounded.Favorite,
                    if (compactHeader) null else stringResource(R.string.favorites),
                    style.accent,
                    onFavorites,
                    badge = favoriteCount.takeIf { it > 0 && !compactHeader }?.toString()
                )
                RetroNavButton(Icons.Rounded.Search, if (compactHeader) null else stringResource(R.string.search), style.accent, onSearch)
                RetroNavButton(Icons.Rounded.Settings, null, style.accent, onSettings)
                if (!compactHeader) {
                    Spacer(Modifier.width(4.dp))
                    RetroClock()
                }
            }

            if (importing) {
                ScanBanner(
                    progress = progress,
                    added = progress?.current ?: 0,
                    accent = style.accent,
                    onPlay = onArcade,
                    playFocus = bannerPlayFocus,
                    onDown = { runCatching { carouselFocus.requestFocus() } },
                    modifier = Modifier.padding(top = 12.dp)
                )
            }

            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                val compactInfo = maxHeight < 300.dp
                val density = LocalDensity.current
                // Sized to the free space right of the info column so the numerals never hide behind it.
                val yearSize = with(density) {
                    minOf(150.dp, (maxWidth - 520.dp).coerceAtLeast(180.dp) / 3.3f, maxHeight * 0.62f).toSp()
                }
                AnimatedContent(
                    targetState = selected,
                    transitionSpec = {
                        val dir = carouselDirection(systemIds, initialState.id, targetState.id)
                        if (reducedMotion) fadeIn(tween(0)) togetherWith fadeOut(tween(0))
                        else (fadeIn(tween(140)) + slideInHorizontally(tween(220)) { dir * it / 4 }) togetherWith
                            (fadeOut(tween(100)) + slideOutHorizontally(tween(160)) { -dir * it / 4 })
                    },
                    contentAlignment = Alignment.CenterEnd,
                    label = "systemYear",
                    modifier = Modifier.align(Alignment.TopEnd).padding(top = 8.dp)
                ) { system ->
                    if (!narrow && maxHeight >= 280.dp && system.year > 0) YearMark(system, yearSize)
                }
                Column(
                    Modifier.align(Alignment.BottomStart).widthIn(max = 560.dp).padding(bottom = 14.dp)
                ) {
                    AnimatedContent(
                        targetState = selected,
                        transitionSpec = {
                            val dir = carouselDirection(systemIds, initialState.id, targetState.id)
                            if (reducedMotion) fadeIn(tween(0)) togetherWith fadeOut(tween(0))
                            else (fadeIn(tween(140)) + slideInHorizontally(tween(220)) { dir * it / 10 }) togetherWith
                                (fadeOut(tween(100)) + slideOutHorizontally(tween(160)) { -dir * it / 12 })
                        },
                        label = "systemInfo"
                    ) { system ->
                        SystemInfo(system, library, counts[system.id] ?: 0, isTr, compact = compactInfo)
                    }
                    if (library.isEmpty() && !importing) {
                        Spacer(Modifier.height(12.dp))
                        RetroPanel {
                            Column(Modifier.padding(14.dp)) {
                                Text(
                                    stringResource(R.string.home_empty_title),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = retroOnSurface()
                                )
                                Text(
                                    stringResource(R.string.home_empty_desc),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = retroOnSurface(0.7f)
                                )
                            }
                        }
                    }
                }
            }

            SystemCarousel(
                systems = systems,
                counts = counts,
                index = index,
                focused = carouselFocused,
                onTapPanel = { i -> if (i == index) onOpenSystem(selected.id) else select(i) },
                onSettle = { i -> if (i in systems.indices) select(i) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .focusRequester(carouselFocus)
                    .onFocusChanged {
                        carouselFocused = it.isFocused
                        if (!it.isFocused) confirmDown = false
                    }
                    .onPreviewKeyEvent { event ->
                        when (event.key) {
                            Key.DirectionLeft -> {
                                if (event.type == KeyEventType.KeyDown) select(index - 1)
                                true
                            }
                            Key.DirectionRight -> {
                                if (event.type == KeyEventType.KeyDown) select(index + 1)
                                true
                            }
                            Key.DirectionUp -> {
                                if (event.type == KeyEventType.KeyDown) {
                                    runCatching {
                                        if (importing) bannerPlayFocus.requestFocus()
                                        else arcadeFocus.requestFocus()
                                    }
                                }
                                true
                            }
                            Key.DirectionDown -> true
                            Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                                when (event.type) {
                                    KeyEventType.KeyDown -> confirmDown = true
                                    // Opening on key-up keeps the release away from the gamelist rows.
                                    KeyEventType.KeyUp -> if (confirmDown) {
                                        confirmDown = false
                                        onOpenSystem(selected.id)
                                    }
                                }
                                true
                            }
                            else -> false
                        }
                    }
                    .focusable()
            )

            GamepadHintBar(
                hints = listOf(
                    GamepadHint("A", stringResource(R.string.hint_select), HintGreen) { onOpenSystem(selected.id) },
                    GamepadHint("X", stringResource(R.string.favorites), HintBlue, onFavorites),
                    GamepadHint("Y", stringResource(R.string.hint_random), HintYellow, ::randomGame),
                    GamepadHint("SELECT", stringResource(R.string.search), retroOnSurface(), onSearch),
                    GamepadHint("START", stringResource(R.string.hint_menu), retroOnSurface(), onSettings)
                ),
                modifier = Modifier.padding(top = 6.dp, bottom = 2.dp)
            )
        }
    }
}

/** Big release year: accent outline over a translucent fill, with a dark halo for bright art. */
@Composable
private fun YearMark(system: ConsoleInfo, fontSize: androidx.compose.ui.unit.TextUnit) {
    val retro = LocalRetroTheme.current
    val accent = if (retro) consoleStyle(system).accent else MaterialTheme.colorScheme.primary
    val strokePx = with(LocalDensity.current) { 2.5.dp.toPx() }
    val face = TextStyle(
        fontFamily = TechFont,
        fontWeight = FontWeight.Black,
        fontSize = fontSize,
        lineHeight = fontSize * 1.05f,
        letterSpacing = fontSize * 0.02f
    )
    val text = system.year.toString()
    Box {
        if (retro) {
            Text(
                text,
                style = face.copy(
                    color = Color.Black.copy(alpha = 0.32f),
                    shadow = Shadow(Color.Black.copy(alpha = 0.75f), Offset.Zero, 48f)
                ),
                maxLines = 1
            )
        }
        Text(text, style = face.copy(color = accent.copy(alpha = if (retro) 0.24f else 0.1f)), maxLines = 1)
        Text(
            text,
            style = face.copy(
                color = accent.copy(alpha = if (retro) 0.85f else 0.4f),
                drawStyle = Stroke(strokePx)
            ),
            maxLines = 1
        )
    }
}

@Composable
private fun SystemInfo(
    system: ConsoleInfo,
    library: List<com.retrobit.app.data.LibraryGame>,
    count: Int,
    isTr: Boolean,
    compact: Boolean = false
) {
    val style = consoleStyle(system)
    val locale = LocalConfiguration.current.locales[0]
    Column {
        Text(
            listOf(system.maker, system.year.takeIf { it > 0 }?.toString())
                .filterNotNull().filter { it.isNotBlank() }
                .joinToString("  ·  ")
                .uppercase(java.util.Locale.ROOT),
            style = MaterialTheme.typography.labelLarge,
            letterSpacing = 3.sp,
            color = style.accent
        )
        Spacer(Modifier.height(if (compact) 4.dp else 10.dp))
        ConsoleWordmark(system, size = if (compact) 38.sp else 60.sp)
        Spacer(Modifier.height(if (compact) 4.dp else 14.dp))
        Text(
            system.localizedName(isTr),
            style = MaterialTheme.typography.titleLarge,
            color = retroOnSurface()
        )
        Text(
            system.localizedDescription(isTr),
            style = MaterialTheme.typography.bodyMedium,
            color = retroOnSurface(0.72f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp)
        )
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (count > 0) style.accent else retroOnSurface(0.16f))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    pluralStringResource(R.plurals.games_plural, count, count).uppercase(locale),
                    fontFamily = TechFont.takeIf { !isTr },
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (count > 0) onAccent(style.accent) else retroOnSurface()
                )
            }
            val last = remember(library, system.id) {
                library.filter { it.consoleId == system.id && it.lastPlayedAt > 0 }
                    .maxByOrNull { it.lastPlayedAt }
            }
            if (last != null) {
                Text(
                    "${stringResource(R.string.last_played)}: ${last.title}",
                    style = MaterialTheme.typography.labelMedium,
                    color = retroOnSurface(0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 12.dp)
                )
            }
        }
    }
}

@Composable
private fun SystemCarousel(
    systems: List<ConsoleInfo>,
    counts: Map<String, Int>,
    index: Int,
    focused: Boolean,
    onTapPanel: (Int) -> Unit,
    onSettle: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = index)
    val reducedMotion = LocalReducedMotion.current
    var dragged by remember { mutableStateOf(false) }
    LaunchedEffect(index, reducedMotion) {
        if (!dragged) {
            if (reducedMotion || abs(listState.firstVisibleItemIndex - index) > 4) listState.scrollToItem(index)
            else listState.animateScrollToItem(index)
        }
    }
    LaunchedEffect(listState) {
        listState.interactionSource.interactions.collect { if (it is DragInteraction.Start) dragged = true }
    }
    val currentOnSettle by rememberUpdatedState(onSettle)
    LaunchedEffect(listState) {
        snapshotFlow {
            val info = listState.layoutInfo
            val center = (info.viewportStartOffset + info.viewportEndOffset) / 2
            Triple(dragged, listState.isScrollInProgress,
                info.visibleItemsInfo.minByOrNull { abs(it.offset + it.size / 2 - center) }?.index)
        }.collect { (touchScroll, scrolling, centered) ->
            if (touchScroll) {
                centered?.let { currentOnSettle(it) }
                if (!scrolling) dragged = false
            }
        }
    }
    BoxWithConstraints(modifier) {
        val panelWidth = 210.dp
        val side = ((maxWidth - panelWidth) / 2).coerceAtLeast(0.dp)
        LazyRow(
            state = listState,
            flingBehavior = rememberSnapFlingBehavior(listState),
            contentPadding = PaddingValues(horizontal = side),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxSize()
        ) {
            itemsIndexed(systems, key = { _, c -> c.id }) { i, console ->
                SystemPanel(
                    console = console,
                    count = counts[console.id] ?: 0,
                    distance = abs(i - index),
                    focused = focused && i == index,
                    onTap = { onTapPanel(i) },
                    modifier = Modifier.width(panelWidth)
                )
            }
        }
    }
}

@Composable
private fun SystemPanel(
    console: ConsoleInfo,
    count: Int,
    distance: Int,
    focused: Boolean,
    onTap: () -> Unit,
    modifier: Modifier = Modifier
) {
    val style = consoleStyle(console)
    val retro = LocalRetroTheme.current
    val selected = distance == 0
    val reducedMotion = LocalReducedMotion.current
    val scale by animateFloatAsState(
        when (distance) {
            0 -> 1.13f
            1 -> 0.93f
            else -> 0.86f
        },
        tween(if (reducedMotion) 0 else 160),
        label = "panelScale"
    )
    val alpha by animateFloatAsState(
        when {
            selected -> 1f
            count == 0 -> 0.36f
            distance == 1 -> 0.7f
            else -> 0.46f
        },
        tween(if (reducedMotion) 0 else 160),
        label = "panelAlpha"
    )
    val glow by animateFloatAsState(if (selected) 1f else 0f, tween(if (reducedMotion) 0 else 160), label = "panelGlow")
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier
            .height(118.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                this.alpha = alpha
            }
            .then(if (retro) Modifier.focusGlow({ glow }, style.accent, shape, 14.dp) else Modifier)
            .clip(shape)
            .background(if (retro) Color.Black else MaterialTheme.colorScheme.surfaceVariant)
            .border(
                if (selected) 3.dp else 1.dp,
                when {
                    focused -> retroOnSurface()
                    selected -> style.accent
                    retro -> Color.White.copy(alpha = 0.15f)
                    else -> MaterialTheme.colorScheme.outlineVariant
                },
                shape
            )
            .pointerInput(onTap) { detectTapGestures { onTap() } }
    ) {
        BackdropImage(console.id, Modifier.fillMaxSize(), widthPx = 420)
        if (retro) {
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.45f to Color.Black.copy(alpha = 0.25f),
                        1f to Color.Black.copy(alpha = 0.88f)
                    )
                )
            )
        }
        if (selected) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .width(5.dp)
                    .background(style.accent)
            )
        }
        ConsoleWordmark(
            console,
            size = 22.sp,
            showStrip = false,
            modifier = Modifier.align(Alignment.BottomStart).padding(start = 12.dp, bottom = 10.dp, end = 10.dp)
        )
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(
                    if (count > 0) style.accent
                    else if (retro) Color.Black.copy(alpha = 0.6f)
                    else MaterialTheme.colorScheme.surface
                )
                .padding(horizontal = 7.dp, vertical = 2.dp)
        ) {
            Text(
                count.toString(),
                fontFamily = TechFont,
                fontWeight = FontWeight.Black,
                fontSize = 11.sp,
                color = if (count > 0) onAccent(style.accent) else retroOnSurface(0.7f)
            )
        }
    }
}

@Composable
private fun ScanBanner(
    progress: ImportProgress?,
    added: Int,
    accent: Color,
    onPlay: () -> Unit,
    playFocus: FocusRequester,
    onDown: () -> Unit,
    modifier: Modifier = Modifier
) {
    val counting = progress == null || progress.scanning
    RetroPanel(modifier.widthIn(max = 560.dp)) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(15.dp), strokeWidth = 2.dp, color = accent)
                Text(
                    stringResource(if (counting) R.string.import_stage_prepare else R.string.import_stage_games),
                    style = MaterialTheme.typography.labelLarge,
                    color = retroOnSurface(),
                    modifier = Modifier.padding(start = 10.dp).weight(1f)
                )
                Text(
                    stringResource(R.string.import_processed, added),
                    style = MaterialTheme.typography.labelMedium,
                    color = accent
                )
            }
            val fraction = progress?.fraction
            val barModifier = Modifier.fillMaxWidth().padding(top = 8.dp).height(4.dp).clip(CircleShape)
            if (fraction != null) {
                LinearProgressIndicator(
                    progress = { fraction },
                    modifier = barModifier,
                    color = accent,
                    trackColor = retroOnSurface(0.12f)
                )
            } else {
                LinearProgressIndicator(modifier = barModifier, color = accent, trackColor = retroOnSurface(0.12f))
            }
            Text(
                if (counting) stringResource(R.string.import_prepare_detail)
                else stringResource(R.string.import_progress, progress?.current ?: 0, progress?.total ?: 0) +
                    (progress?.fileName?.let { "  ·  $it" }.orEmpty()),
                style = MaterialTheme.typography.labelSmall,
                color = retroOnSurface(0.6f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 6.dp)
            )
            progress?.note?.let { note ->
                Text(
                    stringResource(R.string.import_opening, note),
                    style = MaterialTheme.typography.labelSmall,
                    color = accent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            Text(
                stringResource(R.string.import_library_locked),
                style = MaterialTheme.typography.labelSmall,
                color = retroOnSurface(0.72f),
                modifier = Modifier.padding(top = 6.dp)
            )
            Spacer(Modifier.height(9.dp))
            val shape = RoundedCornerShape(50)
            var playFocused by remember { mutableStateOf(false) }
            val fill by animateColorAsState(
                if (playFocused) accent else accent.copy(alpha = 0.16f), tween(160), label = "playFill"
            )
            val ink by animateColorAsState(
                if (playFocused) onAccent(accent) else accent, tween(160), label = "playInk"
            )
            Row(
                Modifier
                    .focusRequester(playFocus)
                    .onFocusChanged { playFocused = it.isFocused }
                    .focusLift(playFocused, accent, shape, lift = 1.08f, spread = 9.dp)
                    .border(
                        if (playFocused) 2.dp else 1.dp,
                        if (playFocused) retroOnSurface() else accent.copy(alpha = 0.7f),
                        shape
                    )
                    .clip(shape)
                    .background(fill)
                    .clickable(onClick = onPlay)
                    .onPreviewKeyEvent { event ->
                        if (event.key == Key.DirectionDown && event.type == KeyEventType.KeyDown) {
                            onDown()
                            true
                        } else {
                            false
                        }
                    }
                    .padding(start = 14.dp, end = if (playFocused) 6.dp else 14.dp)
                    .height(34.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Rounded.SportsEsports,
                    contentDescription = null,
                    tint = ink,
                    modifier = Modifier.size(17.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.wait_play),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = ink
                )
                if (playFocused) {
                    Spacer(Modifier.width(10.dp))
                    Box(
                        Modifier.size(22.dp).clip(CircleShape).background(HintGreen),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("A", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold,
                            color = Color.White)
                    }
                }
            }
        }
    }
}
