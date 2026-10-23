package com.arcanatable.customcards

import com.arcanatable.customcards.cards.SwordOfFeastAndFamine
import com.arcanatable.customcards.support.*
import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Step
import org.junit.Assert.*
import org.junit.Test

class SwordOfFeastAndFamineScenarioTest {
    @Test
    fun combatDamageMakesPlayerDiscardAndUntapsYourLands() {
        val d = customScenario(SwordOfFeastAndFamine, "Grizzly Bears")
        val player = d.player1
        val bearer = d.putCreatureOnBattlefield(player, "Grizzly Bears")
        val sword = d.putPermanentOnBattlefield(player, "Sword of Feast and Famine")
        val forest = d.putLandOnBattlefield(player, "Forest")
        d.tapPermanent(forest)
        d.giveColorlessMana(player, 2)
        val equip = d.cardRegistry.getCard("Sword of Feast and Famine")!!.script.activatedAbilities.single()
        assertNull(d.submit(ActivateAbility(player, sword, equip.id, targets = listOf(ChosenTarget.Permanent(bearer)))).error)
        d.resolveAll(Choices(targets = listOf(bearer)))
        d.removeSummoningSickness(bearer)
        val opponentHandBefore = d.getHandSize(d.player2)

        d.passPriorityUntil(Step.DECLARE_ATTACKERS)
        assertNull(d.declareAttackers(player, listOf(bearer), d.player2).error)
        d.passPriorityUntil(Step.POSTCOMBAT_MAIN)
        d.resolveAll()

        assertEquals(opponentHandBefore - 1, d.getHandSize(d.player2))
        assertTrue(d.getUntappedLands(player).contains(forest))
    }
}
