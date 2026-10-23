package com.arcanatable.customcards

import com.arcanatable.customcards.cards.ConstrictingSliver
import com.arcanatable.customcards.support.*
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.Deck
import org.junit.Assert.*
import org.junit.Test

class ConstrictingSliverScenarioTest {
    @Test
    fun sliverEntryExilesCreatureAndSliverLeavingReturnsIt() {
        val d = CardPool.driver("Grizzly Bears", "Doom Blade", "Constricting Sliver").apply {
            registerCard(ConstrictingSliver)
            initMirrorMatch(Deck.of("Plains" to 40))
            passPriorityUntil(Step.PRECOMBAT_MAIN)
        }
        val p = d.player1
        val opponent = d.player2
        val bear = d.putCreatureOnBattlefield(opponent, "Grizzly Bears")
        val sliver = d.putCardInHand(p, "Constricting Sliver")
        d.giveMana(p, Color.WHITE)
        d.giveColorlessMana(p, 4)
        assertNull(d.castSpell(p, sliver).error)
        d.resolveAll(Choices(targets = listOf(bear)))
        assertFalse(d.permanentsNamed(opponent, "Grizzly Bears").isNotEmpty())
        assertEquals(listOf("Grizzly Bears"), d.getExileCardNames(opponent))

        val doomBlade = d.putCardInHand(p, "Doom Blade")
        d.giveMana(p, Color.BLACK)
        d.giveColorlessMana(p, 1)
        assertNull(d.castSpell(p, doomBlade, listOf(d.findPermanent(p, "Constricting Sliver")!!)).error)
        d.resolveAll()

        assertTrue("leaving Sliver returns its linked exile", d.permanentsNamed(opponent, "Grizzly Bears").isNotEmpty())
        assertTrue(d.getExileCardNames(opponent).isEmpty())
    }
}
