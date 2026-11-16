package com.retrobit.app.ui.minigames

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MiniGameStateTest {
    @Test
    fun rapidTurnsCannotReverseIntoSnakesNeck() {
        val snake = SnakeState().turn(-24).turn(-1)
        assertEquals(-24, snake.pending)
        assertTrue(snake.tick().alive)
        assertEquals(51, snake.tick().body.first())
    }

    @Test
    fun snakeCanTurnAfterThePreviousTurnHasExecuted() {
        val snake = SnakeState().turn(-24).tick().turn(-1).tick()
        assertTrue(snake.alive)
        assertEquals(50, snake.body.first())
    }

    @Test
    fun snakeRejectsDirectReversal() {
        assertEquals(1, SnakeState().turn(-1).pending)
    }

    @Test
    fun flappyUsesVisibleVerticalRadiusAtScreenBoundary() {
        assertTrue(FlappyState(birdY = 0.04f, started = true).step(0f).over)
        assertFalse(FlappyState(birdY = 0.06f, started = true).step(0f).over)
        assertTrue(FlappyState(birdY = 0.96f, started = true).step(0f).over)
    }

    @Test
    fun flappyUsesVisibleVerticalRadiusAtPipeGap() {
        val pipes = listOf(Pipe(x = 0.25f, gapY = 0.5f))
        assertTrue(FlappyState(birdY = 0.39f, pipes = pipes, started = true).step(0f).over)
        assertFalse(FlappyState(birdY = 0.5f, pipes = pipes, started = true).step(0f).over)
    }
}
