package com.retrobit.app.ui.components

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.view.ViewTreeObserver
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.request.ImageRequest
import com.retrobit.app.AppViewModel
import com.retrobit.app.BuildConfig
import com.retrobit.app.R
import com.retrobit.app.data.ConsoleCatalog
import com.retrobit.app.data.ConsoleInfo
import com.retrobit.app.data.LibraryGame
import com.retrobit.app.data.SettingsData
import com.retrobit.app.data.StorageFolders
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.abs

/** Common retail box proportions; regional art is always fitted, never cropped to this slot. */
fun coverAspectRatio(console: ConsoleInfo?): Float = when (console?.id) {
    "snes", "n64" -> 1.4f
    "gb", "gbc", "gba", "nds", "ds", "pce", "ps1", "saturn", "segacd" -> 1f
    "nes", "genesis", "psp", "atari2600" -> 0.72f
    else -> if (console?.glyph == "disc") 1f else 0.74f
}

fun consoleColor(console: ConsoleInfo?): Color = Color(console?.color ?: 0xFF5B4BDB)

fun consoleColor(consoleId: String): Color =
    Color(com.retrobit.app.data.ConsoleCatalog.get(consoleId)?.color ?: 0xFF5B4BDB)

/**
 * True on a device with no touchscreen in the loop, i.e. an Android TV box driven only by a
 * remote. Provided by [com.retrobit.app.ui.RetrobitApp]; screens and cards read it to drop
 * small touch-only overlays and leave exactly one large focus target per row or cell.
 */
val LocalIsTv: ProvidableCompositionLocal<Boolean> = compositionLocalOf { false }

/**
 * Leanback is the honest signal that only a remote drives this device, but plenty of TV boxes ship
 * without the feature flag and some sticks ship it by mistake, so the setting can overrule it.
 */
@Composable
fun rememberIsTv(layoutMode: String = SettingsData.LAYOUT_AUTO): Boolean {
    val context = LocalContext.current
    val leanback = remember(context) {
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
    }
    return when (layoutMode) {
        SettingsData.LAYOUT_PHONE -> false
        SettingsData.LAYOUT_TV -> true
        else -> leanback
    }
}

@Composable
fun RequestFocusOnWindowFocus(focusRequester: FocusRequester) {
    val view = LocalView.current
    val target by rememberUpdatedState(focusRequester)
    DisposableEffect(view) {
        fun request() {
            runCatching { target.requestFocus() }
        }
        if (view.hasWindowFocus()) request()
        val listener = ViewTreeObserver.OnWindowFocusChangeListener { hasFocus ->
            if (hasFocus) request()
        }
        view.viewTreeObserver.addOnWindowFocusChangeListener(listener)
        onDispose { view.viewTreeObserver.removeOnWindowFocusChangeListener(listener) }
    }
}

/**
 * Focus ring for D-pad navigation. On a TV the ring has to be legible from three metres away, so
 * the focused item also grows a little instead of only changing its border colour.
 */
@Composable
fun Modifier.tvFocus(
    shape: Shape = RoundedCornerShape(16.dp),
    color: Color = MaterialTheme.colorScheme.primary
): Modifier {
    var focused by remember { mutableStateOf(false) }
    return this
        .onFocusChanged { focused = it.isFocused }
        .then(
            if (focused) {
                Modifier
                    .border(7.dp, color.copy(alpha = 0.22f), shape)
                    .border(2.dp, color, shape)
            } else {
                Modifier
            }
        )
}

/**
 * A gamepad-friendly click: the confirm button starts the game right away, and keeping it held
 * opens the detail screen. Compose's clickable only reports short presses for key events, so the
 * hold is timed here; pointer long-press still comes from [combinedClickable].
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Modifier.gamepadClickable(
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onTap: (() -> Unit)? = null
): Modifier {
    val scope = rememberCoroutineScope()
    var holdJob by remember { mutableStateOf<Job?>(null) }
    var longFired by remember { mutableStateOf(false) }
    var pressed by remember { mutableStateOf(false) }
    val currentClick by rememberUpdatedState(onClick)
    val currentLongClick by rememberUpdatedState(onLongClick)
    val currentTap by rememberUpdatedState(onTap)
    val isTv = LocalIsTv.current
    return this
        .then(if (isTv) Modifier.focusProperties { canFocus = true } else Modifier)
        .onFocusChanged { focus ->
            if (!focus.isFocused) {
                holdJob?.cancel()
                holdJob = null
                pressed = false
                longFired = false
            }
        }
        .combinedClickable(
            onClick = { (currentTap ?: currentClick)() },
            onLongClick = currentLongClick
        )
        .onPreviewKeyEvent { event ->
            if (event.key != Key.DirectionCenter && event.key != Key.Enter) {
                return@onPreviewKeyEvent false
            }
            when (event.type) {
                KeyEventType.KeyDown -> {
                    if (event.nativeKeyEvent.repeatCount == 0) {
                        pressed = true
                        longFired = false
                    }
                    if (pressed && holdJob == null && !longFired) {
                        holdJob = scope.launch {
                            delay(550)
                            longFired = true
                            holdJob = null
                            GamepadKeyGuard.armCenterUp()
                            currentLongClick()
                        }
                    }
                    true
                }
                KeyEventType.KeyUp -> {
                    holdJob?.cancel()
                    holdJob = null
                    // A key-up without our key-down belongs to the press that opened this screen.
                    if (pressed && !longFired) currentClick()
                    pressed = false
                    longFired = false
                    true
                }
                else -> false
            }
        }
}

@Composable
fun ConsoleGlyph(glyph: String?, modifier: Modifier = Modifier, tint: Color = Color.White) {
    val res = when (glyph) {
        "cartridge" -> R.drawable.ic_glyph_cartridge
        "handheld" -> R.drawable.ic_glyph_handheld
        "dualscreen" -> R.drawable.ic_glyph_dualscreen
        "controller" -> R.drawable.ic_glyph_controller
        "disc" -> R.drawable.ic_glyph_disc
        "cabinet" -> R.drawable.ic_glyph_cabinet
        "joystick" -> R.drawable.ic_glyph_joystick
        "computer" -> R.drawable.ic_glyph_computer
        "card" -> R.drawable.ic_glyph_card
        else -> R.drawable.ic_glyph_controller
    }
    Icon(painterResource(res), contentDescription = null, modifier = modifier, tint = tint)
}

/**
 * Artwork slot for a game: the remote cover when there is one, and a console-tinted monogram
 * (glyph plus the first letters of the title) behind it while it loads or when it is missing.
 */
@Composable
fun GameCover(
    url: String?,
    title: String,
    console: ConsoleInfo?,
    modifier: Modifier = Modifier,
    corner: Dp = 18.dp,
    showMonogram: Boolean = true,
    framed: Boolean = LocalRetroTheme.current
) {
    val accent = consoleColor(console)
    val retro = LocalRetroTheme.current
    val shape = RoundedCornerShape(corner.coerceIn(0.dp, 4.dp))
    var imageReady by remember(url) { mutableStateOf(false) }
    // A neutral matte only: no spine, artwork masks, corner badges or moving loading overlay.
    Box(
        modifier
            .background(
                if (retro) Color(0xFF17171C) else MaterialTheme.colorScheme.surfaceVariant,
                shape
            )
            .then(
                if (framed) Modifier.border(
                    1.dp,
                    if (retro) Color.White.copy(alpha = 0.16f)
                    else MaterialTheme.colorScheme.outlineVariant,
                    shape
                ) else Modifier
            )
            .padding(3.dp)
    ) {
        if (showMonogram && !imageReady) {
            Column(
                Modifier.align(Alignment.Center).padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ConsoleGlyph(console?.glyph, Modifier.size(30.dp), accent.copy(alpha = 0.65f))
                Text(
                    title.take(2).uppercase(Locale.ROOT),
                    color = accent.copy(alpha = 0.65f),
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }
        if (!url.isNullOrBlank()) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(url)
                    .crossfade(if (LocalReducedMotion.current) 0 else 100)
                    .build(),
                contentDescription = title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
                onState = { state -> imageReady = state is AsyncImagePainter.State.Success }
            )
        }
    }
}

@Composable
fun ScreenHeader(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    Row(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(
                start = if (onBack != null) 8.dp else 20.dp,
                top = 10.dp,
                end = 8.dp,
                bottom = 6.dp
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onBack != null) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .tvFocus(RoundedCornerShape(12.dp))
            ) {
                Icon(Icons.Rounded.ArrowBack, contentDescription = stringResource(R.string.cancel))
            }
        }
        Column(
            Modifier
                .weight(1f)
                .padding(horizontal = if (onBack != null) 4.dp else 0.dp)
        ) {
            Text(title, style = MaterialTheme.typography.headlineMedium)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        actions()
    }
}

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    description: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 52.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(38.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(6.dp))
        Text(
            description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(18.dp))
            TextButton(onClick = onAction) {
                Text(actionLabel)
            }
        }
    }
}

fun formatBytes(bytes: Long): String {
    if (bytes <= 0L) return "0 MB"
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0
    return when {
        gb >= 1.0 -> String.format(Locale.US, "%.2f GB", gb)
        mb >= 1.0 -> String.format(Locale.US, "%.1f MB", mb)
        else -> String.format(Locale.US, "%.0f KB", kb)
    }
}

fun toast(context: Context, @StringRes resId: Int) {
    Toast.makeText(context, context.getString(resId), Toast.LENGTH_LONG).show()
}

fun hasStorageAccess(context: Context): Boolean = StorageFolders.hasStorageAccess(context)

/**
 * Direct file paths are only usable with all-files access (sideload build) or on the
 * pre-scoped-storage Android versions. The store build has to go through SAF instead.
 */
fun directFileAccessAvailable(): Boolean = when {
    Build.VERSION.SDK_INT <= Build.VERSION_CODES.P -> true
    BuildConfig.ALL_FILES_ACCESS && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> true
    else -> false
}

/**
 * Returns an action that makes sure the app can read storage before running [onGranted].
 * Android 11+ needs all-files access for direct folder scanning; older versions use the
 * READ_EXTERNAL_STORAGE runtime permission.
 */
@Composable
fun rememberStorageAccessRequester(onGranted: () -> Unit): () -> Unit {
    val context = LocalContext.current
    val currentGranted by rememberUpdatedState(onGranted)
    val accessLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (hasStorageAccess(context)) {
            currentGranted()
        } else {
            toast(context, R.string.folder_permission_required)
        }
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) currentGranted() else toast(context, R.string.folder_permission_required)
    }
    return remember(accessLauncher, permissionLauncher) {
        {
            when {
                hasStorageAccess(context) -> currentGranted()
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                    val appIntent = Intent(
                        Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                        Uri.parse("package:${context.packageName}")
                    )
                    val appLaunched = runCatching {
                        accessLauncher.launch(appIntent)
                        true
                    }.getOrDefault(false)
                    val allLaunched = appLaunched || runCatching {
                        accessLauncher.launch(
                            Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                        )
                        true
                    }.getOrDefault(false)
                    if (!allLaunched) toast(context, R.string.folder_permission_required)
                }
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ->
                    permissionLauncher.launch(
                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                            Manifest.permission.WRITE_EXTERNAL_STORAGE
                        } else {
                            Manifest.permission.READ_EXTERNAL_STORAGE
                        }
                    )
                else -> currentGranted()
            }
        }
    }
}

@Composable
fun rememberImportLauncher(vm: AppViewModel, onImported: () -> Unit = {}): () -> Unit {
    val context = LocalContext.current
    val currentOnImported by rememberUpdatedState(onImported)
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        vm.importUris(uris)
        if (uris.isNotEmpty()) currentOnImported()
    }
    return remember(launcher) {
        {
            runCatching { launcher.launch(arrayOf("*/*")) }
                .onFailure { toast(context, R.string.file_picker_missing) }
        }
    }
}

/**
 * Opens the system/File Manager Plus document picker filtered to archives. Sharing the same
 * ACTION_OPEN_DOCUMENT contract is what makes File Manager Plus appear on TV boxes where the
 * bundled DocumentsUI is missing.
 */
@Composable
fun rememberRomZipLauncher(vm: AppViewModel): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        uris.firstOrNull()?.let(vm::importRomZip)
    }
    return remember(launcher) {
        {
            runCatching {
                launcher.launch(
                    arrayOf(
                        "application/zip",
                        "application/x-zip-compressed",
                        "application/x-7z-compressed",
                        "application/octet-stream"
                    )
                )
            }.onFailure { toast(context, R.string.file_picker_missing) }
        }
    }
}

@Composable
fun RomZipImportCard(
    onPick: () -> Unit,
    importing: Boolean = false,
    modifier: Modifier = Modifier,
    buttonModifier: Modifier = Modifier
) {
    var guideOpen by remember { mutableStateOf(false) }
    val pickInteraction = remember { MutableInteractionSource() }
    val pickFocused by pickInteraction.collectIsFocusedAsState()
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.32f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Rounded.FileDownload,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.size(8.dp))
                Text(
                    stringResource(R.string.zip_guide_title),
                    style = MaterialTheme.typography.titleSmall
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.zip_guide_summary),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick = onPick,
                enabled = !importing,
                interactionSource = pickInteraction,
                border = BorderStroke(
                    if (pickFocused) 2.dp else 1.dp,
                    if (pickFocused) MaterialTheme.colorScheme.secondary
                    else MaterialTheme.colorScheme.outline
                ),
                modifier = buttonModifier.fillMaxWidth().height(46.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Rounded.FileDownload, null, Modifier.size(17.dp))
                Spacer(Modifier.size(7.dp))
                Text(stringResource(R.string.zip_import_button))
            }
            Spacer(Modifier.height(4.dp))
            TextButton(
                onClick = { guideOpen = true },
                modifier = Modifier.fillMaxWidth().tvFocus(RoundedCornerShape(10.dp)),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
            ) {
                Text(
                    stringResource(R.string.zip_guide_open),
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.weight(1f)
                )
                Icon(Icons.Rounded.ChevronRight, null, Modifier.size(18.dp))
            }
        }
    }
    if (guideOpen) {
        AlertDialog(
            onDismissRequest = { guideOpen = false },
            title = { Text(stringResource(R.string.zip_guide_title)) },
            text = {
                Box(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                    Column {
                        RomZipSteps()
                        Spacer(Modifier.height(14.dp))
                        Text(
                            stringResource(R.string.zip_guide_formats_title),
                            style = MaterialTheme.typography.titleSmall
                        )
                        Spacer(Modifier.height(6.dp))
                        RomZipFormats()
                        Spacer(Modifier.height(10.dp))
                        Text(
                            stringResource(R.string.zip_guide_limits),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            stringResource(R.string.zip_guide_note),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { guideOpen = false }) {
                    Text(stringResource(R.string.zip_guide_close))
                }
            }
        )
    }
}

@Composable
private fun RomZipSteps() {
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        listOf(
            R.string.zip_guide_step1,
            R.string.zip_guide_step2,
            R.string.zip_guide_step3,
            R.string.zip_guide_step4,
            R.string.zip_guide_step5
        ).forEach { step ->
            Text(
                stringResource(step),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** Formats are read from the console catalog so the guide can never drift from import support. */
@Composable
private fun RomZipFormats() {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        ConsoleCatalog.consoles.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                pair.forEach { console ->
                    Row(Modifier.weight(1f)) {
                        Text(
                            console.shortName,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            modifier = Modifier.width(52.dp)
                        )
                        Text(
                            console.extensions.joinToString(" ") { ".$it" },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}
