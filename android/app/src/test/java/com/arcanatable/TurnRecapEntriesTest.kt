package com.arcanatable

import org.junit.Assert.assertEquals
import org.junit.Test

class TurnRecapEntriesTest {
    @Test fun opponentTurnsKeepTheirNamesAndTurnOrder() {
        val entries = mergeTurnRecap(
            mergeTurnRecap(emptyList(), 4, 2, TurnRecap(plays = listOf("Baylen"))),
            3, 1, TurnRecap(plays = listOf("Aang")),
        )
        assertEquals("Aang's turn: played Aang.\nBaylen's turn: played Baylen.", formatTurnRecaps(entries, listOf("You", "Aang", "Baylen")))
    }
}
