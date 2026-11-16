package com.retrobit.app.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import com.retrobit.app.ui.components.LocalNavAnimScope
import com.retrobit.app.ui.components.LocalSharedScope
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.retrobit.app.AppViewModel
import com.retrobit.app.R
import com.retrobit.app.data.ConsoleCatalog
import com.retrobit.app.data.ImportProgress
import com.retrobit.app.ui.components.GameCover
import com.retrobit.app.ui.components.ImportWaitScreen
import com.retrobit.app.ui.components.LocalIsTv
import com.retrobit.app.ui.components.LocalReducedMotion
import com.retrobit.app.ui.components.LocalRetroTheme
import com.retrobit.app.ui.components.LocalSafeArea
import com.retrobit.app.ui.components.RetroBackdrop
import com.retrobit.app.ui.components.RetroInk
import com.retrobit.app.ui.components.rememberIsTv
import com.retrobit.app.ui.components.tvFocus
import com.retrobit.app.ui.minigames.MiniGamesScreen
import com.retrobit.app.ui.screens.FavoritesScreen
import com.retrobit.app.ui.screens.GameListScreen
import com.retrobit.app.ui.screens.LibraryDetailScreen
import com.retrobit.app.ui.screens.SearchScreen
import com.retrobit.app.ui.screens.SettingsScreen
import com.retrobit.app.ui.screens.SetupScreen
import com.retrobit.app.ui.screens.ShowcaseScreen
import com.retrobit.app.ui.screens.StatsScreen
import com.retrobit.app.ui.screens.SupportersScreen
import com.retrobit.app.ui.screens.SystemsScreen
import kotlinx.coroutines.delay

@Composable
fun RetrobitApp(vm: AppViewModel) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val isTv = rememberIsTv(settings.layoutMode)
    val dir = LocalLayoutDirection.current
    val bars = WindowInsets.systemBars.union(WindowInsets.displayCutout).asPaddingValues()
    // TVs crop the picture edge, so the frontend keeps a fixed overscan margin instead of insets.
    val safe = if (isTv) {
        PaddingValues(horizontal = 40.dp, vertical = 22.dp)
    } else {
        PaddingValues(
            start = bars.calculateStartPadding(dir) + 20.dp,
            top = bars.calculateTopPadding() + 10.dp,
            end = bars.calculateEndPadding(dir) + 20.dp,
            bottom = bars.calculateBottomPadding() + 8.dp
        )
    }
    CompositionLocalProvider(
        LocalIsTv provides isTv,
        LocalSafeArea provides safe,
        LocalRetroTheme provides settings.retroTheme,
        LocalReducedMotion provides settings.reducedMotion,
        LocalContentColor provides MaterialTheme.colorScheme.onBackground
    ) {
        RetrobitAppContent(vm)
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun RetrobitAppContent(vm: AppViewModel) {
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val isTv = LocalIsTv.current
    val isTr by vm.isTr.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val settingsLoaded by vm.settingsLoaded.collectAsStateWithLifecycle()
    val libraryLoaded by vm.libraryLoaded.collectAsStateWithLifecycle()
    val libraryError by vm.libraryError.collectAsStateWithLifecycle()
    val importing by vm.importing.collectAsStateWithLifecycle()
    val importProgress by vm.importProgress.collectAsStateWithLifecycle()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateFlow
        .collectAsStateWithLifecycle(minActiveState = Lifecycle.State.CREATED)

    LaunchedEffect(Unit) {
        vm.messages.collect { message ->
            snackbarHostState.showSnackbar(
                context.getString(message.resId, *message.args.toTypedArray())
            )
        }
    }

    if (!settingsLoaded || !libraryLoaded) {
        StartupGate(error = libraryError, onRetry = vm::retryLibraryLoad)
        return
    }

    if (!settings.onboardingDone) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            SetupScreen(vm, isTr)
            SnackbarHost(
                snackbarHostState,
                modifier = Modifier.align(Alignment.BottomCenter).padding(LocalSafeArea.current)
            )
        }
        return
    }

    fun open(route: String) {
        navController.navigate(route) { launchSingleTop = true }
    }

    // Menus reached from anywhere replace each other instead of piling up, so B always returns
    // to the system or game list the user came from.
    fun openMenu(route: String) {
        navController.navigate(route) {
            launchSingleTop = true
            if (currentRoute in menuRoutes) {
                currentRoute?.let { popUpTo(it) { inclusive = true } }
            }
        }
    }

    // TV attract mode: after 90s without input the box art takes over the screen. The timer
    // resets on any key or tap, and the slideshow runs in its own dialog window so the screen
    // underneath keeps its focus for when the user comes back.
    val attractEnabled = isTv && settings.supporterUnlocked && settings.attractMode &&
        !importing && currentRoute != "arcade" && lifecycleState == Lifecycle.State.RESUMED
    var interactionTick by remember { mutableIntStateOf(0) }
    var attractVisible by remember { mutableStateOf(false) }
    LaunchedEffect(attractEnabled, interactionTick, currentRoute) {
        attractVisible = false
        if (attractEnabled) {
            delay(90_000)
            attractVisible = true
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(if (settings.retroTheme) RetroInk else MaterialTheme.colorScheme.background)
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) interactionTick++
                false
            }
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        if (event.changes.any { it.pressed }) interactionTick++
                    }
                }
            }
    ) {
        SharedTransitionLayout(Modifier.fillMaxSize()) {
        CompositionLocalProvider(LocalSharedScope provides if (settings.reducedMotion) null else this) {
        NavHost(
            navController = navController,
            startDestination = "systems",
            modifier = Modifier.fillMaxSize(),
            enterTransition = {
                if (settings.reducedMotion) fadeIn(tween(0))
                else fadeIn(tween(220)) + slideInHorizontally(tween(280, easing = FastOutSlowInEasing)) { it / 16 }
            },
            exitTransition = {
                if (settings.reducedMotion) fadeOut(tween(0))
                else fadeOut(tween(160)) + slideOutHorizontally(tween(240, easing = FastOutSlowInEasing)) { -it / 24 }
            },
            popEnterTransition = {
                if (settings.reducedMotion) fadeIn(tween(0))
                else fadeIn(tween(220)) + slideInHorizontally(tween(280, easing = FastOutSlowInEasing)) { -it / 16 }
            },
            popExitTransition = {
                if (settings.reducedMotion) fadeOut(tween(0))
                else fadeOut(tween(160)) + slideOutHorizontally(tween(240, easing = FastOutSlowInEasing)) { it / 24 }
            }
        ) {
            composable("systems") {
                SystemsScreen(
                    vm = vm,
                    isTr = isTr,
                    onOpenSystem = { open("system/$it") },
                    onFavorites = { openMenu("favorites") },
                    onSearch = { openMenu("search") },
                    onSettings = { openMenu("settings") },
                    onArcade = { open("arcade") }
                )
            }
            composable("arcade") {
                MiniGamesScreen(
                    isTr = isTr,
                    importing = importing,
                    progress = importProgress,
                    onExit = { navController.popBackStack() }
                )
            }
            composable("system/{consoleId}") { entry ->
                if (importing) {
                    ImportWaitScreen(importProgress, { open("arcade") }, { navController.popBackStack() })
                } else CompositionLocalProvider(LocalNavAnimScope provides this) {
                    GameListScreen(
                        vm = vm,
                        isTr = isTr,
                        consoleId = entry.arguments?.getString("consoleId").orEmpty(),
                        onBack = { navController.popBackStack() },
                        onDetails = { open("libgame/${it.id}") },
                        onFavorites = { openMenu("favorites") },
                        onSearch = { openMenu("search") },
                        onSettings = { openMenu("settings") }
                    )
                }
            }
            composable("favorites") {
                if (importing) {
                    ImportWaitScreen(importProgress, { open("arcade") }, { navController.popBackStack() })
                } else FavoritesScreen(
                    vm = vm,
                    isTr = isTr,
                    onBack = { navController.popBackStack() },
                    onDetails = { open("libgame/${it.id}") },
                    onSearch = { openMenu("search") },
                    onSettings = { openMenu("settings") }
                )
            }
            composable("search") {
                if (importing) {
                    ImportWaitScreen(importProgress, { open("arcade") }, { navController.popBackStack() })
                } else SearchScreen(
                    vm = vm,
                    isTr = isTr,
                    onBack = { navController.popBackStack() },
                    onDetails = { open("libgame/${it.id}") },
                    onSettings = { openMenu("settings") }
                )
            }
            composable("libgame/{libraryId}") { entry ->
                if (importing) {
                    ImportWaitScreen(importProgress, { open("arcade") }, { navController.popBackStack() })
                } else CompositionLocalProvider(LocalNavAnimScope provides this) {
                    LibraryDetailScreen(
                        vm = vm,
                        isTr = isTr,
                        libraryId = entry.arguments?.getString("libraryId").orEmpty(),
                        onBack = { navController.popBackStack() }
                    )
                }
            }
            composable("settings") {
                RetroSheet(settings.uiScanlines) {
                    SettingsScreen(
                        vm = vm,
                        isTr = isTr,
                        onBack = { navController.popBackStack() },
                        onStats = { open("stats") },
                        onSupporters = { open("supporters") },
                        onShowcase = { open("showcase") }
                    )
                }
            }
            composable("stats") {
                RetroSheet(settings.uiScanlines) {
                    StatsScreen(vm = vm, isTr = isTr, onBack = { navController.popBackStack() })
                }
            }
            composable("supporters") {
                RetroSheet(settings.uiScanlines) {
                    SupportersScreen(vm = vm, onBack = { navController.popBackStack() })
                }
            }
            composable("showcase") {
                RetroSheet(settings.uiScanlines) {
                    ShowcaseScreen(vm = vm, onBack = { navController.popBackStack() })
                }
            }
        }
        }
        }

        SnackbarHost(
            snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(LocalSafeArea.current)
                .padding(bottom = 36.dp)
        )

                if (importing && currentRoute in setOf("settings", "stats", "supporters", "showcase")) {
            ImportStatusPill(
                progress = importProgress,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(LocalSafeArea.current)
                    .padding(bottom = 44.dp)
            )
        }
    }

    if (attractVisible && attractEnabled) {
        AttractModeDialog(
            vm = vm,
            isTr = isTr,
            onDismiss = { interactionTick++ }
        )
    }

}

/**
 * First frame before the library is ready. A read failure used to leave this box blank forever,
 * so the error state now says what happened, promises the files are untouched, and focuses Retry
 * for the remote. Nothing is reset or deleted here.
 */
@Composable
private fun StartupGate(error: Boolean, onRetry: () -> Unit) {
    val retryFocus = remember { FocusRequester() }
    LaunchedEffect(error) {
        if (error) runCatching { retryFocus.requestFocus() }
    }
    Box(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.widthIn(max = 520.dp).padding(horizontal = 32.dp)
        ) {
            Text(
                stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(20.dp))
            if (!error) {
                CircularProgressIndicator(strokeWidth = 3.dp)
                Spacer(Modifier.height(16.dp))
                Text(
                    stringResource(R.string.library_loading),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    stringResource(R.string.library_load_error_title),
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    stringResource(R.string.library_load_error_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(24.dp))
                Row(
                    Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .focusRequester(retryFocus)
                        .tvFocus(RoundedCornerShape(12.dp))
                        .clickable(onClick = onRetry)
                        .padding(horizontal = 30.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        stringResource(R.string.retry),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

/**
 * Non-blocking import status for screens without an inline banner. It is never focusable, so the
 * D-pad keeps working on whatever the user is doing while the ZIP keeps importing.
 */
@Composable
private fun ImportStatusPill(progress: ImportProgress?, modifier: Modifier = Modifier) {
    val scanning = progress == null || progress.scanning
    val fraction = progress?.fraction?.takeUnless { scanning }
    Surface(
        modifier = modifier.widthIn(max = 360.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
        tonalElevation = 4.dp
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(if (scanning) R.string.import_stage_prepare else R.string.import_stage_games),
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (fraction != null) {
                    Text(
                        "${(fraction * 100).toInt()}%",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 10.dp)
                    )
                }
            }
            Spacer(Modifier.height(7.dp))
            if (fraction != null) {
                LinearProgressIndicator(
                    progress = { fraction },
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

private val menuRoutes = setOf("favorites", "search", "settings")

/** Long form screens sit on a centred translucent sheet over the dimmed CRT backdrop. */
@Composable
private fun RetroSheet(scanlines: Boolean, content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize()) {
        RetroBackdrop(null, scanlines = scanlines, dim = 0.55f)
        Surface(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .widthIn(max = 880.dp)
                .fillMaxWidth()
                .padding(
                    top = LocalSafeArea.current.calculateTopPadding(),
                    bottom = LocalSafeArea.current.calculateBottomPadding()
                )
                .fillMaxHeight(),
            color = MaterialTheme.colorScheme.background.copy(alpha = 0.9f),
            contentColor = MaterialTheme.colorScheme.onBackground
        ) {
            content()
        }
    }
}

@Composable
private fun AttractModeDialog(
    vm: AppViewModel,
    isTr: Boolean,
    onDismiss: () -> Unit
) {
    val library by vm.library.collectAsStateWithLifecycle()
    val covers by vm.coverUrls.collectAsStateWithLifecycle()
    val reducedMotion = LocalReducedMotion.current
    val games = remember(library) { library.shuffled().take(12) }
    var index by remember { mutableIntStateOf(0) }
    val focus = remember { FocusRequester() }

    LaunchedEffect(games) {
        games.forEach { vm.ensureCover(it) }
    }
    LaunchedEffect(games.size) {
        if (games.size > 1) {
            while (true) {
                delay(6000)
                index = (index + 1) % games.size
            }
        }
    }
    LaunchedEffect(Unit) {
        delay(120)
        runCatching { focus.requestFocus() }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black)
                .focusRequester(focus)
                .focusable()
                .onKeyEvent {
                    onDismiss()
                    true
                }
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss
                )
        ) {
            val game = games.getOrNull(index % games.size.coerceAtLeast(1))
            if (game == null) {
                Text(
                    stringResource(R.string.attract_collection),
                    style = MaterialTheme.typography.displayMedium,
                    color = Color.White,
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                Crossfade(targetState = game, animationSpec = tween(if (reducedMotion) 0 else 300), label = "attract") { current ->
                    val console = ConsoleCatalog.get(current.consoleId)
                    // Each slide zooms slowly for its 6 s on screen (Ken Burns).
                    val zoom = remember { Animatable(1f) }
                    LaunchedEffect(Unit) {
                        if (!reducedMotion) zoom.animateTo(1.12f, tween(7_000, easing = LinearEasing))
                    }
                    Box(Modifier.fillMaxSize()) {
                        GameCover(
                            url = covers["lib:${current.id}"] ?: current.coverUrl,
                            title = current.title,
                            console = console,
                            modifier = Modifier.fillMaxSize().graphicsLayer {
                                scaleX = zoom.value
                                scaleY = zoom.value
                            },
                            corner = 0.dp
                        )
                        Box(
                            Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        0f to Color.Black.copy(alpha = 0.25f),
                                        0.5f to Color.Transparent,
                                        1f to Color.Black.copy(alpha = 0.9f)
                                    )
                                )
                        )
                        Column(
                            Modifier
                                .align(Alignment.BottomStart)
                                .padding(horizontal = 72.dp, vertical = 56.dp)
                        ) {
                            Text(
                                console?.localizedName(isTr) ?: current.consoleId,
                                style = MaterialTheme.typography.labelLarge,
                                color = Color.White.copy(alpha = 0.7f)
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                current.title,
                                style = MaterialTheme.typography.displayLarge,
                                color = Color.White,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Text(
                            stringResource(R.string.app_name).uppercase(),
                            style = MaterialTheme.typography.labelLarge,
                            letterSpacing = 6.sp,
                            color = Color.White.copy(alpha = 0.55f),
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(horizontal = 44.dp, vertical = 36.dp)
                        )
                        Text(
                            stringResource(R.string.attract_hint),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.5f),
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(horizontal = 44.dp, vertical = 44.dp)
                        )
                    }
                }
            }
        }
    }
}
