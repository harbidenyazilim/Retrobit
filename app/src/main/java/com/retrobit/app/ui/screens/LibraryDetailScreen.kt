package com.retrobit.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.retrobit.app.AppViewModel
import com.retrobit.app.R
import com.retrobit.app.data.ConsoleCatalog
import com.retrobit.app.data.ConsoleInfo
import com.retrobit.app.data.GameMetadata
import com.retrobit.app.data.LibraryGame
import com.retrobit.app.ui.components.RetroPanel
import androidx.compose.ui.res.pluralStringResource
import com.retrobit.app.data.SettingsData
import com.retrobit.app.ui.components.ConsoleStyle
import com.retrobit.app.ui.components.ConsoleWordmark
import com.retrobit.app.ui.components.GamepadHint
import com.retrobit.app.ui.components.GamepadHintBar
import com.retrobit.app.ui.components.HintBlue
import com.retrobit.app.ui.components.HintGreen
import com.retrobit.app.ui.components.HintRed
import com.retrobit.app.ui.components.LocalSafeArea
import com.retrobit.app.ui.components.MetaCell
import com.retrobit.app.ui.components.PerformanceNote
import com.retrobit.app.ui.components.PreviewWithCover
import com.retrobit.app.ui.components.TurkishFlag
import com.retrobit.app.ui.components.RetroBackdrop
import com.retrobit.app.ui.components.RetroNavButton
import com.retrobit.app.ui.components.RetroTopBar
import com.retrobit.app.ui.components.consoleStyle
import com.retrobit.app.ui.components.formatBytes
import com.retrobit.app.ui.components.GameMetadataCells
import androidx.compose.ui.platform.LocalUriHandler
import com.retrobit.app.ui.components.formatLabel
import com.retrobit.app.ui.components.onAccent
import com.retrobit.app.ui.components.playTimeLabel
import com.retrobit.app.ui.components.retroOnSurface
import com.retrobit.app.ui.components.retroShortcuts
import com.retrobit.app.ui.components.rememberLocalSnap
import com.retrobit.app.ui.components.snapUrlFor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LibraryDetailScreen(
    vm: AppViewModel,
    isTr: Boolean,
    libraryId: String,
    onBack: () -> Unit
) {
    val library by vm.library.collectAsStateWithLifecycle()
    val favorites by vm.favorites.collectAsStateWithLifecycle()
    val covers by vm.coverUrls.collectAsStateWithLifecycle()
    val metadataState by vm.gameMetadata.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current
    val installedCores by vm.installedCores.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val safe = LocalSafeArea.current
    val game = library.firstOrNull { it.id == libraryId }

    if (game == null) {
        Box(Modifier.fillMaxSize()) {
            RetroBackdrop(null, scanlines = settings.uiScanlines, dim = 0.4f)
            Column(Modifier.fillMaxSize().padding(safe)) {
                RetroTopBar(stringResource(R.string.details), null, HintBlue, onBack)
                Text(stringResource(R.string.error_generic), color = retroOnSurface())
            }
        }
        return
    }

    var confirmRemove by remember { mutableStateOf(false) }
    val pickCover = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) vm.setCustomCover(game.id, uri)
    }
    val playFocus = remember { FocusRequester() }
    val console = ConsoleCatalog.get(game.consoleId)
    val style = consoleStyle(console)
    var fileSize by remember { mutableStateOf(-2L) }
    LaunchedEffect(game.filePath) {
        fileSize = withContext(Dispatchers.IO) { com.retrobit.app.data.romSizeOnDisk(game.filePath) }
    }
    val exists = fileSize != -1L
    val favorite = game.isFavorite(favorites)
    val coverUrl = covers["lib:${game.id}"] ?: game.coverUrl
    val dateFormat = remember { DateFormat.getDateInstance(DateFormat.MEDIUM) }
    val metadata = metadataState[game.id] ?: vm.metadataFor(game)
    LaunchedEffect(game.id, game.title, game.consoleId) { vm.ensureMetadata(game) }

    LaunchedEffect(game.id) { vm.ensureCover(game) }
    // A save-state capture shows where the player left off, so it outranks the stock screenshot.
    val snap: Any? = rememberLocalSnap(game.id, game.lastPlayedAt) ?: snapUrlFor(coverUrl)

    Box(
        Modifier
            .fillMaxSize()
            .retroShortcuts(onX = { vm.toggleLibraryFavorite(game) })
    ) {
        RetroBackdrop(game.consoleId, scanlines = settings.uiScanlines, dim = 0.25f)

        Column(Modifier.fillMaxSize().padding(safe)) {
            RetroTopBar(
                title = (console?.localizedName(isTr) ?: game.consoleId).uppercase(java.util.Locale.ROOT),
                subtitle = game.fileName.ifBlank { null },
                accent = style.accent,
                onBack = onBack
            )

            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                val wide = maxWidth >= 720.dp
                if (wide) {
                    // Opening this screen means the player is next; the first controller press should
                    // start the game. Only in the wide layout: on short phone screens the focus reveal
                    // would scroll past the artwork, so those screens open at the top instead.
                    LaunchedEffect(libraryId) {
                        delay(220)
                        runCatching { playFocus.requestFocus() }
                    }
                    Row(Modifier.fillMaxSize()) {
                        PreviewWithCover(
                            snapUrl = snap,
                            console = console,
                            scanlines = settings.uiScanlines,
                            coverUrl = coverUrl,
                            title = game.title,
                            gameId = game.id,
                            framed = settings.supporterUnlocked,
                            coverHeight = 190.dp,
                            modifier = Modifier.weight(0.46f).fillMaxHeight().padding(end = 26.dp)
                        )
                        DetailBody(
                            vm = vm,
                            game = game,
                            library = library,
                            console = console,
                            style = style,
                            metadata = metadata,
                            exists = exists,
                            favorite = favorite,
                            fileSize = fileSize,
                            settings = settings,
                            installedCores = installedCores,
                            isTr = isTr,
                            dateFormat = dateFormat,
                            playFocus = playFocus,
                            onPickCover = { runCatching { pickCover.launch("image/*") } },
                            onRequestRemove = { confirmRemove = true },
                            modifier = Modifier.weight(0.54f).fillMaxHeight()
                                .verticalScroll(rememberScrollState())
                        )
                    }
                } else {
                    val coverHeight = (maxWidth * 0.38f).coerceIn(128.dp, 196.dp)
                    // The stacked layout scrolls, but the artwork should still fit the first
                    // screen and leave a hint of the title below instead of opening mid-hero.
                    val heroCap = maxHeight * 0.86f
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                        PreviewWithCover(
                            snapUrl = snap,
                            console = console,
                            scanlines = settings.uiScanlines,
                            coverUrl = coverUrl,
                            title = game.title,
                            gameId = game.id,
                            framed = settings.supporterUnlocked,
                            coverHeight = coverHeight,
                            maxBlockHeight = heroCap,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(14.dp))
                        DetailBody(
                            vm = vm,
                            game = game,
                            library = library,
                            console = console,
                            style = style,
                            metadata = metadata,
                            exists = exists,
                            favorite = favorite,
                            fileSize = fileSize,
                            settings = settings,
                            installedCores = installedCores,
                            isTr = isTr,
                            dateFormat = dateFormat,
                            playFocus = playFocus,
                            onPickCover = { runCatching { pickCover.launch("image/*") } },
                            onRequestRemove = { confirmRemove = true },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            GamepadHintBar(
                hints = listOf(
                    GamepadHint("A", stringResource(R.string.hint_select), HintGreen),
                    GamepadHint("B", stringResource(R.string.hint_back), HintRed, onBack),
                    GamepadHint("X", stringResource(R.string.hint_favorite), HintBlue) { vm.toggleLibraryFavorite(game) }
                ),
                modifier = Modifier.padding(top = 10.dp, bottom = 2.dp)
            )
        }
    }

    if (confirmRemove) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text(stringResource(R.string.remove_from_library)) },
            text = { Text(stringResource(R.string.remove_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    vm.removeLibraryGame(game)
                    confirmRemove = false
                    onBack()
                }) { Text(stringResource(R.string.remove)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmRemove = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DetailBody(
    vm: AppViewModel,
    game: LibraryGame,
    library: List<LibraryGame>,
    console: ConsoleInfo?,
    style: ConsoleStyle,
    metadata: GameMetadata?,
    exists: Boolean,
    favorite: Boolean,
    fileSize: Long,
    settings: SettingsData,
    installedCores: Set<String>,
    isTr: Boolean,
    dateFormat: DateFormat,
    playFocus: FocusRequester,
    onPickCover: () -> Unit,
    onRequestRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uriHandler = LocalUriHandler.current
    Column(modifier) {
        ConsoleWordmark(console, size = 22.sp, showStrip = true)
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                game.title,
                style = MaterialTheme.typography.headlineMedium,
                color = retroOnSurface(),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            if (game.isTurkishRelease) {
                TurkishFlag(20.dp, Modifier.padding(start = 10.dp))
            }
        }
        Spacer(Modifier.height(8.dp))
        StatusLine(
            when {
                fileSize == -1L -> stringResource(R.string.file_missing)
                settings.playerMode != SettingsData.PLAYER_INTERNAL -> null
                game.consoleId in installedCores -> stringResource(R.string.rom_ready)
                else -> stringResource(R.string.rom_core_pending)
            },
            when {
                fileSize == -1L -> HintRed
                game.consoleId in installedCores -> HintGreen
                else -> HintRed
            }
        )
        PerformanceNote(game.consoleId)
        Spacer(Modifier.height(14.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            PlayButton(
                label = stringResource(R.string.play),
                accent = style.accent,
                enabled = exists,
                onClick = { vm.playLibrary(game) },
                modifier = Modifier.focusRequester(playFocus)
            )
            RetroNavButton(
                if (favorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                stringResource(if (favorite) R.string.remove_favorite else R.string.add_favorite),
                style.accent,
                { vm.toggleLibraryFavorite(game) },
                modifier = Modifier.height(48.dp)
            )
        }
        Spacer(Modifier.height(16.dp))
        GameMetadataCells(metadata, style.accent)
        metadata?.players?.takeIf { it.isNotBlank() }?.let { players ->
            Spacer(Modifier.height(6.dp))
            MetaCell(stringResource(R.string.game_meta_players), players, style.accent, Modifier.fillMaxWidth())
        }
        metadata?.developer?.takeIf { it.isNotBlank() }?.let { developer ->
            Spacer(Modifier.height(6.dp))
            MetaCell(
                stringResource(R.string.game_meta_developer), developer, style.accent,
                Modifier.fillMaxWidth()
            )
        }
        if (metadata != null) {
            val sourceLabel = stringResource(
                if (metadata.source == "wikidata") R.string.game_metadata_source_wikidata
                else R.string.game_metadata_source_catalog
            )
            val sourceUrl = metadata.sourceUrl
            if (sourceUrl != null) {
                TextButton(onClick = { runCatching { uriHandler.openUri(sourceUrl) } }) {
                    Text(sourceLabel, style = MaterialTheme.typography.labelSmall)
                }
            } else {
                Text(
                    sourceLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = retroOnSurface(0.55f),
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            MetaCell(stringResource(R.string.console), console?.shortName ?: game.consoleId, style.accent, Modifier.weight(1f))
            MetaCell(stringResource(R.string.meta_format), game.formatLabel(), style.accent, Modifier.weight(1f))
            MetaCell(
                stringResource(R.string.file_size),
                if (fileSize >= 0) formatBytes(fileSize) else "—",
                style.accent,
                Modifier.weight(1f)
            )
        }
        Text(
            stringResource(R.string.added_on) + ": " + dateFormat.format(Date(game.addedAt)),
            style = MaterialTheme.typography.labelSmall,
            color = retroOnSurface(0.55f),
            modifier = Modifier.padding(top = 6.dp)
        )
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            MetaCell(stringResource(R.string.play_count), game.playCount.toString(), style.accent, Modifier.weight(1f))
            MetaCell(stringResource(R.string.stats_playtime), playTimeLabel(game.playSeconds), style.accent, Modifier.weight(1f))
            MetaCell(
                stringResource(R.string.last_played),
                if (game.lastPlayedAt > 0) dateFormat.format(Date(game.lastPlayedAt))
                else stringResource(R.string.never),
                style.accent,
                Modifier.weight(1f)
            )
        }
        Spacer(Modifier.height(14.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (settings.supporterUnlocked) {
                RetroNavButton(
                    Icons.Rounded.Image,
                    stringResource(R.string.cover_change),
                    style.accent,
                    onPickCover
                )
                if (settings.customCovers.containsKey(game.id)) {
                    RetroNavButton(
                        Icons.Rounded.Restore,
                        stringResource(R.string.cover_reset),
                        style.accent,
                        { vm.clearCustomCover(game.id) }
                    )
                }
            }
            RetroNavButton(
                Icons.Rounded.Delete,
                stringResource(R.string.remove_from_library),
                HintRed,
                onRequestRemove
            )
        }
        if (console != null) {
            Spacer(Modifier.height(16.dp))
            SystemPanel(console, game, library, style.accent, isTr)
        }
    }
}

/** The system the game belongs to, and how much of the player's time that system has had. */
@Composable
private fun SystemPanel(
    console: ConsoleInfo,
    game: LibraryGame,
    library: List<LibraryGame>,
    accent: Color,
    isTr: Boolean
) {
    val siblings = remember(library, console.id) { library.filter { it.consoleId == console.id } }
    val totalSeconds = remember(siblings) { siblings.sumOf { it.playSeconds } }
    RetroPanel(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(6.dp).background(accent))
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.detail_about_system),
                    style = MaterialTheme.typography.labelSmall,
                    letterSpacing = 2.sp,
                    color = accent
                )
                Spacer(Modifier.weight(1f))
                Text(
                    listOf(console.maker, console.year.takeIf { it > 0 }?.toString())
                        .filterNotNull().filter { it.isNotBlank() }
                        .joinToString("  ·  ")
                        .uppercase(java.util.Locale.ROOT),
                    style = MaterialTheme.typography.labelSmall,
                    letterSpacing = 2.sp,
                    color = retroOnSurface(0.6f)
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                console.localizedName(isTr),
                style = MaterialTheme.typography.titleSmall,
                color = retroOnSurface()
            )
            Text(
                console.localizedDescription(isTr),
                style = MaterialTheme.typography.bodySmall,
                color = retroOnSurface(0.72f),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp)
            )
            Spacer(Modifier.height(8.dp))
            val parts = buildList {
                add(pluralStringResource(R.plurals.games_plural, siblings.size, siblings.size))
                if (totalSeconds >= 60) add(stringResource(R.string.detail_system_total, playTimeLabel(totalSeconds)))
                if (game.playCount > 0 && game.playSeconds / game.playCount >= 60) {
                    add(stringResource(R.string.detail_avg_session, playTimeLabel(game.playSeconds / game.playCount)))
                }
            }
            Text(
                parts.joinToString("  ·  "),
                style = MaterialTheme.typography.labelMedium,
                color = accent
            )
        }
    }
}

@Composable
private fun StatusLine(text: String?, color: Color) {
    if (text == null) return
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.labelMedium, color = retroOnSurface(0.8f))
    }
}

@Composable
private fun PlayButton(
    label: String,
    accent: Color,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Rounded.PlayArrow
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(24.dp)
    val fill = if (enabled) accent else retroOnSurface(0.2f)
    Row(
        modifier
            .height(48.dp)
            .clip(shape)
            .background(fill)
            .border(if (focused) 3.dp else 0.dp, if (focused) retroOnSurface() else Color.Transparent, shape)
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, onClick = onClick)
            .padding(horizontal = 26.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val content = if (enabled) onAccent(accent) else retroOnSurface(0.6f)
        Icon(icon, null, tint = content, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.titleMedium, color = content)
    }
}
