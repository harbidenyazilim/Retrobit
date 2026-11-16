package com.retrobit.app.ui.screens
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import android.app.Activity
import android.view.KeyEvent
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Collections
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.retrobit.app.AppViewModel
import com.retrobit.app.BuildConfig
import com.retrobit.app.R
import com.retrobit.app.data.ConsoleCatalog
import com.retrobit.app.data.ConsoleInfo
import com.retrobit.app.data.SaveBackup
import com.retrobit.app.data.SettingsData
import com.retrobit.app.ui.components.BackdropImage
import com.retrobit.app.ui.components.ConsoleGlyph
import com.retrobit.app.ui.components.ConsoleWordmark
import com.retrobit.app.ui.components.LocalIsTv
import com.retrobit.app.ui.components.LocalReducedMotion
import com.retrobit.app.ui.components.LocalRetroTheme
import com.retrobit.app.ui.components.RequestFocusOnWindowFocus
import com.retrobit.app.ui.components.RetroInk
import com.retrobit.app.ui.components.consoleStyle
import com.retrobit.app.ui.components.crtOverlay
import com.retrobit.app.ui.components.onAccent
import com.retrobit.app.ui.theme.PixelFont
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import com.retrobit.app.ui.components.RomZipImportCard
import com.retrobit.app.ui.components.ScreenHeader
import com.retrobit.app.ui.components.consoleColor
import com.retrobit.app.ui.components.directFileAccessAvailable
import com.retrobit.app.ui.components.formatBytes
import com.retrobit.app.ui.components.rememberImportLauncher
import com.retrobit.app.ui.components.rememberRomZipLauncher
import com.retrobit.app.ui.components.rememberStorageAccessRequester
import com.retrobit.app.ui.components.tvFocus
import com.retrobit.app.ui.components.GamepadInput
import com.retrobit.app.ui.components.GamepadKeyGuard
import androidx.compose.ui.input.key.onPreviewKeyEvent

// Section headers take the colour of the system the player was last in, so Settings feels
// like part of that console's world instead of a generic Material sheet.
private val LocalSectionAccent = compositionLocalOf<Color?> { null }

private data class SettingsTab(val icon: ImageVector, @StringRes val labelRes: Int)

/**
 * The tab strip is a lazy row and Settings animates in, so a focus request can land before the
 * window is active or the chip is attached and silently not stick. Keep requesting across frames
 * until the tab actually reports focus, otherwise the first shoulder press would be swallowed.
 */
private suspend fun focusTabWhenReady(requester: FocusRequester, target: Int, focusedTab: () -> Int) {
    repeat(90) {
        withFrameNanos { }
        runCatching { requester.requestFocus() }
        if (focusedTab() == target) return
    }
}

/** A settings tab pill: filled with its accent when active, calm outline otherwise. */
@Composable
private fun SettingsTabChip(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    accent: Color?,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val tint = accent ?: MaterialTheme.colorScheme.primary
    val shape = RoundedCornerShape(50)
    val ink = if (selected) onAccent(tint) else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier
            .clip(shape)
            .background(if (selected) tint else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.30f))
            .border(
                1.dp,
                if (selected) tint else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                shape
            )
            .tvFocus(shape, if (selected) Color.White else tint)
            .clickable(onClick = onClick)
            .padding(horizontal = 15.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = ink, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(7.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) ink else MaterialTheme.colorScheme.onSurface,
            maxLines = 1
        )
    }
}

@Composable
fun SettingsScreen(
    vm: AppViewModel,
    isTr: Boolean,
    onBack: () -> Unit,
    onStats: () -> Unit,
    onSupporters: () -> Unit,
    onShowcase: () -> Unit
) {
    val library by vm.library.collectAsStateWithLifecycle()
    val lastConsole = remember(library) {
        library.filter { it.lastPlayedAt > 0 }.maxByOrNull { it.lastPlayedAt }?.consoleId
    }
    val accent = if (LocalRetroTheme.current) consoleStyle(ConsoleCatalog.get(lastConsole)).accent else null
    CompositionLocalProvider(LocalSectionAccent provides accent) {
        SettingsContent(vm, isTr, onBack, onStats, onSupporters, onShowcase)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SettingsContent(
    vm: AppViewModel,
    isTr: Boolean,
    onBack: () -> Unit,
    onStats: () -> Unit,
    onSupporters: () -> Unit,
    onShowcase: () -> Unit
) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val supporterPrice by vm.supporterPrice.collectAsStateWithLifecycle()
    val storageUsage by vm.storageUsage.collectAsStateWithLifecycle()
    val emulators by vm.emulators.collectAsStateWithLifecycle()
    val library by vm.library.collectAsStateWithLifecycle()
    val consoleCounts = remember(library) { library.groupingBy { it.consoleId }.eachCount() }
    val sortableConsoles = remember(library, settings.consoleSort, settings.consoleCustomOrder, isTr) {
        val visible = ConsoleCatalog.consoles
        ConsoleCatalog.sortSystems(
            visible,
            settings.consoleSort,
            settings.consoleCustomOrder,
            consoleCounts,
            isTr
        )
    }
    fun moveConsole(id: String, delta: Int) {
        val ids = sortableConsoles.map { it.id }.toMutableList()
        val from = ids.indexOf(id)
        val to = from + delta
        if (from < 0 || to !in ids.indices) return
        ids[from] = ids[to].also { ids[to] = ids[from] }
        vm.setConsoleSort(SettingsData.CONSOLE_SORT_CUSTOM)
        vm.setConsoleCustomOrder(ids)
    }
    val installedCores by vm.installedCores.collectAsStateWithLifecycle()
    val legacyCoresBytes by vm.legacyCoresBytes.collectAsStateWithLifecycle()
    val importing by vm.importing.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    val isTv = LocalIsTv.current
    val buyFocus = remember { FocusRequester() }
    fun showLockedHint() {
        android.widget.Toast.makeText(context, R.string.supporter_locked_hint, android.widget.Toast.LENGTH_SHORT).show()
        runCatching { buyFocus.requestFocus() }
    }
    val pickFiles = rememberImportLauncher(vm)
    val pickZip = rememberRomZipLauncher(vm)
    var confirmClear by remember { mutableStateOf(false) }
    var confirmClearCovers by remember { mutableStateOf(false) }
    var confirmRemoveCores by remember { mutableStateOf(false) }
    var showRestoreDialog by remember { mutableStateOf(false) }
    var pendingRestore by remember { mutableStateOf<SaveBackup?>(null) }
    var showGamepadRemapDialog by remember { mutableStateOf(false) }
    var showCdKeyDialog by remember { mutableStateOf(false) }
    var showNameDialog by remember { mutableStateOf(false) }
    var showPremiumPreview by remember { mutableStateOf(false) }
    var cdKeyError by remember { mutableStateOf(false) }
    val directAccess = directFileAccessAvailable()
    val backupWithAccess = rememberStorageAccessRequester { vm.backupSaves() }
    val backupAction: () -> Unit = if (directAccess) backupWithAccess else ({ vm.backupSaves() })
    val tabs = remember {
        listOf(
            SettingsTab(Icons.Rounded.Favorite, R.string.settings_tab_supporter),
            SettingsTab(Icons.Rounded.VideoLibrary, R.string.library),
            SettingsTab(Icons.Rounded.Palette, R.string.settings_appearance),
            SettingsTab(Icons.Rounded.PlayArrow, R.string.player),
            SettingsTab(Icons.Rounded.SportsEsports, R.string.settings_tab_gamepad),
            SettingsTab(Icons.Rounded.Save, R.string.settings_tab_storage),
            SettingsTab(Icons.Rounded.Info, R.string.about)
        )
    }
    var selectedTab by rememberSaveable { mutableStateOf(0) }
    var focusedTab by remember { mutableStateOf(-1) }
    val tabFocus = remember { List(tabs.size) { FocusRequester() } }
    val tabListState = rememberLazyListState()
    val listState = rememberLazyListState()
    LaunchedEffect(selectedTab) { listState.scrollToItem(0) }
    // Settings loads asynchronously, so isTv can flip false->true after the first frame (the box
    // has no leanback flag and the TV layout comes from DataStore). Re-run when it settles so the
    // tab strip is focused on entry and D-pad left/right switches tabs immediately.
    LaunchedEffect(isTv) {
        if (isTv) focusTabWhenReady(tabFocus[selectedTab], selectedTab) { focusedTab }
    }
    // Also take focus whenever the window (re)gains it, e.g. returning from a dialog or the picker.
    RequestFocusOnWindowFocus(tabFocus[selectedTab])
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 36.dp)
    ) {
        item {
            ScreenHeader(
                title = stringResource(R.string.settings),
                onBack = onBack
            )
        }
        item {
            LazyRow(
                state = tabListState,
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(tabs.size) { index ->
                    SettingsTabChip(
                        icon = tabs[index].icon,
                        label = stringResource(tabs[index].labelRes),
                        selected = index == selectedTab,
                        accent = if (index == 0) SupporterRose else LocalSectionAccent.current,
                        modifier = Modifier
                            .focusRequester(tabFocus[index])
                            .onFocusChanged { st ->
                                if (st.isFocused) {
                                    selectedTab = index
                                    focusedTab = index
                                } else if (focusedTab == index) {
                                    focusedTab = -1
                                }
                            },
                        onClick = { selectedTab = index }
                    )
                }
            }
        }
        item {
            Text(
                stringResource(R.string.settings_tabs_hint),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 20.dp, top = 7.dp, bottom = 2.dp)
            )
        }
        if (selectedTab == 0) {
            item {
                SupporterPitchSection(
                    vm = vm,
                    settings = settings,
                    supporterPrice = supporterPrice,
                    buyFocus = buyFocus,
                    onPreview = { showPremiumPreview = true },
                    onCdKey = { showCdKeyDialog = true }
                )
            }
            item {
                SupporterInterfaceSection(
                    vm = vm,
                    settings = settings,
                    onPreview = { showPremiumPreview = true },
                    onLockedClick = ::showLockedHint
                )
            }
            item {
                SupporterLooksSection(
                    vm = vm,
                    settings = settings,
                    onLockedClick = ::showLockedHint
                )
            }
            item {
                SupporterMoreSection(
                    settings = settings,
                    onLockedClick = ::showLockedHint,
                    onStats = onStats,
                    onShowcase = onShowcase,
                    onSupporters = onSupporters,
                    onName = { showNameDialog = true }
                )
            }
        }
        if (selectedTab == 1) item {
            SettingsSection(title = stringResource(R.string.library)) {
                RomZipImportCard(onPick = pickZip, importing = importing)
                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick = pickFiles,
                    enabled = !importing,
                    modifier = Modifier.fillMaxWidth().tvFocus(RoundedCornerShape(12.dp)),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 9.dp)
                ) {
                    Icon(Icons.Rounded.FileDownload, null, Modifier.size(15.dp))
                    Spacer(Modifier.width(5.dp))
                    Text(stringResource(R.string.import_files), style = MaterialTheme.typography.labelMedium)
                }
            }
        }
        if (selectedTab == 2) item {
            SettingsSection(title = stringResource(R.string.settings_appearance)) {
                SettingLabel(stringResource(R.string.console_sort_title))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ChoiceChip(
                        label = stringResource(R.string.console_sort_count),
                        selected = settings.consoleSort == SettingsData.CONSOLE_SORT_COUNT,
                        onClick = { vm.setConsoleSort(SettingsData.CONSOLE_SORT_COUNT) }
                    )
                    ChoiceChip(
                        label = stringResource(R.string.console_sort_year),
                        selected = settings.consoleSort == SettingsData.CONSOLE_SORT_YEAR,
                        onClick = { vm.setConsoleSort(SettingsData.CONSOLE_SORT_YEAR) }
                    )
                    ChoiceChip(
                        label = stringResource(R.string.console_sort_name),
                        selected = settings.consoleSort == SettingsData.CONSOLE_SORT_NAME,
                        onClick = { vm.setConsoleSort(SettingsData.CONSOLE_SORT_NAME) }
                    )
                    ChoiceChip(
                        label = stringResource(R.string.console_sort_custom),
                        selected = settings.consoleSort == SettingsData.CONSOLE_SORT_CUSTOM,
                        onClick = { vm.setConsoleSort(SettingsData.CONSOLE_SORT_CUSTOM) }
                    )
                }
                Text(
                    stringResource(R.string.console_sort_desc),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
                if (settings.consoleSort == SettingsData.CONSOLE_SORT_CUSTOM) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        stringResource(R.string.console_order_hint),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))
                    sortableConsoles.forEachIndexed { i, console ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 1.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                console.localizedName(isTr),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                (consoleCounts[console.id] ?: 0).toString(),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(end = 8.dp)
                            )
                            IconButton(
                                onClick = { moveConsole(console.id, -1) },
                                enabled = i > 0,
                                modifier = Modifier.tvFocus(CircleShape)
                            ) {
                                Icon(
                                    Icons.Rounded.KeyboardArrowUp,
                                    contentDescription = stringResource(R.string.console_move_up),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            IconButton(
                                onClick = { moveConsole(console.id, 1) },
                                enabled = i < sortableConsoles.lastIndex,
                                modifier = Modifier.tvFocus(CircleShape)
                            ) {
                                Icon(
                                    Icons.Rounded.KeyboardArrowDown,
                                    contentDescription = stringResource(R.string.console_move_down),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                    TextButton(onClick = { vm.setConsoleCustomOrder(emptyList()) }) {
                        Text(stringResource(R.string.console_order_reset))
                    }
                }
                Spacer(Modifier.height(14.dp))
                SettingSwitchRow(
                    title = stringResource(R.string.hide_empty_consoles),
                    description = stringResource(R.string.hide_empty_consoles_desc),
                    checked = settings.hideEmptyConsoles,
                    onCheckedChange = { vm.setHideEmptyConsoles(it) }
                )
                Spacer(Modifier.height(14.dp))
                SettingSwitchRow(
                    title = stringResource(R.string.ui_sounds_title),
                    description = stringResource(R.string.ui_sounds_desc),
                    checked = settings.uiSounds,
                    onCheckedChange = { vm.setUiSounds(it) }
                )
                Spacer(Modifier.height(14.dp))
                SettingLabel(stringResource(R.string.language))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ChoiceChip(
                        label = stringResource(R.string.language_system),
                        selected = settings.language == SettingsData.LANGUAGE_SYSTEM,
                        onClick = { vm.setLanguage(SettingsData.LANGUAGE_SYSTEM) }
                    )
                    ChoiceChip(
                        label = stringResource(R.string.language_en),
                        selected = settings.language == SettingsData.LANGUAGE_EN,
                        onClick = { vm.setLanguage(SettingsData.LANGUAGE_EN) }
                    )
                    ChoiceChip(
                        label = stringResource(R.string.language_tr),
                        selected = settings.language == SettingsData.LANGUAGE_TR,
                        onClick = { vm.setLanguage(SettingsData.LANGUAGE_TR) }
                    )
                }
                Spacer(Modifier.height(12.dp))
                SettingLabel(stringResource(R.string.theme))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ChoiceChip(
                        label = stringResource(R.string.theme_system),
                        selected = settings.effectiveTheme == SettingsData.THEME_SYSTEM,
                        onClick = { vm.setTheme(SettingsData.THEME_SYSTEM) }
                    )
                    ChoiceChip(
                        label = stringResource(R.string.theme_dark),
                        selected = settings.effectiveTheme == SettingsData.THEME_DARK,
                        onClick = { vm.setTheme(SettingsData.THEME_DARK) }
                    )
                    ChoiceChip(
                        label = stringResource(R.string.theme_light),
                        selected = settings.effectiveTheme == SettingsData.THEME_LIGHT,
                        onClick = { vm.setTheme(SettingsData.THEME_LIGHT) }
                    )
                }
                Spacer(Modifier.height(12.dp))
                SettingLabel(stringResource(R.string.layout_mode))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ChoiceChip(
                        label = stringResource(R.string.layout_auto),
                        selected = settings.layoutMode == SettingsData.LAYOUT_AUTO,
                        onClick = { vm.setLayoutMode(SettingsData.LAYOUT_AUTO) }
                    )
                    ChoiceChip(
                        label = stringResource(R.string.layout_phone),
                        selected = settings.layoutMode == SettingsData.LAYOUT_PHONE,
                        onClick = { vm.setLayoutMode(SettingsData.LAYOUT_PHONE) }
                    )
                    ChoiceChip(
                        label = stringResource(R.string.layout_tv),
                        selected = settings.layoutMode == SettingsData.LAYOUT_TV,
                        onClick = { vm.setLayoutMode(SettingsData.LAYOUT_TV) }
                    )
                }
                Text(
                    stringResource(R.string.layout_mode_desc),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
        if (selectedTab == 3) item {
            SettingsSection(title = stringResource(R.string.player)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ChoiceChip(
                        label = stringResource(R.string.player_mode_internal),
                        selected = settings.playerMode == SettingsData.PLAYER_INTERNAL,
                        onClick = { vm.setPlayerMode(SettingsData.PLAYER_INTERNAL) }
                    )
                    ChoiceChip(
                        label = stringResource(R.string.player_mode_external),
                        selected = settings.playerMode == SettingsData.PLAYER_EXTERNAL,
                        onClick = { vm.setPlayerMode(SettingsData.PLAYER_EXTERNAL) }
                    )
                }
                Text(
                    stringResource(R.string.player_mode_desc),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp)
                )
                if (settings.playerMode == SettingsData.PLAYER_INTERNAL) {
                    Spacer(Modifier.height(14.dp))
                    SettingLabel(stringResource(R.string.settings_startup_title))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ChoiceChip(
                            label = stringResource(R.string.settings_startup_resume),
                            selected = settings.startupBehavior == SettingsData.STARTUP_RESUME,
                            onClick = { vm.setStartupBehavior(SettingsData.STARTUP_RESUME) }
                        )
                        ChoiceChip(
                            label = stringResource(R.string.settings_startup_fresh),
                            selected = settings.startupBehavior == SettingsData.STARTUP_FRESH,
                            onClick = { vm.setStartupBehavior(SettingsData.STARTUP_FRESH) }
                        )
                        ChoiceChip(
                            label = stringResource(R.string.settings_startup_ask),
                            selected = settings.startupBehavior == SettingsData.STARTUP_ASK,
                            onClick = { vm.setStartupBehavior(SettingsData.STARTUP_ASK) }
                        )
                    }
                    Text(
                        stringResource(R.string.settings_startup_desc),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                    Spacer(Modifier.height(14.dp))
                    SettingSwitchRow(
                        title = stringResource(R.string.settings_auto_save_title),
                        description = stringResource(R.string.settings_auto_save_desc),
                        checked = settings.autoSaveState,
                        onCheckedChange = { vm.setAutoSaveState(it) }
                    )
                    Spacer(Modifier.height(14.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(R.string.builtin_cores),
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.weight(1f)
                        )
                        if (legacyCoresBytes > 0L) {
                            TextButton(
                                onClick = { confirmRemoveCores = true },
                                contentPadding = PaddingValues(horizontal = 6.dp)
                            ) {
                                Icon(Icons.Rounded.Delete, null, Modifier.size(15.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    stringResource(R.string.remove_cores) + " (" + formatBytes(legacyCoresBytes) + ")",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(7.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        vm.builtinCores.forEach { core ->
                            val ready = installedCores.contains(core.consoleId)
                            val console = ConsoleCatalog.get(core.consoleId)
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                            ) {
                                Row(
                                    Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (ready) consoleColor(console)
                                                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                            )
                                    )
                                    Text(
                                        "  ${console?.shortName ?: core.consoleId} · ${core.displayName}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (ready) MaterialTheme.colorScheme.onSurface
                                        else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                } else {
                    Spacer(Modifier.height(14.dp))
                    SettingLabel(stringResource(R.string.default_emulator))
                    EmulatorRow(
                        label = stringResource(R.string.auto_detect),
                        detail = stringResource(R.string.emulator_chooser_hint),
                        selected = settings.defaultEmulator.isBlank(),
                        onClick = { vm.setDefaultEmulator("") }
                    )
                    emulators.forEach { emulator ->
                        EmulatorRow(
                            label = emulator.label,
                            detail = emulator.packageName,
                            selected = settings.defaultEmulator == emulator.packageName,
                            onClick = { vm.setDefaultEmulator(emulator.packageName) }
                        )
                    }
                    if (emulators.isEmpty()) {
                        Text(
                            stringResource(R.string.no_emulator_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }
                    TextButton(
                        onClick = {
                            if (emulators.isEmpty()) {
                                uriHandler.openUri(
                                    "https://play.google.com/store/apps/details?id=com.retroarch"
                                )
                            } else {
                                vm.refreshEmulators()
                            }
                        },
                        contentPadding = PaddingValues(horizontal = 0.dp)
                    ) {
                        if (emulators.isEmpty()) {
                            Icon(Icons.Rounded.OpenInNew, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(5.dp))
                            Text(stringResource(R.string.install_retroarch))
                        } else {
                            Text(stringResource(R.string.refresh))
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
                SettingLabel(stringResource(R.string.settings_shader))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ChoiceChip(
                        label = stringResource(R.string.player_shader_default),
                        selected = settings.defaultShader == SettingsData.SHADER_DEFAULT,
                        onClick = { vm.setDefaultShader(SettingsData.SHADER_DEFAULT) }
                    )
                    ChoiceChip(
                        label = stringResource(R.string.player_shader_crt),
                        selected = settings.defaultShader == SettingsData.SHADER_CRT,
                        onClick = { vm.setDefaultShader(SettingsData.SHADER_CRT) }
                    )
                    ChoiceChip(
                        label = stringResource(R.string.player_shader_lcd),
                        selected = settings.defaultShader == SettingsData.SHADER_LCD,
                        onClick = { vm.setDefaultShader(SettingsData.SHADER_LCD) }
                    )
                    ChoiceChip(
                        label = stringResource(R.string.player_shader_sharp),
                        selected = settings.defaultShader == SettingsData.SHADER_SHARP,
                        onClick = { vm.setDefaultShader(SettingsData.SHADER_SHARP) }
                    )
                }
                Text(
                    stringResource(R.string.settings_shader_desc),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        }

        if (selectedTab == 4) item {
            SettingsSection(title = stringResource(R.string.settings_gamepad)) {
                SettingsActionRow(
                    icon = Icons.Rounded.SportsEsports,
                    label = stringResource(R.string.gamepad_test),
                    locked = false,
                    onClick = { context.startActivity(android.content.Intent(context, GamepadTestActivity::class.java)) }
                )
                SettingLabel(stringResource(R.string.settings_gamepad_layout))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ChoiceChip(
                        label = stringResource(R.string.gamepad_layout_xbox),
                        selected = settings.gamepadLayout == SettingsData.GAMEPAD_XBOX,
                        onClick = { vm.setGamepadLayout(SettingsData.GAMEPAD_XBOX) }
                    )
                    ChoiceChip(
                        label = stringResource(R.string.gamepad_layout_nintendo),
                        selected = settings.gamepadLayout == SettingsData.GAMEPAD_NINTENDO,
                        onClick = { vm.setGamepadLayout(SettingsData.GAMEPAD_NINTENDO) }
                    )
                    ChoiceChip(
                        label = stringResource(R.string.gamepad_layout_custom),
                        selected = settings.gamepadLayout == SettingsData.GAMEPAD_CUSTOM,
                        onClick = { vm.setGamepadLayout(SettingsData.GAMEPAD_CUSTOM) }
                    )
                }
                Text(
                    stringResource(R.string.settings_gamepad_layout_desc),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp)
                )
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = { showGamepadRemapDialog = true },
                    modifier = Modifier.tvFocus(RoundedCornerShape(12.dp)),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 9.dp)
                ) {
                    Icon(Icons.Rounded.SportsEsports, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.settings_gamepad_remap), style = MaterialTheme.typography.labelMedium)
                }
            }
        }

        if (selectedTab == 5) item {
            SettingsSection(title = stringResource(R.string.settings_storage_backup)) {
                Text(
                    stringResource(R.string.storage_total, formatBytes(storageUsage.totalBytes)),
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    StorageLine(stringResource(R.string.storage_cores), storageUsage.coresBytes)
                    StorageLine(stringResource(R.string.storage_saves), storageUsage.savesBytes)
                    StorageLine(stringResource(R.string.storage_covers), storageUsage.coversBytes)
                    StorageLine(stringResource(R.string.storage_roms), storageUsage.romsBytes)
                }
                Text(
                    "${library.size} ${stringResource(R.string.stats_games)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TextButton(
                        onClick = { confirmClearCovers = true },
                        enabled = storageUsage.coversBytes > 0L,
                        contentPadding = PaddingValues(horizontal = 0.dp)
                    ) {
                        Icon(Icons.Rounded.Image, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(5.dp))
                        Text(stringResource(R.string.storage_clear_covers))
                    }
                    TextButton(
                        onClick = { confirmClear = true },
                        contentPadding = PaddingValues(horizontal = 14.dp)
                    ) {
                        Icon(Icons.Rounded.Delete, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(5.dp))
                        Text(stringResource(R.string.clear_library))
                    }
                }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp)
                        .height(1.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                )
                Text(
                    stringResource(R.string.backup_saves_desc),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = backupAction,
                        modifier = Modifier.tvFocus(RoundedCornerShape(12.dp)),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 9.dp)
                    ) {
                        Icon(Icons.Rounded.Save, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.backup_saves), style = MaterialTheme.typography.labelMedium)
                    }
                    OutlinedButton(
                        onClick = {
                            vm.refreshSaveBackups()
                            showRestoreDialog = true
                        },
                        modifier = Modifier.tvFocus(RoundedCornerShape(12.dp)),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 9.dp)
                    ) {
                        Icon(Icons.Rounded.Download, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.restore_saves), style = MaterialTheme.typography.labelMedium)
                    }
                }
                Text(
                    stringResource(R.string.data_sources_desc),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 14.dp)
                )
            }
        }
        if (selectedTab == 6) item {
            SettingsSection(
                title = stringResource(R.string.about),
                modifier = Modifier.fillMaxWidth().tvFocus(RoundedCornerShape(12.dp))
                    .focusable(enabled = isTv)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.foundation.Image(
                        painter = androidx.compose.ui.res.painterResource(R.drawable.ic_launcher_foreground),
                        contentDescription = null,
                        modifier = Modifier.size(64.dp)
                    )
                    Column(Modifier.padding(start = 12.dp)) {
                        Text(
                            stringResource(R.string.app_name).uppercase(),
                            style = MaterialTheme.typography.titleLarge,
                            letterSpacing = 3.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "${stringResource(R.string.version)} ${BuildConfig.VERSION_NAME}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Text(
                    stringResource(R.string.about_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 10.dp)
                )
                Text(
                    stringResource(R.string.made_with),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(top = 8.dp)
                )
                if (settings.supporterUnlocked) {
                    Text(
                        stringResource(R.string.about_supporter_thanks),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                Spacer(Modifier.height(10.dp))
                Text(stringResource(R.string.legal), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.legal_text),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 5.dp)
                )
                Text(
                    stringResource(R.string.source_license_note),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
    if (showPremiumPreview) {
        PremiumPreviewDialog(
            vm = vm,
            supporterPrice = supporterPrice,
            onDismiss = { showPremiumPreview = false }
        )
    }
    if (confirmRemoveCores) {
        AlertDialog(
            onDismissRequest = { confirmRemoveCores = false },
            title = { Text(stringResource(R.string.remove_cores)) },
            text = {
                Text(
                    stringResource(
                        R.string.remove_cores_confirm,
                        formatBytes(legacyCoresBytes)
                    )
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmRemoveCores = false
                        vm.removeLegacyCores()
                    }
                ) {
                    Text(
                        stringResource(R.string.remove),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmRemoveCores = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
    if (confirmClearCovers) {
        AlertDialog(
            onDismissRequest = { confirmClearCovers = false },
            title = { Text(stringResource(R.string.storage_clear_covers)) },
            text = { Text(stringResource(R.string.storage_clear_covers_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmClearCovers = false
                    vm.clearCoverCache()
                }) { Text(stringResource(R.string.storage_clear_covers)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClearCovers = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.clear_library_title)) },
            text = { Text(stringResource(R.string.clear_library_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    vm.clearLibrary()
                    confirmClear = false
                }) { Text(stringResource(R.string.clear_library)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
    if (showRestoreDialog) {
        val backups by vm.saveBackups.collectAsStateWithLifecycle()
        AlertDialog(
            onDismissRequest = { showRestoreDialog = false },
            title = { Text(stringResource(R.string.restore_pick_backup)) },
            text = {
                if (backups.isEmpty()) {
                    Text(
                        stringResource(R.string.restore_no_backups),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(backups.size) { index ->
                            val backup = backups[index]
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .tvFocus(RoundedCornerShape(10.dp))
                                    .clickable {
                                        showRestoreDialog = false
                                        pendingRestore = backup
                                    },
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Rounded.Save,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            backup.name,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            formatBytes(backup.sizeBytes),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showRestoreDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    pendingRestore?.let { backup ->
        AlertDialog(
            onDismissRequest = { pendingRestore = null },
            title = { Text(stringResource(R.string.restore_confirm_title)) },
            text = { Text(stringResource(R.string.restore_confirm_body, backup.name)) },
            confirmButton = {
                TextButton(onClick = {
                    pendingRestore = null
                    vm.restoreSaves(backup)
                }) {
                    Text(stringResource(R.string.restore_saves))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingRestore = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (showGamepadRemapDialog) {
        var draft by remember { mutableStateOf(settings.gamepadCustomMapping.toMap()) }
        val firstFocus = remember { FocusRequester() }
        val save: () -> Unit = {
            vm.setGamepadCustomMapping(draft)
            showGamepadRemapDialog = false
        }
        val dismiss: () -> Unit = { showGamepadRemapDialog = false }
        AlertDialog(
            onDismissRequest = dismiss,
            modifier = Modifier.onPreviewKeyEvent { event ->
                val native = event.nativeKeyEvent
                if (native.keyCode == KeyEvent.KEYCODE_BUTTON_B || native.keyCode == KeyEvent.KEYCODE_BUTTON_2) {
                    if (native.action == KeyEvent.ACTION_UP && !native.isCanceled) dismiss()
                    true
                } else false
            },
            title = { Text(stringResource(R.string.settings_gamepad_remap)) },
            text = {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stringResource(R.string.player_gamepad_global_hint), style = MaterialTheme.typography.bodySmall)
                    val names = listOf("A ↔ B", "X ↔ Y", "L1 ↔ R1", "L2 ↔ R2")
                    GamepadInput.swapPairs.forEachIndexed { index, (first, second) ->
                        val enabled = index >= 2 || settings.gamepadLayout == SettingsData.GAMEPAD_CUSTOM
                        val toggle: () -> Unit = { draft = GamepadInput.togglePair(draft, first, second) }
                        OutlinedButton(
                            onClick = toggle,
                            enabled = enabled,
                            modifier = Modifier.fillMaxWidth()
                                .then(if (index == (if (settings.gamepadLayout == SettingsData.GAMEPAD_CUSTOM) 0 else 2))
                                    Modifier.focusRequester(firstFocus) else Modifier)
                                .remapDialogConfirm(enabled, toggle).tvFocus(RoundedCornerShape(10.dp)),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(stringResource(
                                if (GamepadInput.pairSwapped(draft, first, second)) R.string.player_gamepad_swap_on
                                else R.string.player_gamepad_swap_off, names[index]
                            ))
                        }
                    }
                    val reset: () -> Unit = { draft = emptyMap() }
                    TextButton(onClick = reset, modifier = Modifier.remapDialogConfirm(true, reset)) {
                        Text(stringResource(R.string.player_gamepad_reset_swaps))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = save, modifier = Modifier.remapDialogConfirm(true, save)) {
                    Text(stringResource(R.string.player_gamepad_save))
                }
            },
            dismissButton = {
                TextButton(onClick = dismiss, modifier = Modifier.remapDialogConfirm(true, dismiss)) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
        androidx.compose.runtime.LaunchedEffect(Unit) {
            androidx.compose.runtime.withFrameNanos { }
            runCatching { firstFocus.requestFocus() }
        }
    }

    if (showCdKeyDialog && BuildConfig.CD_KEY_ENTRY) {
        var keyInput by remember { mutableStateOf("") }
        var checking by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = {
                showCdKeyDialog = false
                cdKeyError = false
            },
            title = { Text(stringResource(R.string.supporter_cdkey_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        stringResource(R.string.supporter_cdkey_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = keyInput,
                        onValueChange = {
                            keyInput = it
                            cdKeyError = false
                        },
                        singleLine = true,
                        isError = cdKeyError,
                        label = { Text(stringResource(R.string.supporter_cdkey_hint)) },
                        supportingText = if (cdKeyError) {
                            {
                                Text(stringResource(R.string.supporter_cdkey_invalid))
                            }
                        } else {
                            null
                        }
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        checking = true
                        vm.redeemSupporterKey(keyInput) { valid ->
                            checking = false
                            if (valid) {
                                showCdKeyDialog = false
                                cdKeyError = false
                            } else {
                                cdKeyError = true
                            }
                        }
                    },
                    enabled = keyInput.isNotBlank() && !checking
                ) {
                    Text(stringResource(R.string.supporter_cdkey_redeem))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showCdKeyDialog = false
                        cdKeyError = false
                    }
                ) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (showNameDialog) {
        var nameDraft by remember { mutableStateOf(settings.supporterName) }
        var listedDraft by remember { mutableStateOf(settings.supporterListed) }
        AlertDialog(
            onDismissRequest = { showNameDialog = false },
            title = { Text(stringResource(R.string.supporter_name_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        stringResource(R.string.supporter_name_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = nameDraft,
                        onValueChange = { nameDraft = it.take(24) },
                        singleLine = true,
                        label = { Text(stringResource(R.string.supporter_name_hint)) }
                    )
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .tvFocus(RoundedCornerShape(10.dp))
                            .clickable { listedDraft = !listedDraft }
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = listedDraft,
                            onCheckedChange = { listedDraft = it }
                        )
                        Text(
                            stringResource(R.string.supporter_listed),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(start = 4.dp)
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.setSupporterName(nameDraft.trim(), listedDraft)
                        showNameDialog = false
                    }
                ) {
                    Text(stringResource(R.string.saved))
                }
            },
            dismissButton = {
                TextButton(onClick = { showNameDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}
/** Dialog windows do not pass gamepad A/B through the activity's menu remapper. */
@Composable
private fun Modifier.remapDialogConfirm(enabled: Boolean, action: () -> Unit): Modifier {
    var pressed by remember { mutableStateOf(false) }
    return onFocusChanged { if (!it.isFocused) pressed = false }.onPreviewKeyEvent { event ->
        val native = event.nativeKeyEvent
        if (native.keyCode !in setOf(KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_BUTTON_1,
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER)) return@onPreviewKeyEvent false
        when (native.action) {
            KeyEvent.ACTION_DOWN -> if (native.repeatCount == 0) pressed = true
            KeyEvent.ACTION_UP -> {
                if (!GamepadKeyGuard.consumeCenterUp() && pressed && enabled && !native.isCanceled) action()
                pressed = false
            }
        }
        true
    }
}

@Composable
private fun SettingsActionRow(
    icon: ImageVector,
    label: String,
    locked: Boolean,
    onLockedClick: () -> Unit = {},
    onClick: () -> Unit
) {
    // Locked rows stay focusable so a D-pad user can still land on them and learn how to unlock.
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .tvFocus(RoundedCornerShape(10.dp))
            .clickable(onClick = if (locked) onLockedClick else onClick)
            .padding(horizontal = 2.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (locked) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            else MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp)
        )
        Text(
            label,
            style = MaterialTheme.typography.titleSmall,
            color = if (locked) MaterialTheme.colorScheme.onSurfaceVariant
            else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f).padding(horizontal = 10.dp)
        )
        if (locked) {
            Icon(
                Icons.Rounded.Lock,
                contentDescription = stringResource(R.string.supporter_feature),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(14.dp)
            )
        }
    }
}
@Composable
private fun SettingSwitchRow(
    title: String,
    description: String,
    checked: Boolean,
    enabled: Boolean = true,
    locked: Boolean = false,
    onLockedClick: () -> Unit = {},
    onCheckedChange: (Boolean) -> Unit
) {
    val lockedDescription = stringResource(R.string.supporter_feature)
    // The whole row is the toggle target: a right-aligned Switch alone falls outside the
    // D-pad focus beam from the chips above/below and can never be reached on a TV.
    // Locked rows remain reachable, but confirming only explains the lock; it never toggles.
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .tvFocus(RoundedCornerShape(10.dp))
            .semantics { if (locked) stateDescription = lockedDescription }
            .toggleable(
                value = checked,
                enabled = enabled || locked,
                role = Role.Switch,
                onValueChange = {
                    if (locked) onLockedClick() else if (enabled) onCheckedChange(it)
                }
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(
                description,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (locked) {
            Icon(
                Icons.Rounded.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.padding(horizontal = 8.dp).size(14.dp)
            )
        }
        Switch(checked = checked, onCheckedChange = null, enabled = enabled && !locked)
    }
}
@Composable
private fun SettingsSection(
    title: String,
    modifier: Modifier = Modifier,
    headerColor: Color? = null,
    headerTrailing: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val sectionAccent = LocalSectionAccent.current
    val tint = headerColor ?: sectionAccent
    val reducedMotion = LocalReducedMotion.current
    val entrance = remember(reducedMotion) { Animatable(if (reducedMotion) 1f else 0f) }
    val entranceOffset = with(LocalDensity.current) { 8.dp.toPx() }
    LaunchedEffect(reducedMotion) {
        if (reducedMotion) entrance.snapTo(1f)
        else entrance.animateTo(1f, tween(220, easing = FastOutSlowInEasing))
    }
    Column(
        modifier.graphicsLayer {
            alpha = entrance.value
            translationY = (1f - entrance.value) * entranceOffset
        }
    ) {
        if (sectionAccent != null) {
            val headerTint = headerColor ?: sectionAccent
            // Retro header: three fading pixel blocks and a pixel-font caption in the console colour.
            Row(
                Modifier.padding(start = 20.dp, top = 22.dp, bottom = 9.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(3) { i ->
                    Box(Modifier.size(6.dp).background(headerTint.copy(alpha = 1f - i * 0.32f)))
                    Spacer(Modifier.width(2.dp))
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    title.uppercase(),
                    fontFamily = PixelFont,
                    fontSize = 11.sp,
                    letterSpacing = 1.sp,
                    color = headerTint
                )
                if (headerTrailing != null) {
                    Spacer(Modifier.width(8.dp))
                    headerTrailing()
                }
            }
        } else {
            Row(
                Modifier.padding(start = 20.dp, top = 20.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    title.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = headerColor ?: MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold
                )
                if (headerTrailing != null) {
                    Spacer(Modifier.width(8.dp))
                    headerTrailing()
                }
            }
        }
        Surface(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f),
            border = BorderStroke(1.dp, tint?.copy(alpha = 0.32f) ?: MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(Modifier.padding(16.dp)) { content() }
        }
    }
}
private val SupporterGold = Color(0xFFFFC24B)
private val SupporterRose = Color(0xFFFF5C7A)
private val SupporterViolet = Color(0xFF8B5CF6)

/** A steady heartbeat that sells the pack; it pulses even when cool animations are off. */
@Composable
private fun GlowingHeart() {
    val transition = rememberInfiniteTransition(label = "supporterHeart")
    val glow by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            tween(1200, easing = FastOutSlowInEasing),
            RepeatMode.Reverse
        ),
        label = "supporterHeartGlow"
    )
    val scale by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1.14f,
        animationSpec = infiniteRepeatable(
            tween(1200, easing = FastOutSlowInEasing),
            RepeatMode.Reverse
        ),
        label = "supporterHeartScale"
    )
    Box(
        Modifier
            .size(26.dp)
            .background(
                Brush.radialGradient(
                    colors = listOf(SupporterRose.copy(alpha = glow), Color.Transparent)
                ),
                CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.Rounded.Favorite,
            contentDescription = null,
            tint = SupporterRose,
            modifier = Modifier.size(15.dp).graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
        )
    }
}

/** The pitch card: status, purchase and a doorway into the exclusive-interface preview. */
@Composable
private fun SupporterPitchSection(
    vm: AppViewModel,
    settings: SettingsData,
    supporterPrice: String?,
    buyFocus: FocusRequester,
    onPreview: () -> Unit,
    onCdKey: () -> Unit
) {
    val context = LocalContext.current
    val unlocked = settings.supporterUnlocked
    SettingsSection(
        title = stringResource(R.string.supporter_settings_title),
        headerColor = SupporterRose,
        headerTrailing = { GlowingHeart() }
    ) {
        Text(
            stringResource(R.string.supporter_settings_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(SupporterRose.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Rounded.Star,
                    contentDescription = null,
                    tint = SupporterRose,
                    modifier = Modifier.size(22.dp)
                )
            }
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(
                    stringResource(
                        if (unlocked) R.string.supporter_active else R.string.supporter_section
                    ),
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    stringResource(
                        if (unlocked) R.string.supporter_thanks else R.string.supporter_intro
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (unlocked) {
                Surface(shape = CircleShape, color = SupporterRose) {
                    Text(
                        stringResource(R.string.supporter_badge),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp)
                    )
                }
            }
        }
        if (!unlocked) {
            Spacer(Modifier.height(12.dp))
            if (BuildConfig.PLAY_BILLING) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = { (context as? Activity)?.let { vm.purchaseSupporter(it) } },
                        modifier = Modifier.focusRequester(buyFocus).tvFocus(RoundedCornerShape(12.dp)),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 9.dp)
                    ) {
                        Text(
                            supporterPrice?.let {
                                stringResource(R.string.supporter_buy_price, it)
                            } ?: stringResource(R.string.supporter_buy),
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                    OutlinedButton(
                        onClick = { vm.restoreSupporter() },
                        modifier = Modifier.tvFocus(RoundedCornerShape(12.dp)),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 9.dp)
                    ) {
                        Text(
                            stringResource(R.string.supporter_restore),
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            } else {
                Text(
                    stringResource(
                        if (BuildConfig.CD_KEY_ENTRY) R.string.supporter_unavailable
                        else R.string.supporter_store_unavailable
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = onPreview,
                modifier = Modifier.fillMaxWidth().tvFocus(RoundedCornerShape(12.dp)),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 9.dp)
            ) {
                Icon(Icons.Rounded.AutoAwesome, null, Modifier.size(16.dp), tint = SupporterGold)
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.premium_preview_open), style = MaterialTheme.typography.labelMedium)
            }
            if (BuildConfig.CD_KEY_ENTRY) {
                TextButton(
                    onClick = onCdKey,
                    contentPadding = PaddingValues(horizontal = 0.dp, vertical = 4.dp)
                ) {
                    Text(
                        stringResource(R.string.supporter_cdkey_title),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
    }
}

/** Interface style first, then the library look that belongs to the same exclusive frontend. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SupporterInterfaceSection(
    vm: AppViewModel,
    settings: SettingsData,
    onPreview: () -> Unit,
    onLockedClick: () -> Unit
) {
    val unlocked = settings.supporterUnlocked
    SettingsSection(
        title = stringResource(R.string.interface_style),
        headerColor = SupporterRose
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SpecialStyleChip(
                label = stringResource(R.string.theme_premium),
                selected = settings.retroTheme,
                locked = !unlocked,
                onClick = { if (unlocked) vm.setRetroThemeTest(true) else onPreview() }
            )
            ChoiceChip(
                label = stringResource(R.string.theme_standard),
                selected = !settings.retroTheme,
                onClick = { vm.setRetroThemeTest(false) }
            )
        }
        Text(
            stringResource(R.string.interface_style_desc),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp)
        )
        if (settings.retroTheme || !unlocked) {
            Spacer(Modifier.height(12.dp))
            SettingSwitchRow(
                title = stringResource(R.string.ui_scanlines),
                description = stringResource(R.string.ui_scanlines_desc),
                checked = settings.uiScanlines,
                enabled = unlocked,
                locked = !unlocked,
                onLockedClick = onLockedClick,
                onCheckedChange = { vm.setUiScanlines(it) }
            )
        }
        Spacer(Modifier.height(12.dp))
        SettingSwitchRow(
            title = stringResource(R.string.cool_animations),
            description = stringResource(R.string.cool_animations_desc),
            checked = settings.coolAnimations,
            enabled = unlocked,
            locked = !unlocked,
            onLockedClick = onLockedClick,
            onCheckedChange = { vm.setCoolAnimations(it) }
        )
        Box(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp)
                .height(1.dp)
                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.library_personalize),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f)
            )
            if (!unlocked) {
                Icon(
                    Icons.Rounded.Lock,
                    contentDescription = stringResource(R.string.supporter_feature),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.size(14.dp)
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        SettingLabel(stringResource(R.string.library_layout))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ChoiceChip(
                label = stringResource(R.string.library_layout_grid),
                selected = !settings.effectiveLibraryShelf,
                onClick = { vm.setLibraryShelf(false) },
                enabled = unlocked,
                locked = !unlocked,
                onLockedClick = onLockedClick
            )
            ChoiceChip(
                label = stringResource(R.string.library_layout_shelf),
                selected = settings.effectiveLibraryShelf,
                onClick = { vm.setLibraryShelf(true) },
                enabled = unlocked,
                locked = !unlocked,
                onLockedClick = onLockedClick
            )
        }
        Spacer(Modifier.height(10.dp))
        SettingLabel(stringResource(R.string.cover_size))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ChoiceChip(
                label = stringResource(R.string.cover_small),
                selected = settings.effectiveCoverSize == SettingsData.COVER_SMALL,
                onClick = { vm.setCoverSize(SettingsData.COVER_SMALL) },
                enabled = unlocked,
                locked = !unlocked,
                onLockedClick = onLockedClick
            )
            ChoiceChip(
                label = stringResource(R.string.cover_medium),
                selected = settings.effectiveCoverSize == SettingsData.COVER_MEDIUM,
                onClick = { vm.setCoverSize(SettingsData.COVER_MEDIUM) },
                enabled = unlocked,
                locked = !unlocked,
                onLockedClick = onLockedClick
            )
            ChoiceChip(
                label = stringResource(R.string.cover_large),
                selected = settings.effectiveCoverSize == SettingsData.COVER_LARGE,
                onClick = { vm.setCoverSize(SettingsData.COVER_LARGE) },
                enabled = unlocked,
                locked = !unlocked,
                onLockedClick = onLockedClick
            )
        }
        Spacer(Modifier.height(12.dp))
        SettingSwitchRow(
            title = stringResource(R.string.attract_mode),
            description = stringResource(R.string.attract_mode_desc),
            checked = settings.attractMode,
            enabled = unlocked,
            locked = !unlocked,
            onLockedClick = onLockedClick,
            onCheckedChange = { vm.setAttractMode(it) }
        )
    }
}

/** Colour themes and shader packs: the visual flavour of the exclusive interface. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SupporterLooksSection(
    vm: AppViewModel,
    settings: SettingsData,
    onLockedClick: () -> Unit
) {
    val unlocked = settings.supporterUnlocked
    SettingsSection(
        title = stringResource(R.string.theme_extra),
        headerColor = SupporterViolet
    ) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SupporterThemeChip(settings, SettingsData.THEME_OLED, R.string.theme_oled, vm, onLockedClick)
            SupporterThemeChip(settings, SettingsData.THEME_AMBER, R.string.theme_amber, vm, onLockedClick)
            SupporterThemeChip(settings, SettingsData.THEME_GREEN, R.string.theme_green, vm, onLockedClick)
            SupporterThemeChip(settings, SettingsData.THEME_NINTENDO, R.string.theme_nintendo, vm, onLockedClick)
            SupporterThemeChip(settings, SettingsData.THEME_SEGA, R.string.theme_sega, vm, onLockedClick)
            SupporterThemeChip(settings, SettingsData.THEME_PSX, R.string.theme_psx, vm, onLockedClick)
        }
        Box(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp)
                .height(1.dp)
                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.settings_shader_packs),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f)
            )
            if (!unlocked) {
                Icon(
                    Icons.Rounded.Lock,
                    contentDescription = stringResource(R.string.supporter_feature),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.size(14.dp)
                )
            }
        }
        Spacer(Modifier.height(7.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ShaderPackChip(settings, SettingsData.SHADER_APERTURE, R.string.player_shader_aperture, vm, onLockedClick)
            ShaderPackChip(settings, SettingsData.SHADER_SCANLINE, R.string.player_shader_scanline, vm, onLockedClick)
            ShaderPackChip(settings, SettingsData.SHADER_DOTMATRIX, R.string.player_shader_dotmatrix, vm, onLockedClick)
            ShaderPackChip(settings, SettingsData.SHADER_GHOST, R.string.player_shader_ghost, vm, onLockedClick)
        }
        Text(
            stringResource(R.string.settings_shader_packs_desc),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

/** Supporter identity and the social corners: name, stats, showcase and the wall. */
@Composable
private fun SupporterMoreSection(
    settings: SettingsData,
    onLockedClick: () -> Unit,
    onStats: () -> Unit,
    onShowcase: () -> Unit,
    onSupporters: () -> Unit,
    onName: () -> Unit
) {
    val unlocked = settings.supporterUnlocked
    SettingsSection(
        title = stringResource(R.string.supporter_more),
        headerColor = SupporterViolet
    ) {
        if (unlocked) {
            SettingsActionRow(
                icon = Icons.Rounded.Star,
                label = settings.supporterName.ifBlank {
                    stringResource(R.string.supporter_name_title)
                },
                locked = false,
                onClick = onName
            )
        }
        SettingsActionRow(
            icon = Icons.Rounded.BarChart,
            label = stringResource(R.string.stats),
            locked = !unlocked,
            onLockedClick = onLockedClick,
            onClick = onStats
        )
        SettingsActionRow(
            icon = Icons.Rounded.Collections,
            label = stringResource(R.string.showcase),
            locked = !unlocked,
            onLockedClick = onLockedClick,
            onClick = onShowcase
        )
        SettingsActionRow(
            icon = Icons.Rounded.Favorite,
            label = stringResource(R.string.supporter_wall),
            locked = !unlocked,
            onLockedClick = onLockedClick,
            onClick = onSupporters
        )
    }
}

/** The one warm-coloured control in Settings; locked taps open the interface preview instead of toggling. */
@Composable
private fun SpecialStyleChip(
    label: String,
    selected: Boolean,
    locked: Boolean,
    onClick: () -> Unit
) {
    val transition = rememberInfiniteTransition(label = "specialStyle")
    val glow by transition.animateFloat(
        initialValue = 0.55f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            tween(1600, easing = FastOutSlowInEasing),
            RepeatMode.Reverse
        ),
        label = "specialStyleGlow"
    )
    val shape = RoundedCornerShape(50)
    val lockedDescription = stringResource(R.string.supporter_feature)
    val selectedBrush = Brush.linearGradient(listOf(SupporterGold, SupporterRose, SupporterViolet))
    val idleBrush = Brush.linearGradient(
        listOf(SupporterRose.copy(alpha = 0.16f), SupporterViolet.copy(alpha = 0.16f))
    )
    Row(
        Modifier
            .clip(shape)
            .background(if (selected) selectedBrush else idleBrush)
            .border(
                1.4.dp,
                if (selected) Color.White.copy(alpha = glow) else SupporterRose.copy(alpha = glow),
                shape
            )
            .semantics { if (locked) stateDescription = lockedDescription }
            .tvFocus(shape, SupporterGold)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            if (locked) Icons.Rounded.Lock else Icons.Rounded.AutoAwesome,
            contentDescription = null,
            tint = if (selected) Color.White else SupporterGold,
            modifier = Modifier.size(15.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PremiumPreviewDialog(vm: AppViewModel, supporterPrice: String?, onDismiss: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                GlowingHeart()
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.premium_preview_title),
                    style = MaterialTheme.typography.titleMedium
                )
            }
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                PremiumPreviewMock()
                Spacer(Modifier.height(12.dp))
                Text(
                    stringResource(R.string.premium_preview_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    PerkChip(stringResource(R.string.premium_perk_worlds))
                    PerkChip(stringResource(R.string.premium_perk_logos))
                    PerkChip(stringResource(R.string.premium_perk_shelf))
                    PerkChip(stringResource(R.string.premium_perk_shaders))
                    PerkChip(stringResource(R.string.premium_perk_motion))
                    PerkChip(stringResource(R.string.premium_perk_slots))
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    stringResource(R.string.premium_preview_caption).uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = SupporterRose,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        confirmButton = {
            if (BuildConfig.PLAY_BILLING) {
                Button(
                    onClick = {
                        (context as? Activity)?.let { vm.purchaseSupporter(it) }
                        onDismiss()
                    },
                    modifier = Modifier.tvFocus(RoundedCornerShape(12.dp)),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 9.dp)
                ) {
                    Text(
                        supporterPrice?.let { stringResource(R.string.supporter_buy_price, it) }
                            ?: stringResource(R.string.premium_preview_buy),
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.tvFocus(RoundedCornerShape(12.dp))) {
                Text(stringResource(R.string.premium_preview_close))
            }
        }
    )
}

/** A living miniature of the exclusive frontend: real console worlds, wordmarks and a carousel. */
@Composable
private fun PremiumPreviewMock() {
    val consoles = remember {
        listOf("snes", "n64", "gb", "genesis", "ps1", "psp").mapNotNull { ConsoleCatalog.get(it) }
    }
    var index by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        while (isActive) {
            delay(2400)
            index = (index + 1) % consoles.size
        }
    }
    CompositionLocalProvider(LocalRetroTheme provides true) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(216.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(RetroInk)
        ) {
            AnimatedContent(
                targetState = index,
                transitionSpec = { fadeIn(tween(650)) togetherWith fadeOut(tween(650)) },
                label = "previewWorld",
                modifier = Modifier.fillMaxSize()
            ) { i ->
                BackdropImage(consoles[i].id, Modifier.fillMaxSize(), widthPx = 640)
            }
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.25f),
                        0.45f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.85f)
                    )
                )
            )
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "RETROBIT",
                    fontFamily = PixelFont,
                    fontSize = 12.sp,
                    letterSpacing = 2.sp,
                    color = Color.White
                )
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                    AnimatedContent(
                        targetState = index,
                        transitionSpec = { fadeIn(tween(450)) togetherWith fadeOut(tween(450)) },
                        label = "previewMark"
                    ) { i ->
                        ConsoleWordmark(consoles[i], size = 18.sp, showStrip = false, alignEnd = true)
                    }
                }
            }
            Row(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                consoles.forEachIndexed { i, console ->
                    MiniConsoleCard(console, selected = i == index, modifier = Modifier.weight(1f))
                }
            }
            Box(Modifier.fillMaxSize().crtOverlay(true))
        }
    }
}

/** One carousel slot in the preview, echoing the home system cards. */
@Composable
private fun MiniConsoleCard(console: ConsoleInfo, selected: Boolean, modifier: Modifier = Modifier) {
    val style = consoleStyle(console)
    val scale by animateFloatAsState(if (selected) 1.06f else 0.94f, tween(240), label = "miniCardScale")
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier
            .height(62.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(shape)
            .background(Color.Black)
            .border(
                if (selected) 2.dp else 1.dp,
                if (selected) style.accent else Color.White.copy(alpha = 0.14f),
                shape
            )
    ) {
        BackdropImage(console.id, Modifier.fillMaxSize(), widthPx = 160)
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(0f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.85f))
            )
        )
        if (selected) {
            Box(Modifier.fillMaxHeight().width(3.dp).background(style.accent))
        }
        Column(Modifier.align(Alignment.BottomStart).padding(start = 6.dp, bottom = 5.dp, end = 4.dp)) {
            ConsoleGlyph(console.glyph, Modifier.size(13.dp), style.accent)
            Spacer(Modifier.height(2.dp))
            Text(
                console.shortName,
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun PerkChip(label: String) {
    Surface(
        shape = RoundedCornerShape(50),
        color = SupporterGold.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, SupporterGold.copy(alpha = 0.35f))
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
        )
    }
}

@Composable
private fun SettingLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(bottom = 7.dp)
    )
}
@Composable
private fun StorageLine(label: String, bytes: Long) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            formatBytes(bytes),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold
        )
    }
}
@Composable
private fun ChoiceChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    enabled: Boolean = true,
    locked: Boolean = false,
    onLockedClick: () -> Unit = {}
) {
    val lockedDescription = stringResource(R.string.supporter_feature)
    FilterChip(
        selected = selected,
        onClick = { if (locked) onLockedClick() else if (enabled) onClick() },
        // Locked choices stay in the D-pad focus chain without invoking the setting callback.
        enabled = enabled || locked,
        // The stock chip only tints when focused, which is invisible on an already selected chip.
        modifier = Modifier.tvFocus(RoundedCornerShape(8.dp))
            .semantics { if (locked) stateDescription = lockedDescription },
        label = {
            Text(
                label,
                maxLines = 1,
                color = if (locked) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                else Color.Unspecified
            )
        },
        leadingIcon = when {
            locked -> {
                {
                    Icon(
                        Icons.Rounded.Lock,
                        null,
                        Modifier.size(13.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }
            }
            selected -> {
                { Icon(Icons.Rounded.Check, null, Modifier.size(15.dp)) }
            }
            else -> null
        }
    )
}

@Composable
private fun SupporterThemeChip(
    settings: SettingsData,
    themeId: String,
    @StringRes labelRes: Int,
    vm: AppViewModel,
    onLockedClick: () -> Unit
) {
    ChoiceChip(
        label = stringResource(labelRes),
        selected = settings.effectiveTheme == themeId,
        onClick = { vm.setTheme(themeId) },
        enabled = settings.supporterUnlocked,
        locked = !settings.supporterUnlocked,
        onLockedClick = onLockedClick
    )
}

@Composable
private fun ShaderPackChip(
    settings: SettingsData,
    shaderId: String,
    @StringRes labelRes: Int,
    vm: AppViewModel,
    onLockedClick: () -> Unit
) {
    ChoiceChip(
        label = stringResource(labelRes),
        selected = settings.defaultShader == shaderId,
        onClick = { vm.setDefaultShader(shaderId) },
        enabled = settings.supporterUnlocked,
        locked = !settings.supporterUnlocked,
        onLockedClick = onLockedClick
    )
}
@Composable
private fun EmulatorRow(
    label: String,
    detail: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .tvFocus(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(
                detail,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}
