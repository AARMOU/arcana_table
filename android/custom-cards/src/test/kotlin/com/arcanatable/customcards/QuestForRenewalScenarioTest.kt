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

class QuestForRenewalScenarioTest {
    @Test
    fun fourQuestCountersUntapCreaturesOnOtherPlayersTurns() {
        val d = scenario("Grizzly Bears")
        val p = d.player1
        val quest = d.putPermanentOnBattlefield(p, "Quest for Renewal")
        val bears = List(4) { d.putCreatureOnBattlefield(p, "Grizzly Bears").also(d::removeSummoningSickness) }
        d.passPriorityUntil(Step.DECLARE_ATTACKERS)
        d.declareAttackers(p, bears, d.player2)
        d.resolveAll(Choices(yes = true))
        assertEquals(4, d.state.getEntity(quest)!!.get<CountersComponent>()!!.getCount(CounterType.QUEST))
        d.passPriorityUntil(Step.UPKEEP, activePlayer = d.player2)
        d.resolveAll()
        assertTrue("untapped at the start of the opponent's turn", bears.none { d.isTapped(it) })
    }
}
