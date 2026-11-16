package com.retrobit.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.retrobit.app.AppViewModel
import com.retrobit.app.R
import com.retrobit.app.data.ConsoleCatalog
import com.retrobit.app.data.GameRepository
import com.retrobit.app.data.LibraryGame
import com.retrobit.app.ui.components.GamepadHint
import com.retrobit.app.ui.components.GamepadHintBar
import com.retrobit.app.ui.components.HintGreen
import com.retrobit.app.ui.components.HintRed
import com.retrobit.app.ui.components.HintYellow
import com.retrobit.app.ui.components.LocalIsTv
import com.retrobit.app.ui.components.LocalRetroTheme
import com.retrobit.app.ui.components.LocalSafeArea
import com.retrobit.app.ui.components.RetroBackdrop
import com.retrobit.app.ui.components.RetroChip
import com.retrobit.app.ui.components.RetroGameCard
import com.retrobit.app.ui.components.RetroInk
import com.retrobit.app.ui.components.RetroPanel
import com.retrobit.app.ui.components.RetroTopBar
import com.retrobit.app.ui.components.consoleStyle
import com.retrobit.app.ui.components.retroOnSurface
import com.retrobit.app.ui.components.retroShortcuts
import com.retrobit.app.ui.theme.PixelFont
import kotlinx.coroutines.delay

private const val NON_LETTER = '#'

private fun initialOf(title: String): Char =
    title.firstOrNull()?.uppercaseChar()?.takeIf { it in 'A'..'Z' } ?: NON_LETTER

@Composable
fun SearchScreen(
    vm: AppViewModel,
    isTr: Boolean,
    onBack: () -> Unit,
    onDetails: (LibraryGame) -> Unit,
    onSettings: () -> Unit
) {
    var query by rememberSaveable { mutableStateOf("") }
    var consoleFilter by rememberSaveable { mutableStateOf<String?>(null) }
    var letter by rememberSaveable { mutableStateOf<Char?>(null) }
    var focusedId by rememberSaveable { mutableStateOf<String?>(null) }
    val requesters = remember { HashMap<String, FocusRequester>() }
    val gridState = rememberLazyGridState()
    val library by vm.library.collectAsStateWithLifecycle()
    val covers by vm.coverUrls.collectAsStateWithLifecycle()
    val favorites by vm.favorites.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val safe = LocalSafeArea.current
    val isTv = LocalIsTv.current
    val focusManager = LocalFocusManager.current
    val fieldFocus = remember { FocusRequester() }
    val letterFocus = remember { FocusRequester() }

    val present = remember(library) {
        ConsoleCatalog.consoles.filter { c -> library.any { it.consoleId == c.id } }
    }
    val letters = remember(library, consoleFilter) {
        library.asSequence()
            .filter { consoleFilter == null || it.consoleId == consoleFilter }
            .map { initialOf(it.title) }
            .toSortedSet(compareBy { if (it == NON_LETTER) ' ' else it })
            .toList()
    }
    // Normalising every title on each keystroke is wasted work; rebuild only when the library does.
    val normalizedTitles = remember(library) {
        library.associate { it.id to GameRepository.normalize(it.title) }
    }
    val results = remember(query, consoleFilter, letter, library, normalizedTitles) {
        val wanted = GameRepository.normalize(query)
        // A lone character is read as "titles starting with", like the letter strip; normalising
        // would drop it entirely when it is an article such as "a".
        val single = query.trim().singleOrNull()
        library.asSequence()
            .filter { consoleFilter == null || it.consoleId == consoleFilter }
            .filter { letter == null || initialOf(it.title) == letter }
            .filter {
                when {
                    single != null -> it.title.trimStart().startsWith(single, ignoreCase = true)
                    wanted.isBlank() -> true
                    else -> normalizedTitles[it.id]?.contains(wanted) == true
                }
            }
            .sortedBy { it.title.lowercase() }
            .toList()
    }
    val focusedGame = results.firstOrNull { it.id == focusedId }
    val backdrop = focusedGame?.consoleId ?: consoleFilter
    val accent = consoleStyle(ConsoleCatalog.get(backdrop)).accent

    // A TV remote has no keyboard of its own, so start on the letter strip there; the text field
    // is one press up and opens the on-screen keyboard only when chosen.
    LaunchedEffect(Unit) {
        delay(150)
        val index = results.indexOfFirst { it.id == focusedId }
        if (index >= 0 && requesters[focusedId] == null) gridState.scrollToItem(index)
        if (index >= 0) withFrameNanos { }
        runCatching {
            requesters[focusedId]?.requestFocus()
                ?: if (isTv && letters.isNotEmpty()) letterFocus.requestFocus() else fieldFocus.requestFocus()
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .retroShortcuts(
                onX = { focusedGame?.let { vm.toggleLibraryFavorite(it) } },
                onY = { results.randomOrNull()?.let { vm.playLibrary(it) } },
                onStart = onSettings
            )
    ) {
        RetroBackdrop(backdrop, scanlines = settings.uiScanlines, dim = 0.35f)
        Column(Modifier.fillMaxSize().padding(safe).imePadding()) {
            RetroTopBar(
                title = stringResource(R.string.search),
                subtitle = pluralStringResource(R.plurals.games_plural, results.size, results.size),
                accent = accent,
                onBack = onBack
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text(stringResource(R.string.search_hint), color = retroOnSurface(0.5f)) },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, tint = accent) },
                    trailingIcon = {
                        if (query.isNotBlank()) {
                            IconButton(onClick = { query = "" }) {
                                Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.cancel), tint = retroOnSurface())
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { focusManager.moveFocus(FocusDirection.Down) }),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = retroOnSurface(),
                        unfocusedTextColor = retroOnSurface(),
                        focusedContainerColor = if (LocalRetroTheme.current) Color.Black.copy(alpha = 0.6f)
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        unfocusedContainerColor = if (LocalRetroTheme.current) Color.Black.copy(alpha = 0.45f)
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        focusedBorderColor = accent,
                        unfocusedBorderColor = if (LocalRetroTheme.current) Color.White.copy(alpha = 0.2f)
                        else MaterialTheme.colorScheme.outlineVariant,
                        cursorColor = accent
                    ),
                    modifier = Modifier.width(420.dp).focusRequester(fieldFocus)
                )
            }
            if (present.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)) {
                    item {
                        RetroChip(stringResource(R.string.all), consoleFilter == null, accent, {
                            consoleFilter = null
                            letter = null
                        })
                    }
                    items(present, key = { it.id }) { console ->
                        RetroChip(
                            console.shortName,
                            consoleFilter == console.id,
                            consoleStyle(console).accent,
                            {
                                consoleFilter = if (consoleFilter == console.id) null else console.id
                                letter = null
                            }
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(5.dp), contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)) {
                    items(letters.size, key = { letters[it] }) { i ->
                        val ch = letters[i]
                        LetterKey(
                            ch,
                            selected = letter == ch,
                            accent = accent,
                            onClick = { letter = if (letter == ch) null else ch },
                            modifier = if (i == 0) Modifier.focusRequester(letterFocus) else Modifier
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (results.isEmpty()) {
                    RetroPanel(Modifier.align(Alignment.Center)) {
                        Column(Modifier.padding(24.dp)) {
                            Text(
                                stringResource(R.string.search_empty_title),
                                style = MaterialTheme.typography.titleMedium,
                                color = retroOnSurface()
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                stringResource(R.string.search_empty_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = retroOnSurface(0.7f)
                            )
                        }
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(112.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 16.dp),
                        state = gridState,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        itemsIndexed(results, key = { _, g -> g.id }) { _, game ->
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
                                showConsole = true,
                                onPlay = { vm.playLibrary(game) },
                                onDetails = { onDetails(game) },
                                onFocused = { focusedId = game.id },
                                focusRequester = requester
                            )
                        }
                    }
                }
            }
            GamepadHintBar(
                hints = listOf(
                    GamepadHint("A", stringResource(R.string.play), HintGreen) { focusedGame?.let { vm.playLibrary(it) } },
                    GamepadHint("B", stringResource(R.string.hint_back), HintRed, onBack),
                    GamepadHint("Y", stringResource(R.string.hint_random), HintYellow) {
                        results.randomOrNull()?.let { vm.playLibrary(it) }
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

@Composable
private fun LetterKey(
    ch: Char,
    selected: Boolean,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val retro = LocalRetroTheme.current
    val shape = RoundedCornerShape(6.dp)
    Box(
        modifier
            .size(32.dp)
            .clip(shape)
            .background(
                when {
                    focused -> accent
                    selected -> accent.copy(alpha = 0.35f)
                    retro -> Color.Black.copy(alpha = 0.45f)
                    else -> MaterialTheme.colorScheme.surfaceVariant
                }
            )
            .border(
                if (focused) 2.dp else 1.dp,
                when {
                    focused || selected -> retroOnSurface()
                    retro -> Color.White.copy(alpha = 0.12f)
                    else -> MaterialTheme.colorScheme.outlineVariant
                },
                shape
            )
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            ch.toString(),
            fontFamily = PixelFont,
            fontWeight = FontWeight.Normal,
            fontSize = 11.sp,
            color = if (focused) RetroInk else retroOnSurface()
        )
    }
}
