package com.arcanatable

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadableNotificationDurationTest {
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
