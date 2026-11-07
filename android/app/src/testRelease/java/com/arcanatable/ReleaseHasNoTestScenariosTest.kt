package com.arcanatable

import com.arcanatable.engine.OfflineCardLibrary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * Release variant only (src/testRelease, run by testReleaseUnitTest): the test scenarios are
 * compiled from src/release, an empty stub, so release builds have no scenario boards and the
 * home screen's test-scenario card has nothing to show.
 */
class ReleaseHasNoTestScenariosTest {
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
