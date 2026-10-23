package com.arcanatable.customcards

import com.arcanatable.customcards.cards.SautekhImmortal
import com.arcanatable.customcards.support.*
import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.CounterType
import com.wingedsheep.sdk.core.Keyword
import org.junit.Assert.*
import org.junit.Test

class SautekhImmortalScenarioTest {
    @Test
    fun entryCountersCountDeathsUnderBothPlayersIncludingTokens() {
        val d = customScenario(SautekhImmortal, "Grizzly Bears", "Doom Blade")
        val p = d.player1
        val ownCreature = d.putCreatureOnBattlefield(p, "Grizzly Bears")
        val enemyToken = d.putToken(d.player2, "Grizzly Bears")
        for (victim in listOf(ownCreature, enemyToken)) {
            val blade = d.putCardInHand(p, "Doom Blade")
            d.giveMana(p, Color.BLACK)
            d.giveColorlessMana(p, 1)
            assertNull(d.castSpell(p, blade, listOf(victim)).error)
            d.resolveAll()
        }
        val immortal = d.putCardInHand(p, "Sautekh Immortal")
        d.giveMana(p, Color.BLACK)
        d.giveColorlessMana(p, 2)
        assertNull(d.castSpell(p, immortal).error)
        d.resolveAll()

        val permanent = d.findPermanent(p, "Sautekh Immortal")!!
        assertEquals(2, d.state.getEntity(permanent)!!.get<CountersComponent>()!!.getCount(CounterType.PLUS_ONE_PLUS_ONE))
        assertEquals(4, d.projected.getPower(permanent))
        assertEquals(4, d.projected.getToughness(permanent))
        assertTrue(d.projected.hasKeyword(permanent, Keyword.FLASH))
    }

    @Test
    fun flashAllowsCastingOnOpponentsTurnAndNoDeathsMeansNoCounters() {
        val d = customScenario(SautekhImmortal)
        val p = d.player2
        val immortal = d.putCardInHand(p, "Sautekh Immortal")
        d.giveMana(p, Color.BLACK)
        d.giveColorlessMana(p, 2)
        assertNull(d.passPriority(d.player1).error)
        assertNull(d.castSpell(p, immortal).error)
        d.resolveAll()

        val permanent = d.findPermanent(p, "Sautekh Immortal")!!
        assertEquals(0, d.state.getEntity(permanent)!!.get<CountersComponent>()?.getCount(CounterType.PLUS_ONE_PLUS_ONE) ?: 0)
        assertEquals(2, d.projected.getPower(permanent))
        assertEquals(2, d.projected.getToughness(permanent))
    }
}
