package com.arcanatable.customcards

import com.arcanatable.customcards.cards.AwakeningZone
import com.arcanatable.customcards.support.*
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.Deck
import org.junit.Assert.*
import org.junit.Test

class AwakeningZoneScenarioTest {
    @Test
    fun beginningOfOwnersUpkeepMayMakeAnEldraziSpawn() {
        val d = CardPool.driver("Grizzly Bears").apply {
            registerCard(AwakeningZone)
            initMirrorMatch(Deck.of("Forest" to 40))
            passPriorityUntil(Step.PRECOMBAT_MAIN)
        }
        val p = d.player1
        d.putPermanentOnBattlefield(p, "Awakening Zone")
        d.passPriorityUntil(Step.UPKEEP, activePlayer = p)
        d.resolveAll(Choices(yes = true))

        val spawn = d.permanentsNamed(p, "Eldrazi Spawn")
        assertEquals(1, spawn.size)
        assertTrue(spawn.all { d.abilityActions(p, it).isNotEmpty() })
    }

    @Test
    fun playerMayDeclineSpawnAtBeginningOfUpkeep() {
        val d = CardPool.driver("Grizzly Bears").apply {
            registerCard(AwakeningZone)
            initMirrorMatch(Deck.of("Forest" to 40))
            passPriorityUntil(Step.PRECOMBAT_MAIN)
        }
        val p = d.player1
        d.putPermanentOnBattlefield(p, "Awakening Zone")
        d.passPriorityUntil(Step.UPKEEP, activePlayer = p)
        d.resolveAll(Choices(yes = false))
        assertTrue(d.permanentsNamed(p, "Eldrazi Spawn").isEmpty())
    }
}
