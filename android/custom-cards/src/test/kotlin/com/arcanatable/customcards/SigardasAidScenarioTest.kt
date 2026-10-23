package com.arcanatable.customcards

import com.arcanatable.customcards.cards.SigardasAid
import com.arcanatable.customcards.support.*
import com.wingedsheep.engine.state.components.battlefield.AttachedToComponent
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.Deck
import org.junit.Assert.*
import org.junit.Test

class SigardasAidScenarioTest {
    @Test
    fun equipmentEnteringCanBeAttachedToYourCreature() {
        val d = CardPool.driver("Grizzly Bears", "Swiftfoot Boots").apply {
            registerCard(SigardasAid)
            initMirrorMatch(Deck.of("Plains" to 40))
            passPriorityUntil(com.wingedsheep.sdk.core.Step.PRECOMBAT_MAIN)
        }
        val player = d.player1
        d.putPermanentOnBattlefield(player, "Sigarda's Aid")
        val bear = d.putCreatureOnBattlefield(player, "Grizzly Bears")
        val boots = d.putCardInHand(player, "Swiftfoot Boots")
        d.putLandOnBattlefield(player, "Forest")
        d.putLandOnBattlefield(player, "Forest")

        d.passPriorityUntil(Step.PRECOMBAT_MAIN, activePlayer = d.player2)
        d.passPriority(d.player2)
        assertNull(d.castSpell(player, boots).error)
        d.resolveAll(Choices(targets = listOf(bear)))

        assertEquals(bear, d.state.getEntity(boots)?.get<AttachedToComponent>()?.targetId)
    }
}
