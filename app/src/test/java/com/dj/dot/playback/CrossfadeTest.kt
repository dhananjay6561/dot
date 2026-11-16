package com.dj.dot.playback

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CrossfadeTest {
    private val clock = { System.nanoTime() / 1_000_000 }

    @Test fun `fade reaches full progress while playing`() = runBlocking {
        var progress = 0f
        withTimeout(2_000) { runCrossfade(100, MutableStateFlow(true), clock) { progress = it } }
        assertEquals(1f, progress)
    }

    @Test fun `paused fade sleeps instead of ticking`() = runBlocking {
        val playing = MutableStateFlow(false)
        var steps = 0
        var progress = 0f
        val fade = launch { runCrossfade(100, playing, clock) { steps++; progress = it } }
        // Paused for longer than the whole fade: nothing should run and nothing should be counted.
        delay(300)
        assertEquals(0, steps)
        assertEquals(0f, progress)

        playing.value = true
        withTimeout(2_000) { fade.join() }
        assertEquals(1f, progress)
        assertTrue("a 100 ms fade should take a handful of frames, took $steps", steps in 3..20)
    }

    @Test fun `pausing mid-fade holds progress until resumed`() = runBlocking {
        val playing = MutableStateFlow(true)
        var progress = 0f
        val fade = launch { runCrossfade(400, playing, clock) { progress = it } }
        delay(120)
        playing.value = false
        delay(40)
        val held = progress
        assertTrue("fade should be part way, was $held", held > 0f && held < 1f)
        delay(300)
        assertEquals(held, progress)

        playing.value = true
        withTimeout(2_000) { fade.join() }
        assertEquals(1f, progress)
    }
}
