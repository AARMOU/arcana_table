package com.arcanatable.customcards

import com.arcanatable.customcards.cards.NecronDeathmark
import com.arcanatable.customcards.support.*
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Zone
import org.junit.Assert.*
import org.junit.Test

class NecronDeathmarkScenarioTest {
    @Test
    fun flashNecronDestroysChosenCreatureAndTargetPlayerMillsThree() {
        val d = customScenario(NecronDeathmark, "Grizzly Bears")
        val p = d.player1
        val victim = d.putCreatureOnBattlefield(d.player2, "Grizzly Bears")
        val necron = d.putCardInHand(p, "Necron Deathmark")
        d.giveMana(p, Color.BLACK)
        d.giveMana(p, Color.BLACK)
        d.giveColorlessMana(p, 3)

        assertNull(d.castSpell(p, necron).error)
        d.resolveAll(Choices(targets = listOf(victim, d.player2)))

        val onField = d.findPermanent(p, "Necron Deathmark")!!
        assertTrue(d.projected.hasKeyword(onField, Keyword.FLASH))
        assertTrue(d.permanentsNamed(d.player2, "Grizzly Bears").isEmpty())
        assertEquals(3, d.zoneNames(d.player2, Zone.GRAVEYARD).count { it == "Plains" })
    }
}
