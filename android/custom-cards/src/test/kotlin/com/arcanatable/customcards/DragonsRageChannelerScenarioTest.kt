package com.arcanatable.customcards

import com.arcanatable.customcards.cards.DragonsRageChanneler
import com.arcanatable.customcards.support.*
import com.wingedsheep.sdk.core.*
import org.junit.Assert.*
import org.junit.Test

class DragonsRageChannelerScenarioTest {
    @Test
    fun castingANoncreatureSpellSurveilsTheTopCardIntoTheGraveyard() {
        val d = customScenario(DragonsRageChanneler, "Lightning Bolt", "Mountain")
        val p = d.player1
        d.putCreatureOnBattlefield(p, "Dragon's Rage Channeler")
        val bolt = d.putCardInHand(p, "Lightning Bolt")
        d.putCardOnTopOfLibrary(p, "Mountain")
        d.giveMana(p, Color.RED)

        assertNull(d.castSpell(p, bolt, listOf(d.player2)).error)
        d.resolveAll(Choices(pick = listOf("Mountain")))

        d.assertInGraveyard(p, "Mountain")
    }

    @Test
    fun decliningToPutTheSurveilledCardInTheGraveyardKeepsItOnTop() {
        val d = customScenario(DragonsRageChanneler, "Lightning Bolt", "Mountain")
        val p = d.player1
        d.putCreatureOnBattlefield(p, "Dragon's Rage Channeler")
        val bolt = d.putCardInHand(p, "Lightning Bolt")
        d.putCardOnTopOfLibrary(p, "Mountain")
        d.giveMana(p, Color.RED)

        assertNull(d.castSpell(p, bolt, listOf(d.player2)).error)
        d.resolveAll(Choices(pick = emptyList()))

        assertEquals("Mountain", d.zoneNames(p, Zone.LIBRARY).first())
        assertFalse(d.getGraveyardCardNames(p).contains("Mountain"))
    }

    @Test
    fun castingACreatureSpellDoesNotTriggerSurveil() {
        val d = customScenario(DragonsRageChanneler, "Grizzly Bears", "Mountain")
        val p = d.player1
        d.putCreatureOnBattlefield(p, "Dragon's Rage Channeler")
        val bears = d.putCardInHand(p, "Grizzly Bears")
        d.putCardOnTopOfLibrary(p, "Mountain")
        d.giveMana(p, Color.GREEN, 2)

        assertNull(d.castSpell(p, bears).error)
        d.resolveAll(Choices(pick = listOf("Mountain")))

        assertEquals("Mountain", d.zoneNames(p, Zone.LIBRARY).first())
        assertFalse(d.getGraveyardCardNames(p).contains("Mountain"))
    }

    @Test
    fun deliriumNeedsFourDistinctCardTypes() {
        val d = customScenario(DragonsRageChanneler, "Forest", "Grizzly Bears", "Lightning Bolt")
        val p = d.player1
        d.putCardInGraveyard(p, "Forest")
        repeat(2) { d.putCardInGraveyard(p, "Grizzly Bears") }
        d.putCardInGraveyard(p, "Lightning Bolt")
        val channeler = d.putCreatureOnBattlefield(p, "Dragon's Rage Channeler")
        d.removeSummoningSickness(channeler)

        assertEquals(1, d.projected.getPower(channeler))
        assertEquals(1, d.projected.getToughness(channeler))
        assertFalse(d.projected.hasKeyword(channeler, Keyword.FLYING))

        d.passPriorityUntil(Step.DECLARE_ATTACKERS, p)
        assertNull(d.declareAttackers(p, emptyList(), d.player2).error)
    }

    @Test
    fun deliriumGrantsStatsFlyingAndRequiresAttacking() {
        val d = customScenario(
            DragonsRageChanneler,
            "Forest",
            "Grizzly Bears",
            "Lightning Bolt",
            "Divination",
        )
        val p = d.player1
        d.putCardInGraveyard(p, "Forest")
        d.putCardInGraveyard(p, "Grizzly Bears")
        d.putCardInGraveyard(p, "Lightning Bolt")
        d.putCardInGraveyard(p, "Divination")
        val channeler = d.putCreatureOnBattlefield(p, "Dragon's Rage Channeler")
        d.removeSummoningSickness(channeler)

        assertEquals(3, d.projected.getPower(channeler))
        assertEquals(3, d.projected.getToughness(channeler))
        assertTrue(d.projected.hasKeyword(channeler, Keyword.FLYING))

        d.passPriorityUntil(Step.DECLARE_ATTACKERS, p)
        assertNotNull(d.declareAttackers(p, emptyList(), d.player2).error)
        assertNull(d.declareAttackers(p, listOf(channeler), d.player2).error)
    }
}
