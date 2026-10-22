package com.arcanatable.customcards

import com.arcanatable.customcards.cards.MandateOfAbaddon
import com.arcanatable.customcards.support.*
import com.wingedsheep.sdk.core.*
import org.junit.Assert.*
import org.junit.Test

class MandateOfAbaddonScenarioTest {
    @Test fun destroysStrictlyLowerPowerOnBothSidesWithoutKillingTarget() {
        val d = customScenario(MandateOfAbaddon, "Hill Giant", "Grizzly Bears", "Llanowar Elves")
        val p = d.player1
        val benchmark = d.putCreatureOnBattlefield(p, "Hill Giant")
        val equalPower = d.putCreatureOnBattlefield(d.player2, "Hill Giant")
        val smallAlly = d.putCreatureOnBattlefield(p, "Grizzly Bears")
        val smallEnemy = d.putCreatureOnBattlefield(d.player2, "Llanowar Elves")
        val spell = d.putCardInHand(p, "Mandate of Abaddon")
        d.giveMana(p, Color.BLACK)
        d.giveColorlessMana(p, 3)
        assertNull(d.castSpell(p, spell, listOf(benchmark)).error)
        d.resolveAll()
        assertTrue(benchmark in d.getCreatures(p))
        assertTrue(equalPower in d.getCreatures(d.player2))
        assertFalse(smallAlly in d.getCreatures(p))
        assertFalse(smallEnemy in d.getCreatures(d.player2))
    }

    @Test fun cannotSelectOpponentsCreatureAsBenchmark() {
        val d = customScenario(MandateOfAbaddon, "Hill Giant")
        val p = d.player1
        val opponent = d.putCreatureOnBattlefield(d.player2, "Hill Giant")
        val spell = d.putCardInHand(p, "Mandate of Abaddon")
        d.giveMana(p, Color.BLACK)
        d.giveColorlessMana(p, 3)
        assertNotNull(d.castSpell(p, spell, listOf(opponent)).error)
    }
}
