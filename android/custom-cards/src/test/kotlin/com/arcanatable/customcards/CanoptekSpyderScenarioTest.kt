package com.arcanatable.customcards

import com.arcanatable.customcards.cards.CanoptekSpyder
import com.arcanatable.customcards.support.*
import com.wingedsheep.sdk.core.*
import com.wingedsheep.sdk.dsl.card
import org.junit.Assert.*
import org.junit.Test

class CanoptekSpyderScenarioTest {
    @Test fun ownNontokenArtifactCreatureAndVehicleDrawButOrdinaryArtifactDoesNot() {
        val d = customScenario(CanoptekSpyder, "Ornithopter", "Sol Ring")
        d.registerCard(card("Scenario Vehicle") {
            manaCost = "{1}"
            typeLine = "Artifact — Vehicle"
        })
        val p = d.player1
        val spyder = d.putCreatureOnBattlefield(p, "Canoptek Spyder")
        assertTrue(d.projected.hasKeyword(spyder, Keyword.FLYING))
        assertEquals(4, d.projected.getPower(spyder))

        val ring = d.putCardInHand(p, "Sol Ring")
        val beforeRing = d.getHand(p).size
        assertNull(d.castSpell(p, ring).error)
        d.resolveAll()
        assertEquals(beforeRing - 1, d.getHand(p).size)

        val thopter = d.putCardInHand(p, "Ornithopter")
        val beforeThopter = d.getHand(p).size
        assertNull(d.castSpell(p, thopter).error)
        d.resolveAll()
        assertEquals(beforeThopter, d.getHand(p).size)

        val vehicle = d.putCardInHand(p, "Scenario Vehicle")
        val beforeVehicle = d.getHand(p).size
        d.giveColorlessMana(p, 1)
        assertNull(d.castSpell(p, vehicle).error)
        d.resolveAll()
        assertEquals(beforeVehicle, d.getHand(p).size)
    }

    @Test fun enemyArtifactCreatureDoesNotDrawAndSpyderDoesNotTriggerOnSelf() {
        val d = customScenario(CanoptekSpyder, "Ornithopter")
        val p = d.player1
        val spyder = d.putCardInHand(p, "Canoptek Spyder")
        val before = d.getHand(p).size
        d.giveColorlessMana(p, 5)
        assertNull(d.castSpell(p, spyder).error)
        d.resolveAll()
        assertEquals(before - 1, d.getHand(p).size)

        d.passPriorityUntil(Step.PRECOMBAT_MAIN, activePlayer = d.player2)
        val beforeOpponent = d.getHand(p).size
        val enemy = d.putCardInHand(d.player2, "Ornithopter")
        assertNull(d.castSpell(d.player2, enemy).error)
        d.resolveAll()
        assertEquals(beforeOpponent, d.getHand(p).size)
    }
}
