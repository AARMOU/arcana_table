package com.arcanatable

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EngineProcessingInfoTest {
    @Test fun onlyRealPendingProcessingShowsTime() {
        assertNull(processingInfo(isOffline = true, awaitingAction = false,
            nonInteractiveEngineUpdate = false, elapsedMs = 9200, lastCompletedMs = 1000))
        val result = processingInfo(true, true, false, 12500, 342)!!
        assertEquals("Argentum is resolving your action", result.label)
        assertEquals(12, result.elapsedSeconds)
        assertEquals(342, result.lastCompletedMs)
    }
    @Test fun backgroundEngineUpdateAndNetworkWaitAreDifferent() {
        assertEquals("Argentum is advancing the game", processingInfo(true, false, true, 500, null)!!.label)
        assertEquals("Waiting for Argentum to confirm your action", processingInfo(false, true, false, 1000, null)!!.label)
    }
    @Test fun elapsedTimeNeverBecomesAFalsePercentageOrEta() {
        assertEquals(0, processingInfo(true, true, false, -6000, null)!!.elapsedSeconds)
        assertEquals(3600, processingInfo(true, true, false, 3_600_000, null)!!.elapsedSeconds)
    }
}
