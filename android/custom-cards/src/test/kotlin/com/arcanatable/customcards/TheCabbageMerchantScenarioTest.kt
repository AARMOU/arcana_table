package com.arcanatable.customcards

import com.arcanatable.customcards.support.*
import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.state.components.battlefield.TappedComponent
import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.sdk.core.*
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.scripting.AdditionalCostPayment
import org.junit.Assert.*
import org.junit.Test

class TheCabbageMerchantScenarioTest {
    @Test
    fun opponentsNoncreatureSpellMakesFoodAndTwoFoodsTapForMana() {
        val d = scenario("Dark Ritual")
        val p = d.player1
        val opp = d.player2
        d.putCreatureOnBattlefield(p, "The Cabbage Merchant")
        val ritual = d.putCardInHand(opp, "Dark Ritual")
        d.passPriority(p)
        d.giveMana(opp, Color.BLACK)
        assertNull(d.castSpell(opp, ritual).error)
        d.resolveAll()
        assertEquals(1, d.permanentsNamed(p, "Food").size)

        d.putToken(p, "Food")
        val merchant = d.findPermanent(p, "The Cabbage Merchant")!!
        if (d.priorityPlayer != p) d.passPriority(d.priorityPlayer!!)
        val tapFoods = d.abilityActions(p, merchant).single().action as ActivateAbility
        val foods = d.permanentsNamed(p, "Food")
        assertNull(d.submit(tapFoods.copy(costPayment = AdditionalCostPayment(tappedPermanents = foods), manaColorChoice = Color.BLUE)).error)
        d.resolveAll(Choices(color = Color.BLUE))
        assertEquals(1, d.manaPool(p).blue)
        assertTrue(foods.all { d.isTapped(it) })
    }
}
