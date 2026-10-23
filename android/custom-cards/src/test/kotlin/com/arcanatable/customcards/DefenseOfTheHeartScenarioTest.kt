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

class DefenseOfTheHeartScenarioTest {
    @Test
    fun upkeepWithThreeOpposingCreaturesFetchesTwoCreatures() {
        val d = scenario("Grizzly Bears", "Craw Wurm")
        val p = d.player1
        val opp = d.player2
        d.putPermanentOnBattlefield(p, "Defense of the Heart")
        repeat(3) { d.putCreatureOnBattlefield(opp, "Grizzly Bears") }
        d.putCardOnTopOfLibrary(p, "Craw Wurm")
        d.putCardOnTopOfLibrary(p, "Craw Wurm")
        d.passPriorityUntil(Step.UPKEEP, activePlayer = p)
        d.resolveAll(Choices(pick = listOf("Craw Wurm")))
        assertEquals(2, d.permanentsNamed(p, "Craw Wurm").size)
        assertNull(d.findPermanent(p, "Defense of the Heart"))
    }
}
