package com.arcanatable.customcards

import com.arcanatable.customcards.cards.OblivionSower
import com.arcanatable.customcards.support.*
import com.wingedsheep.engine.core.ChooseTargetsDecision
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.EntityId
import org.junit.Assert.*
import org.junit.Test

class OblivionSowerIndependentCertificationTest {
    private fun GameTestDriver.castSower(): EntityId {
        val sower = putCardInHand(player1, "Oblivion Sower")
        giveColorlessMana(player1, 6)
        assertNull(castSpell(player1, sower).error)
        return sower
    }

    private fun GameTestDriver.putOnTopInOrder(player: EntityId, cards: List<String>) {
        cards.asReversed().forEach { putCardOnTopOfLibrary(player, it) }
    }

    @Test
    fun castingRequiresSixGenericManaAndEntersAsAFiveEightCreature() {
        val d = customScenario(OblivionSower)
        val player = d.player1
        val sower = d.putCardInHand(player, "Oblivion Sower")
        d.giveColorlessMana(player, 5)

        assertNotNull(d.castSpell(player, sower).error)
        assertTrue(d.getHand(player).contains(sower))
        assertTrue(d.permanentsNamed(player, "Oblivion Sower").isEmpty())

        d.giveColorlessMana(player, 1)
        assertNull(d.castSpell(player, sower).error)
        d.resolveAll(Choices(targets = listOf(d.player2), pick = emptyList()))

        val permanent = d.permanentsNamed(player, "Oblivion Sower").single()
        assertEquals(5, d.projected.getPower(permanent))
        assertEquals(8, d.projected.getToughness(permanent))
    }

    @Test
    fun castingTargetsOnlyAnOpponentAndExilesExactlyTheTopFourCards() {
        val d = customScenario(OblivionSower, "Forest", "Island", "Mountain", "Swamp", "Grizzly Bears")
        val player = d.player1
        val opponent = d.player2
        d.putOnTopInOrder(
            opponent,
            listOf("Forest", "Grizzly Bears", "Island", "Mountain", "Swamp"),
        )
        d.castSower()

        assertTrue(d.zoneNames(opponent, Zone.EXILE).isEmpty())
        // Cast triggers can reach their target prompt only after priority advances.
        repeat(30) {
            if (d.state.pendingDecision == null && d.state.stack.isNotEmpty()) {
                assertNull(d.bothPass().error)
            }
        }
        val decision = d.state.pendingDecision as? ChooseTargetsDecision
        assertNotNull("the cast trigger must choose its target before resolving", decision)
        val legalTargets = decision!!.legalTargets.values.flatten()
        assertFalse(legalTargets.contains(player))
        assertTrue(legalTargets.contains(opponent))
        assertNotNull(d.submitTargetSelection(player, listOf(player)).error)
        assertNull(d.submitTargetSelection(player, listOf(opponent)).error)
        d.resolveAll(Choices(pick = emptyList()))

        assertEquals(
            setOf("Forest", "Grizzly Bears", "Island", "Mountain"),
            d.zoneNames(opponent, Zone.EXILE).toSet(),
        )
        assertEquals(4, d.zoneNames(opponent, Zone.EXILE).size)
        assertEquals("Swamp", d.zoneNames(opponent, Zone.LIBRARY).first())
    }

    @Test
    fun mayPutAnyChosenNumberOfTargetOpponentsLandsOntoYourBattlefield() {
        val d = customScenario(OblivionSower, "Forest", "Island", "Mountain", "Swamp", "Grizzly Bears")
        val player = d.player1
        val opponent = d.player2
        d.putOnTopInOrder(
            opponent,
            listOf("Forest", "Island", "Mountain", "Grizzly Bears", "Swamp"),
        )
        d.castSower()
        d.resolveAll(Choices(targets = listOf(opponent), pick = listOf("Forest", "Mountain")))

        assertEquals(1, d.permanentsNamed(player, "Forest").size)
        assertEquals(1, d.permanentsNamed(player, "Mountain").size)
        assertTrue(d.permanentsNamed(opponent, "Forest").isEmpty())
        assertTrue(d.permanentsNamed(opponent, "Mountain").isEmpty())
        assertTrue(d.zoneNames(opponent, Zone.EXILE).contains("Island"))
        assertTrue(d.zoneNames(opponent, Zone.EXILE).contains("Grizzly Bears"))
        assertFalse(d.zoneNames(opponent, Zone.EXILE).contains("Forest"))
        assertFalse(d.zoneNames(opponent, Zone.EXILE).contains("Mountain"))
    }

    @Test
    fun nonlandsAndLandsOwnedByAnotherPlayerCannotBeTaken() {
        val d = customScenario(OblivionSower, "Forest", "Grizzly Bears")
        val player = d.player1
        val opponent = d.player2
        d.putCardInExile(player, "Forest")
        d.putOnTopInOrder(opponent, List(4) { "Grizzly Bears" })
        d.castSower()
        d.resolveAll(Choices(targets = listOf(opponent), pick = listOf("Forest", "Grizzly Bears")))

        assertEquals(listOf("Forest"), d.zoneNames(player, Zone.EXILE))
        assertEquals(4, d.zoneNames(opponent, Zone.EXILE).count { it == "Grizzly Bears" })
        assertTrue(d.permanentsNamed(player, "Forest").isEmpty())
        assertTrue(d.permanentsNamed(player, "Grizzly Bears").isEmpty())
    }

    @Test
    fun puttingSowerOntoTheBattlefieldWithoutCastingDoesNotTriggerIt() {
        val d = customScenario(OblivionSower)
        val opponent = d.player2
        val libraryBefore = d.zoneNames(opponent, Zone.LIBRARY)

        d.putCreatureOnBattlefield(d.player1, "Oblivion Sower")

        assertEquals(libraryBefore, d.zoneNames(opponent, Zone.LIBRARY))
        assertTrue(d.zoneNames(opponent, Zone.EXILE).isEmpty())
    }
}
