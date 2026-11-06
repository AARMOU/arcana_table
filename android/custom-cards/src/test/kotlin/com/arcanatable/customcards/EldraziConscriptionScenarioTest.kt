package com.arcanatable.customcards

import com.arcanatable.customcards.cards.EldraziConscription
import com.arcanatable.customcards.support.*
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import org.junit.Assert.*
import org.junit.Test

class EldraziConscriptionScenarioTest {
    @Test
    fun creatureAuraGrantsExactStatsAndTrampleWithoutEnchantingLand() {
        val d = customScenario(EldraziConscription, "Grizzly Bears", "Plains", "Doom Blade")
        val owner = d.player1
        val bear = d.putCreatureOnBattlefield(owner, "Grizzly Bears")
        val land = d.putPermanentOnBattlefield(owner, "Plains")
        val aura = d.putCardInHand(owner, "Eldrazi Conscription")
        d.giveColorlessMana(owner, 8)

        assertNotNull("An Aura with enchant creature must reject a land", d.castSpell(owner, aura, listOf(land)).error)
        assertNull(d.castSpell(owner, aura, listOf(bear)).error)
        d.resolveAll()

        assertEquals(12, d.projected.getPower(bear))
        assertEquals(12, d.projected.getToughness(bear))
        assertTrue(d.projected.hasKeyword(bear, Keyword.TRAMPLE))
        assertNotNull(d.findPermanent(owner, "Eldrazi Conscription"))

        val blade = d.putCardInHand(owner, "Doom Blade")
        d.giveColorlessMana(owner, 1)
        d.giveMana(owner, com.wingedsheep.sdk.core.Color.BLACK)
        assertNull(d.castSpell(owner, blade, listOf(bear)).error)
        d.resolveAll()
        assertTrue(d.permanentsNamed(owner, "Eldrazi Conscription").isEmpty())
        assertTrue(d.zoneNames(owner, Zone.GRAVEYARD).contains("Eldrazi Conscription"))
    }

    @Test
    fun grantedAnnihilatorTwoMakesDefendingPlayerSacrificeBeforeBlocks() {
        val d = customScenario(EldraziConscription, "Grizzly Bears")
        val attacker = d.putCreatureOnBattlefield(d.player1, "Grizzly Bears")
        val alliedBear = d.putCreatureOnBattlefield(d.player1, "Grizzly Bears")
        d.putCreatureOnBattlefield(d.player2, "Grizzly Bears")
        d.putCreatureOnBattlefield(d.player2, "Grizzly Bears")
        val aura = d.putCardInHand(d.player1, "Eldrazi Conscription")
        d.giveColorlessMana(d.player1, 8)
        assertNull(d.castSpell(d.player1, aura, listOf(attacker)).error)
        d.resolveAll()

        d.removeSummoningSickness(attacker)
        d.passPriorityUntil(Step.DECLARE_ATTACKERS)
        assertNull(d.declareAttackers(d.player1, listOf(attacker), d.player2).error)
        d.resolveAll()

        assertEquals("Defender sacrifices both permanents", 0, d.permanentsNamed(d.player2, "Grizzly Bears").size)
        assertEquals("The non-attacking creature's controller sacrifices nothing", 2, d.permanentsNamed(d.player1, "Grizzly Bears").size)
        assertEquals(12, d.projected.getPower(attacker))
        assertEquals(2, d.projected.getPower(alliedBear))
    }
}
