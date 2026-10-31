package com.arcanatable.customcards

import com.arcanatable.customcards.cards.MarneusCalgar
import com.arcanatable.customcards.support.*
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.identity.TokenComponent
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import org.junit.Assert.*
import org.junit.Test

class MarneusCalgarScenarioTest {
    @Test
    fun doubleStrikeAndSimultaneousTokenEntryDrawsExactlyOnce() {
        val d = customScenario(MarneusCalgar, "Call the Cavalry", "Grizzly Bears")
        val player = d.player1
        val marneus = d.putCreatureOnBattlefield(player, "Marneus Calgar")
        val bear = d.putCreatureOnBattlefield(player, "Grizzly Bears")

        assertTrue(d.projected.hasKeyword(marneus, Keyword.DOUBLE_STRIKE))
        assertFalse(d.projected.hasKeyword(bear, Keyword.DOUBLE_STRIKE))

        val cavalry = d.putCardInHand(player, "Call the Cavalry")
        val handSizeBeforeCast = d.zoneNames(player, Zone.HAND).size
        d.giveMana(player, Color.WHITE)
        d.giveColorlessMana(player, 3)
        assertNull(d.castSpell(player, cavalry).error)
        d.resolveAll()

        assertEquals(2, d.getCreatures(player).count { d.state.getEntity(it)?.has<TokenComponent>() == true })
        assertEquals(handSizeBeforeCast, d.zoneNames(player, Zone.HAND).size)
    }

    @Test
    fun nonTokenAndOpponentTokenEntriesDoNotTriggerMasterTactician() {
        val d = customScenario(MarneusCalgar, "Call the Cavalry", "Grizzly Bears")
        val player = d.player1
        val opponent = d.player2
        d.putCreatureOnBattlefield(player, "Marneus Calgar")
        val bear = d.putCardInHand(player, "Grizzly Bears")
        val handSizeBeforeCast = d.zoneNames(player, Zone.HAND).size
        d.giveMana(player, Color.GREEN)
        d.giveColorlessMana(player, 1)
        assertNull(d.castSpell(player, bear).error)

        d.resolveAll()
        assertEquals(handSizeBeforeCast - 1, d.zoneNames(player, Zone.HAND).size)
        val handSize = d.zoneNames(player, Zone.HAND).size

        d.passPriorityUntil(Step.PRECOMBAT_MAIN, opponent)
        val cavalry = d.putCardInHand(opponent, "Call the Cavalry")
        d.giveMana(opponent, Color.WHITE)
        d.giveColorlessMana(opponent, 3)
        assertNull(d.castSpell(opponent, cavalry).error)
        d.resolveAll()

        assertEquals(2, d.getCreatures(opponent).count { d.state.getEntity(it)?.has<TokenComponent>() == true })
        assertEquals(handSize, d.zoneNames(player, Zone.HAND).size)
    }

    @Test
    fun chapterMasterRequiresSixGenericManaAndCreatesTwoVigilantAstartesWarriors() {
        val d = customScenario(MarneusCalgar)
        val player = d.player1
        val marneus = d.putCreatureOnBattlefield(player, "Marneus Calgar")

        assertTrue(d.abilityActions(player, marneus).isEmpty())
        d.giveColorlessMana(player, 5)
        assertTrue(d.abilityActions(player, marneus).isEmpty())
        d.giveColorlessMana(player, 1)
        assertEquals(1, d.abilityActions(player, marneus).size)
        assertNull(d.activate(player, marneus).error)
        d.resolveAll()

        val tokens = d.getCreatures(player).filter { d.state.getEntity(it)?.has<TokenComponent>() == true }
        assertEquals(2, tokens.size)
        tokens.forEach { token ->
            val characteristics = d.state.getEntity(token)!!.get<CardComponent>()!!
            assertEquals(setOf(Color.WHITE), characteristics.colors)
            val subtypes = characteristics.typeLine.subtypes.map { it.value }.toSet()
            assertTrue("Astartes" in subtypes)
            assertTrue("Warrior" in subtypes)
            assertEquals(2, characteristics.baseStats?.basePower)
            assertEquals(2, characteristics.baseStats?.baseToughness)
            assertTrue(d.projected.hasKeyword(token, Keyword.VIGILANCE))
        }
    }
}
