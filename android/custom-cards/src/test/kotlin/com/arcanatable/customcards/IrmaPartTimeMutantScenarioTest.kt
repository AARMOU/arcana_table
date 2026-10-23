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

class IrmaPartTimeMutantScenarioTest {
    @Test
    fun beginningOfCombatBecomesACopyKeepingHerNameAndGetsACounter() {
        val d = scenario("Craw Wurm")
        val p = d.player1
        val irma = d.putCreatureOnBattlefield(p, "Irma, Part-Time Mutant")
        val wurm = d.putCreatureOnBattlefield(p, "Craw Wurm")
        d.passPriorityUntil(Step.BEGIN_COMBAT)
        d.resolveAll(Choices(targets = listOf(wurm)))
        assertEquals("Irma, Part-Time Mutant", d.getCardName(irma))
        assertEquals(7, d.projected.getPower(irma)) // Craw Wurm 6/4 plus a +1/+1 counter
    }
}
