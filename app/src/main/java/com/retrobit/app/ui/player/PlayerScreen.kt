package com.retrobit.app.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.sp
import com.retrobit.app.ui.components.ConsoleWordmark
import com.retrobit.app.ui.components.LocalIsTv
import com.retrobit.app.ui.components.LocalRetroTheme
import com.retrobit.app.ui.components.consoleStyle
import com.retrobit.app.ui.components.focusGlow
import com.retrobit.app.ui.components.gamepadClickable
import com.retrobit.app.ui.components.onAccent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.opengl.GLSurfaceView
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.KeyEvent
import android.view.PixelCopy
import android.view.SurfaceView
import android.view.View
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Tv
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.retrobit.app.R
import com.retrobit.app.RetrobitApplication
import com.retrobit.app.data.ConsoleCatalog
import com.retrobit.app.data.LibraryGame
import com.retrobit.app.data.LibretroCore
import com.retrobit.app.data.SettingsData
import com.retrobit.app.data.GamepadPrefs
import com.retrobit.app.data.StartupProgress
import com.retrobit.app.data.StartupRestoreGate
import com.retrobit.app.data.scanStartupProgress
import com.retrobit.app.ui.components.GamepadInput
import kotlinx.coroutines.flow.first
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.material3.FilterChip
import com.retrobit.app.ui.components.ConsoleGlyph
import com.retrobit.app.ui.components.consoleColor
import com.retrobit.app.ui.components.rememberIsTv
import com.retrobit.app.ui.components.RequestFocusOnWindowFocus
import com.retrobit.app.ui.components.tvFocus
import com.retrobit.app.ui.theme.consoleGradient
import com.swordfish.libretrodroid.GLRetroView
import com.swordfish.libretrodroid.GLRetroViewData
import com.swordfish.libretrodroid.ShaderConfig
import com.swordfish.libretrodroid.Variable
import com.swordfish.libretrodroid.ViewportAlignment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

class PlayerController {
    var session: RetainedPlayerSession = RetainedPlayerSession()
    @Volatile var consoleId: String = ""
    @Volatile var writesAllowed: Boolean = false

    fun bindView(current: GLRetroView) {
        if (emulationPaused) pausedTotalMs += SystemClock.elapsedRealtime() - pausedSince
        emulationPaused = false
        view = current
        writesAllowed = false
    }

    fun captureForRecreation() {
        if (!writesAllowed || !coreLoaded) return
        val current = view ?: return
        session.state = runCatching { current.serializeState().takeIf { it.isNotEmpty() } }.getOrNull()
        session.sram = runCatching {
            current.serializeSRAM(useEmulationThread = false).takeIf { it.isNotEmpty() }
        }.getOrNull()
        session.choice = "resume"
    }

    @Volatile
    var view: GLRetroView? = null

    @Volatile
    var sramFile: File? = null

    @Volatile
    var savesDir: File? = null

    @Volatile
    var gameId: String = ""

    @Volatile
    var activeSlot: Int = 1

    @Volatile
    var autoSaveEnabled: Boolean = true

    @Volatile
    var currentShader: String = SettingsData.SHADER_DEFAULT

    /**
     * Pack ids are drawn by a Compose overlay on top of the surface; the engine shader underneath
     * only carries the base look, so these map to the closest preset.
     */
    fun shaderConfigFor(id: String): ShaderConfig = when (id) {
        SettingsData.SHADER_CRT,
        SettingsData.SHADER_APERTURE,
        SettingsData.SHADER_SCANLINE -> ShaderConfig.CRT

        SettingsData.SHADER_LCD,
        SettingsData.SHADER_DOTMATRIX,
        SettingsData.SHADER_GHOST -> ShaderConfig.LCD

        SettingsData.SHADER_SHARP -> ShaderConfig.Sharp
        else -> ShaderConfig.Default
    }

    fun setShader(shaderId: String) {
        currentShader = shaderId
        val v = view ?: return
        val target = shaderConfigFor(shaderId)
        runCatching {
            Thread {
                runCatching { v.shader = target }
                // A paused view only redraws on request, so show the new filter immediately.
                if (emulationPaused) runCatching { v.requestRender() }
            }.start()
        }
    }

    @Volatile
    var emulationPaused = false
        private set

    private var pausedSince = 0L
    private var pausedTotalMs = 0L

    /** Time spent frozen behind the menu, kept out of the play-time statistic. */
    fun pausedMillis(): Long =
        pausedTotalMs + if (emulationPaused) SystemClock.elapsedRealtime() - pausedSince else 0L

    /**
     * LibretroDroid steps the core inside onDrawFrame, so on-demand rendering freezes the game
     * while the GL thread keeps serving save/load requests. frameSpeed must not be used here.
     */
    fun setEmulationPaused(paused: Boolean) {
        val current = view ?: return
        if (paused == emulationPaused || (paused && !coreLoaded)) return
        emulationPaused = paused
        val now = SystemClock.elapsedRealtime()
        if (paused) pausedSince = now else pausedTotalMs += now - pausedSince
        runCatching {
            current.renderMode = if (paused) GLSurfaceView.RENDERMODE_WHEN_DIRTY
            else GLSurfaceView.RENDERMODE_CONTINUOUSLY
        }
        runCatching { current.audioEnabled = !paused }
    }

    fun cycleShader(supporterUnlocked: Boolean): String {
        val cycle = SettingsData.FREE_SHADERS +
            if (supporterUnlocked) SettingsData.SUPPORTER_SHADERS else emptyList()
        val index = cycle.indexOf(currentShader)
        val next = cycle[(index + 1).takeIf { it in cycle.indices } ?: 0]
        setShader(next)
        return next
    }

    @Volatile
    var coreLoaded = false

    /** True while the player menu or another overlay owns gamepad input. */
    var onInputReset: (() -> Unit)? = null

    @Volatile
    var menuOpen = false
        set(value) {
            if (value && !field) onInputReset?.invoke()
            field = value
        }

    /** True once the emulation surface is ready and no error overlay is shown. */
    @Volatile
    var inputReady = false

    fun stateFileForSlot(slot: Int): File {
        val dir = savesDir ?: return File("")
        val slotFile = File(dir, "${gameId}_slot$slot.state")
        if (slot == 1 && !slotFile.exists()) {
            val legacy = File(dir, "$gameId.state")
            if (legacy.exists()) return legacy
        }
        return slotFile
    }

    fun hasState(slot: Int = activeSlot): Boolean = stateFileForSlot(slot).isFile

    /** Screenshot stored next to a state so the slot picker can show what it contains. */
    fun thumbnailFileForSlot(slot: Int): File? =
        savesDir?.let { File(it, "${gameId}_slot$slot.png") }

    fun autoStateFile(): File? = savesDir?.let { File(it, "${gameId}_auto.state") }

    fun autoTimestamp(): Long? =
        autoStateFile()?.takeIf { it.isFile }?.lastModified()?.takeIf { it > 0L }

    fun slotTimestamp(slot: Int): Long? {
        val file = stateFileForSlot(slot)
        return if (file.isFile) file.lastModified() else null
    }

    fun saveState(slot: Int = activeSlot, thumbnail: Bitmap? = null): Boolean {
        if (!writesAllowed) return false
        val current = view ?: return false
        val dir = savesDir ?: return false
        val file = File(dir, "${gameId}_slot$slot.state")
        val saved = runCatching {
            val data = current.serializeState()
            if (data.isEmpty()) false else {
                com.retrobit.app.data.AtomicFiles.write(file) { it.write(data) }
                true
            }
        }.getOrDefault(false)
        if (saved && thumbnail != null) writeThumbnail(thumbnailFileForSlot(slot), thumbnail)
        return saved
    }

    /**
     * Recovery state kept apart from the user's slots so a periodic write can never
     * overwrite a deliberate save.
     */
    fun saveAutoState(): Boolean {
        if (!writesAllowed) return false
        val current = view ?: return false
        val file = autoStateFile() ?: return false
        val saved = runCatching {
            val data = current.serializeState()
            if (data.isEmpty()) false else {
                com.retrobit.app.data.AtomicFiles.write(file) { it.write(data) }
                true
            }
        }.getOrDefault(false)
        if (saved) captureGameFrame(current) { frame ->
            if (frame != null) {
                writeThumbnail(savesDir?.let { File(it, "${gameId}_auto.png") }, frame)
                frame.recycle()
            }
        }
        return saved
    }

    fun loadAutoState(): Boolean {
        if (!writesAllowed) return false
        val current = view ?: return false
        val file = autoStateFile()?.takeIf { it.isFile } ?: return false
        return runCatching { current.unserializeState(file.readBytes()) }.getOrDefault(false)
    }

    private fun writeThumbnail(target: File?, bitmap: Bitmap) {
        if (target == null) return
        runCatching {
            com.retrobit.app.data.AtomicFiles.write(target) { out ->
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 90, out))
            }
        }
    }

    fun loadState(slot: Int = activeSlot): Boolean {
        if (!writesAllowed) return false
        val current = view ?: return false
        val file = stateFileForSlot(slot).takeIf { it.isFile } ?: return false
        return runCatching {
            current.unserializeState(file.readBytes())
        }.getOrDefault(false)
    }

    /** Removes a user slot and its thumbnail; the automatic recovery state is untouched. */
    fun deleteState(slot: Int): Boolean {
        val dir = savesDir ?: return false
        // Slot 1 may still use the legacy "<game>.state" name.
        val files = listOf(
            stateFileForSlot(slot),
            File(dir, "${gameId}_slot$slot.state"),
            File(dir, "${gameId}_slot$slot.png")
        ).distinct()
        var deleted = false
        for (file in files) {
            if (!file.exists()) continue
            if (!runCatching { file.delete() }.getOrDefault(false)) return false
            deleted = true
        }
        return deleted
    }

    fun pauseForSave() {
        val current = view ?: return
        val ready = coreLoaded
        releaseInput()
        // Write the recovery state while the emulation thread is still alive.
        if (ready && autoSaveEnabled) saveAutoState()
        coreLoaded = false
        if (!ready) return
        runCatching { current.onPause() }
        saveSram()
    }

    /** Releases every virtual RetroPad input so nothing stays stuck between pauses. */
    fun releaseInput() {
        val current = view ?: return
        val keys = intArrayOf(
            KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_BUTTON_B,
            KeyEvent.KEYCODE_BUTTON_X, KeyEvent.KEYCODE_BUTTON_Y,
            KeyEvent.KEYCODE_BUTTON_L1, KeyEvent.KEYCODE_BUTTON_R1,
            KeyEvent.KEYCODE_BUTTON_L2, KeyEvent.KEYCODE_BUTTON_R2,
            KeyEvent.KEYCODE_BUTTON_THUMBL, KeyEvent.KEYCODE_BUTTON_THUMBR,
            KeyEvent.KEYCODE_BUTTON_START, KeyEvent.KEYCODE_BUTTON_SELECT,
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT
        )
        keys.forEach { key ->
            runCatching { current.sendKeyEvent(KeyEvent.ACTION_UP, key, 0) }
        }
        runCatching {
            current.sendMotionEvent(GLRetroView.MOTION_SOURCE_DPAD, 0f, 0f, 0)
        }
        runCatching {
            current.sendMotionEvent(GLRetroView.MOTION_SOURCE_ANALOG_LEFT, 0f, 0f, 0)
        }
        runCatching {
            current.sendMotionEvent(GLRetroView.MOTION_SOURCE_ANALOG_RIGHT, 0f, 0f, 0)
        }
    }

    fun saveSram(): Boolean {
        if (!writesAllowed) return false
        val current = view ?: return false
        val file = sramFile ?: return false
        return runCatching {
            val data = current.serializeSRAM(useEmulationThread = false)
            if (data.isEmpty()) false else {
                com.retrobit.app.data.AtomicFiles.write(file) { it.write(data) }
                true
            }
        }.getOrDefault(false)
    }

    fun reset() {
        runCatching { view?.reset(useEmulationThread = false) }
    }
}

/** Give each surface its own lifecycle so a replaced view's internal observers also stop. */
private class PlayerSurfaceLifecycle(
    private val activityLifecycle: Lifecycle,
    val view: GLRetroView
) : LifecycleOwner, LifecycleEventObserver {
    private val registry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle get() = registry
    init {
        registry.addObserver(view)
        activityLifecycle.addObserver(this)
    }
    override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
        if (registry.currentState != Lifecycle.State.DESTROYED) registry.handleLifecycleEvent(event)
    }
    fun dispose() {
        activityLifecycle.removeObserver(this)
        if (registry.currentState != Lifecycle.State.DESTROYED) registry.currentState = Lifecycle.State.DESTROYED
    }
}

@Composable
fun PlayerScreen(
    game: LibraryGame,
    controller: PlayerController,
    onExit: () -> Unit
) {
    val context = LocalContext.current
    val app = remember(context) { context.applicationContext as RetrobitApplication }
    val coreManager = app.container.coreManager
    val libraryRepository = app.container.libraryRepository
    val importer = app.container.romImporter
    val scope = rememberCoroutineScope()

    val console = remember(game.consoleId) { ConsoleCatalog.get(game.consoleId) }
    var core by remember(game.id) { mutableStateOf(coreManager.coreFor(game.consoleId, game.filePath)) }
    val analogConsole = game.consoleId in setOf("n64", "ps1", "psp")
    val isTr = remember { java.util.Locale.getDefault().language == "tr" }
    val storedSettings by app.container.settingsRepository.settings
        .collectAsStateWithLifecycle(SettingsData())
    val isTv = rememberIsTv(storedSettings.layoutMode)

    val sramFile = remember(game.id) { File(coreManager.savesDirectory, "${game.id}.srm") }
    controller.sramFile = sramFile
    controller.savesDir = coreManager.savesDirectory
    controller.gameId = game.id
    controller.consoleId = game.consoleId

    // Activity state/retained bytes turn an already-started game into Resume on recreation.
    // A saveable copy of the original Fresh choice would otherwise boot fresh after process death.
    var startupChoice by remember(game.id) { mutableStateOf(controller.session.choice) }
    var startupProgress by remember(game.id) { mutableStateOf<StartupProgress?>(null) }
    var startupFailed by remember { mutableStateOf(false) }
    var sramReadFailed by remember { mutableStateOf(false) }
    var startupScanVersion by remember { mutableIntStateOf(0) }
    var bootPrepared by remember { mutableStateOf(false) }
    var bootSram by remember { mutableStateOf<ByteArray?>(null) }
    var sessionReady by remember { mutableStateOf(false) }
    val effectiveGamepad = storedSettings.effectiveGamepad(game.id, game.consoleId)

    // Resolving the initial choice must not cancel the preparation that resolved it.
    LaunchedEffect(game.id, startupScanVersion) {
        bootPrepared = false
        val preferences = app.container.settingsRepository.settings.first()
        val inventory = withContext(Dispatchers.IO) {
            runCatching { scanStartupProgress(coreManager.savesDirectory, game.id) }
        }
        val available = inventory.getOrNull()
        startupProgress = available
        startupFailed = inventory.isFailure
        if (available == null) return@LaunchedEffect
        val choice = startupChoice ?: when {
            !available.canResume -> SettingsData.STARTUP_FRESH
            preferences.startupBehavior == SettingsData.STARTUP_RESUME -> SettingsData.STARTUP_RESUME
            preferences.startupBehavior == SettingsData.STARTUP_FRESH -> SettingsData.STARTUP_FRESH
            else -> null
        }
        if (choice == null) return@LaunchedEffect
        startupChoice = choice
        controller.session.choice = choice
        controller.onInputReset?.invoke()
        val result = withContext(Dispatchers.IO) {
            runCatching { controller.session.sram ?: available.sram?.read() }
        }
        sramReadFailed = result.isFailure
        startupFailed = result.isFailure || (choice == SettingsData.STARTUP_RESUME &&
            controller.session.state == null && !available.canResume)
        bootSram = result.getOrNull()
        bootPrepared = !startupFailed
    }

    val displayOverride = storedSettings.gameDisplayOverrides[game.id]
    val supporterUnlocked = storedSettings.supporterUnlocked
    val preferredShader = (displayOverride?.shader ?: storedSettings.defaultShader).let {
        if (!supporterUnlocked && it in SettingsData.SUPPORTER_SHADERS) {
            SettingsData.SHADER_DEFAULT
        } else {
            it
        }
    }
    val slotLimit = if (supporterUnlocked) SettingsData.SUPPORTER_SLOTS else SettingsData.FREE_SLOTS

    var activeSlot by remember { mutableIntStateOf(controller.activeSlot) }
    var slotVersion by remember { mutableIntStateOf(0) }
    var currentShader by remember { mutableStateOf(preferredShader) }

    LaunchedEffect(preferredShader) {
        currentShader = preferredShader
        controller.currentShader = preferredShader
    }

    LaunchedEffect(supporterUnlocked) {
        if (!supporterUnlocked && activeSlot > SettingsData.FREE_SLOTS) {
            activeSlot = 1
            controller.activeSlot = 1
        }
    }

    var attempt by remember { mutableIntStateOf(0) }
    var coreFile by remember { mutableStateOf<File?>(null) }
    var romFile by remember { mutableStateOf<File?>(null) }
    var romFailed by remember { mutableStateOf(false) }
    var coreFailed by remember { mutableStateOf(false) }
    var playerView by remember { mutableStateOf<GLRetroView?>(null) }
    var surfaceLifecycle by remember { mutableStateOf<PlayerSurfaceLifecycle?>(null) }
    var surfaceReady by remember { mutableStateOf(false) }
    var glError by remember { mutableStateOf<Int?>(null) }
    var viewGeneration by remember { mutableIntStateOf(0) }
    // The stored layout mode arrives a frame after the first composition, so derive the overlay
    // visibility instead of latching it: a TV has no touchscreen to press these buttons with.
    var overlayRequested by remember { mutableStateOf(true) }
    val overlayVisible = overlayRequested && !isTv
    var menuVisible by remember { mutableStateOf(false) }
    var autoSaveStamp by remember { mutableStateOf<Long?>(null) }
    var status by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(storedSettings.autoSaveState) {
        controller.autoSaveEnabled = storedSettings.autoSaveState
    }

    // Recovery saves are separate from the user's slots, so a crash or power cut never costs
    // more than a couple of minutes of progress without touching deliberate saves.
    LaunchedEffect(sessionReady, storedSettings.autoSaveState) {
        if (!sessionReady || !storedSettings.autoSaveState) return@LaunchedEffect
        autoSaveStamp = withContext(Dispatchers.IO) { controller.autoTimestamp() }
        while (true) {
            delay(120_000)
            if (controller.coreLoaded) {
                controller.saveAutoState()
                autoSaveStamp = controller.autoTimestamp()
            }
        }
    }

    val savedLabel = stringResource(R.string.player_state_saved)
    val stateFailedLabel = stringResource(R.string.player_state_failed)
    val stateLoadedLabel = stringResource(R.string.player_state_loaded)
    val resetLabel = stringResource(R.string.player_reset_done)
    val applyAllLabel = stringResource(R.string.player_apply_all_done)

    SideEffect {
        controller.menuOpen = menuVisible || startupChoice == null || startupFailed || !sessionReady
        controller.inputReady = sessionReady && surfaceReady && glError == null && playerView === controller.view
        controller.setEmulationPaused(menuVisible || !sessionReady || startupFailed || glError != null)
    }

    LaunchedEffect(attempt, bootPrepared) {
        if (!bootPrepared || startupChoice == null) return@LaunchedEffect
        coreFile = null
        romFile = null
        romFailed = false
        coreFailed = false
        if (core == null) {
            coreFailed = true
            return@LaunchedEffect
        }
        val resolved = try {
            importer.prepareRom(game)
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
        if (resolved == null) {
            romFailed = true
            return@LaunchedEffect
        }
        // The prepared file names the real format, e.g. an .sms unpacked for the SEGA card.
        val selected = coreManager.coreFor(game.consoleId, resolved.name)
        core = selected
        if (selected == null || !coreManager.isAvailable(selected)) {
            coreFailed = true
            return@LaunchedEffect
        }
        try {
            coreManager.prepareSystemFiles(selected)
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            coreFailed = true
            return@LaunchedEffect
        }
        coreFile = coreManager.coreFile(selected)
        romFile = resolved
    }

    LaunchedEffect(playerView) {
        // A recreated AndroidView starts with no surface; never inherit readiness from the old one.
        surfaceReady = false
        glError = null
        controller.inputReady = false
        controller.coreLoaded = false
        controller.writesAllowed = false
        sessionReady = false
        val view = playerView ?: return@LaunchedEffect
        val restoreGate = StartupRestoreGate()
        launch {
            view.getGLRetroEvents().collect { event ->
                if (controller.view !== view) return@collect
                // The flow replays only its latest event: a frame can replace SurfaceCreated
                // before we subscribe. Frames also restore readiness after a preserved-EGL pause.
                if (event is GLRetroView.GLRetroEvents.SurfaceCreated ||
                    event is GLRetroView.GLRetroEvents.FrameRendered
                ) {
                    surfaceReady = true
                    val resumed = (context as? ComponentActivity)?.lifecycle?.currentState
                        ?.isAtLeast(Lifecycle.State.RESUMED) == true
                    if (resumed) {
                        controller.coreLoaded = true
                        if (restoreGate.begin()) {
                            // Freeze before reading/restoring. No input, stats, timer or writes yet.
                            controller.setEmulationPaused(true)
                            val restore = startupChoice == "resume" || controller.session.state != null
                            val (result, sramOk) = withContext(Dispatchers.IO) {
                                val batteryOk = runCatching {
                                    controller.view === view && (bootSram?.let { view.unserializeSRAM(it) } ?: true)
                                }.getOrDefault(false)
                                val restored = batteryOk && runCatching {
                                    val bytes = if (restore) controller.session.state
                                        ?: startupProgress?.snapshot?.read() else null
                                    if (bytes != null) {
                                        check(bytes.isNotEmpty())
                                        if (controller.view !== view) false else view.unserializeState(bytes)
                                    } else !restore || startupProgress?.sram != null || controller.session.sram != null
                                }.getOrDefault(false)
                                restored to batteryOk
                            }
                            if (controller.view !== view) return@collect
                            val accepted = result && glError == null
                            restoreGate.complete(accepted)
                            controller.writesAllowed = restoreGate.writesAllowed
                            sessionReady = accepted
                            sramReadFailed = !sramOk
                            startupFailed = !accepted
                            controller.onInputReset?.invoke()
                        }
                        controller.inputReady = sessionReady && glError == null
                        controller.setEmulationPaused(menuVisible || !sessionReady || startupFailed || glError != null)
                    }
                }
            }
        }
        launch {
            view.getGLRetroErrors().collect { error ->
                if (controller.view === view) {
                    controller.inputReady = false
                    controller.writesAllowed = false
                    controller.onInputReset?.invoke()
                    controller.setEmulationPaused(true)
                    glError = error
                }
            }
        }
    }

    LaunchedEffect(sessionReady) {
        if (sessionReady && !controller.session.markedPlayed) {
            controller.session.markedPlayed = true
            libraryRepository.markPlayed(game.id)
        }
    }

    DisposableEffect(sessionReady, game.id) {
        if (!sessionReady) {
            onDispose { }
        } else {
            val startedAt = SystemClock.elapsedRealtime()
            val pausedAtStart = controller.pausedMillis()
            onDispose {
                val pausedMs = controller.pausedMillis() - pausedAtStart
                val seconds = (SystemClock.elapsedRealtime() - startedAt - pausedMs) / 1000
                if (seconds >= 5) libraryRepository.recordPlayTime(game.id, seconds)
            }
        }
    }

    LaunchedEffect(status) {
        if (status != null) {
            delay(2200)
            status = null
        }
    }

    val closeMenu: () -> Unit = {
        controller.onInputReset?.invoke()
        controller.releaseInput()
        menuVisible = false
        controller.menuOpen = false
        controller.setEmulationPaused(false)
    }

    val openMenu: () -> Unit = {
        if (!menuVisible && sessionReady && surfaceReady && glError == null && controller.view != null) {
            // Own input immediately, not one coroutine/recomposition after BACK was released.
            controller.menuOpen = true
            controller.setEmulationPaused(true)
            controller.currentShader = currentShader
            controller.releaseInput()
            controller.saveSram()
            autoSaveStamp = controller.autoTimestamp()
            menuVisible = true
        }
    }

    val exitPlayer: () -> Unit = {
        controller.pauseForSave()
        onExit()
    }

    // A GL/library failure can be a stale surface or a transient driver error; rebuild the view
    // once instead of forcing the player out. The lifecycle observer goes with the old view.
    val retryPlayerView: () -> Unit = {
        val stale = playerView
        if (surfaceLifecycle?.view === stale) {
            // Pause/destroy the old core before constructing another; never save a failed restore.
            surfaceLifecycle?.dispose()
            surfaceLifecycle = null
        }
        if (controller.view === stale) controller.view = null
        controller.onInputReset?.invoke()
        controller.coreLoaded = false
        controller.inputReady = false
        closeMenu()
        playerView = null
        surfaceReady = false
        sessionReady = false
        controller.writesAllowed = false
        glError = null
        viewGeneration++
    }

    BackHandler {
        when {
            startupChoice == null || startupFailed -> exitPlayer()
            glError != null -> exitPlayer()
            menuVisible -> closeMenu()
            sessionReady && surfaceReady && controller.view != null -> openMenu()
            else -> exitPlayer()
        }
    }

    CompositionLocalProvider(LocalIsTv provides isTv) {
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        val readyCore = coreFile
        val readyRom = romFile
        if (startupChoice != null && bootPrepared && readyCore != null && readyRom != null) {
            key(viewGeneration) {
            AndroidView(
                factory = { ctx ->
                    val data = GLRetroViewData(ctx).apply {
                        coreFilePath = readyCore.absolutePath
                        gameFilePath = readyRom.absolutePath
                        systemDirectory = coreManager.systemDirectory.absolutePath
                        savesDirectory = coreManager.savesDirectory.absolutePath
                        variables = core?.variables.orEmpty()
                            .map { (key, value) -> Variable(key, value) }.toTypedArray()
                        saveRAMState = bootSram
                        shader = controller.shaderConfigFor(preferredShader)
                        viewportAlignment = ViewportAlignment.CENTER
                    }
                    GLRetroView(ctx, data).also { view ->
                        // Activity routes ROM input; the native surface must never steal menu focus.
                        view.isFocusable = false
                        view.isFocusableInTouchMode = false
                        controller.inputReady = false
                        controller.coreLoaded = false
                        controller.bindView(view)
                        (ctx as? ComponentActivity)?.lifecycle?.let {
                            surfaceLifecycle = PlayerSurfaceLifecycle(it, view)
                        }
                        playerView = view
                    }
                },
                modifier = Modifier.fillMaxSize().focusProperties { canFocus = false },
                onRelease = { released ->
                    if (surfaceLifecycle?.view === released) {
                        surfaceLifecycle?.dispose()
                        surfaceLifecycle = null
                    }
                    if (controller.view === released) {
                        controller.onInputReset?.invoke()
                        controller.inputReady = false
                        controller.coreLoaded = false
                        controller.view = null
                    }
                    if (playerView === released) {
                        playerView = null
                        surfaceReady = false
                    }
                }
            )
            }
            if (currentShader in SettingsData.SUPPORTER_SHADERS && glError == null) {
                ShaderPackOverlay(packId = currentShader)
            }
        }

        if (startupChoice != null && !startupFailed && (readyCore == null || readyRom == null)) {
            CoreSetup(
                game = game,
                core = core,
                isTr = isTr,
                failed = coreFailed || romFailed,
                failureMessage = when {
                    romFailed -> stringResource(R.string.player_error_game)
                    coreFailed || core == null ->
                        if (core == null) stringResource(R.string.player_core_missing)
                        else stringResource(R.string.player_core_failed_desc, core?.displayName ?: "")
                    else -> null
                },
                busyLabel = if (bootPrepared && readyRom == null && !romFailed) {
                    stringResource(R.string.player_rom_unpacking)
                } else null,
                onRetry = { attempt++ },
                onExit = exitPlayer
            )
        }

        val view = playerView
        if (sessionReady && view != null && glError == null && !menuVisible) {
            Box(
                Modifier
                    .fillMaxSize()
                    .safeDrawingPadding()
            ) {
                // On a TV BACK or the gamepad combo opens the menu; its Exit action leaves the game.
                if (!isTv) {
                    Row(
                        Modifier
                            .align(Alignment.TopStart)
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        GlassButton(icon = Icons.Rounded.Menu) { openMenu() }
                        Spacer(Modifier.width(10.dp))
                        GlassButton(
                            icon = if (overlayVisible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility
                        ) { overlayRequested = !overlayVisible }
                        Spacer(Modifier.width(14.dp))
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = Color.Black.copy(alpha = 0.38f),
                            modifier = Modifier.widthIn(max = 360.dp)
                        ) {
                            Text(
                                game.title,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                                color = Color.White.copy(alpha = 0.86f),
                                style = MaterialTheme.typography.labelMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        GlassButton(icon = Icons.Rounded.Close) { exitPlayer() }
                    }
                }

                if (overlayVisible) {
                    Row(
                        Modifier
                            .align(Alignment.BottomStart)
                            .padding(start = 20.dp, end = 12.dp, bottom = 18.dp),
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                ShoulderButton("L2", KeyEvent.KEYCODE_BUTTON_L2, view)
                                ShoulderButton("L1", KeyEvent.KEYCODE_BUTTON_L1, view)
                            }
                            Dpad(view)
                        }
                        if (analogConsole) {
                            AnalogStick(view)
                        }
                    }

                    Column(
                        Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 20.dp, bottom = 18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ShoulderButton("R1", KeyEvent.KEYCODE_BUTTON_R1, view)
                            ShoulderButton("R2", KeyEvent.KEYCODE_BUTTON_R2, view)
                        }
                        FaceButtons(view)
                    }

                    Row(
                        Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        PillButton("SELECT", KeyEvent.KEYCODE_BUTTON_SELECT, view)
                        PillButton("START", KeyEvent.KEYCODE_BUTTON_START, view)
                    }
                }

                if (!menuVisible) {
                    status?.let { message ->
                        Surface(
                            modifier = Modifier.align(Alignment.TopCenter).padding(top = 66.dp),
                            shape = RoundedCornerShape(12.dp),
                            color = Color.Black.copy(alpha = 0.75f)
                        ) {
                            Text(
                                message,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                color = Color.White,
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                }

            }
        }

        glError?.let { code ->
            GlErrorOverlay(code = code, onRetry = {
                bootPrepared = false
                retryPlayerView()
                startupChoice = controller.session.choice ?: SettingsData.STARTUP_FRESH
                startupFailed = false
                startupScanVersion++
            }, onExit = exitPlayer)
        }

        if (glError == null && (startupChoice == null || startupFailed)) {
            StartupChooser(
                title = game.title,
                available = startupProgress,
                failed = startupFailed,
                freshEnabled = startupProgress != null && !sramReadFailed,
                onResume = {
                    controller.onInputReset?.invoke()
                    if (startupFailed) {
                        bootPrepared = false
                        retryPlayerView()
                        startupFailed = false
                    }
                    startupChoice = "resume"
                    controller.session.choice = "resume"
                    startupScanVersion++
                },
                onFresh = {
                    controller.onInputReset?.invoke()
                    bootPrepared = false
                    if (playerView != null) retryPlayerView()
                    controller.session.state = null
                    startupChoice = "fresh"
                    controller.session.choice = "fresh"
                    startupFailed = false
                    startupScanVersion++
                },
                onCancel = exitPlayer
            )
        }

        AnimatedVisibility(
            visible = menuVisible && sessionReady && glError == null,
            enter = fadeIn(tween(if (storedSettings.reducedMotion) 0 else 140)),
            exit = fadeOut(tween(if (storedSettings.reducedMotion) 0 else 160))
        ) {
            fun stopAfterFailedRestore() {
                controller.writesAllowed = false
                controller.inputReady = false
                controller.onInputReset?.invoke()
                controller.setEmulationPaused(true)
                sessionReady = false
                menuVisible = false
                startupFailed = true
            }
            fun saveSlot(slot: Int) {
                val glView = controller.view
                if (glView == null) {
                    status = stateFailedLabel
                    return
                }
                captureGameFrame(glView) { frame ->
                    val ok = controller.saveState(slot, frame)
                    status = if (ok) savedLabel else stateFailedLabel
                    if (ok) {
                        activeSlot = slot
                        controller.activeSlot = slot
                        slotVersion++
                    }
                    frame?.recycle()
                }
            }
            CompositionLocalProvider(LocalRetroTheme provides storedSettings.retroTheme) {
                PlayerMenu(
                    game = game,
                    retro = storedSettings.retroTheme,
                    reducedMotion = storedSettings.reducedMotion,
                    core = core,
                    isTr = isTr,
                    isTv = isTv,
                    activeSlot = activeSlot,
                    slotLimit = slotLimit,
                    onTapSlot = { slot ->
                        if (slot <= slotLimit) {
                            activeSlot = slot
                            controller.activeSlot = slot
                            // A filled slot loads on tap; an empty one starts a new save.
                            if (controller.hasState(slot)) {
                                val ok = controller.loadState(slot)
                                status = if (ok) stateLoadedLabel else stateFailedLabel
                                if (ok) {
                                    slotVersion++
                                    closeMenu()
                                } else {
                                    stopAfterFailedRestore()
                                }
                            } else {
                                saveSlot(slot)
                            }
                        }
                    },
                    slotTimestamp = { slot -> controller.slotTimestamp(slot) },
                    slotThumbnail = { slot -> controller.thumbnailFileForSlot(slot) },
                    slotVersion = slotVersion,
                    autoSaveStamp = autoSaveStamp,
                    currentShader = currentShader,
                    supporterUnlocked = supporterUnlocked,
                    onModalInputReset = { controller.onInputReset?.invoke() },
                    gamepadPrefs = effectiveGamepad,
                    onSaveGamepad = { prefs, applyConsole ->
                        controller.onInputReset?.invoke()
                        controller.releaseInput()
                        if (applyConsole) {
                            val ids = withContext(Dispatchers.IO) {
                                libraryRepository.loaded.first { it }
                                libraryRepository.library.value.filter { it.consoleId == game.consoleId }.map { it.id }
                            }
                            app.container.settingsRepository.applyGamepadToConsole(game.consoleId, prefs, ids)
                        } else {
                            app.container.settingsRepository.setGamepadForGame(game.id, prefs)
                        }
                    },
                    onToggleShader = {
                        val next = controller.cycleShader(supporterUnlocked)
                        currentShader = next
                        scope.launch {
                            app.container.settingsRepository.setGameDisplay(game.id, next)
                        }
                        val name = context.getString(shaderLabelRes(next))
                        status = context.getString(R.string.player_shader, name)
                    },
                    onApplyDisplayToAll = {
                        scope.launch {
                            app.container.settingsRepository.applyDisplayToAll(currentShader)
                        }
                        status = applyAllLabel
                    },
                    overlayVisible = overlayVisible,
                    showControlsToggle = !isTv,
                    onResume = closeMenu,
                    onOverwriteSlot = { slot -> saveSlot(slot) },
                    onDeleteSlot = { slot ->
                        val ok = controller.deleteState(slot)
                        status = if (ok) context.getString(R.string.player_slot_deleted, slot)
                        else context.getString(R.string.player_slot_delete_failed, slot)
                        if (ok) slotVersion++
                    },
                    onLoadAutoState = {
                        val loaded = controller.loadAutoState()
                        status = if (loaded) stateLoadedLabel else stateFailedLabel
                        if (loaded) closeMenu() else stopAfterFailedRestore()
                    },
                    onReset = {
                        controller.reset()
                        status = resetLabel
                        closeMenu()
                    },
                    onToggleControls = {
                        overlayRequested = !overlayVisible
                        closeMenu()
                    },
                    onExit = exitPlayer,
                    onDismiss = { closeMenu() }
                )
            }
        }
    }
    }
}

@Composable
private fun PlayerModal(title: String, content: @Composable () -> Unit) {
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.82f))
            .pointerInput(Unit) { detectTapGestures { } },
        contentAlignment = Alignment.Center
    ) {
        BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp), contentAlignment = Alignment.Center) {
            Surface(
                modifier = Modifier.widthIn(max = 540.dp).fillMaxWidth().heightIn(max = maxHeight),
                shape = RoundedCornerShape(18.dp)
            ) {
                Column(Modifier.padding(18.dp).focusGroup()) {
                    Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(10.dp))
                    MenuOverflowBody(Modifier.fillMaxWidth().weight(1f, fill = false), content)
                }
            }
        }
    }
}

@Composable
private fun StartupChooser(
    title: String,
    available: StartupProgress?,
    failed: Boolean,
    freshEnabled: Boolean,
    onResume: () -> Unit,
    onFresh: () -> Unit,
    onCancel: () -> Unit
) {
    val firstFocus = remember { FocusRequester() }
    val resumeEnabled = failed || available?.canResume == true
    PlayerModal(title) {
        Text(
            stringResource(if (failed) R.string.player_startup_restore_failed else R.string.player_startup_prompt),
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(10.dp))
        if (available == null && !failed) CircularProgressIndicator(Modifier.size(24.dp))
        MenuRow(
            Icons.Rounded.History,
            stringResource(if (failed) R.string.player_startup_retry else R.string.player_startup_resume),
            resumeEnabled, onResume,
            modifier = if (resumeEnabled) Modifier.focusRequester(firstFocus) else Modifier,
            compact = true
        )
        if (available != null && !available.canResume && !failed) {
            Text(stringResource(R.string.player_startup_empty), style = MaterialTheme.typography.labelSmall)
        }
        MenuRow(
            Icons.Rounded.RestartAlt, stringResource(R.string.player_startup_fresh), freshEnabled, onFresh,
            modifier = if (!resumeEnabled && freshEnabled) Modifier.focusRequester(firstFocus) else Modifier,
            compact = true
        )
        Text(stringResource(R.string.player_startup_fresh_desc), style = MaterialTheme.typography.labelSmall)
        MenuRow(
            Icons.Rounded.Close, stringResource(R.string.cancel), true, onCancel,
            modifier = if (!resumeEnabled && !freshEnabled) Modifier.focusRequester(firstFocus) else Modifier,
            compact = true
        )
    }
    LaunchedEffect(resumeEnabled, freshEnabled) {
        withFrameNanos { }
        runCatching { firstFocus.requestFocus() }
    }
    RequestFocusOnWindowFocus(firstFocus)
}

/** Copies the emulator surface (not the Compose menu above it) for the slot picker. */
private fun captureGameFrame(view: View, onCaptured: (Bitmap?) -> Unit) {
    val width = view.width
    val height = view.height
    if (width <= 0 || height <= 0 || view !is SurfaceView) {
        onCaptured(null)
        return
    }
    val full = runCatching {
        Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    }.getOrNull() ?: run {
        onCaptured(null)
        return
    }
    runCatching {
        PixelCopy.request(view, full, { result ->
            if (result == PixelCopy.SUCCESS) {
                val targetW = 480
                val targetH = (height * targetW / width).coerceAtLeast(1)
                val scaled = runCatching {
                    Bitmap.createScaledBitmap(full, targetW, targetH, true)
                }.getOrNull()
                if (scaled !== full) full.recycle()
                onCaptured(scaled)
            } else {
                full.recycle()
                onCaptured(null)
            }
        }, Handler(Looper.getMainLooper()))
    }.onFailure {
        full.recycle()
        onCaptured(null)
    }
}

@Composable
private fun CoreSetup(
    game: LibraryGame,
    core: LibretroCore?,
    isTr: Boolean,
    failed: Boolean,
    failureMessage: String?,
    busyLabel: String?,
    onRetry: () -> Unit,
    onExit: () -> Unit
) {
    val console = ConsoleCatalog.get(game.consoleId)
    val preparingText = busyLabel ?: stringResource(R.string.player_core_preparing)

    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF0C0B16), Color(0xFF050509))
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.widthIn(max = 420.dp).padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                Modifier
                    .size(92.dp)
                    .clip(RoundedCornerShape(30.dp))
                    .background(consoleGradient(consoleColor(console))),
                contentAlignment = Alignment.Center
            ) {
                ConsoleGlyph(console?.glyph, Modifier.size(44.dp))
            }
            Spacer(Modifier.height(20.dp))
            Text(
                game.title,
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                console?.localizedName(isTr) ?: game.consoleId,
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.55f),
                modifier = Modifier.padding(top = 4.dp)
            )
            Spacer(Modifier.height(28.dp))

            if (failed) {
                Icon(
                    Icons.Rounded.ErrorOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(28.dp)
                )
                Text(
                    failureMessage ?: stringResource(R.string.error_generic),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.8f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 12.dp)
                )
                Spacer(Modifier.height(22.dp))
                if (core != null) {
                    PlayerAction(stringResource(R.string.retry), primary = true, onClick = onRetry)
                }
                Spacer(Modifier.height(10.dp))
                PlayerAction(stringResource(R.string.player_exit), onClick = onExit)
            } else {
                Text(
                    preparingText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.8f),
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(18.dp))
                CircularProgressIndicator(
                    modifier = Modifier.size(30.dp),
                    strokeWidth = 3.dp,
                    color = MaterialTheme.colorScheme.secondary
                )
                Spacer(Modifier.height(20.dp))
                PlayerAction(stringResource(R.string.cancel), onClick = onExit)
            }
        }
    }
}

@Composable
private fun GlErrorOverlay(
    code: Int,
    onRetry: () -> Unit,
    onExit: () -> Unit
) {
    val message = when (code) {
        GLRetroView.ERROR_LOAD_LIBRARY -> stringResource(R.string.player_error_core)
        GLRetroView.ERROR_LOAD_GAME -> stringResource(R.string.player_error_game)
        GLRetroView.ERROR_GL_NOT_COMPATIBLE -> stringResource(R.string.player_error_gl)
        else -> stringResource(R.string.error_generic)
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.82f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier.widthIn(max = 400.dp).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Rounded.ErrorOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(34.dp)
            )
            Text(
                message,
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 14.dp)
            )
            Spacer(Modifier.height(22.dp))
            val retryFocus = remember { FocusRequester() }
            LaunchedEffect(Unit) { runCatching { retryFocus.requestFocus() } }
            PlayerAction(stringResource(R.string.retry), primary = true, onClick = onRetry, modifier = Modifier.focusRequester(retryFocus))
            Spacer(Modifier.height(10.dp))
            PlayerAction(stringResource(R.string.player_exit), onClick = onExit)
        }
    }
}

private val LocalMenuAccent = compositionLocalOf<Color?> { null }

private fun shaderLabelRes(id: String): Int = when (id) {
    SettingsData.SHADER_CRT -> R.string.player_shader_crt
    SettingsData.SHADER_LCD -> R.string.player_shader_lcd
    SettingsData.SHADER_SHARP -> R.string.player_shader_sharp
    SettingsData.SHADER_APERTURE -> R.string.player_shader_aperture
    SettingsData.SHADER_SCANLINE -> R.string.player_shader_scanline
    SettingsData.SHADER_DOTMATRIX -> R.string.player_shader_dotmatrix
    SettingsData.SHADER_GHOST -> R.string.player_shader_ghost
    else -> R.string.player_shader_default
}

@Composable
private fun PlayerMenu(
    game: LibraryGame,
    retro: Boolean,
    reducedMotion: Boolean,
    core: LibretroCore?,
    isTr: Boolean,
    isTv: Boolean,
    activeSlot: Int,
    slotLimit: Int,
    slotTimestamp: (Int) -> Long?,
    slotThumbnail: (Int) -> File?,
    slotVersion: Int,
    autoSaveStamp: Long?,
    currentShader: String,
    supporterUnlocked: Boolean,
    onToggleShader: () -> Unit,
    onApplyDisplayToAll: () -> Unit,
    onModalInputReset: () -> Unit,
    gamepadPrefs: GamepadPrefs,
    onSaveGamepad: suspend (GamepadPrefs, Boolean) -> Unit,
    overlayVisible: Boolean,
    showControlsToggle: Boolean,
    onResume: () -> Unit,
    onTapSlot: (Int) -> Unit,
    onOverwriteSlot: (Int) -> Unit,
    onDeleteSlot: (Int) -> Unit,
    onLoadAutoState: () -> Unit,
    onReset: () -> Unit,
    onToggleControls: () -> Unit,
    onExit: () -> Unit,
    onDismiss: () -> Unit
) {
    val console = ConsoleCatalog.get(game.consoleId)
    val accent = consoleStyle(console).accent
    val firstFocus = remember { FocusRequester() }
    var heldSlot by remember { mutableStateOf<Int?>(null) }
    var gamepadDraft by remember { mutableStateOf<GamepadPrefs?>(null) }
    val subdialogOpen = heldSlot != null || gamepadDraft != null
    LaunchedEffect(subdialogOpen) { onModalInputReset() }
    BackHandler(enabled = subdialogOpen) {
        if (heldSlot != null) heldSlot = null else gamepadDraft = null
    }
    val appear = remember(reducedMotion) { Animatable(if (reducedMotion) 1f else 0f) }
    LaunchedEffect(reducedMotion) {
        if (reducedMotion) appear.snapTo(1f)
        else appear.animateTo(1f, tween(220, easing = FastOutSlowInEasing))
    }
    if (!subdialogOpen) {
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = appear.value }
            .background(Color.Black.copy(alpha = if (retro) 0.62f else 0.55f))
            .pointerInput(Unit) { detectTapGestures { onDismiss() } }
    ) {
        BoxWithConstraints(
            Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            // Use the window's actual safe space, including split-screen and TV insets.
            val twoColumns = maxWidth >= 600.dp && (isTv || maxWidth > maxHeight)
            val compact = twoColumns || maxHeight < 420.dp
            val shape = RoundedCornerShape(if (retro) 18.dp else 24.dp)
            val autoTime = remember(autoSaveStamp) {
                autoSaveStamp?.takeIf { it > 0L }?.let {
                    java.text.SimpleDateFormat("dd MMM HH:mm", java.util.Locale.getDefault())
                        .format(java.util.Date(it))
                }
            }
            Surface(
                modifier = Modifier
                    .widthIn(max = if (twoColumns) 880.dp else 440.dp)
                    .fillMaxWidth()
                    .heightIn(max = maxHeight)
                    .graphicsLayer {
                        val s = 0.94f + 0.06f * appear.value
                        scaleX = s
                        scaleY = s
                    }
                    .then(if (retro) Modifier.focusGlow({ appear.value }, accent, shape, 16.dp) else Modifier)
                    .pointerInput(Unit) { detectTapGestures { } },
                shape = shape,
                color = if (retro) Color(0xF20C0C14) else MaterialTheme.colorScheme.surface,
                contentColor = if (retro) Color.White else MaterialTheme.colorScheme.onSurface,
                border = if (retro) {
                    BorderStroke(2.dp, Brush.verticalGradient(listOf(accent, accent.copy(alpha = 0.2f))))
                } else {
                    BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                }
            ) {
                CompositionLocalProvider(LocalMenuAccent provides accent) {
                    Column(Modifier.focusGroup().padding(if (compact) 16.dp else 20.dp)) {
                        if (retro && !compact) {
                            ConsoleWordmark(console, size = 22.sp, showStrip = true)
                            Spacer(Modifier.height(10.dp))
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    game.title,
                                    style = if (compact) MaterialTheme.typography.titleMedium
                                    else MaterialTheme.typography.titleLarge,
                                    maxLines = if (compact) 1 else 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    listOfNotNull(console?.localizedName(isTr), core?.displayName)
                                        .joinToString("  •  "),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(top = 3.dp)
                                )
                            }
                            if (retro && compact) {
                                Spacer(Modifier.width(12.dp))
                                ConsoleWordmark(console, size = 18.sp, showStrip = false)
                            }
                        }
                        Text(
                            stringResource(R.string.player_menu_hint),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                            maxLines = if (compact) 2 else 3,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = if (compact) 6.dp else 10.dp)
                        )
                        Spacer(Modifier.height(if (compact) 8.dp else 14.dp))
                        // These actions and the heading are outside the only scrollable viewport.
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MenuRow(
                                Icons.Rounded.PlayArrow,
                                stringResource(R.string.resume),
                                true,
                                onResume,
                                modifier = Modifier.weight(1f).focusRequester(firstFocus),
                                compact = compact
                            )
                            MenuRow(
                                Icons.Rounded.PowerSettingsNew,
                                stringResource(R.string.player_exit),
                                true,
                                onExit,
                                danger = true,
                                modifier = Modifier.weight(1f),
                                compact = compact
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        val slots: @Composable () -> Unit = {
                            SaveSlotSelector(
                                activeSlot = activeSlot,
                                slotLimit = slotLimit,
                                onTapSlot = onTapSlot,
                                onHoldSlot = { slot -> heldSlot = slot },
                                slotTimestamp = slotTimestamp,
                                slotThumbnail = slotThumbnail,
                                slotVersion = slotVersion
                            )
                            if (!supporterUnlocked) {
                                Text(
                                    stringResource(R.string.player_slots_hint),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                            Text(
                                stringResource(R.string.player_slot_tap_hint),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                                modifier = Modifier.padding(top = 4.dp)
                            )
                            Spacer(Modifier.height(6.dp))
                            MenuRow(
                                Icons.Rounded.History,
                                if (autoTime != null) stringResource(R.string.player_load_auto, autoTime)
                                else stringResource(R.string.player_load_auto_empty),
                                autoTime != null,
                                onLoadAutoState,
                                compact = compact
                            )
                        }
                        val actions: @Composable () -> Unit = {
                            MenuRow(
                                Icons.Rounded.SportsEsports,
                                stringResource(R.string.player_gamepad_edit),
                                true,
                                { gamepadDraft = gamepadPrefs.copy(mapping = gamepadPrefs.mapping.toMap()) },
                                compact = compact
                            )
                            val shaderName = stringResource(shaderLabelRes(currentShader))
                            MenuRow(
                                Icons.Rounded.Tv,
                                stringResource(R.string.player_shader, shaderName),
                                true,
                                onToggleShader,
                                compact = compact
                            )
                            MenuRow(
                                Icons.Rounded.DoneAll,
                                stringResource(R.string.player_apply_all),
                                true,
                                onApplyDisplayToAll,
                                compact = compact
                            )
                            MenuRow(
                                Icons.Rounded.RestartAlt,
                                stringResource(R.string.player_reset),
                                true,
                                onReset,
                                compact = compact
                            )
                            if (showControlsToggle) {
                                MenuRow(
                                    if (overlayVisible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                    if (overlayVisible) stringResource(R.string.player_hide_controls)
                                    else stringResource(R.string.player_show_controls),
                                    true,
                                    onToggleControls,
                                    compact = compact
                                )
                            }
                        }
                        MenuOverflowBody(Modifier.fillMaxWidth().weight(1f, fill = false)) {
                            if (twoColumns) {
                                Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                                    Column(Modifier.weight(1f)) { slots() }
                                    Column(Modifier.weight(1f)) { actions() }
                                }
                            } else {
                                slots()
                                actions()
                            }
                        }
                    }
                }
            }
            // Request after placement rather than relying on a fixed delay; regain focus after
            // a slot dialog/window interruption too. Native/touch gameplay targets are excluded.
            LaunchedEffect(twoColumns, compact, subdialogOpen) {
                if (!subdialogOpen) {
                    withFrameNanos { }
                    runCatching { firstFocus.requestFocus() }
                }
            }
            if (!subdialogOpen) RequestFocusOnWindowFocus(firstFocus)
        }
    }
    }
    heldSlot?.let { slot ->
        val slotFocus = remember { FocusRequester() }
        PlayerModal(stringResource(R.string.player_slot_actions_title, slot)) {
            Text(stringResource(R.string.player_slot_actions_desc))
            MenuRow(
                Icons.Rounded.History, stringResource(R.string.player_slot_overwrite), true,
                { heldSlot = null; onOverwriteSlot(slot) },
                modifier = Modifier.focusRequester(slotFocus), compact = true
            )
            MenuRow(
                Icons.Rounded.Close, stringResource(R.string.player_slot_delete), true,
                { heldSlot = null; onDeleteSlot(slot) }, danger = true, compact = true
            )
            MenuRow(Icons.Rounded.Close, stringResource(R.string.cancel), true, { heldSlot = null }, compact = true)
        }
        LaunchedEffect(slot) { withFrameNanos { }; runCatching { slotFocus.requestFocus() } }
        RequestFocusOnWindowFocus(slotFocus)
    }
    gamepadDraft?.let { initial ->
        PlayerGamepadEditor(
            initial = initial,
            consoleName = console?.localizedName(isTr) ?: game.consoleId,
            onSave = onSaveGamepad,
            onDismiss = { gamepadDraft = null }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlayerGamepadEditor(
    initial: GamepadPrefs,
    consoleName: String,
    onSave: suspend (GamepadPrefs, Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var draft by remember { mutableStateOf(initial) }
    var saving by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val firstFocus = remember { FocusRequester() }
    fun save(applyConsole: Boolean) {
        if (saving) return
        saving = true
        failed = false
        scope.launch {
            try {
                onSave(draft, applyConsole)
                onDismiss()
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                failed = true
            } finally {
                saving = false
            }
        }
    }
    PlayerModal(stringResource(R.string.player_gamepad_edit)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                SettingsData.GAMEPAD_XBOX to R.string.gamepad_layout_xbox,
                SettingsData.GAMEPAD_NINTENDO to R.string.gamepad_layout_nintendo,
                SettingsData.GAMEPAD_CUSTOM to R.string.gamepad_layout_custom
            ).forEachIndexed { index, (layout, label) ->
                FilterChip(
                    selected = draft.layout == layout,
                    onClick = { draft = draft.copy(layout = layout) },
                    enabled = !saving,
                    modifier = Modifier.then(if (index == 0) Modifier.focusRequester(firstFocus) else Modifier)
                        .tvFocus(RoundedCornerShape(8.dp)),
                    label = { Text(stringResource(label)) }
                )
            }
        }
        Text(stringResource(R.string.player_gamepad_scope_hint), style = MaterialTheme.typography.labelSmall)
        val names = listOf("A ↔ B", "X ↔ Y", "L1 ↔ R1", "L2 ↔ R2")
        GamepadInput.swapPairs.forEachIndexed { index, (first, second) ->
            val swapped = GamepadInput.pairSwapped(draft.mapping, first, second)
            MenuRow(
                Icons.Rounded.SportsEsports,
                stringResource(if (swapped) R.string.player_gamepad_swap_on else R.string.player_gamepad_swap_off, names[index]),
                !saving && (index >= 2 || draft.layout == SettingsData.GAMEPAD_CUSTOM),
                { draft = draft.copy(mapping = GamepadInput.togglePair(draft.mapping, first, second)) },
                compact = true
            )
        }
        MenuRow(
            Icons.Rounded.RestartAlt, stringResource(R.string.player_gamepad_reset_swaps), !saving,
            { draft = draft.copy(mapping = emptyMap()) }, compact = true
        )
        Text(stringResource(R.string.player_gamepad_touch_hint), style = MaterialTheme.typography.labelSmall)
        if (failed) Text(stringResource(R.string.player_gamepad_save_failed), color = MaterialTheme.colorScheme.error)
        if (saving) CircularProgressIndicator(Modifier.size(20.dp))
        MenuRow(Icons.Rounded.DoneAll, stringResource(R.string.player_gamepad_save_game), !saving, { save(false) }, compact = true)
        MenuRow(
            Icons.Rounded.DoneAll, stringResource(R.string.player_gamepad_apply_console, consoleName), !saving,
            { save(true) }, compact = true
        )
        MenuRow(Icons.Rounded.Close, stringResource(R.string.cancel), !saving, onDismiss, compact = true)
    }
    LaunchedEffect(Unit) { withFrameNanos { }; runCatching { firstFocus.requestFocus() } }
    RequestFocusOnWindowFocus(firstFocus)
}

/** Only create a scroll/focus node when the measured body exceeds its remaining safe viewport. */
@Composable
private fun MenuOverflowBody(modifier: Modifier, content: @Composable () -> Unit) {
    var contentHeight by remember { mutableIntStateOf(0) }
    val scrollState = rememberScrollState()
    BoxWithConstraints(modifier.clipToBounds()) {
        val overflows = contentHeight > constraints.maxHeight
        Layout(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (overflows) Modifier.verticalScroll(scrollState) else Modifier),
            content = {
                Column(Modifier.fillMaxWidth().onSizeChanged { contentHeight = it.height }) {
                    content()
                }
            }
        ) { measurables, viewport ->
            // Measure the same content once at its natural height, not a duplicate composition.
            val body = measurables.single().measure(
                viewport.copy(minHeight = 0, maxHeight = Constraints.Infinity)
            )
            layout(body.width, body.height.coerceIn(viewport.minHeight, viewport.maxHeight)) {
                body.placeRelative(0, 0)
            }
        }
    }
}

@Composable
private fun SaveSlotSelector(
    activeSlot: Int,
    slotLimit: Int,
    onTapSlot: (Int) -> Unit,
    onHoldSlot: (Int) -> Unit,
    slotTimestamp: (Int) -> Long?,
    slotThumbnail: (Int) -> File?,
    slotVersion: Int
) {
    val activeTime = remember(activeSlot, slotVersion) { slotTimestamp(activeSlot) }
    val formattedTime = remember(activeTime) {
        if (activeTime != null && activeTime > 0L) {
            java.text.SimpleDateFormat("dd MMM HH:mm", java.util.Locale.getDefault()).format(java.util.Date(activeTime))
        } else null
    }

    val slotAccent = LocalMenuAccent.current?.takeIf { LocalRetroTheme.current } ?: MaterialTheme.colorScheme.primary
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                stringResource(R.string.player_save_slot_title).uppercase(),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Text(
                formattedTime ?: stringResource(R.string.player_slot_empty),
                style = MaterialTheme.typography.labelSmall,
                color = if (formattedTime != null) slotAccent else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(Modifier.height(6.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            for (slot in 1..SettingsData.SUPPORTER_SLOTS) {
                val locked = slot > slotLimit
                val isSelected = slot == activeSlot
                val hasSave = !locked && slotTimestamp(slot) != null
                val thumbnail = if (!locked && hasSave) {
                    val file = remember(slot, slotVersion) { slotThumbnail(slot) }
                    var bitmap by remember { mutableStateOf<ImageBitmap?>(null) }
                    LaunchedEffect(file?.path, file?.lastModified()) {
                        bitmap = file?.let { path ->
                            withContext(Dispatchers.IO) {
                                runCatching {
                                    BitmapFactory.decodeFile(path.absolutePath)?.asImageBitmap()
                                }.getOrNull()
                            }
                        }
                    }
                    bitmap
                } else null
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = when {
                        locked -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.22f)
                        thumbnail != null -> Color.Black
                        isSelected -> slotAccent.copy(alpha = 0.16f)
                        hasSave -> MaterialTheme.colorScheme.surfaceVariant
                        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                    },
                    border = BorderStroke(
                        if (isSelected) 2.dp else 1.dp,
                        if (isSelected) slotAccent
                        else MaterialTheme.colorScheme.outlineVariant
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp)
                        .tvFocus(RoundedCornerShape(10.dp), if (LocalRetroTheme.current) Color.White else slotAccent)
                        .gamepadClickable(
                            onClick = { if (!locked) onTapSlot(slot) },
                            onLongClick = { if (!locked && hasSave) onHoldSlot(slot) }
                        )
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (locked) {
                            Icon(
                                Icons.Rounded.Lock,
                                contentDescription = stringResource(R.string.supporter_feature),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                                modifier = Modifier.size(13.dp)
                            )
                        } else {
                            thumbnail?.let { image ->
                                Image(
                                    bitmap = image,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(10.dp))
                                )
                                Box(
                                    Modifier
                                        .fillMaxSize()
                                        .background(
                                            Brush.verticalGradient(
                                                listOf(Color.Black.copy(alpha = 0.15f), Color.Black.copy(alpha = 0.72f))
                                            )
                                        )
                                )
                            }
                            Row(
                                Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(horizontal = 5.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (hasSave) {
                                    Box(
                                        Modifier
                                            .size(5.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF4CD964))
                                    )
                                    Spacer(Modifier.width(3.dp))
                                }
                                Text(
                                    "$slot",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = when {
                                        isSelected -> slotAccent
                                        thumbnail != null -> Color.White
                                        else -> MaterialTheme.colorScheme.onSurface
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MenuRow(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    danger: Boolean = false,
    modifier: Modifier = Modifier,
    accent: Color? = LocalMenuAccent.current,
    compact: Boolean = false
) {
    val retro = LocalRetroTheme.current && accent != null
    val interaction = remember { MutableInteractionSource() }
    // Programmatic focus may arrive while Compose is still in touch input mode.
    var focused by remember { mutableStateOf(false) }
    val fill by animateColorAsState(
        if (retro && focused) (if (danger) Color(0xFFE5484D) else accent!!) else Color.Transparent,
        tween(150),
        label = "menuRowFill"
    )
    val tint = when {
        !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
        retro && focused -> onAccent(if (danger) Color(0xFFE5484D) else accent!!)
        danger -> MaterialTheme.colorScheme.error
        retro -> Color.White
        else -> MaterialTheme.colorScheme.onSurface
    }
    val shape = if (retro) RoundedCornerShape(8.dp) else RoundedCornerShape(14.dp)
    val halo = if (danger) MaterialTheme.colorScheme.error else accent ?: MaterialTheme.colorScheme.primary
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = if (compact) 40.dp else 48.dp)
            .onFocusChanged { focused = it.isFocused }
            .then(
                if (focused && enabled) Modifier
                    .focusGlow({ 1f }, halo, shape, 8.dp)
                    .border(7.dp, halo.copy(alpha = 0.22f), shape)
                    .border(2.dp, halo, shape)
                else Modifier
            )
            .clip(shape)
            .background(fill)
            .then(if (LocalIsTv.current) Modifier.focusProperties { canFocus = enabled } else Modifier)
            .clickable(interactionSource = interaction, indication = if (retro) null else LocalIndication.current, enabled = enabled, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = if (compact) 7.dp else 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(if (compact) 18.dp else 20.dp))
        Spacer(Modifier.width(if (compact) 10.dp else 14.dp))
        Text(
            label,
            style = if (compact) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodyLarge,
            color = tint,
            maxLines = if (compact) 2 else 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.alpha(if (enabled) 1f else 0.7f)
        )
    }
}

@Composable
private fun PlayerAction(
    label: String,
    primary: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(50),
        color = if (primary) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.12f),
        modifier = modifier
            .tvFocus(RoundedCornerShape(50), color = if (primary) Color.White else MaterialTheme.colorScheme.primary)
            .clickable(onClick = onClick)
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
            style = MaterialTheme.typography.labelLarge,
            color = if (primary) MaterialTheme.colorScheme.onPrimary else Color.White
        )
    }
}

@Composable
private fun GlassButton(
    icon: ImageVector,
    active: Boolean = false,
    label: String? = null,
    onClick: () -> Unit
) {
    Surface(
        shape = CircleShape,
        color = if (active) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.38f),
        border = BorderStroke(
            1.dp,
            if (active) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.16f)
        ),
        modifier = Modifier
            .height(40.dp)
            .tvFocus(CircleShape)
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = if (label != null) 12.dp else 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (active) MaterialTheme.colorScheme.onPrimary else Color.White,
                modifier = Modifier.size(20.dp)
            )
            if (label != null) {
                Spacer(Modifier.width(5.dp))
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (active) MaterialTheme.colorScheme.onPrimary else Color.White
                )
            }
        }
    }
}

@Composable
private fun ShoulderButton(label: String, keyCode: Int, view: GLRetroView) {
    Box(
        Modifier
            .width(62.dp)
            .height(30.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color.Black.copy(alpha = 0.32f))
            .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(10.dp))
            .gameKey(view, keyCode),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.85f),
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun PillButton(label: String, keyCode: Int, view: GLRetroView) {
    Box(
        Modifier
            .width(78.dp)
            .height(28.dp)
            .clip(RoundedCornerShape(50))
            .background(Color.Black.copy(alpha = 0.32f))
            .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(50))
            .gameKey(view, keyCode),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.85f),
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun Dpad(view: GLRetroView) {
    Box(Modifier.size(142.dp)) {
        PadKey(
            Icons.Rounded.KeyboardArrowUp,
            KeyEvent.KEYCODE_DPAD_UP,
            view,
            Modifier.align(Alignment.TopCenter)
        )
        PadKey(
            Icons.Rounded.KeyboardArrowDown,
            KeyEvent.KEYCODE_DPAD_DOWN,
            view,
            Modifier.align(Alignment.BottomCenter)
        )
        PadKey(
            Icons.Rounded.KeyboardArrowLeft,
            KeyEvent.KEYCODE_DPAD_LEFT,
            view,
            Modifier.align(Alignment.CenterStart)
        )
        PadKey(
            Icons.Rounded.KeyboardArrowRight,
            KeyEvent.KEYCODE_DPAD_RIGHT,
            view,
            Modifier.align(Alignment.CenterEnd)
        )
    }
}

@Composable
private fun PadKey(
    icon: ImageVector,
    keyCode: Int,
    view: GLRetroView,
    modifier: Modifier = Modifier
) {
    Box(
        modifier
            .size(46.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color.Black.copy(alpha = 0.32f))
            .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(14.dp))
            .gameKey(view, keyCode),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = Color.White.copy(alpha = 0.9f), modifier = Modifier.size(26.dp))
    }
}

@Composable
private fun FaceButtons(view: GLRetroView) {
    Box(Modifier.size(150.dp)) {
        FaceButton("Y", KeyEvent.KEYCODE_BUTTON_Y, Color(0xFFFACC15), view, Modifier.align(Alignment.TopCenter))
        FaceButton("X", KeyEvent.KEYCODE_BUTTON_X, Color(0xFF60A5FA), view, Modifier.align(Alignment.CenterStart))
        FaceButton("B", KeyEvent.KEYCODE_BUTTON_B, Color(0xFFF87171), view, Modifier.align(Alignment.CenterEnd))
        FaceButton("A", KeyEvent.KEYCODE_BUTTON_A, Color(0xFF4ADE80), view, Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun FaceButton(
    label: String,
    keyCode: Int,
    accent: Color,
    view: GLRetroView,
    modifier: Modifier = Modifier
) {
    Box(
        modifier
            .size(46.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.4f))
            .border(1.5.dp, accent.copy(alpha = 0.65f), CircleShape)
            .gameKey(view, keyCode),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            style = MaterialTheme.typography.titleSmall,
            color = accent,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun AnalogStick(view: GLRetroView) {
    var thumb by remember { mutableStateOf(Offset.Zero) }
    Box(
        Modifier
            .size(112.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.26f))
            .border(1.dp, Color.White.copy(alpha = 0.16f), CircleShape)
            .pointerInput(view) {
                val limit = size.width / 2f - 24.dp.toPx()
                val center = Offset(size.width / 2f, size.height / 2f)
                fun apply(position: Offset) {
                    var delta = position - center
                    val distance = delta.getDistance()
                    if (distance > limit && distance > 0f) {
                        delta = delta * (limit / distance)
                    }
                    thumb = delta
                    view.sendMotionEvent(
                        GLRetroView.MOTION_SOURCE_ANALOG_LEFT,
                        delta.x / limit,
                        delta.y / limit
                    )
                }
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    apply(down.position)
                    drag(down.id) { change ->
                        change.consume()
                        apply(change.position)
                    }
                    thumb = Offset.Zero
                    view.sendMotionEvent(GLRetroView.MOTION_SOURCE_ANALOG_LEFT, 0f, 0f)
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier
                .offset { IntOffset(thumb.x.roundToInt(), thumb.y.roundToInt()) }
                .size(46.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.5f))
                .border(1.dp, Color.White.copy(alpha = 0.6f), CircleShape)
        )
    }
}

private fun Modifier.gameKey(view: GLRetroView, keyCode: Int): Modifier = this.then(
    Modifier.pointerInput(view, keyCode) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            down.consume()
            // Touch labels are logical RetroPad keys; physical gamepad preferences do not remap them.
            view.sendKeyEvent(KeyEvent.ACTION_DOWN, keyCode)
            try {
                waitForUpOrCancellation()
            } finally {
                runCatching { view.sendKeyEvent(KeyEvent.ACTION_UP, keyCode) }
            }
        }
    }
)

/**
 * Supporter shader packs are tiled overlay patterns rather than engine shaders: LibretroDroid
 * 0.14 only exposes fixed presets. A tiny bitmap repeated as an ImageShader keeps the cost
 * independent of resolution.
 */
@Composable
private fun ShaderPackOverlay(packId: String) {
    val image = remember(packId) { shaderPattern(packId)?.asImageBitmap() } ?: return
    val brush = remember(image) {
        ShaderBrush(ImageShader(image, TileMode.Repeated, TileMode.Repeated))
    }
    Box(Modifier.fillMaxSize().drawBehind { drawRect(brush) })
}

private fun shaderPattern(packId: String): Bitmap? {
    val size = when (packId) {
        SettingsData.SHADER_APERTURE, SettingsData.SHADER_DOTMATRIX -> 6
        SettingsData.SHADER_SCANLINE, SettingsData.SHADER_GHOST -> 8
        else -> return null
    }
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)
    val paint = android.graphics.Paint()
    when (packId) {
        SettingsData.SHADER_APERTURE -> {
            paint.strokeWidth = 1f
            intArrayOf(0x2EFF3B4E, 0x2E35FF6A, 0x2E3E8BFF).forEachIndexed { index, color ->
                paint.color = color.toInt()
                val x = index * 2f
                canvas.drawLine(x, 0f, x, size.toFloat(), paint)
            }
        }
        SettingsData.SHADER_SCANLINE -> {
            paint.color = 0x33000000
            paint.strokeWidth = 1.2f
            for (i in 0..2) {
                val x = i * 4f
                canvas.drawLine(x, 0f, x + size, size.toFloat(), paint)
            }
        }
        SettingsData.SHADER_DOTMATRIX -> {
            paint.color = 0x33000000
            paint.isAntiAlias = true
            canvas.drawCircle(size / 2f, size / 2f, 1.15f, paint)
        }
        SettingsData.SHADER_GHOST -> {
            paint.color = 0x12FFFFFF
            canvas.drawRect(0f, 0f, size.toFloat(), 2f, paint)
            paint.color = 0x08FFFFFF
            canvas.drawRect(0f, 2.5f, size.toFloat(), 4f, paint)
        }
    }
    return bitmap
}
