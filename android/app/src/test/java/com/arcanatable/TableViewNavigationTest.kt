package com.arcanatable

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The overview never navigates away just because the active player changes. */
class TableViewNavigationTest {
    @Test fun tableViewDoesNotAutoFollowEvenWhenPreferenceEnabled() {
        for (active in 0..3) {
            assertFalse(shouldFollowActivePlayerToBoard(followEnabled = true, showingTable = true, active = active, playerCount = 4))
        }
    }

    @Test fun playerViewStillFollowsWhenEnabled() {
        for (active in 0..3) {
            assertTrue(shouldFollowActivePlayerToBoard(followEnabled = true, showingTable = false, active = active, playerCount = 4))
        }
    }

    @Test fun disabledPreferenceAndOutOfRangePlayersDoNotAutoNavigate() {
        assertFalse(shouldFollowActivePlayerToBoard(followEnabled = false, showingTable = false, active = 1, playerCount = 4))
        assertFalse(shouldFollowActivePlayerToBoard(followEnabled = true, showingTable = false, active = -1, playerCount = 4))
        assertFalse(shouldFollowActivePlayerToBoard(followEnabled = true, showingTable = false, active = 4, playerCount = 4))
        assertFalse(shouldFollowActivePlayerToBoard(followEnabled = true, showingTable = false, active = 0, playerCount = 0))
    }
}
