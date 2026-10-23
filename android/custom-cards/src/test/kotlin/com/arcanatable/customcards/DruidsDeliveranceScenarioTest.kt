package com.arcanatable.customcards

import com.arcanatable.customcards.cards.DruidsDeliverance
import com.arcanatable.customcards.support.*
import com.wingedsheep.sdk.core.*
import org.junit.Assert.*
import org.junit.Test

class DruidsDeliveranceScenarioTest {
    @Test
    fun preventsCombatDamageAndPopulatesFromYourCreatureToken() {
        val d = scenario()
        d.registerCard(DruidsDeliverance)
        val p = d.player1
        // The library fixture pool includes Squirrel tokens, but has no Bear Token definition.
        val squirrelToken = d.putToken(p, "Squirrel")
        val spell = d.putCardInHand(p, "Druid's Deliverance")
        d.giveMana(p, Color.GREEN)
        d.giveColorlessMana(p, 1)

        d.castSpell(p, spell)
        d.resolveAll()

        assertEquals(2, d.permanentsNamed(p, "Squirrel").size)
        assertNotNull("the original token remains", d.state.getEntity(squirrelToken))
    }
}
