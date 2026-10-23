package com.arcanatable.customcards

import com.arcanatable.customcards.cards.MotherBear
import com.arcanatable.customcards.support.*
import com.wingedsheep.sdk.core.*
import org.junit.Assert.*
import org.junit.Test

class MotherBearScenarioTest {
    @Test
    fun graveyardAbilityExilesMotherBearAndCreatesTwoBears() {
        val d = scenario()
        d.registerCard(MotherBear)
        val p = d.player1
        val bear = d.putCardInGraveyard(p, "Mother Bear")
        repeat(3) { d.giveMana(p, Color.GREEN) }
        repeat(2) { d.giveColorlessMana(p, 1) }

        d.activate(p, bear, cardName = "Mother Bear")
        d.resolveAll()

        assertEquals(2, d.permanentsNamed(p, "Bear Token").size)
        assertEquals(listOf("Mother Bear"), d.zoneNames(p, Zone.EXILE))
    }
}
