package com.arcanatable.customcards

import com.arcanatable.customcards.cards.BeaconOfUnrest
import com.arcanatable.customcards.support.*
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import org.junit.Assert.*
import org.junit.Test

class BeaconOfUnrestScenarioTest {
    @Test
    fun reanimatesOpponentsCreatureUnderYourControlAndShufflesSpell() {
        val d = customScenario(BeaconOfUnrest, "Grizzly Bears")
        val p = d.player1
        val target = d.putCardInGraveyard(d.player2, "Grizzly Bears")
        val spell = d.putCardInHand(p, "Beacon of Unrest")
        d.giveMana(p, Color.BLACK, 2)
        d.giveColorlessMana(p, 3)
        assertNull(d.castSpellWithTargets(p, spell, listOf(ChosenTarget.Card(target, d.player2, Zone.GRAVEYARD))).error)
        d.resolveAll()
        assertEquals(1, d.permanentsNamed(p, "Grizzly Bears").size)
        assertTrue(d.permanentsNamed(d.player2, "Grizzly Bears").isEmpty())
        assertTrue(d.zoneNames(d.player2, Zone.GRAVEYARD).none { it == "Grizzly Bears" })
        assertTrue(d.zoneNames(p, Zone.LIBRARY).contains("Beacon of Unrest"))
        assertFalse(d.zoneNames(p, Zone.GRAVEYARD).contains("Beacon of Unrest"))
    }

    @Test
    fun reanimatesArtifactNotJustCreature() {
        val d = customScenario(BeaconOfUnrest, "Sol Ring")
        val p = d.player1
        val target = d.putCardInGraveyard(d.player2, "Sol Ring")
        val spell = d.putCardInHand(p, "Beacon of Unrest")
        d.giveMana(p, Color.BLACK, 2)
        d.giveColorlessMana(p, 3)
        assertNull(d.castSpellWithTargets(p, spell, listOf(ChosenTarget.Card(target, d.player2, Zone.GRAVEYARD))).error)
        d.resolveAll()
        assertNotNull(d.findPermanent(p, "Sol Ring"))
        assertTrue(d.zoneNames(p, Zone.LIBRARY).contains("Beacon of Unrest"))
    }
}
