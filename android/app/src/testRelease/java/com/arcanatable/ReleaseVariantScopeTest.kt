package com.arcanatable

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Release-variant checks for owner-approved Build 134 scope.
 * Run under :app:testReleaseUnitTest to exercise actual release BuildConfig.
 */
class ReleaseVariantScopeTest {
    @Test fun releaseVariantEnablesDeckImportAndMultiplayer() {
        assertFalse("Release tests must use release BuildConfig", BuildConfig.DEBUG)
        assertTrue("Build 134 enables deck import", ReleaseFeatures.deckImportEnabled)
        assertTrue("Build 134 enables four-seat multiplayer", ReleaseFeatures.multiplayerPreviewEnabled)
    }

    @Test fun releaseVariantBoundsPersistedAiOpponentCountsToThree() {
        for ((saved, expected) in listOf(-5 to 1, 0 to 1, 1 to 1, 2 to 2, 3 to 3, 4 to 3, 99 to 3)) {
            assertEquals("saved opponents=$saved", expected, ReleaseFeatures.aiCount(saved))
        }
    }

    @Test fun releaseVariantShowsImportedAndPremadeDecksWithoutMutatingSavedData() {
        val builtIn = DeckChoice("builtin:release-test", "Premade", null, true)
        val imported = DeckChoice("saved:release-test", "Saved import", null, false)
        val all = listOf(imported, builtIn)
        assertEquals(all, ReleaseFeatures.availableDecks(all))
        assertEquals("Deck listing must not mutate saved data", listOf(imported, builtIn), all)
    }
}
