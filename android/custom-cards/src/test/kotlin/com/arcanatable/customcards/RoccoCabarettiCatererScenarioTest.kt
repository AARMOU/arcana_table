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

class RoccoCabarettiCatererScenarioTest {
    @Test
    fun castWithXFindsACreatureWithManaValueXOrLess() {
        val d = scenario("Grizzly Bears", "Craw Wurm")
        val p = d.player1
        d.putCardOnTopOfLibrary(p, "Craw Wurm")
        d.putCardOnTopOfLibrary(p, "Grizzly Bears")
        val rocco = d.putCardInHand(p, "Rocco, Cabaretti Caterer")
        d.giveMana(p, Color.RED); d.giveMana(p, Color.GREEN); d.giveMana(p, Color.WHITE); d.giveColorlessMana(p, 2)
        assertNull(d.castXSpell(p, rocco, 2).error)
        d.resolveAll(Choices(pick = listOf("Grizzly Bears", "Craw Wurm")))
        assertNotNull(d.findPermanent(p, "Grizzly Bears"))
        assertNull("mana value 6 is above X", d.findPermanent(p, "Craw Wurm"))
    }
}
