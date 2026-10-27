package com.arcanatable

import org.junit.Assert.assertEquals
import org.junit.Test

class ReleaseFeaturesTest {
    @Test fun testCandidateAllowsPersistedThreeAiOpponents() {
        for (saved in listOf(-1, 0, 1, 2, 3, 100))
            assertEquals("Persisted opponent setting $saved must clamp to 1..3", saved.coerceIn(1, 3),
                ReleaseFeatures.aiCount(saved, debug = false))
    }

    @Test fun debugStillPermitsMultiplayerExperiments() {
        assertEquals(1, ReleaseFeatures.aiCount(0, debug = true))
        assertEquals(2, ReleaseFeatures.aiCount(2, debug = true))
        assertEquals(3, ReleaseFeatures.aiCount(4, debug = true))
    }

    @Test fun testCandidateOffersSavedImportedDecksWithoutModifyingThem() {
        val premade = DeckChoice("builtin:test", "Premade", null, true)
        val saved = DeckChoice("my-deck", "My saved deck", null, false)
        val stored = listOf(premade, saved)
        assertEquals(stored, ReleaseFeatures.availableDecks(stored, debug = false))
        assertEquals(listOf(premade, saved), stored)
        assertEquals(stored, ReleaseFeatures.availableDecks(stored, debug = true))
    }
}
