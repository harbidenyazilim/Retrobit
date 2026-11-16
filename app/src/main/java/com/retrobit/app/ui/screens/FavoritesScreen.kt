package com.retrobit.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.retrobit.app.AppViewModel
import com.retrobit.app.R
import com.retrobit.app.data.ConsoleCatalog
import com.retrobit.app.data.LibraryGame
import com.retrobit.app.ui.components.GamepadHint
import com.retrobit.app.ui.components.GamepadHintBar
import com.retrobit.app.ui.components.HintBlue
import com.retrobit.app.ui.components.HintGreen
import com.retrobit.app.ui.components.HintRed
import com.retrobit.app.ui.components.HintYellow
import com.retrobit.app.ui.components.LocalSafeArea
import com.retrobit.app.ui.components.RetroBackdrop
import com.retrobit.app.ui.components.RetroChip
import com.retrobit.app.ui.components.RetroGameCard
import com.retrobit.app.ui.components.RetroPanel
import com.retrobit.app.ui.components.RetroTopBar
import com.retrobit.app.ui.components.consoleStyle
import com.retrobit.app.ui.components.rememberEntryDelay
import com.retrobit.app.ui.components.retroOnSurface
import com.retrobit.app.ui.components.retroShortcuts
import kotlinx.coroutines.delay

private const val SORT_AZ = "az"
private const val SORT_RECENT = "recent"
private const val SORT_MOST = "most"

@Composable
fun FavoritesScreen(
    vm: AppViewModel,
    isTr: Boolean,
    onBack: () -> Unit,
    onDetails: (LibraryGame) -> Unit,
    onSearch: () -> Unit,
    onSettings: () -> Unit
) {
    val favorites by vm.favorites.collectAsStateWithLifecycle()
    val library by vm.library.collectAsStateWithLifecycle()
    val covers by vm.coverUrls.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val safe = LocalSafeArea.current

    var consoleFilter by rememberSaveable { mutableStateOf<String?>(null) }
    var sort by rememberSaveable { mutableStateOf(SORT_AZ) }
    var focusedId by rememberSaveable { mutableStateOf<String?>(null) }
    val gridState = rememberLazyGridState()

    val all = remember(favorites, library) { library.filter { it.isFavorite(favorites) } }
    val present = remember(all) {
        ConsoleCatalog.consoles.filter { c -> all.any { it.consoleId == c.id } }
    }
    val shown = remember(all, consoleFilter, sort) {
        val filtered = all.filter { consoleFilter == null || it.consoleId == consoleFilter }
        when (sort) {
            SORT_RECENT -> filtered.sortedByDescending { it.lastPlayedAt }
            SORT_MOST -> filtered.sortedWith(compareByDescending<LibraryGame> { it.playSeconds }.thenByDescending { it.playCount })
            else -> filtered.sortedBy { it.title.lowercase() }
        }
    }
    val focusedGame = shown.firstOrNull { it.id == focusedId }
    val backdropConsole = focusedGame?.consoleId ?: consoleFilter ?: shown.firstOrNull()?.consoleId
    val accent = consoleStyle(ConsoleCatalog.get(backdropConsole)).accent
    val requesters = remember { HashMap<String, FocusRequester>() }
    val backFocus = remember { FocusRequester() }
    var refocusIndex by remember { mutableStateOf<Int?>(null) }
    val openedAt = remember { android.os.SystemClock.uptimeMillis() }

    fun removeFocused() {
        val game = focusedGame ?: return
        refocusIndex = shown.indexOf(game)
        vm.toggleLibraryFavorite(game)
    }

    // Returning from a detail sheet restores the card the user left, not the first one.
    LaunchedEffect(Unit) {
        delay(60)
        val index = shown.indexOfFirst { it.id == focusedId }
        if (index >= 0 && requesters[focusedId] == null) gridState.scrollToItem(index)
        if (index >= 0) withFrameNanos { }
        runCatching {
            requesters[focusedId]?.requestFocus()
                ?: shown.firstOrNull()?.let { requesters[it.id]?.requestFocus() }
                ?: backFocus.requestFocus()
        }
    }
    // The card that held focus is gone once it leaves the favourites; hand focus to its neighbour.
    LaunchedEffect(shown) {
        val index = refocusIndex ?: return@LaunchedEffect
        refocusIndex = null
        withFrameNanos { }
        runCatching {
            shown.getOrNull(index.coerceAtMost(shown.lastIndex))?.let { requesters[it.id]?.requestFocus() }
                ?: backFocus.requestFocus()
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .retroShortcuts(
                onX = ::removeFocused,
                onY = { shown.randomOrNull()?.let { vm.playLibrary(it) } },
                onStart = onSettings,
                onSelect = onSearch
            )
    ) {
        RetroBackdrop(backdropConsole, scanlines = settings.uiScanlines, dim = 0.3f)
        Column(Modifier.fillMaxSize().padding(safe)) {
            RetroTopBar(
                title = stringResource(R.string.favorites),
                subtitle = pluralStringResource(R.plurals.games_plural, all.size, all.size),
                accent = accent,
                onBack = onBack,
                backFocus = backFocus
            )
            if (all.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)) {
                    item {
                        RetroChip(stringResource(R.string.all), consoleFilter == null, accent, { consoleFilter = null })
                    }
                    items(present, key = { it.id }) { console ->
                        RetroChip(
                            console.shortName,
                            consoleFilter == console.id,
                            consoleStyle(console).accent,
                            { consoleFilter = if (consoleFilter == console.id) null else console.id }
                        )
                    }
                    item { Spacer(Modifier.width(18.dp)) }
                    item { RetroChip(stringResource(R.string.sort_az), sort == SORT_AZ, accent, { sort = SORT_AZ }) }
                    item { RetroChip(stringResource(R.string.sort_recent), sort == SORT_RECENT, accent, { sort = SORT_RECENT }) }
                    item { RetroChip(stringResource(R.string.sort_most), sort == SORT_MOST, accent, { sort = SORT_MOST }) }
                }
                Spacer(Modifier.height(10.dp))
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (shown.isEmpty()) {
                    RetroPanel(Modifier.align(Alignment.Center)) {
                        Column(Modifier.padding(24.dp)) {
                            Text(
                                stringResource(R.string.favorites_empty_title),
                                style = MaterialTheme.typography.titleMedium,
                                color = retroOnSurface()
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                stringResource(R.string.favorites_empty_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = retroOnSurface(0.7f)
                            )
                        }
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(118.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 16.dp),
                        state = gridState,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        itemsIndexed(shown, key = { _, g -> g.id }) { i, game ->
                            val requester = remember(game.id) { FocusRequester() }
                            DisposableEffect(game.id) {
                                requesters[game.id] = requester
                                onDispose { requesters.remove(game.id) }
                            }
                            LaunchedEffect(game.id) { vm.ensureCover(game) }
                            RetroGameCard(
                                game = game,
                                coverUrl = covers["lib:${game.id}"] ?: game.coverUrl,
                                favorite = true,
                                showConsole = true,
                                onPlay = { vm.playLibrary(game) },
                                onDetails = { onDetails(game) },
                                onFocused = { focusedId = game.id },
                                focusRequester = requester,
                                entryDelayMs = rememberEntryDelay(openedAt, i)
                            )
                        }
                    }
                }
            }
            GamepadHintBar(
                hints = listOf(
                    GamepadHint("A", stringResource(R.string.play), HintGreen) { focusedGame?.let { vm.playLibrary(it) } },
                    GamepadHint("B", stringResource(R.string.hint_back), HintRed, onBack),
                    GamepadHint("X", stringResource(R.string.remove_favorite), HintBlue, ::removeFocused),
                    GamepadHint("Y", stringResource(R.string.hint_random), HintYellow) {
                        shown.randomOrNull()?.let { vm.playLibrary(it) }
                    },
                    GamepadHint("START", stringResource(R.string.hint_menu), retroOnSurface(), onSettings)
                ),
                modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
                trailing = {
                    Text(
                        stringResource(R.string.hint_hold_details),
                        style = MaterialTheme.typography.labelSmall,
                        color = retroOnSurface(0.55f)
                    )
                }
            )
        }
    }
}
