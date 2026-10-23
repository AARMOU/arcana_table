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

class MotherOfRunesScenarioTest {
    @Test
    fun givesACreatureYouControlProtectionFromTheChosenColor() {
        val d = scenario("Grizzly Bears")
        val p = d.player1
        val mother = d.putCreatureOnBattlefield(p, "Mother of Runes")
        d.removeSummoningSickness(mother)
        val bears = d.putCreatureOnBattlefield(p, "Grizzly Bears")
        val ability = d.cardRegistry.getCard("Mother of Runes")!!.script.activatedAbilities.single()
        d.submitSuccess(ActivateAbility(p, mother, ability.id, targets = listOf(ChosenTarget.Permanent(bears))))
        d.resolveAll(Choices(color = Color.RED))
        assertTrue(d.projected.getKeywords(bears).toString().contains("PROTECTION_FROM_RED"))
    }
}
