package com.arcanatable.customcards

import com.arcanatable.customcards.cards.DragonsRageChanneler
import com.arcanatable.customcards.support.*
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DragonsRageChannelerScenarioTest {
    @Test
    fun castingANoncreatureSpellSurveilsOne() {
        val d = customScenario(DragonsRageChanneler, "Sol Ring", "Mountain")
        val p = d.player1
        d.putCreatureOnBattlefield(p, "Dragon's Rage Channeler")
        d.putCardOnTopOfLibrary(p, "Mountain")
        val solRing = d.putCardInHand(p, "Sol Ring")
        d.giveColorlessMana(p, 1)

        assertNull(d.castSpell(p, solRing).error)
        d.resolveAll(Choices(pick = listOf("Mountain")))

        assertTrue(d.zoneNames(p, Zone.GRAVEYARD).contains("Mountain"))
    }

    @Test
    fun surveilMayKeepTheTopCardOnTheLibrary() {
        val d = customScenario(DragonsRageChanneler, "Sol Ring", "Mountain")
        val p = d.player1
        d.putCreatureOnBattlefield(p, "Dragon's Rage Channeler")
        d.putCardOnTopOfLibrary(p, "Mountain")
        val solRing = d.putCardInHand(p, "Sol Ring")
        d.giveColorlessMana(p, 1)

        assertNull(d.castSpell(p, solRing).error)
        d.resolveAll(Choices())

        assertTrue(d.zoneNames(p, Zone.LIBRARY).contains("Mountain"))
        assertFalse(d.zoneNames(p, Zone.GRAVEYARD).contains("Mountain"))
    }

    @Test
    fun castingACreatureSpellDoesNotTriggerSurveil() {
        val d = customScenario(DragonsRageChanneler, "Grizzly Bears", "Mountain")
        val p = d.player1
        d.putCreatureOnBattlefield(p, "Dragon's Rage Channeler")
        d.putCardOnTopOfLibrary(p, "Mountain")
        val bears = d.putCardInHand(p, "Grizzly Bears")
        d.giveMana(p, Color.GREEN)
        d.giveColorlessMana(p, 1)

        assertNull(d.castSpell(p, bears).error)
        d.resolveAll()

        assertTrue(d.zoneNames(p, Zone.LIBRARY).contains("Mountain"))
        assertFalse(d.zoneNames(p, Zone.GRAVEYARD).contains("Mountain"))
    }

    @Test
    fun fourDistinctGraveyardCardTypesGrantStatsFlyingAndRequireAttacking() {
        val d = customScenario(DragonsRageChanneler, "Plains", "Grizzly Bears", "Sol Ring", "Dark Ritual")
        val p = d.player1
        listOf("Plains", "Grizzly Bears", "Sol Ring", "Dark Ritual").forEach {
            d.putCardInGraveyard(p, it)
        }
        val channeler = d.putCreatureOnBattlefield(p, "Dragon's Rage Channeler")

        assertEquals(3, d.projected.getPower(channeler))
        assertEquals(3, d.projected.getToughness(channeler))
        assertTrue(d.projected.hasKeyword(channeler, Keyword.FLYING))

        d.removeSummoningSickness(channeler)
        d.passPriorityUntil(Step.DECLARE_ATTACKERS)
        assertNotNull(d.declareAttackers(p, emptyList(), d.player2).error)
        assertNull(d.declareAttackers(p, listOf(channeler), d.player2).error)
    }

    @Test
    fun fourCardsOfOneTypeDoNotEnableDeliriumOrForceAnAttack() {
        val d = customScenario(DragonsRageChanneler, "Grizzly Bears")
        val p = d.player1
        repeat(4) { d.putCardInGraveyard(p, "Grizzly Bears") }
        val channeler = d.putCreatureOnBattlefield(p, "Dragon's Rage Channeler")

        assertEquals(1, d.projected.getPower(channeler))
        assertEquals(1, d.projected.getToughness(channeler))
        assertFalse(d.projected.hasKeyword(channeler, Keyword.FLYING))

        d.removeSummoningSickness(channeler)
        d.passPriorityUntil(Step.DECLARE_ATTACKERS)
        assertNull(d.declareAttackers(p, emptyList(), d.player2).error)
    }
}
