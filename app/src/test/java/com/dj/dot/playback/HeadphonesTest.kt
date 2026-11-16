package com.dj.dot.playback

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HeadphonesTest {
    private val buds = Headphones.Info("Buds", 90, "AA:BB:CC:DD:EE:01")

    @Test fun `battery from the earphones themselves is accepted`() {
        assertTrue(buds.isDevice("AA:BB:CC:DD:EE:01"))
    }

    @Test fun `battery from a watch or tracker is ignored`() {
        assertFalse(buds.isDevice("AA:BB:CC:DD:EE:02"))
    }

    @Test fun `a broadcast that doesn't say who sent it is ignored`() {
        assertFalse(buds.isDevice(null))
    }

    @Test fun `earphones without a known address accept nothing`() {
        assertFalse(Headphones.Info("Buds", 90).isDevice("AA:BB:CC:DD:EE:01"))
        assertFalse(Headphones.Info("Buds", 90).isDevice(null))
    }
}
