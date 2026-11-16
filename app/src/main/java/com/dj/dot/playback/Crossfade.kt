package com.dj.dot.playback

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first

private const val FRAME_MS = 16L

/**
 * Drives a crossfade over [durationMs] of playing time, calling [step] with progress 0..1 about
 * once a frame. Paused time doesn't count: while [playing] is false the fade holds where it is and
 * the coroutine sleeps until playback resumes, instead of waking every frame.
 */
internal suspend fun runCrossfade(
    durationMs: Long,
    playing: StateFlow<Boolean>,
    clock: () -> Long,
    step: (Float) -> Unit,
) {
    var elapsed = 0L
    var last = clock()
    while (elapsed < durationMs) {
        if (!playing.value) {
            playing.first { it }
            last = clock()
        }
        delay(FRAME_MS)
        val now = clock()
        if (playing.value) elapsed += now - last
        last = now
        step((elapsed.toFloat() / durationMs).coerceIn(0f, 1f))
    }
}
