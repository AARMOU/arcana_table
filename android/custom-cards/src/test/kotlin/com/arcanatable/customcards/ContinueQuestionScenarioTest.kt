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

class ContinueQuestionScenarioTest {
    @Test
    fun returnsCreaturesThatDiedThisTurn() {
        val d = scenario("Grizzly Bears", "Wrath of God")
        val p = d.player1
        repeat(2) { d.putCreatureOnBattlefield(p, "Grizzly Bears") }
        val wrath = d.putCardInHand(p, "Wrath of God")
        d.giveMana(p, Color.WHITE); d.giveMana(p, Color.WHITE); d.giveColorlessMana(p, 2)
        d.castSpell(p, wrath)
        d.resolveAll()
        val dead = d.state.getZone(p, Zone.GRAVEYARD).filter { d.getCardName(it) == "Grizzly Bears" }
        assertEquals(2, dead.size)
        val cont = d.putCardInHand(p, "Continue?")
        d.giveMana(p, Color.WHITE); d.giveColorlessMana(p, 1)
        val result = d.castSpellWithTargets(p, cont, dead.map { ChosenTarget.Card(it, p, Zone.GRAVEYARD) })
        assertNull(result.error)
        d.resolveAll()
        assertEquals(2, d.permanentsNamed(p, "Grizzly Bears").size)
    }
}
