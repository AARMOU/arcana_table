package com.arcanatable.customcards

import com.arcanatable.customcards.cards.SunkenRuins
import com.arcanatable.customcards.support.*
import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.sdk.core.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SunkenRuinsScenarioTest {
    @Test
    fun hybridFilterAbilityAddsTwoBlueMana() {
        val d = customScenario(SunkenRuins, "Forest")
        val land = d.putLandOnBattlefield(d.player1, "Sunken Ruins")
        d.giveMana(d.player1, Color.BLUE)
        val ability = d.cardRegistry.getCard("Sunken Ruins")!!.script.activatedAbilities[1]
        assertNull(d.submit(ActivateAbility(d.player1, land, ability.id)).error)
        d.resolveAll()
        assertEquals(2, d.manaPool(d.player1).blue)
    }
}
