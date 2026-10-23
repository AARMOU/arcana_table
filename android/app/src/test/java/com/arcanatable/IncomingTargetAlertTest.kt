package com.arcanatable

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IncomingTargetAlertTest {
    private fun stack(id: String, yours: Boolean = false, targets: List<String>) =
        ArgentumStackItem(id, "Removal", "Opponent", yours, null, emptyList(), targets)

    @Test fun alertsOnlyOnOpponentTargetsWithServerIds() {
        val result = incomingTargetAlerts(
            stack = listOf(stack("one", targets = listOf("our-card")), stack("two", targets = listOf("other-card"))),
            ownPlayerId = "local",
            ownCardIds = setOf("our-card"),
            ownCardNames = mapOf("our-card" to "My Creature")
        )
        assertEquals(1, result.size)
        assertEquals("one", result.single().stackId)
        assertEquals(listOf("YOUR My Creature"), result.single().targets)
    }

    @Test fun ownSpellsDoNotWarnAndNamesCannotTriggerFalseAlerts() {
        assertTrue(incomingTargetAlerts(
            listOf(stack("ours", yours = true, targets = listOf("local")),
                stack("bad", targets = listOf("enemy"))),
            "local", emptySet(), emptyMap()
        ).isEmpty())
    }

    @Test fun targetingLocalPlayerAndCardDeduplicatesExactId() {
        val result = incomingTargetAlerts(
            listOf(stack("s", targets = listOf("local", "ours", "ours"))),
            "local", setOf("ours"), mapOf("ours" to "Treasure")
        )
        assertEquals(listOf("YOU", "YOUR Treasure"), result.single().targets)
    }

    @Test fun noStackOrNoPlayerMeansNoStaleAlert() {
        assertTrue(incomingTargetAlerts(emptyList(), "local", setOf("ours"), emptyMap()).isEmpty())
        assertTrue(incomingTargetAlerts(listOf(stack("s", targets = listOf("ours"))), "", setOf("ours"), emptyMap()).isEmpty())
    }
}
