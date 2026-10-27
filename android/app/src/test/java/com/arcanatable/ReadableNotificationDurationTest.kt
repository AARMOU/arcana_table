package com.arcanatable

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadableNotificationDurationTest {
    @Test fun playedCardDurationFollowsTheSliderEvenForLongRulesText() {
        // The played-card popup must not inherit the shared 5-second minimum or
        // length-based extension used by informational notices.
        assertEquals(1000L, playedCardPopupDurationMs(1f))
        assertEquals(2500L, playedCardPopupDurationMs(2.5f))
        assertEquals(4000L, playedCardPopupDurationMs(4f))
        assertEquals(5000L, playedCardPopupDurationMs(5f))
        assertEquals(8000L, playedCardPopupDurationMs(8f))
    }

    @Test fun playedCardDurationBoundsAndNoticeReadabilityRemainIndependent() {
        assertEquals(1000L, playedCardPopupDurationMs(0.25f))
        assertEquals(8000L, playedCardPopupDurationMs(30f))
        assertEquals(5000L, readableNotificationDurationMs("Short trigger", 1f))
        assertTrue(readableNotificationDurationMs(List(60) { "trigger" }.joinToString(" "), 3f) > 8000L)
    }

    @Test fun aShortDefaultNoticeStaysLongEnoughToRead() {
        assertEquals(5000L, readableNotificationDurationMs("Your turn.", 1.5f))
    }

    @Test fun longNoticesStayUpLongerThanShortOnes() {
        val short = readableNotificationDurationMs("One ability resolved", 4f)
        val long = readableNotificationDurationMs(List(65) { "trigger" }.joinToString(" "), 4f)
        assertTrue("a 65-word notification needs more reading time", long > short)
    }

    @Test fun chosenLongerDurationAndHardMaximumAreRespected() {
        assertEquals(20000L, readableNotificationDurationMs("Card played", 20f))
        assertEquals(30000L, readableNotificationDurationMs(List(200) { "trigger" }.joinToString(" "), 4f))
    }
}
