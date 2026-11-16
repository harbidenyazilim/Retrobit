package com.retrobit.app.ui.player

import android.content.Intent
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.retrobit.app.ui.components.LocalUiSounds
import com.retrobit.app.ui.components.UiSounds
import com.retrobit.app.ui.components.rememberUiSounds
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.retrobit.app.RetrobitApplication
import com.retrobit.app.data.LibraryGame
import com.retrobit.app.data.SettingsData
import com.retrobit.app.ui.theme.RetrobitTheme
import com.retrobit.app.ui.components.GamepadInput
import com.retrobit.app.ui.components.GamepadKeyGuard
import com.retrobit.app.ui.components.GamepadNavigation
import com.retrobit.app.ui.components.PhysicalTriggerSources
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.swordfish.libretrodroid.GLRetroView
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first

/** Retain bytes, never a native surface/activity, across configuration recreation. */
class RetainedPlayerSession : ViewModel() {
    var gameId: String = ""
    var choice: String? = null
    var state: ByteArray? = null
    var sram: ByteArray? = null
    var markedPlayed: Boolean = false
}

class RetroPlayerActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_LIBRARY_ID = "extra_library_id"

        private const val TRIGGER_THRESHOLD = 0.5f
        private const val MENU_HOLD_MS = 650L

        /** SELECT/START wait this long for a combo partner before reaching the core. */
        private const val COMBO_GRACE_MS = 140L
        private const val TAP_RELEASE_MS = 60L

        private val deferredKeys = setOf(
            KeyEvent.KEYCODE_BUTTON_SELECT, KeyEvent.KEYCODE_BUTTON_START
        )

        private val gamepadKeys = setOf(
            KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_BUTTON_B,
            KeyEvent.KEYCODE_BUTTON_X, KeyEvent.KEYCODE_BUTTON_Y,
            KeyEvent.KEYCODE_BUTTON_1, KeyEvent.KEYCODE_BUTTON_2,
            KeyEvent.KEYCODE_BUTTON_3, KeyEvent.KEYCODE_BUTTON_4,
            KeyEvent.KEYCODE_BUTTON_L1, KeyEvent.KEYCODE_BUTTON_R1,
            KeyEvent.KEYCODE_BUTTON_L2, KeyEvent.KEYCODE_BUTTON_R2,
            KeyEvent.KEYCODE_BUTTON_THUMBL, KeyEvent.KEYCODE_BUTTON_THUMBR,
            KeyEvent.KEYCODE_BUTTON_START, KeyEvent.KEYCODE_BUTTON_SELECT,
            KeyEvent.KEYCODE_BUTTON_MODE,
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_DPAD_UP_LEFT, KeyEvent.KEYCODE_DPAD_UP_RIGHT,
            KeyEvent.KEYCODE_DPAD_DOWN_LEFT, KeyEvent.KEYCODE_DPAD_DOWN_RIGHT
        )
    }

    private val controller = PlayerController()
    private var uiSounds: UiSounds? = null
    private val gamepadNavigation = GamepadNavigation { dispatchKeyEvent(it) }
    private val pressedCoreKeys = mutableSetOf<Int>()
    private val triggerSources = PhysicalTriggerSources()
    private val axisNeedsNeutral = mutableSetOf<String>()
    private val rawAxisTriggers = mutableSetOf<String>()
    private val physicalKeysDown = mutableSetOf<Int>()
    private val blockedUntilUp = mutableSetOf<Int>()
    private val menuConfirmDown = mutableSetOf<Int>()
    private val inputListener = object : android.hardware.input.InputManager.InputDeviceListener {
        override fun onInputDeviceAdded(deviceId: Int) = Unit
        override fun onInputDeviceChanged(deviceId: Int) = Unit
        override fun onInputDeviceRemoved(deviceId: Int) { resetInput() }
    }
    private lateinit var retainedSession: RetainedPlayerSession

    private val heldComboKeys = mutableSetOf<Int>()
    private var comboStartedAt = 0L
    private var comboFired = false
    private var comboArmed = false
    private val comboHandler = Handler(Looper.getMainLooper())
    private val comboCheck = Runnable { fireComboIfHeld() }

    private val pendingKeys = mutableMapOf<Int, Int>()
    private val forwardedKeys = mutableMapOf<Int, Int>()
    private val swallowedKeys = mutableSetOf<Int>()
    private val flushPending = Runnable { flushPendingKeys() }

    private var gamepadLayout: String = SettingsData.GAMEPAD_XBOX
    private var gamepadCustomMapping: Map<Int, Int> = emptyMap()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        controller.onInputReset = ::resetInput
        getSystemService(android.hardware.input.InputManager::class.java).registerInputDeviceListener(inputListener, comboHandler)
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        hideSystemBars()
        volumeControlStream = AudioManager.STREAM_MUSIC
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val app = application as RetrobitApplication
        retainedSession = ViewModelProvider(this)[RetainedPlayerSession::class.java]
        controller.session = retainedSession
        if (retainedSession.choice == null) {
            retainedSession.choice = savedInstanceState?.getString("startup_choice")
            retainedSession.markedPlayed = savedInstanceState?.getBoolean("startup_marked") ?: false
        }
        lifecycleScope.launch {
            val game = withContext(Dispatchers.IO) {
                app.container.libraryRepository.loaded.first { it }
                resolveGame(app, intent)
            }
            if (game == null) finish() else showPlayer(app, game)
        }
    }

    private fun showPlayer(app: RetrobitApplication, game: LibraryGame) {
        if (retainedSession.gameId != game.id) {
            if (retainedSession.gameId.isNotEmpty()) {
                retainedSession.choice = null
                retainedSession.state = null
                retainedSession.sram = null
                retainedSession.markedPlayed = false
            }
            retainedSession.gameId = game.id
        }
        lifecycleScope.launch {
            app.container.settingsRepository.settings.collect { s ->
                val prefs = s.effectiveGamepad(game.id, game.consoleId)
                if (gamepadLayout != prefs.layout || gamepadCustomMapping != prefs.mapping) resetInput()
                gamepadLayout = prefs.layout
                gamepadCustomMapping = prefs.mapping
            }
        }

        setContent {
            val settings by app.container.settingsRepository.settings.collectAsStateWithLifecycle(SettingsData())
            val sounds = rememberUiSounds(settings.uiSounds)
            SideEffect { uiSounds = sounds }
            CompositionLocalProvider(LocalUiSounds provides sounds) {
                RetrobitTheme {
                    PlayerScreen(
                        game = game,
                        controller = controller,
                        onExit = { finish() }
                    )
                }
            }
        }
    }

    private fun resolveGame(app: RetrobitApplication, intent: Intent?): LibraryGame? {
        val id = intent?.getStringExtra(EXTRA_LIBRARY_ID) ?: return null
        return app.container.libraryRepository.library.value.firstOrNull { it.id == id }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        recreate()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val physical = GamepadInput.canonicalKey(event.keyCode)
        val down = event.action == KeyEvent.ACTION_DOWN
        val confirm = physical in setOf(KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER)
        // The launching card may still own a held confirm. Guard before even considering ROM input.
        if (confirm && event.action == KeyEvent.ACTION_UP && GamepadKeyGuard.consumeCenterUp()) {
            physicalKeysDown.remove(event.keyCode)
            menuConfirmDown.remove(event.keyCode)
            blockedUntilUp.remove(event.keyCode)
            return true
        }
        if (event.keyCode in blockedUntilUp) {
            if (!down) {
                blockedUntilUp.remove(event.keyCode)
                physicalKeysDown.remove(event.keyCode)
            }
            return true
        }
        val wasDown = event.keyCode in physicalKeysDown
        if (down) {
            if (event.repeatCount == 0) physicalKeysDown.add(event.keyCode)
        } else physicalKeysDown.remove(event.keyCode)
        val view = controller.view
        val gameplay = view != null && controller.coreLoaded && controller.inputReady &&
            !controller.menuOpen
        if (!gameplay || event.keyCode == KeyEvent.KEYCODE_BACK || event.keyCode == KeyEvent.KEYCODE_BUTTON_MODE) {
            uiSounds?.observeKey(event)
        }
        if (gameplay && physical in deferredKeys) trackComboKey(physical, down)
        if (event.keyCode == KeyEvent.KEYCODE_BACK ||
            event.keyCode == KeyEvent.KEYCODE_BUTTON_MODE
        ) {
            if (event.action == KeyEvent.ACTION_UP && !event.isCanceled) {
                onBackPressedDispatcher.onBackPressed()
            }
            return true
        }
        if (!gameplay && event.keyCode in deferredKeys && event.action == KeyEvent.ACTION_UP) {
            pendingKeys.remove(event.keyCode)
            forwardedKeys.remove(event.keyCode)
            swallowedKeys.remove(event.keyCode)
        }
        if (gameplay) {
            if (confirm && !wasDown && (!down || event.repeatCount > 0)) return true
            if (view != null && event.keyCode in deferredKeys) {
                deferKey(view, event.keyCode, event.action == KeyEvent.ACTION_DOWN, event.repeatCount)
                return true
            }
            if (view != null && physical in setOf(KeyEvent.KEYCODE_BUTTON_L2, KeyEvent.KEYCODE_BUTTON_R2)) {
                triggerSources.update(physical, "digital:${event.deviceId}:${event.keyCode}", down)
                updateTrigger(view, physical)
                return true
            }
            val target = gamepadKey(event.keyCode)
            if (view != null && target in gamepadKeys) {
                sendCoreKey(view, event.action, target)
                return true
            }
        }
        if (!gameplay) {
            if (confirm) {
                if (down) {
                    if (event.repeatCount != 0) return true
                    menuConfirmDown.add(event.keyCode)
                } else if (!menuConfirmDown.remove(event.keyCode)) return true
            }
            when (event.keyCode) {
                KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_BUTTON_1 -> {
                    val mapped = GamepadInput.remap(event, KeyEvent.KEYCODE_DPAD_CENTER)
                    super.dispatchKeyEvent(mapped)
                    return true
                }
                KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_BUTTON_2 -> {
                    if (event.action == KeyEvent.ACTION_UP && !event.isCanceled) {
                        onBackPressedDispatcher.onBackPressed()
                    }
                    return true
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        val view = controller.view
        val gameplay = view != null && controller.coreLoaded && controller.inputReady && !controller.menuOpen
        if (GamepadInput.isJoystick(event) && event.action == MotionEvent.ACTION_MOVE) {
            listOf(
                KeyEvent.KEYCODE_BUTTON_L2 to maxOf(event.getAxisValue(MotionEvent.AXIS_LTRIGGER), event.getAxisValue(MotionEvent.AXIS_BRAKE)),
                KeyEvent.KEYCODE_BUTTON_R2 to maxOf(event.getAxisValue(MotionEvent.AXIS_RTRIGGER), event.getAxisValue(MotionEvent.AXIS_GAS))
            ).forEach { (key, value) ->
                val source = "axis:${event.deviceId}:$key"
                if (value > TRIGGER_THRESHOLD) {
                    rawAxisTriggers.add(source)
                    if (!gameplay) axisNeedsNeutral.add(source)
                } else {
                    rawAxisTriggers.remove(source)
                    axisNeedsNeutral.remove(source)
                }
            }
        }
        if (!gameplay && gamepadNavigation.onMotion(event)) return true
        if (GamepadInput.isJoystick(event) && event.action == MotionEvent.ACTION_MOVE && gameplay && view != null) {
            gamepadNavigation.reset()
            val hatX = event.getAxisValue(MotionEvent.AXIS_HAT_X)
            val hatY = event.getAxisValue(MotionEvent.AXIS_HAT_Y)
            if (event.device?.getMotionRange(MotionEvent.AXIS_HAT_X, event.source) != null ||
                event.device?.getMotionRange(MotionEvent.AXIS_HAT_Y, event.source) != null
            ) view.sendMotionEvent(GLRetroView.MOTION_SOURCE_DPAD, hatX, hatY, 0)
            val left = stick(event, MotionEvent.AXIS_X, MotionEvent.AXIS_Y, -1, -1)
            val right = stick(event, MotionEvent.AXIS_RX, MotionEvent.AXIS_RY, MotionEvent.AXIS_Z, MotionEvent.AXIS_RZ)
            view.sendMotionEvent(
                GLRetroView.MOTION_SOURCE_ANALOG_LEFT,
                deadzone(left.first),
                deadzone(left.second),
                0
            )
            view.sendMotionEvent(
                GLRetroView.MOTION_SOURCE_ANALOG_RIGHT,
                deadzone(right.first),
                deadzone(right.second),
                0
            )

            val l2 = maxOf(
                event.getAxisValue(MotionEvent.AXIS_LTRIGGER),
                event.getAxisValue(MotionEvent.AXIS_BRAKE)
            )
            val r2 = maxOf(
                event.getAxisValue(MotionEvent.AXIS_RTRIGGER),
                event.getAxisValue(MotionEvent.AXIS_GAS)
            )
            fun axisPressed(key: Int, value: Float): Boolean =
                value > TRIGGER_THRESHOLD && "axis:${event.deviceId}:$key" !in axisNeedsNeutral
            triggerSources.update(KeyEvent.KEYCODE_BUTTON_L2, "axis:${event.deviceId}", axisPressed(KeyEvent.KEYCODE_BUTTON_L2, l2))
            triggerSources.update(KeyEvent.KEYCODE_BUTTON_R2, "axis:${event.deviceId}", axisPressed(KeyEvent.KEYCODE_BUTTON_R2, r2))
            updateTrigger(view, KeyEvent.KEYCODE_BUTTON_L2)
            updateTrigger(view, KeyEvent.KEYCODE_BUTTON_R2)
            return true
        }
        return super.dispatchGenericMotionEvent(event)
    }

    /**
     * TV remotes and gamepads have no way to reach the on-screen menu button while a game owns
     * the screen, so holding two shoulder/menu keys opens it.
     */
    private fun trackComboKey(keyCode: Int, pressed: Boolean) {
        if (pressed) {
            if (heldComboKeys.isEmpty()) {
                comboStartedAt = SystemClock.uptimeMillis()
                comboFired = false
                comboArmed = false
            }
            heldComboKeys.add(keyCode)
            // Held keys repeat every ~50ms, so the timer must only be armed once per combo.
            if (!comboArmed && !comboFired && GamepadInput.menuPairHeld(heldComboKeys)) {
                comboStartedAt = SystemClock.uptimeMillis()
                comboArmed = true
                comboHandler.postDelayed(comboCheck, MENU_HOLD_MS)
            }
            return
        }
        heldComboKeys.remove(keyCode)
        if (!GamepadInput.menuPairHeld(heldComboKeys)) {
            comboHandler.removeCallbacks(comboCheck)
            comboArmed = false
        }
        if (heldComboKeys.isEmpty()) comboFired = false
    }

    private fun fireComboIfHeld() {
        comboArmed = false
        if (comboFired || !GamepadInput.menuPairHeld(heldComboKeys)) return
        if (controller.view == null) return
        if (!controller.coreLoaded || !controller.inputReady || controller.menuOpen) return
        if (SystemClock.uptimeMillis() - comboStartedAt < MENU_HOLD_MS) {
            comboArmed = true
            comboHandler.postDelayed(comboCheck, 80L)
            return
        }
        comboFired = true
        comboHandler.removeCallbacks(comboCheck)
        resetInput()
        onBackPressedDispatcher.onBackPressed()
    }

    /**
     * SELECT+START is the menu combo, so the first of the pair is held back briefly: a lone
     * press reaches the core ~140 ms late, a combo never reaches it (e.g. START on a title
     * screen would otherwise start the game behind the menu).
     */
    private fun deferKey(view: GLRetroView, keyCode: Int, pressed: Boolean, repeat: Int) {
        val target = gamepadKey(keyCode)
        if (pressed) {
            if (repeat > 0 || keyCode in pendingKeys || keyCode in forwardedKeys ||
                keyCode in swallowedKeys
            ) return
            if (pendingKeys.isNotEmpty()) {
                comboHandler.removeCallbacks(flushPending)
                swallowedKeys += pendingKeys.keys
                swallowedKeys += keyCode
                pendingKeys.clear()
                return
            }
            if (swallowedKeys.isNotEmpty()) {
                swallowedKeys += keyCode
                return
            }
            pendingKeys[keyCode] = target
            comboHandler.postDelayed(flushPending, COMBO_GRACE_MS)
            return
        }
        if (swallowedKeys.remove(keyCode)) return
        pendingKeys.remove(keyCode)?.let { code ->
            if (pendingKeys.isEmpty()) comboHandler.removeCallbacks(flushPending)
            sendCoreKey(view, KeyEvent.ACTION_DOWN, code)
            // Cores poll once per frame; an instant release would be missed entirely.
            comboHandler.postDelayed({ sendCoreKey(view, KeyEvent.ACTION_UP, code) }, TAP_RELEASE_MS)
            return
        }
        sendCoreKey(view, KeyEvent.ACTION_UP, forwardedKeys.remove(keyCode) ?: target)
    }

    private fun flushPendingKeys() {
        val view = controller.view ?: return
        pendingKeys.forEach { (key, code) ->
            sendCoreKey(view, KeyEvent.ACTION_DOWN, code)
            forwardedKeys[key] = code
        }
        pendingKeys.clear()
    }

    private fun clearDeferredKeys(view: GLRetroView?) {
        comboHandler.removeCallbacks(flushPending)
        forwardedKeys.values.forEach { code ->
            runCatching { view?.sendKeyEvent(KeyEvent.ACTION_UP, code, 0) }
        }
        pendingKeys.clear()
        forwardedKeys.clear()
        swallowedKeys.clear()
    }

    private fun gamepadKey(keyCode: Int): Int =
        GamepadInput.mappedKey(keyCode, gamepadLayout, gamepadCustomMapping)

    private fun stick(
        event: MotionEvent,
        primaryX: Int,
        primaryY: Int,
        fallbackX: Int,
        fallbackY: Int
    ): Pair<Float, Float> {
        val device = event.device
        val primaryAvailable = device?.getMotionRange(primaryX, event.source) != null &&
            device.getMotionRange(primaryY, event.source) != null
        val xAxis = if (primaryAvailable || fallbackX < 0) primaryX else fallbackX
        val yAxis = if (primaryAvailable || fallbackY < 0) primaryY else fallbackY
        return centeredAxis(event, xAxis) to centeredAxis(event, yAxis)
    }

    private fun centeredAxis(event: MotionEvent, axis: Int): Float {
        val range = event.device?.getMotionRange(axis, event.source) ?: return 0f
        return GamepadInput.deadzone(event.getAxisValue(axis), range.flat)
    }

    private fun deadzone(value: Float): Float = GamepadInput.deadzone(value)

    private fun sendCoreKey(view: GLRetroView, action: Int, key: Int, port: Int = 0) {
        if (action == KeyEvent.ACTION_DOWN) pressedCoreKeys.add(key) else pressedCoreKeys.remove(key)
        view.sendKeyEvent(action, key, port)
    }

    private fun updateTrigger(view: GLRetroView, key: Int) {
        val pressed = triggerSources.held(key)
        val target = gamepadKey(key)
        if (pressed != (target in pressedCoreKeys)) {
            sendCoreKey(view, if (pressed) KeyEvent.ACTION_DOWN else KeyEvent.ACTION_UP, target)
        }
        trackComboKey(key, pressed)
    }

    private fun resetInput() {
        blockedUntilUp += physicalKeysDown
        menuConfirmDown.clear()
        axisNeedsNeutral += rawAxisTriggers
        gamepadNavigation.reset()
        comboHandler.removeCallbacksAndMessages(null)
        val view = controller.view
        pressedCoreKeys.toList().forEach { key ->
            runCatching { view?.sendKeyEvent(KeyEvent.ACTION_UP, key, 0) }
        }
        pressedCoreKeys.clear()
        clearDeferredKeys(view)
        if (controller.coreLoaded) {
            controller.releaseInput()
            runCatching {
                view?.sendMotionEvent(GLRetroView.MOTION_SOURCE_DPAD, 0f, 0f, 0)
                view?.sendMotionEvent(GLRetroView.MOTION_SOURCE_ANALOG_LEFT, 0f, 0f, 0)
                view?.sendMotionEvent(GLRetroView.MOTION_SOURCE_ANALOG_RIGHT, 0f, 0f, 0)
            }
        }
        heldComboKeys.clear()
        triggerSources.clear()
        comboArmed = false
        comboFired = false
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars() else resetInput()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        controller.captureForRecreation()
        // Process recreation must resume progress, not silently repeat a Start fresh boot.
        outState.putString("startup_choice", if (controller.writesAllowed) "resume" else retainedSession.choice)
        outState.putBoolean("startup_marked", retainedSession.markedPlayed)
        super.onSaveInstanceState(outState)
    }

    override fun onPause() {
        resetInput()
        controller.captureForRecreation()
        controller.pauseForSave()
        super.onPause()
    }

    override fun onDestroy() {
        resetInput()
        controller.onInputReset = null
        getSystemService(android.hardware.input.InputManager::class.java).unregisterInputDeviceListener(inputListener)
        comboHandler.removeCallbacks(comboCheck)
        comboHandler.removeCallbacksAndMessages(null)
        heldComboKeys.clear()
        controller.menuOpen = false
        controller.inputReady = false
        controller.coreLoaded = false
        controller.view = null
        super.onDestroy()
    }

    private fun hideSystemBars() {
        val insets = WindowCompat.getInsetsController(window, window.decorView)
        insets.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        insets.hide(WindowInsetsCompat.Type.systemBars())
    }
}
