package com.retrobit.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import android.view.MotionEvent
import com.retrobit.app.ui.components.GamepadInput
import com.retrobit.app.ui.components.GamepadNavigation
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import com.retrobit.app.ui.components.LocalUiSounds
import com.retrobit.app.ui.components.UiSounds
import com.retrobit.app.ui.components.rememberUiSounds
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.retrobit.app.ui.RetrobitApp
import com.retrobit.app.ui.components.GamepadKeyGuard
import com.retrobit.app.ui.theme.RetrobitTheme

class MainActivity : AppCompatActivity() {

    private val viewModel: AppViewModel by viewModels()
    private val gamepadNavigation = GamepadNavigation { dispatchKeyEvent(it) }
    private var uiSounds: UiSounds? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) {
            handleRomIntent(intent)
        }
        setContent {
            val settings by viewModel.settings.collectAsStateWithLifecycle()
            val sounds = rememberUiSounds(settings.uiSounds)
            SideEffect { uiSounds = sounds }
            CompositionLocalProvider(LocalUiSounds provides sounds) {
                RetrobitTheme(themeId = settings.effectiveTheme) {
                    RetrobitApp(viewModel)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleRomIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshCores()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        uiSounds?.observeKey(event)
        if (event.keyCode == KeyEvent.KEYCODE_BUTTON_B ||
            event.keyCode == KeyEvent.KEYCODE_BUTTON_2
        ) {
            // A synthetic BACK sent through the view tree is not guaranteed to reach the dispatcher.
            if (event.action == KeyEvent.ACTION_UP && !event.isCanceled) {
                onBackPressedDispatcher.onBackPressed()
            }
            return true
        }
        val mapped = when (event.keyCode) {
            KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_BUTTON_1 -> KeyEvent.KEYCODE_DPAD_CENTER
            else -> null
        }
        val keyCode = mapped ?: event.keyCode
        if (event.action == KeyEvent.ACTION_UP &&
            (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) &&
            GamepadKeyGuard.consumeCenterUp()
        ) {
            return true
        }
        if (mapped == null) return super.dispatchKeyEvent(event)
        return super.dispatchKeyEvent(GamepadInput.remap(event, keyCode))
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean =
        gamepadNavigation.onMotion(event) || super.dispatchGenericMotionEvent(event)

    override fun onPause() {
        gamepadNavigation.reset()
        super.onPause()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus) gamepadNavigation.reset()
    }

    private fun handleRomIntent(intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_VIEW -> intent.data?.let { viewModel.importUris(listOf(it)) }
            Intent.ACTION_SEND -> intent.shareUris().let { if (it.isNotEmpty()) viewModel.importUris(it) }
            Intent.ACTION_SEND_MULTIPLE ->
                intent.shareUris().let { if (it.isNotEmpty()) viewModel.importUris(it) }
        }
    }

    @Suppress("DEPRECATION")
    private fun Intent.shareUris(): List<Uri> = when (action) {
        Intent.ACTION_SEND -> listOfNotNull(getParcelableExtra<Uri>(Intent.EXTRA_STREAM))
        Intent.ACTION_SEND_MULTIPLE ->
            getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM).orEmpty()
        else -> emptyList()
    }
}
