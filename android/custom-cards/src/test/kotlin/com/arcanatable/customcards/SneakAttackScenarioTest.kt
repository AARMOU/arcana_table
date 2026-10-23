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

class SneakAttackScenarioTest {
    @Test
    fun putsACreatureInWithHasteAndSacrificesItAtEndStep() {
        val d = scenario("Craw Wurm")
        val p = d.player1
        val attack = d.putPermanentOnBattlefield(p, "Sneak Attack")
        d.putCardInHand(p, "Craw Wurm")
        d.giveMana(p, Color.RED)
        d.activate(p, attack)
        d.resolveAll(Choices(pick = listOf("Craw Wurm")))
        val wurm = d.findPermanent(p, "Craw Wurm")!!
        assertTrue(d.projected.hasKeyword(wurm, Keyword.HASTE))
        d.passPriorityUntil(Step.END)
        d.resolveAll()
        assertNull(d.findPermanent(p, "Craw Wurm"))
        assertTrue(d.zoneNames(p, Zone.GRAVEYARD).contains("Craw Wurm"))
    }
}
