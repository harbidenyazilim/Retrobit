package com.retrobit.app.ui.components

import java.util.concurrent.atomic.AtomicBoolean

/**
 * Holding confirm on a card navigates while the key is still down; the matching key-up would
 * then activate the first control of the new screen (for example the back arrow). Drop it.
 */
object GamepadKeyGuard {
    private val pendingCenterUp = AtomicBoolean(false)

    fun armCenterUp() {
        pendingCenterUp.set(true)
    }

    fun consumeCenterUp(): Boolean = pendingCenterUp.compareAndSet(true, false)
}
