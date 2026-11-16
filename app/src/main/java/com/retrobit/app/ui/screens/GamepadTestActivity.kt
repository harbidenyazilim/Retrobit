package com.retrobit.app.ui.screens

import android.hardware.input.InputManager
import android.os.Bundle
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import com.retrobit.app.ui.components.tvFocus
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.retrobit.app.R
import com.retrobit.app.RetrobitApplication
import com.retrobit.app.data.SettingsData
import com.retrobit.app.ui.components.GamepadInput
import com.retrobit.app.ui.components.GamepadNavigation
import com.retrobit.app.ui.components.LocalIsTv
import com.retrobit.app.ui.components.LocalRetroTheme
import com.retrobit.app.ui.components.LocalSafeArea
import com.retrobit.app.ui.components.RetroTopBar
import com.retrobit.app.ui.components.HintBlue
import com.retrobit.app.ui.theme.RetrobitTheme
import kotlinx.coroutines.launch
import java.util.Locale

class GamepadTestActivity : AppCompatActivity(), InputManager.InputDeviceListener {
    private var devices by mutableStateOf<List<InputDevice>>(emptyList())
    private var keyReport by mutableStateOf("")
    private var axisReport by mutableStateOf("")
    private var settings by mutableStateOf(SettingsData())
    private val navigation = GamepadNavigation { dispatchKeyEvent(it) }
    private val inputManager by lazy { getSystemService(InputManager::class.java) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        lifecycleScope.launch {
            (application as RetrobitApplication).container.settingsRepository.settings.collect { settings = it }
        }
        setContent {
            RetrobitTheme(themeId = settings.effectiveTheme) {
                CompositionLocalProvider(
                    LocalIsTv provides true,
                    LocalRetroTheme provides settings.retroTheme,
                    LocalSafeArea provides PaddingValues(horizontal = 40.dp, vertical = 22.dp)
                ) {
                    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(40.dp, 22.dp)) {
                        RetroTopBar(
                            title = stringResource(R.string.gamepad_test),
                            subtitle = stringResource(R.string.gamepad_test_local),
                            accent = HintBlue,
                            onBack = { finish() }
                        )
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            item {
                                Text(
                                    stringResource(R.string.gamepad_test_help),
                                    color = MaterialTheme.colorScheme.onBackground,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                            item {
                                Surface(shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth().tvFocus().focusable()) {
                                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text(stringResource(R.string.gamepad_test_keys), color = HintBlue)
                                        Text(keyReport.ifEmpty { "—" }, style = MaterialTheme.typography.bodyMedium)
                                        Text(stringResource(R.string.gamepad_test_axes), color = HintBlue)
                                        Text(axisReport.ifEmpty { "—" }, style = MaterialTheme.typography.bodyMedium)
                                    }
                                }
                            }
                            if (devices.isEmpty()) item {
                                Text(
                                    stringResource(R.string.gamepad_test_no_device),
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                            }
                            items(devices, key = { it.id }) { device ->
                                Surface(shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth().tvFocus().focusable()) {
                                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                        Text(device.name, style = MaterialTheme.typography.titleMedium)
                                        Text(
                                            "ID ${device.id} · VID ${device.vendorId.toString(16)} · PID ${device.productId.toString(16)} · source 0x${device.sources.toString(16)}",
                                            style = MaterialTheme.typography.labelMedium
                                        )
                                        Text(
                                            device.motionRanges.filter { it.source and InputDevice.SOURCE_CLASS_JOYSTICK != 0 }
                                                .joinToString(" · ") { MotionEvent.axisToString(it.axis).removePrefix("AXIS_") },
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        inputManager.registerInputDeviceListener(this, null)
        refreshDevices()
    }

    override fun onStop() {
        navigation.reset()
        inputManager.unregisterInputDeviceListener(this)
        super.onStop()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus) navigation.reset()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val mapped = GamepadInput.mappedKey(event.keyCode, settings.gamepadLayout, settings.gamepadCustomMapping)
        keyReport = "${event.device?.name ?: event.deviceId} · ${KeyEvent.keyCodeToString(event.keyCode)} (${event.keyCode}) · scan ${event.scanCode} · ${if (event.action == KeyEvent.ACTION_DOWN) "DOWN" else "UP"}\nRetroPad → ${KeyEvent.keyCodeToString(mapped)} · source 0x${event.source.toString(16)}"
        val uiKey = when (GamepadInput.canonicalKey(event.keyCode)) {
            KeyEvent.KEYCODE_BUTTON_A -> KeyEvent.KEYCODE_DPAD_CENTER
            KeyEvent.KEYCODE_BUTTON_B -> KeyEvent.KEYCODE_BACK
            else -> event.keyCode
        }
        return super.dispatchKeyEvent(if (uiKey == event.keyCode) event else GamepadInput.remap(event, uiKey))
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        if (GamepadInput.isJoystick(event)) {
            axisReport = "${event.device?.name ?: event.deviceId} · " + axes.joinToString(" · ") {
                "${MotionEvent.axisToString(it).removePrefix("AXIS_")}: ${String.format(Locale.ROOT, "%.2f", event.getAxisValue(it))}"
            }
        }
        return navigation.onMotion(event) || super.dispatchGenericMotionEvent(event)
    }

    private fun refreshDevices() {
        devices = InputDevice.getDeviceIds().toList().mapNotNull(InputDevice::getDevice).filter {
            it.supportsSource(InputDevice.SOURCE_GAMEPAD) || it.supportsSource(InputDevice.SOURCE_JOYSTICK)
        }
    }

    override fun onInputDeviceAdded(deviceId: Int) = refreshDevices()
    override fun onInputDeviceChanged(deviceId: Int) = refreshDevices()
    override fun onInputDeviceRemoved(deviceId: Int) {
        navigation.reset()
        refreshDevices()
    }

    companion object {
        private val axes = listOf(
            MotionEvent.AXIS_HAT_X, MotionEvent.AXIS_HAT_Y,
            MotionEvent.AXIS_X, MotionEvent.AXIS_Y, MotionEvent.AXIS_RX, MotionEvent.AXIS_RY,
            MotionEvent.AXIS_Z, MotionEvent.AXIS_RZ,
            MotionEvent.AXIS_LTRIGGER, MotionEvent.AXIS_RTRIGGER
        )
    }
}
