package com.arcanatable

import com.arcanatable.engine.OfflineCardLibrary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * Release variant only (src/testRelease, run by testReleaseUnitTest): the test scenarios are
 * compiled from src/release, an empty stub, so release builds have no scenario boards and the
 * home screen's test-scenario card has nothing to show.
 */
class ReleaseHasNoTestScenariosTest {
    @Test fun releaseKeepsUnapprovedFeaturesDisabled() {
        assertFalse(ReleaseFeatures.deckImportEnabled)
        assertFalse(ReleaseFeatures.multiplayerPreviewEnabled)
        assertFalse(DeckBuilderFlag.enabled)
        assertEquals(1, ReleaseFeatures.aiCount(3))
        assertEquals(emptyList<DeckChoice>(), ReleaseFeatures.availableDecks(
            listOf(DeckChoice("saved", "An imported deck", null, false))))
    }

    @Test fun releaseHasNoScenarios() {
        assertEquals(emptyList<TestScenario>(), TestScenarios.available)
    }

    @Test fun releaseCannotBuildAScenario() {
        val any = TestScenario("iron-spider-mixed-counters", "Iron Spider — mixed counters", "")
        assertThrows(IllegalStateException::class.java) {
            TestScenarios.build(any, OfflineCardLibrary({ null }))
        }
    }
}
