package com.retrobit.app.ui.components

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.SystemClock
import android.view.KeyEvent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.sin

/** Original, short sine-tone gestures; no samples, music, assets or audio-focus stealing. */
enum class UiSound(val durationMs: Int, val cooldownMs: Long) {
    Move(42, 70), Confirm(86, 120), Back(74, 120),
    Score(130, 100), Collision(55, 85), GameOver(230, 600)
}

val LocalUiSounds = staticCompositionLocalOf { UiSounds() }

/** Main-thread API. Missing/failed loads are silent, never queued for later playback. */
class UiSounds internal constructor() {
    private var pool: SoundPool? = null
    private val samples = IntArray(UiSound.entries.size)
    private val loaded = HashSet<Int>()
    private val lastPlayed = LongArray(UiSound.entries.size) { -10_000L }
    private val recent = LongArray(12) { -10_000L }
    private var recentIndex = 0
    private val streams = IntArray(2)
    private val expires = LongArray(2)
    private var lastAny = -10_000L
    private var enabled = false
    private var gameplayOwners = 0
    private var lastKeyTime = Long.MIN_VALUE
    private var lastKeyCode = -1

    internal fun setEnabled(value: Boolean) {
        enabled = value
        if (!value) release()
    }

    /** Suppress menu move/confirm tones while the game supplies meaningful action sounds. */
    fun beginGameplay(): () -> Unit {
        gameplayOwners++
        return { gameplayOwners = (gameplayOwners - 1).coerceAtLeast(0) }
    }

    /** Observes only: callers must still dispatch/return false. MainActivity already maps A/B. */
    fun observeKey(event: KeyEvent) {
        if (event.action != KeyEvent.ACTION_DOWN || event.repeatCount != 0) return
        if (lastKeyTime == event.eventTime && lastKeyCode == event.keyCode) return
        lastKeyTime = event.eventTime
        lastKeyCode = event.keyCode
        val sound = when (event.keyCode) {
            KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ESCAPE,
            KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_BUTTON_2 -> UiSound.Back
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER,
            KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_BUTTON_1 ->
                if (gameplayOwners == 0) UiSound.Confirm else null
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN ->
                if (gameplayOwners == 0) UiSound.Move else null
            else -> null
        }
        sound?.let(::play)
    }

    fun play(sound: UiSound) {
        if (!enabled) return
        val current = pool ?: return
        val sample = samples[sound.ordinal]
        if (sample !in loaded) return
        val now = SystemClock.uptimeMillis()
        // At most 12 starts/sec, 2 overlapping voices, and no keyboard-repeat chatter.
        if (now - lastAny < 40 || now - lastPlayed[sound.ordinal] < sound.cooldownMs ||
            now - recent[recentIndex] < 1_000
        ) return
        var slot = expires.indexOfFirst { it <= now }
        if (slot < 0) {
            if (sound != UiSound.GameOver) return
            slot = if (expires[0] < expires[1]) 0 else 1
            current.stop(streams[slot])
        }
        val stream = current.play(sample, 0.16f, 0.16f, 1, 0, 1f)
        if (stream == 0) return
        streams[slot] = stream
        expires[slot] = now + sound.durationMs + 20
        lastPlayed[sound.ordinal] = now
        lastAny = now
        recent[recentIndex] = now
        recentIndex = (recentIndex + 1) % recent.size
    }

    internal fun load(files: List<File>): SoundPool? {
        release()
        if (!enabled) return null
        val current = SoundPool.Builder().setMaxStreams(2).setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        ).build()
        pool = current
        current.setOnLoadCompleteListener { source, sample, status ->
            if (source === pool && status == 0) loaded.add(sample)
        }
        try {
            files.forEachIndexed { i, file -> samples[i] = current.load(file.absolutePath, 1) }
        } catch (_: RuntimeException) {
            release(current)
            return null
        }
        return current
    }

    internal fun release(expected: SoundPool? = pool) {
        if (pool !== expected) return // A cancelled IO job must not release a newer session.
        pool?.setOnLoadCompleteListener(null)
        pool?.release()
        pool = null
        loaded.clear()
        samples.fill(0)
        streams.fill(0)
        expires.fill(0)
    }
}

@Composable
fun rememberUiSounds(enabled: Boolean): UiSounds {
    val context = LocalContext.current.applicationContext
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val sounds = remember { UiSounds() }
    SideEffect { sounds.setEnabled(enabled) }
    DisposableEffect(sounds, lifecycle) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP ||
                event == Lifecycle.Event.ON_DESTROY
            ) sounds.release()
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            sounds.release()
        }
    }
    LaunchedEffect(sounds, lifecycle, enabled) {
        if (!enabled) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            var session: SoundPool? = null
            try {
                // Tiny WAVs are synthesized/cached on IO, never in a frame or key callback.
                val files = withContext(Dispatchers.IO) {
                    runCatching { cachedUiSounds(context) }.getOrNull()
                }
                if (files != null) session = runCatching { sounds.load(files) }.getOrNull()
                awaitCancellation()
            } finally {
                sounds.release(session)
            }
        }
    }
    return sounds
}

private val uiSoundCacheLock = Any()

private fun cachedUiSounds(context: Context): List<File> = synchronized(uiSoundCacheLock) {
    UiSound.entries.map { sound ->
        // Versioned, bounded cache: six files, under 32 KB total, safe to regenerate if evicted.
        // Serialize tiny IO writes so a rapid mute/resume cannot expose a half-written WAV.
        val file = File(context.cacheDir, "ui-tone-v1-${sound.name}.wav")
        val bytes = synthesizeUiSound(sound)
        if (!file.isFile || file.length() != bytes.size.toLong()) file.writeBytes(bytes)
        file
    }
}

/** Mono 16-bit little-endian PCM at 22.05 kHz, with attack/release to avoid clicks. */
internal fun synthesizeUiSound(sound: UiSound): ByteArray {
    val rate = 22_050
    val count = rate * sound.durationMs / 1_000
    val dataSize = count * 2
    val wav = ByteBuffer.allocate(44 + dataSize).order(ByteOrder.LITTLE_ENDIAN)
    wav.put("RIFF".toByteArray(Charsets.US_ASCII)).putInt(36 + dataSize)
    wav.put("WAVEfmt ".toByteArray(Charsets.US_ASCII)).putInt(16)
    wav.putShort(1.toShort()).putShort(1.toShort()).putInt(rate).putInt(rate * 2)
    wav.putShort(2.toShort()).putShort(16.toShort())
    wav.put("data".toByteArray(Charsets.US_ASCII)).putInt(dataSize)
    var phase = 0.0
    repeat(count) { i ->
        val t = i.toDouble() / rate
        val p = i.toDouble() / (count - 1).coerceAtLeast(1)
        val frequency = when (sound) {
            UiSound.Move -> 440.0 - 80.0 * p
            UiSound.Confirm -> if (p < 0.45) 523.25 else 659.25
            UiSound.Back -> 420.0 - 170.0 * p
            UiSound.Score -> when { p < 0.33 -> 523.25; p < 0.66 -> 659.25; else -> 783.99 }
            UiSound.Collision -> 210.0 - 90.0 * p
            UiSound.GameOver -> 330.0 - 165.0 * p
        }
        phase += 2 * PI * frequency / rate
        val envelope = minOf(1.0, t / 0.006, (1 - p) / 0.22).coerceAtLeast(0.0)
        val tone = sin(phase) * 0.85 + sin(phase * 2) * 0.15
        wav.putShort((tone * envelope * 0.32 * Short.MAX_VALUE).toInt().toShort())
    }
    return wav.array()
}
