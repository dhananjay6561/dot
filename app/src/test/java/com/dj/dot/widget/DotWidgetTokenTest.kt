package com.dj.dot.widget

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DotWidgetTokenTest {
    @Test fun `widget commands require the complete installed token`() {
        val token = "e72b1c00-8f71-4ebf-b650-17c2ace63a14"
        assertTrue(DotWidget.tokensMatch(token, token))
        assertFalse(DotWidget.tokensMatch(token, null))
        assertFalse(DotWidget.tokensMatch(null, token))
        assertFalse(DotWidget.tokensMatch(token, ""))
        assertFalse(DotWidget.tokensMatch(token, token.dropLast(1)))
        assertFalse(DotWidget.tokensMatch(token, token + "0"))
        assertFalse(DotWidget.tokensMatch(token, "b" + token.drop(1)))
    }
}
