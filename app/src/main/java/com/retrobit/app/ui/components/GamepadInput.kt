package com.retrobit.app.ui.components

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import com.retrobit.app.data.SettingsData
import kotlin.math.abs

object GamepadInput {
    fun canonicalKey(key: Int): Int = when (key) {
        KeyEvent.KEYCODE_BUTTON_1 -> KeyEvent.KEYCODE_BUTTON_A
        KeyEvent.KEYCODE_BUTTON_2 -> KeyEvent.KEYCODE_BUTTON_B
        KeyEvent.KEYCODE_BUTTON_3 -> KeyEvent.KEYCODE_BUTTON_X
        KeyEvent.KEYCODE_BUTTON_4 -> KeyEvent.KEYCODE_BUTTON_Y
        KeyEvent.KEYCODE_BUTTON_5, KeyEvent.KEYCODE_L -> KeyEvent.KEYCODE_BUTTON_L1
        KeyEvent.KEYCODE_BUTTON_6, KeyEvent.KEYCODE_R -> KeyEvent.KEYCODE_BUTTON_R1
        KeyEvent.KEYCODE_BUTTON_7 -> KeyEvent.KEYCODE_BUTTON_L2
        KeyEvent.KEYCODE_BUTTON_8 -> KeyEvent.KEYCODE_BUTTON_R2
        else -> key
    }

    fun mappedKey(key: Int, layout: String, custom: Map<Int, Int>): Int {
        val canonical = canonicalKey(key)
        val shoulderPartner = when (canonical) {
            KeyEvent.KEYCODE_BUTTON_L1 -> KeyEvent.KEYCODE_BUTTON_R1
            KeyEvent.KEYCODE_BUTTON_R1 -> KeyEvent.KEYCODE_BUTTON_L1
            KeyEvent.KEYCODE_BUTTON_L2 -> KeyEvent.KEYCODE_BUTTON_R2
            KeyEvent.KEYCODE_BUTTON_R2 -> KeyEvent.KEYCODE_BUTTON_L2
            else -> null
        }
        // Shoulder swaps are independent of the face-button layout, and mapping is one-pass.
        if (shoulderPartner != null) {
            return if (custom[canonical] == shoulderPartner) shoulderPartner else canonical
        }
        if (layout == SettingsData.GAMEPAD_CUSTOM) {
            return custom[key] ?: custom[canonical] ?: canonical
        }
        if (layout != SettingsData.GAMEPAD_NINTENDO) return canonical
        return when (canonical) {
            KeyEvent.KEYCODE_BUTTON_A -> KeyEvent.KEYCODE_BUTTON_B
            KeyEvent.KEYCODE_BUTTON_B -> KeyEvent.KEYCODE_BUTTON_A
            KeyEvent.KEYCODE_BUTTON_X -> KeyEvent.KEYCODE_BUTTON_Y
            KeyEvent.KEYCODE_BUTTON_Y -> KeyEvent.KEYCODE_BUTTON_X
            else -> canonical
        }
    }

    val swapPairs: List<Pair<Int, Int>> = listOf(
        KeyEvent.KEYCODE_BUTTON_A to KeyEvent.KEYCODE_BUTTON_B,
        KeyEvent.KEYCODE_BUTTON_X to KeyEvent.KEYCODE_BUTTON_Y,
        KeyEvent.KEYCODE_BUTTON_L1 to KeyEvent.KEYCODE_BUTTON_R1,
        KeyEvent.KEYCODE_BUTTON_L2 to KeyEvent.KEYCODE_BUTTON_R2
    )

    fun pairSwapped(mapping: Map<Int, Int>, first: Int, second: Int): Boolean =
        mapping[first] == second && mapping[second] == first

    fun togglePair(mapping: Map<Int, Int>, first: Int, second: Int): Map<Int, Int> {
        val swapped = pairSwapped(mapping, first, second)
        val result = mapping.filterKeys { canonicalKey(it) != first && canonicalKey(it) != second }.toMutableMap()
        if (!swapped) {
            result[first] = second
            result[second] = first
        }
        return result
    }

    fun menuPairHeld(keys: Set<Int>): Boolean =
        (KeyEvent.KEYCODE_BUTTON_SELECT in keys && KeyEvent.KEYCODE_BUTTON_START in keys) ||
            (KeyEvent.KEYCODE_BUTTON_L2 in keys && KeyEvent.KEYCODE_BUTTON_R2 in keys)

    fun direction(x: Float, y: Float): Int = when {
        abs(x) < 0.5f && abs(y) < 0.5f -> 0
        abs(x) > abs(y) -> if (x < 0) KeyEvent.KEYCODE_DPAD_LEFT else KeyEvent.KEYCODE_DPAD_RIGHT
        else -> if (y < 0) KeyEvent.KEYCODE_DPAD_UP else KeyEvent.KEYCODE_DPAD_DOWN
    }

    fun deadzone(value: Float, flat: Float = 0.12f): Float =
        if (abs(value) <= maxOf(0.12f, flat)) 0f else value.coerceIn(-1f, 1f)

    fun isJoystick(event: MotionEvent): Boolean =
        event.isFromSource(InputDevice.SOURCE_JOYSTICK) ||
            event.isFromSource(InputDevice.SOURCE_GAMEPAD)

    fun remap(event: KeyEvent, key: Int): KeyEvent = KeyEvent(
        event.downTime, event.eventTime, event.action, key, event.repeatCount,
        event.metaState, event.deviceId, event.scanCode, event.flags, event.source
    )
}

/** Source identities are physical (device + axis/digital alias), never mapped output keys. */
class PhysicalTriggerSources {
    private val sources = mutableMapOf<Int, MutableSet<String>>()
    fun update(physicalKey: Int, source: String, pressed: Boolean): Boolean {
        val key = GamepadInput.canonicalKey(physicalKey)
        val held = sources.getOrPut(key) { mutableSetOf() }
        if (pressed) held.add(source) else held.remove(source)
        return held.isNotEmpty()
    }
    fun held(key: Int): Boolean = sources[GamepadInput.canonicalKey(key)]?.isNotEmpty() == true
    fun clear() { sources.clear() }
}

class GamepadNavigation(private val dispatch: (KeyEvent) -> Unit) {
    private val handler = Handler(Looper.getMainLooper())
    private var direction = 0
    private var deviceId = -1
    private val repeat = object : Runnable {
        override fun run() {
            if (direction == 0) return
            sendStep()
            handler.postDelayed(this, 110L)
        }
    }

    fun onMotion(event: MotionEvent): Boolean {
        if (!GamepadInput.isJoystick(event) || event.action != MotionEvent.ACTION_MOVE) return false
        val hatX = event.getAxisValue(MotionEvent.AXIS_HAT_X)
        val hatY = event.getAxisValue(MotionEvent.AXIS_HAT_Y)
        val next = if (abs(hatX) > 0.5f || abs(hatY) > 0.5f) {
            GamepadInput.direction(hatX, hatY)
        } else {
            GamepadInput.direction(
                centered(event, MotionEvent.AXIS_X), centered(event, MotionEvent.AXIS_Y)
            )
        }
        if (next != direction || event.deviceId != deviceId) {
            reset()
            direction = next
            deviceId = event.deviceId
            if (direction != 0) {
                sendStep()
                handler.postDelayed(repeat, 350L)
            }
        }
        return true
    }

    private fun centered(event: MotionEvent, axis: Int): Float {
        val range = event.device?.getMotionRange(axis, event.source) ?: return 0f
        return GamepadInput.deadzone(event.getAxisValue(axis), range.flat)
    }

    private fun sendStep() {
        val now = SystemClock.uptimeMillis()
        dispatch(KeyEvent(now, now, KeyEvent.ACTION_DOWN, direction, 0, 0, deviceId, 0, 0, InputDevice.SOURCE_DPAD))
        dispatch(KeyEvent(now, now, KeyEvent.ACTION_UP, direction, 0, 0, deviceId, 0, 0, InputDevice.SOURCE_DPAD))
    }

    fun reset() {
        handler.removeCallbacks(repeat)
        direction = 0
        deviceId = -1
    }
}
