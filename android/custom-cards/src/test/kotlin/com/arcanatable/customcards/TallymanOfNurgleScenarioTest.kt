package com.arcanatable.customcards

import com.arcanatable.customcards.cards.TallymanOfNurgle
import com.arcanatable.customcards.support.*
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.engine.state.components.player.CreaturesDiedThisTurnComponent
import org.junit.Assert.*
import org.junit.Test

class TallymanOfNurgleScenarioTest {
    @Test
    fun aCreatureDyingBeforeEndStepDrawsOneAndLosesOneLife() {
        val d = customScenario(TallymanOfNurgle, "Grizzly Bears", "Doom Blade")
        val p = d.player1
        val tallyman = d.putCreatureOnBattlefield(p, "Tallyman of Nurgle")
        d.putCreatureOnBattlefield(d.player2, "Grizzly Bears")
        val bear = d.findPermanent(d.player2, "Grizzly Bears")!!
        assertTrue(d.projected.hasKeyword(tallyman, Keyword.LIFELINK))
        val blade = d.putCardInHand(p, "Doom Blade")
        d.giveMana(p, Color.BLACK)
        d.giveColorlessMana(p, 1)
        assertNull(d.castSpell(p, blade, listOf(bear)).error)
        d.resolveAll()
        assertTrue("Doom Blade destroyed the creature before end step", d.permanentsNamed(d.player2, "Grizzly Bears").isEmpty())
        val deathsByPlayer = d.state.turnOrder.map { playerId ->
            d.state.getEntity(playerId)!!.get<CreaturesDiedThisTurnComponent>()?.count ?: 0
        }
        assertTrue("A creature died but the engine recorded no death: $deathsByPlayer", deathsByPlayer.sum() > 0)
        val beforeEnd = d.getHand(p).size

        d.passPriorityUntil(Step.END, activePlayer = p)
        assertTrue("Tallyman must have a pending end-step trigger with recorded deaths $deathsByPlayer",
            d.state.stack.isNotEmpty())
        d.resolveAll()

        assertEquals(19, d.getLifeTotal(p))
        assertEquals(20, d.getLifeTotal(d.player2))
        assertEquals(beforeEnd + 1, d.getHand(p).size)
    }

    @Test
    fun sevenCreatureDeathsDrawSevenAndLoseSevenLife() {
        val d = customScenario(TallymanOfNurgle, "Grizzly Bears", "Doom Blade")
        val p = d.player1
        d.putCreatureOnBattlefield(p, "Tallyman of Nurgle")
        repeat(7) {
            val creature = d.putCreatureOnBattlefield(d.player2, "Grizzly Bears")
            val blade = d.putCardInHand(p, "Doom Blade")
            d.giveMana(p, Color.BLACK)
            d.giveColorlessMana(p, 1)
            assertNull(d.castSpell(p, blade, listOf(creature)).error)
            d.resolveAll()
        }
        assertEquals(0, d.permanentsNamed(d.player2, "Grizzly Bears").size)
        val deaths = d.state.turnOrder.sumOf { playerId ->
            d.state.getEntity(playerId)!!.get<CreaturesDiedThisTurnComponent>()?.count ?: 0
        }
        assertEquals(7, deaths)
        val beforeEnd = d.getHand(p).size
        d.passPriorityUntil(Step.END, activePlayer = p)
        d.resolveAll()
        assertEquals(13, d.getLifeTotal(p))
        assertEquals(20, d.getLifeTotal(d.player2))
        assertEquals(beforeEnd + 7, d.getHand(p).size)
    }

    @Test
    fun noDeathThisTurnMeansNoChantTrigger() {
        val d = customScenario(TallymanOfNurgle)
        val p = d.player1
        d.putCreatureOnBattlefield(p, "Tallyman of Nurgle")
        val before = d.getHand(p).size
        d.passPriorityUntil(Step.END, activePlayer = p)
        d.resolveAll()
        assertEquals(20, d.getLifeTotal(p))
        assertEquals(before, d.getHand(p).size)
    }
}
