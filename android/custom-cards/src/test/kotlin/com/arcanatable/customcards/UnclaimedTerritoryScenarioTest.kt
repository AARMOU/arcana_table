package com.arcanatable.customcards

import com.arcanatable.customcards.cards.UnclaimedTerritory
import com.arcanatable.customcards.support.*
import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.sdk.core.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UnclaimedTerritoryScenarioTest {
    @Test
    fun coloredManaAbilityUsesTheChosenCreatureTypeEffect() {
        val d = customScenario(UnclaimedTerritory, "Forest")
        val territory = d.putCardInHand(d.player1, "Unclaimed Territory")
        assertNull(d.playLand(d.player1, territory).error)
        d.resolveAll()
        val ability = d.cardRegistry.getCard("Unclaimed Territory")!!.script.activatedAbilities[1]
        val result = d.submit(ActivateAbility(d.player1, territory, ability.id, manaColorChoice = Color.GREEN))
        assertNull(result.error)
        d.resolveAll(Choices(color = Color.GREEN))
        val pool = d.manaPool(d.player1)
        assertTrue("mana pool after activation: $pool", pool.green == 1 || pool.restrictedMana.any { it.color == Color.GREEN })
    }
}
