package com.retrobit.app.ui.minigames

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

internal typealias GameSound = com.retrobit.app.ui.components.UiSound

/** One bounded 30 Hz clock per active game; no catch-up after backgrounding or IO stalls. */
@Composable
internal fun GameFrames(running: Boolean, frame: (Float) -> Unit) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val currentFrame by rememberUpdatedState(frame)
    LaunchedEffect(lifecycle, running) {
        if (!running) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            var last = withFrameNanos { it }
            while (true) {
                val now = withFrameNanos { it }
                if (now - last >= 30_000_000L) {
                    val dt = ((now - last) / 1_000_000_000f).coerceIn(0f, 0.05f)
                    last = now
                    currentFrame(dt)
                }
            }
        }
    }
}

internal fun DrawScope.flapTransform(
    cx: Float, cy: Float, tilt: Float, squash: Float, bird: DrawScope.() -> Unit
) {
    withTransform({
        rotate(tilt, Offset(cx, cy))
        scale(squash, 1f / squash, Offset(cx, cy))
    }) { bird() }
}

/** Fixed storage: 32 particles and 10 trail samples. Clock is read only by Canvas. */
internal class MiniEffects {
    private val x = FloatArray(32)
    private val y = FloatArray(32)
    private val vx = FloatArray(32)
    private val vy = FloatArray(32)
    private val age = FloatArray(32) { 1f }
    private val colors = Array(32) { Color.White }
    private var next = 0
    private val tx = FloatArray(10)
    private val ty = FloatArray(10)
    private val ta = FloatArray(10) { 1f }
    private var trailNext = 0
    private var trailWait = 0f
    var time by mutableFloatStateOf(0f)
        private set
    var active by mutableStateOf(false)
        private set

    fun clear() {
        age.fill(1f)
        ta.fill(1f)
        active = false
        time = 0f
        trailWait = 0f
    }

    fun burst(cx: Float, cy: Float, color: Color, count: Int = 8) {
        repeat(count.coerceIn(1, 12)) { i ->
            val angle = i * 2 * PI / count
            x[next] = cx
            y[next] = cy
            vx[next] = cos(angle).toFloat() * 0.13f
            vy[next] = sin(angle).toFloat() * 0.20f
            age[next] = 0f
            colors[next] = color
            next = (next + 1) % x.size
        }
        active = true
    }

    fun trail(cx: Float, cy: Float) {
        if (trailWait > 0f) return
        tx[trailNext] = cx
        ty[trailNext] = cy
        ta[trailNext] = 0f
        trailNext = (trailNext + 1) % tx.size
        trailWait = 0.03f
        active = true
    }

    fun step(dt: Float, driftX: Float = 0f) {
        time = (time + dt) % 60f
        trailWait -= dt
        var live = false
        for (i in age.indices) {
            if (age[i] >= 0.34f) continue
            age[i] += dt
            x[i] += vx[i] * dt
            y[i] += vy[i] * dt
            vy[i] += dt * 0.12f
            live = live || age[i] < 0.34f
        }
        for (i in ta.indices) {
            if (ta[i] >= 0.24f) continue
            ta[i] += dt
            tx[i] -= driftX * dt
            live = live || ta[i] < 0.24f
        }
        active = live
    }

    fun draw(scope: DrawScope, trailColor: Color = Color.White, trailRadius: Float = 0.012f) {
        with(scope) {
            // Reading time invalidates drawing without recomposing the entire scaffold.
            @Suppress("UNUSED_VARIABLE") val clock = time
            for (i in ta.indices) {
                val fade = (1f - ta[i] / 0.24f).coerceIn(0f, 1f)
                if (fade == 0f) continue
                drawCircle(trailColor.copy(alpha = fade * 0.24f),
                    size.width * trailRadius * (0.4f + 0.6f * fade),
                    Offset(tx[i] * size.width, ty[i] * size.height))
            }
            for (i in age.indices) {
                val fade = (1f - age[i] / 0.34f).coerceIn(0f, 1f)
                if (fade == 0f) continue
                drawCircle(colors[i].copy(alpha = fade * 0.85f),
                    size.minDimension * 0.007f * (0.5f + fade * 0.5f),
                    Offset(x[i] * size.width, y[i] * size.height))
            }
        }
    }
}
