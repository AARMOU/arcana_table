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

class SakashimaOfAThousandFacesScenarioTest {
    @Test
    fun copiesALegendYouControlAndBothStay() {
        val d = scenario()
        val p = d.player1
        d.putCreatureOnBattlefield(p, "Jaws, Relentless Predator")
        val sakashima = d.putCardInHand(p, "Sakashima of a Thousand Faces")
        d.giveMana(p, Color.BLUE); d.giveColorlessMana(p, 3)
        d.castSpell(p, sakashima)
        d.resolveAll(Choices(yes = true, pick = listOf("Jaws, Relentless Predator")))
        assertEquals(2, d.getCreatures(p).count { d.getCardName(it) == "Jaws, Relentless Predator" })
    }

    @Test
    fun withoutCopyingTheLegendRuleIsOffForYourPermanents() {
        val d = scenario()
        val p = d.player1
        d.putCreatureOnBattlefield(p, "Sakashima of a Thousand Faces")
        val jaws = List(2) { d.putCardInHand(p, "Jaws, Relentless Predator") }
        for (j in jaws) { d.giveMana(p, Color.RED); d.giveMana(p, Color.RED); d.giveColorlessMana(p, 3); d.castSpell(p, j); d.resolveAll() }
        assertEquals(2, d.permanentsNamed(p, "Jaws, Relentless Predator").size)
    }
}
