package com.arcanatable

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * Durable release-variant gate for the first Build 134 release.
 *
 * Unlike a debug unit test passing debug=false explicitly, these assertions
 * exercise the actual release BuildConfig and default feature accessors.
 * This must run under :app:testReleaseUnitTest, never testDebugUnitTest.
 */
class ReleaseVariantScopeTest {
    @Test fun releaseVariantDisablesDeckImportAndMultiplayerPreview() {
        assertFalse("Release tests must use release BuildConfig", BuildConfig.DEBUG)
        assertFalse("No user deck import in first public release", ReleaseFeatures.deckImportEnabled)
        assertFalse("No four-seat multiplayer preview in first public release", ReleaseFeatures.multiplayerPreviewEnabled)
    }

    @Test fun releaseVariantClampsAllPersistedOpponentCounts() {
        for (saved in listOf(-5, 0, 1, 2, 3, 4, 99)) {
            assertEquals("saved opponents=$saved", 1, ReleaseFeatures.aiCount(saved))
        }
    }

    @Test fun releaseVariantShowsOnlyPremadeDecksWithoutDeletingSavedDecks() {
        val builtIn = DeckChoice("builtin:release-test", "Premade", null, true)
        val imported = DeckChoice("saved:release-test", "Saved import", null, false)
        val all = listOf(imported, builtIn)
        assertEquals(listOf(builtIn), ReleaseFeatures.availableDecks(all))
        assertEquals("Filtering must not mutate saved deck data", listOf(imported, builtIn), all)
    }
}
