package com.arcanatable.customcards

import com.arcanatable.customcards.cards.FatalPush
import com.arcanatable.customcards.cards.FoodChain
import com.wingedsheep.sdk.scripting.AdditionalCostPayment
import com.arcanatable.customcards.support.*
import com.wingedsheep.sdk.core.Color
import org.junit.Assert.*
import org.junit.Test

class FatalPushScenarioTest {
    @Test
    fun destroysTwoManaCreatureWithoutRevolt() {
        val d = customScenario(FatalPush, "Grizzly Bears")
        val p = d.player1
        val bears = d.putCreatureOnBattlefield(p, "Grizzly Bears")
        val spell = d.putCardInHand(p, "Fatal Push")
        d.giveMana(p, Color.BLACK)

        assertNull(d.castSpell(p, spell, listOf(bears)).error)
        d.resolveStack()
        assertNull(d.getPermanents(p).find { d.getCardName(it) == "Grizzly Bears" })
    }

    @Test
    fun revoltRaisesTheManaValueLimitToFour() {
        val d = customScenario(FatalPush, "Centaur Courser", "Craw Wurm").apply { registerCard(FoodChain) }
        val p = d.player1
        val courser = d.putCreatureOnBattlefield(p, "Centaur Courser")
        val foodChain = d.putPermanentOnBattlefield(p, "Food Chain")
        val wurm = d.putCreatureOnBattlefield(p, "Craw Wurm")
        val exileForMana = d.abilityActions(p, foodChain).first().action as com.wingedsheep.engine.core.ActivateAbility
        assertNull(d.submit(exileForMana.copy(costPayment = AdditionalCostPayment(exiledCards = listOf(wurm)))).error)
        d.resolveAll(Choices(color = Color.GREEN))
        val spell = d.putCardInHand(p, "Fatal Push")
        d.giveMana(p, Color.BLACK)

        assertNull(d.castSpell(p, spell, listOf(courser)).error)
        d.resolveStack()
        assertNull(d.getPermanents(p).find { d.getCardName(it) == "Centaur Courser" })
    }
}
