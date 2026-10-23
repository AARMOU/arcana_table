package com.arcanatable.customcards
import com.arcanatable.customcards.cards.Plasmancer
import com.arcanatable.customcards.support.*
import com.wingedsheep.sdk.core.*
import com.wingedsheep.sdk.model.Deck
import org.junit.Assert.*
import org.junit.Test
class PlasmancerScenarioTest {
    @Test fun enteringFindsBasicSwampAndKeepsFlying() {
        val d = CardPool.driver().apply {
            registerCard(Plasmancer)
            initMirrorMatch(Deck.of("Swamp" to 40))
            passPriorityUntil(Step.PRECOMBAT_MAIN)
        }
        val p = d.player1
        val spell = d.putCardInHand(p, "Plasmancer")
        repeat(2) { d.giveMana(p, Color.BLACK) }
        d.giveColorlessMana(p, 2)
        assertNull(d.castSpell(p, spell).error)
        d.resolveAll(Choices(pick = listOf("Swamp")))
        assertTrue(d.zoneNames(p, Zone.HAND).contains("Swamp"))
        assertTrue(d.projected.hasKeyword(d.findPermanent(p, "Plasmancer")!!, Keyword.FLYING))
    }
}
