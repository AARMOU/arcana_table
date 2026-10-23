package com.arcanatable.customcards

import com.arcanatable.customcards.cards.SwordOfWarAndPeace
import com.arcanatable.customcards.support.*
import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Step
import org.junit.Assert.*
import org.junit.Test

class SwordOfWarAndPeaceScenarioTest {
    @Test
    fun combatDamageScalesToBothPlayersHandSizes() {
        val d = customScenario(SwordOfWarAndPeace, "Grizzly Bears")
        val player = d.player1
        val bearer = d.putCreatureOnBattlefield(player, "Grizzly Bears")
        val sword = d.putPermanentOnBattlefield(player, "Sword of War and Peace")
        d.giveColorlessMana(player, 2)
        val equip = d.cardRegistry.getCard("Sword of War and Peace")!!.script.activatedAbilities.single()
        assertNull(d.submit(ActivateAbility(player, sword, equip.id, targets = listOf(ChosenTarget.Permanent(bearer)))).error)
        d.resolveAll(Choices(targets = listOf(bearer)))
        d.removeSummoningSickness(bearer)
        val ownHand = d.getHandSize(player)
        val opponentHand = d.getHandSize(d.player2)
        val ownLife = d.getLifeTotal(player)
        val opponentLife = d.getLifeTotal(d.player2)

        d.passPriorityUntil(Step.DECLARE_ATTACKERS)
        assertNull(d.declareAttackers(player, listOf(bearer), d.player2).error)
        d.passPriorityUntil(Step.POSTCOMBAT_MAIN)
        d.resolveAll()

        assertEquals(ownLife + ownHand, d.getLifeTotal(player))
        // The equipped Grizzly Bears deals 4 combat damage before the Sword's hand-size damage.
        assertEquals(opponentLife - 4 - opponentHand, d.getLifeTotal(d.player2))
    }
}
