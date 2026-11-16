package com.retrobit.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.retrobit.app.AppViewModel
import com.retrobit.app.R
import com.retrobit.app.data.ConsoleCatalog
import com.retrobit.app.data.LibraryGame
import com.retrobit.app.data.SettingsData
import com.retrobit.app.ui.components.ConsoleWordmark
import com.retrobit.app.ui.components.GameListRow
import com.retrobit.app.ui.components.GamepadHint
import com.retrobit.app.ui.components.GamepadHintBar
import com.retrobit.app.ui.components.HintBlue
import com.retrobit.app.ui.components.HintGreen
import com.retrobit.app.ui.components.HintRed
import com.retrobit.app.ui.components.HintYellow
import com.retrobit.app.ui.components.LocalSafeArea
import com.retrobit.app.ui.components.MetaCell
import com.retrobit.app.ui.components.PerformanceNote
import com.retrobit.app.ui.components.PreviewWithCover
import com.retrobit.app.ui.components.TurkishFlag
import com.retrobit.app.ui.components.genreLabel
import com.retrobit.app.ui.components.RetroBackdrop
import com.retrobit.app.ui.components.RetroChip
import com.retrobit.app.ui.components.RetroClock
import com.retrobit.app.ui.components.RetroGameCard
import com.retrobit.app.ui.components.RetroNavButton
import com.retrobit.app.ui.components.RetroPanel
import com.retrobit.app.ui.components.consoleStyle
import com.retrobit.app.ui.components.LocalReducedMotion
import com.retrobit.app.ui.components.GameMetadataCells
import com.retrobit.app.ui.components.playTimeLabel
import com.retrobit.app.ui.components.rememberEntryDelay
import com.retrobit.app.ui.components.retroOnSurface
import com.retrobit.app.ui.components.retroShortcuts
import com.retrobit.app.ui.components.rememberLocalSnap
import com.retrobit.app.ui.components.snapUrlFor
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GameListScreen(
    vm: AppViewModel,
    isTr: Boolean,
    consoleId: String,
    onBack: () -> Unit,
    onDetails: (LibraryGame) -> Unit,
    onFavorites: () -> Unit,
    onSearch: () -> Unit,
    onSettings: () -> Unit
) {
    val library by vm.library.collectAsStateWithLifecycle()
    val favorites by vm.favorites.collectAsStateWithLifecycle()
    val covers by vm.coverUrls.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    BackHandler(onBack = onBack)
    val safe = LocalSafeArea.current
    val console = ConsoleCatalog.get(consoleId)
    val style = consoleStyle(console)
    val scope = rememberCoroutineScope()

    val games = remember(library, consoleId, settings.librarySort) {
        val filtered = library.filter { it.consoleId == consoleId }
        when (settings.librarySort) {
            SettingsData.SORT_RECENT -> filtered.sortedWith(
                compareByDescending<LibraryGame> { it.lastPlayedAt }.thenBy { it.title.lowercase() }
            )
            SettingsData.SORT_PLAYTIME -> filtered.sortedWith(
                compareByDescending<LibraryGame> { it.playSeconds }.thenBy { it.title.lowercase() }
            )
            else -> filtered.sortedBy { it.title.lowercase() }
        }
    }
    var selectedId by rememberSaveable(consoleId) {
        mutableStateOf(
            games.filter { it.lastPlayedAt > 0 }.maxByOrNull { it.lastPlayedAt }?.id ?: games.firstOrNull()?.id
        )
    }
    val selectedIndex = games.indexOfFirst { it.id == selectedId }.coerceAtLeast(0)
    val selectedGame = games.getOrNull(selectedIndex)
    val shelf = settings.effectiveLibraryShelf
    val selectedCover = selectedGame?.let { covers["lib:${it.id}"] ?: it.coverUrl }
    val localSnap = rememberLocalSnap(selectedGame?.id.orEmpty(), selectedGame?.lastPlayedAt)
    // A save-state capture shows where the player left off, so it outranks the stock screenshot.
    val snap: Any? = localSnap.takeIf { selectedGame != null } ?: snapUrlFor(selectedCover)

    val requesters = remember { androidx.compose.runtime.mutableStateMapOf<String, FocusRequester>() }
    var entryFocusRequested by remember(consoleId, shelf) { mutableStateOf(false) }
    var gamesFocused by remember { mutableStateOf(false) }
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateFlow
        .collectAsStateWithLifecycle(minActiveState = Lifecycle.State.CREATED)
    val windowFocused = LocalWindowInfo.current.isWindowFocused
    val backFocus = remember { FocusRequester() }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = (selectedIndex - 4).coerceAtLeast(0))
    val gridState = rememberLazyGridState(initialFirstVisibleItemIndex = selectedIndex)
    var lastSort by remember { mutableStateOf(settings.librarySort) }
    val openedAt = remember { android.os.SystemClock.uptimeMillis() }

    // A new sort reorders every card, so both scroll positions are meaningless afterwards.
    LaunchedEffect(settings.librarySort) {
        if (lastSort != settings.librarySort) {
            lastSort = settings.librarySort
            listState.scrollToItem(0)
            gridState.scrollToItem(0)
            games.firstOrNull()?.let { selectedId = it.id }
        }
    }

    fun focusGame(i: Int) {
        val target = games.getOrNull(i) ?: return
        selectedId = target.id
        scope.launch {
            if (requesters[target.id] == null) {
                if (shelf) gridState.scrollToItem(i) else listState.scrollToItem((i - 4).coerceAtLeast(0))
                withFrameNanos { }
            }
            runCatching { requesters[target.id]?.requestFocus() }
        }
    }

    // Shoulder buttons skip to the next initial, like paging through a long arcade romset.
    fun jumpLetter(forward: Boolean) {
        if (games.isEmpty()) return
        fun key(g: LibraryGame) = g.title.firstOrNull()?.uppercaseChar()?.takeIf { it.isLetter() } ?: '#'
        val current = key(games[selectedIndex])
        val target = if (forward) {
            (selectedIndex + 1 until games.size).firstOrNull { key(games[it]) != current } ?: (games.size - 1)
        } else {
            val startOfGroup = (selectedIndex downTo 0).lastOrNull { key(games[it]) == current } ?: 0
            if (startOfGroup < selectedIndex) startOfGroup
            else {
                val prevKey = games.getOrNull(startOfGroup - 1)?.let(::key)
                if (prevKey == null) 0 else (startOfGroup - 1 downTo 0).last { key(games[it]) == prevKey }
            }
        }
        focusGame(target)
    }

    fun randomGame() {
        games.randomOrNull()?.let { vm.playLibrary(it) }
    }

    LaunchedEffect(consoleId, games.isEmpty(), shelf, requesters[selectedGame?.id.orEmpty()], lifecycleState, windowFocused) {
        if (entryFocusRequested || lifecycleState != Lifecycle.State.RESUMED || !windowFocused) return@LaunchedEffect
        withFrameNanos { }
        val requester = if (games.isEmpty()) backFocus
            else requesters[selectedGame?.id.orEmpty()] ?: return@LaunchedEffect
        runCatching { requester.requestFocus() }
    }

    // A game removed from its detail sheet must not leave the selection on a disposed card.
    LaunchedEffect(games) {
        if (games.isNotEmpty() && games.none { it.id == selectedId }) {
            selectedId = games.first().id
            focusGame(0)
        }
    }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .retroShortcuts(
                onX = { selectedGame?.let { vm.toggleLibraryFavorite(it) } },
                onY = ::randomGame,
                onStart = onSettings,
                onSelect = onSearch,
                onL1 = { jumpLetter(false) },
                onR1 = { jumpLetter(true) }
            )
    ) {
        val narrow = maxWidth < 600.dp
        RetroBackdrop(consoleId, scanlines = settings.uiScanlines, dim = 0.18f, hero = snap)

        Column(Modifier.fillMaxSize().padding(safe)) {
            Row(
                Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                RetroNavButton(
                    Icons.Rounded.ArrowBack,
                    null,
                    style.accent,
                    onBack,
                    modifier = Modifier.focusRequester(backFocus).onFocusChanged {
                        if (it.isFocused && games.isEmpty()) entryFocusRequested = true
                    }
                )
                ConsoleWordmark(console, size = if (narrow) 20.sp else 26.sp, showStrip = false)
                Column(Modifier.weight(1f).padding(start = 6.dp)) {
                    if (!narrow) Text(
                        console?.localizedName(isTr) ?: consoleId,
                        style = MaterialTheme.typography.labelLarge,
                        color = retroOnSurface(),
                        maxLines = 1
                    )
                    Text(
                        pluralStringResource(R.plurals.games_plural, games.size, games.size),
                        style = MaterialTheme.typography.labelSmall,
                        color = style.accent
                    )
                }
                RetroNavButton(Icons.Rounded.Favorite, null, style.accent, onFavorites)
                RetroNavButton(Icons.Rounded.Search, null, style.accent, onSearch)
                RetroNavButton(Icons.Rounded.Settings, null, style.accent, onSettings)
                if (!narrow) RetroClock(Modifier.padding(start = 4.dp))
            }

            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            // Phone landscape leaves too little height for the metadata grid; the screenshot wins.
            val compact = narrow || maxHeight < 360.dp
            val paneHeight = maxHeight
            val paneWidth = maxWidth
            val previewWidth = if (narrow) paneWidth else paneWidth * (if (compact) 0.5f else 0.44f)
            FlowRow(Modifier.fillMaxSize()) {
                Column(Modifier.width(previewWidth).height(if (narrow) paneHeight * 0.4f else paneHeight)
                    .padding(end = if (narrow) 0.dp else 18.dp, bottom = if (narrow) 12.dp else 0.dp)) {
                    if (selectedGame != null) {
                        GamePreview(vm, selectedGame, snap, covers, settings, compact)
                    } else {
                        Spacer(Modifier.weight(1f))
                        Text(
                            console?.localizedDescription(isTr).orEmpty(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = retroOnSurface(0.75f)
                        )
                        PerformanceNote(consoleId)
                    }
                }
                Column(Modifier.width(if (narrow) paneWidth else paneWidth - previewWidth)
                    .height(if (narrow) paneHeight * 0.6f else paneHeight)) {
                    if (games.isNotEmpty()) {
                        Row(
                            Modifier.fillMaxWidth().padding(bottom = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RetroChip(
                                stringResource(R.string.library_sort_recent),
                                settings.librarySort == SettingsData.SORT_RECENT,
                                style.accent,
                                onClick = { vm.setLibrarySort(SettingsData.SORT_RECENT) }
                            )
                            RetroChip(
                                stringResource(R.string.library_sort_title),
                                settings.librarySort == SettingsData.SORT_TITLE,
                                style.accent,
                                onClick = { vm.setLibrarySort(SettingsData.SORT_TITLE) }
                            )
                            RetroChip(
                                stringResource(R.string.library_sort_playtime),
                                settings.librarySort == SettingsData.SORT_PLAYTIME,
                                style.accent,
                                onClick = { vm.setLibrarySort(SettingsData.SORT_PLAYTIME) }
                            )
                        }
                    }
                    Box(Modifier.weight(1f).fillMaxWidth()
                        .onFocusChanged { gamesFocused = it.hasFocus }
                        .onPreviewKeyEvent { event ->
                            if (!shelf && gamesFocused && games.isNotEmpty() && event.key in listOf(Key.DirectionLeft, Key.DirectionRight)) {
                                if (event.type == KeyEventType.KeyDown) {
                                    val page = listState.layoutInfo.visibleItemsInfo.size.coerceAtLeast(1)
                                    val direction = if (event.key == Key.DirectionRight) 1 else -1
                                    focusGame((selectedIndex + direction * page).coerceIn(0, games.lastIndex))
                                }
                                true
                            } else false
                        }) {
                        when {
                            games.isEmpty() -> RetroPanel(Modifier.align(Alignment.Center), style.cardShape) {
                                Column(Modifier.padding(22.dp)) {
                                    Text(
                                        stringResource(R.string.system_empty_title),
                                        style = MaterialTheme.typography.titleMedium,
                                        color = retroOnSurface()
                                    )
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        stringResource(R.string.system_empty_desc),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = retroOnSurface(0.7f)
                                    )
                                }
                            }
                            shelf -> LazyVerticalGrid(
                                state = gridState,
                                columns = GridCells.Adaptive(
                                    when (settings.effectiveCoverSize) {
                                        SettingsData.COVER_SMALL -> 92.dp
                                        SettingsData.COVER_LARGE -> 140.dp
                                        else -> 112.dp
                                    }
                                ),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 16.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                itemsIndexed(games, key = { _, g -> g.id }) { i, game ->
                                    val requester = remember(game.id) { FocusRequester() }
                                    DisposableEffect(game.id) {
                                        requesters[game.id] = requester
                                        onDispose { requesters.remove(game.id) }
                                    }
                                    LaunchedEffect(game.id) { vm.ensureCover(game) }
                                    RetroGameCard(
                                        game = game,
                                        coverUrl = covers["lib:${game.id}"] ?: game.coverUrl,
                                        favorite = game.isFavorite(favorites),
                                        showConsole = false,
                                        onPlay = { vm.playLibrary(game) },
                                        onDetails = { onDetails(game) },
                                        onFocused = {
                                            selectedId = game.id
                                            entryFocusRequested = true
                                        },
                                        focusRequester = requester,
                                        entryDelayMs = rememberEntryDelay(openedAt, i) { gridState.firstVisibleItemIndex },
                                        framed = settings.supporterUnlocked
                                    )
                                }
                            }
                            else -> LazyColumn(
                                state = listState,
                                verticalArrangement = Arrangement.spacedBy(5.dp),
                                contentPadding = PaddingValues(vertical = 8.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                itemsIndexed(games, key = { _, g -> g.id }) { i, game ->
                                    val requester = remember(game.id) { FocusRequester() }
                                    DisposableEffect(game.id) {
                                        requesters[game.id] = requester
                                        onDispose { requesters.remove(game.id) }
                                    }
                                    GameListRow(
                                        game = game,
                                        index = i,
                                        style = style,
                                        favorite = game.isFavorite(favorites),
                                        selected = game.id == selectedGame?.id,
                                        focusRequester = requester,
                                        onFocused = {
                                            selectedId = game.id
                                            entryFocusRequested = true
                                        },
                                        onPlay = { vm.playLibrary(game) },
                                        onDetails = { onDetails(game) },
                                        onSelectTap = { focusGame(i) },
                                        entryDelayMs = rememberEntryDelay(openedAt, i) { listState.firstVisibleItemIndex }
                                    )
                                }
                            }
                        }
                    }
                }
            }
            }

            GamepadHintBar(
                hints = if (games.isEmpty()) listOf(
                    GamepadHint("B", stringResource(R.string.hint_back), HintRed, onBack),
                    GamepadHint("START", stringResource(R.string.hint_menu), retroOnSurface(), onSettings)
                ) else listOf(
                    GamepadHint("A", stringResource(R.string.play), HintGreen) { selectedGame?.let { vm.playLibrary(it) } },
                    GamepadHint("B", stringResource(R.string.hint_back), HintRed, onBack),
                    GamepadHint("X", stringResource(R.string.hint_favorite), HintBlue) {
                        selectedGame?.let { vm.toggleLibraryFavorite(it) }
                    },
                    GamepadHint("Y", stringResource(R.string.hint_random), HintYellow, ::randomGame),
                    GamepadHint("L/R", stringResource(R.string.hint_jump), retroOnSurface()) { jumpLetter(true) },
                    GamepadHint("START", stringResource(R.string.hint_menu), retroOnSurface(), onSettings)
                ),
                modifier = Modifier.padding(top = 10.dp, bottom = 2.dp),
                trailing = {
                    if (games.isNotEmpty()) Text(
                        stringResource(R.string.hint_hold_details),
                        style = MaterialTheme.typography.labelSmall,
                        color = retroOnSurface(0.55f)
                    )
                }
            )
        }
    }
}


@Composable
private fun androidx.compose.foundation.layout.ColumnScope.GamePreview(
    vm: AppViewModel,
    game: LibraryGame,
    snap: Any?,
    covers: Map<String, String?>,
    settings: SettingsData,
    compact: Boolean
) {
    val console = ConsoleCatalog.get(game.consoleId)
    val style = consoleStyle(console)
    LaunchedEffect(game.id) { vm.ensureCover(game) }
    val cover = covers["lib:${game.id}"] ?: game.coverUrl
    val metadataState by vm.gameMetadata.collectAsStateWithLifecycle()
    val metadata = metadataState[game.id] ?: vm.metadataFor(game)
    LaunchedEffect(game.id, game.title, game.consoleId) { vm.ensureMetadata(game) }
    val dateFormat = remember { DateFormat.getDateInstance(DateFormat.MEDIUM) }

    PreviewWithCover(
        snapUrl = snap,
        console = console,
        scanlines = settings.uiScanlines,
        coverUrl = cover,
        title = game.title,
        gameId = game.id,
        framed = settings.supporterUnlocked,
        coverHeight = if (compact) 96.dp else 128.dp,
        coverAtStart = true,
        shareCover = !LocalReducedMotion.current,
        modifier = Modifier.weight(1f, fill = false).fillMaxWidth(),
        beside = {
            Column(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        game.title,
                        style = if (compact) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge,
                        color = retroOnSurface(),
                        maxLines = if (compact) 3 else 4,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (game.isTurkishRelease) TurkishFlag(14.dp, Modifier.padding(start = 8.dp))
                }
                if (compact) {
                    Text(
                        listOf(genreLabel(metadata?.genre), metadata?.year?.toString() ?: "—").joinToString(" · "),
                        style = MaterialTheme.typography.labelSmall,
                        color = style.accent,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    )
    PerformanceNote(game.consoleId, Modifier.padding(top = 8.dp))
    if (!compact) {
        Spacer(Modifier.height(8.dp))
        GameMetadataCells(metadata, style.accent)
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            MetaCell(stringResource(R.string.play_count), game.playCount.toString(), style.accent, Modifier.weight(1f))
            MetaCell(stringResource(R.string.stats_playtime), playTimeLabel(game.playSeconds), style.accent, Modifier.weight(1f))
            MetaCell(
                stringResource(R.string.last_played),
                if (game.lastPlayedAt > 0) dateFormat.format(Date(game.lastPlayedAt)) else stringResource(R.string.never),
                style.accent,
                Modifier.weight(1f)
            )
        }
    }
}
