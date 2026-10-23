package com.arcanatable.customcards

import com.arcanatable.customcards.cards.KinjallisSunwing
import com.arcanatable.customcards.support.*
import com.wingedsheep.engine.state.components.battlefield.TappedComponent
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Step
import org.junit.Assert.*
import org.junit.Test

class KinjallisSunwingScenarioTest {
    @Test
    fun opponentCreatureEntersTappedWhileTheSunwingIsOnTheBattlefield() {
        val d = customScenario(KinjallisSunwing, "Grizzly Bears")
        val player = d.player1
        d.putCreatureOnBattlefield(player, "Kinjalli's Sunwing")

        d.passPriorityUntil(Step.PRECOMBAT_MAIN, activePlayer = d.player2)

        val bear = d.putCardInHand(d.player2, "Grizzly Bears")
        d.giveMana(d.player2, Color.GREEN, 2)
        assertNull(d.castSpell(d.player2, bear).error)
        d.resolveAll()

        val enteredBear = d.findPermanent(d.player2, "Grizzly Bears")!!
        assertNotNull(d.state.getEntity(enteredBear)!!.get<TappedComponent>())
    }
}
