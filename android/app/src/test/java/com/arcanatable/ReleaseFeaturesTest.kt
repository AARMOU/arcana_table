package com.arcanatable

import org.junit.Assert.assertEquals
import org.junit.Test

class ReleaseFeaturesTest {
    @Test fun productionClampsPersistedMultiplayerSettingsToOneOpponent() {
        for (saved in listOf(-1, 0, 1, 2, 3, 100))
            assertEquals("Persisted setting $saved must still launch a two-player game", 1,
                ReleaseFeatures.aiCount(saved, debug = false))
    }

    @Test fun debugStillPermitsMultiplayerExperiments() {
        assertEquals(1, ReleaseFeatures.aiCount(0, debug = true))
        assertEquals(2, ReleaseFeatures.aiCount(2, debug = true))
        assertEquals(3, ReleaseFeatures.aiCount(4, debug = true))
    }

    @Test fun releaseHidesSavedImportedDecksWithoutModifyingThem() {
        val premade = DeckChoice("builtin:test", "Premade", null, true)
        val saved = DeckChoice("my-deck", "My saved deck", null, false)
        val stored = listOf(premade, saved)
        assertEquals(listOf(premade), ReleaseFeatures.availableDecks(stored, debug = false))
        assertEquals(listOf(premade, saved), stored)
        assertEquals(stored, ReleaseFeatures.availableDecks(stored, debug = true))
    }
}
