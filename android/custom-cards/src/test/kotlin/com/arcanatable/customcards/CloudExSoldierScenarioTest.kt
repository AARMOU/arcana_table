package com.arcanatable.customcards

import com.arcanatable.customcards.cards.CloudExSoldier
import com.arcanatable.customcards.support.CardPool
import com.arcanatable.customcards.support.Choices
import com.arcanatable.customcards.support.resolveAll
import com.wingedsheep.engine.state.components.battlefield.AttachedToComponent
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.Deck
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CloudExSoldierScenarioTest {
    @Test
    fun entersEquippingItselfThenDrawsForEquippedAttackers() {
        val d = CardPool.driver("Buster Sword", "Forest").apply {
            registerCard(CloudExSoldier)
            initMirrorMatch(Deck.of("Plains" to 40))
            passPriorityUntil(Step.PRECOMBAT_MAIN)
        }
        val p = d.player1
        val cloud = d.putCardInHand(p, "Cloud, Ex-SOLDIER")
        val sword = d.putPermanentOnBattlefield(p, "Buster Sword")
        d.giveColorlessMana(p, 2)
        d.giveMana(p, Color.RED)
        d.giveMana(p, Color.GREEN)
        d.giveMana(p, Color.WHITE)
        assertNull(d.castSpell(p, cloud).error)
        d.resolveAll(Choices(targets = listOf(sword)))
        assertEquals(cloud, d.state.getEntity(sword)?.get<AttachedToComponent>()?.targetId)

        d.removeSummoningSickness(cloud)
        d.passPriorityUntil(Step.DECLARE_ATTACKERS, activePlayer = p)
        val handBefore = d.getHandSize(p)
        assertNull(d.declareAttackers(p, listOf(cloud), d.player2).error)
        d.resolveAll()

        assertEquals("an equipped Cloud is one equipped attacking creature", handBefore + 1, d.getHandSize(p))
    }
}
