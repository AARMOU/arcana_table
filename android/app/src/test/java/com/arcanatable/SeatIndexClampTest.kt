package com.arcanatable

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Build 107 crash: after a 4-player offline game, choosing a deck swapped the player list back to
 * the 2-seat table while the board/turn index still pointed at seat 3, and TableMainLayout read
 * players[2] from a 2-entry list. Seat indexes must always stay inside the current list.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SeatIndexClampTest {
    private fun controller() = TableController(ApplicationProvider.getApplicationContext(), CoroutineScope(Dispatchers.Unconfined), CardZoomController())

    @Test fun shrinkingThePlayerListPullsSeatIndexesBackInside() {
        val t = controller()
        t.players = List(4) { TablePlayer("P${it + 1}") }
        t.selectedPlayer = 2
        t.activePlayer = 3
        t.localSeat = 2
        t.players = List(2) { TablePlayer("P${it + 1}") }
        assertEquals(1, t.selectedPlayer)
        assertEquals(1, t.activePlayer)
        assertEquals(1, t.localSeat)
        // What TableMainLayout does on every frame must not throw.
        t.players[t.selectedPlayer].hand.size
        t.players[t.activePlayer].isComputer
    }

    @Test fun pickingASeatThatDoesNotExistIsClamped() {
        val t = controller()
        t.players = List(2) { TablePlayer("P${it + 1}") }
        t.selectedPlayer = 2
        t.activePlayer = -1
        assertEquals(1, t.selectedPlayer)
        assertEquals(0, t.activePlayer)
    }

    @Test fun inRangeSeatsAreUntouched() {
        val t = controller()
        t.players = List(4) { TablePlayer("P${it + 1}") }
        t.selectedPlayer = 3
        t.activePlayer = 2
        t.players = List(4) { TablePlayer("Q${it + 1}") }
        assertEquals(3, t.selectedPlayer)
        assertEquals(2, t.activePlayer)
    }
}
