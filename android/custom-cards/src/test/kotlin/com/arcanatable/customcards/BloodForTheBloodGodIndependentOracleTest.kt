package com.arcanatable.customcards

import com.arcanatable.customcards.cards.BloodForTheBloodGod
import com.arcanatable.customcards.support.*
import com.wingedsheep.engine.state.components.player.CreaturesDiedThisTurnComponent
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.Deck
import org.junit.Assert.*
import org.junit.Test

/**
 * Independent post-Copilot Oracle tests (owner's #81 certification lane).
 * This file was not authored by the card implementation worker.
 * Use real Argentum actions/resolution; do not accept CI-green alone as certification.
 */
class BloodForTheBloodGodIndependentOracleTest {
    private fun twoDeaths(): GameTestDriver {
        val d = customScenario(BloodForTheBloodGod, "Grizzly Bears", "Doom Blade")
        val p = d.player1
        val opponent = d.player2
        val first = d.putCreatureOnBattlefield(p, "Grizzly Bears")
        val second = d.putCreatureOnBattlefield(opponent, "Grizzly Bears")
        val doom1 = d.putCardInHand(p, "Doom Blade")
        val doom2 = d.putCardInHand(p, "Doom Blade")
        d.giveColorlessMana(p, 2)
        d.giveMana(p, Color.BLACK, 2)
        assertNull("Own creature can die", d.castSpell(p, doom1, listOf(first)).error)
        d.resolveAll()
        assertNull("Opponent creature can die", d.castSpell(p, doom2, listOf(second)).error)
        d.resolveAll()
        val deaths = d.state.turnOrder.sumOf { playerId ->
            d.state.getEntity(playerId)!!.get<CreaturesDiedThisTurnComponent>()?.count ?: 0
        }
        assertEquals("Cost reduction must see both controllers' creature deaths", 2, deaths)
        return d
    }

    @Test
    fun twoDeathsReduceOnlyGenericByExactlyTwoAndFailedCastDoesNotSpendMana() {
        val d = twoDeaths()
        val p = d.player1
        val spell = d.putCardInHand(p, "Blood for the Blood God!")
        d.giveColorlessMana(p, 5)
        d.giveMana(p, Color.BLACK, 2)
        d.giveMana(p, Color.RED, 1)
        assertNotNull("Five generic is one short after two deaths", d.castSpell(p, spell).error)
        d.giveColorlessMana(p, 1)
        assertNull("Six generic with BRB is payable after two deaths", d.castSpell(p, spell).error)
        d.resolveAll()
        assertEquals(12, d.getLifeTotal(d.player2))
        assertEquals(20, d.getLifeTotal(p))
        assertTrue("This spell must exile on resolution", "Blood for the Blood God!" in d.zoneNames(p, Zone.EXILE))
    }

    @Test
    fun creatureDeathsCannotReduceTheTwoBlackOrSingleRedPip() {
        val d = twoDeaths()
        val p = d.player1
        val spell = d.putCardInHand(p, "Blood for the Blood God!")
        d.giveColorlessMana(p, 6)
        d.giveMana(p, Color.BLACK, 1)
        d.giveMana(p, Color.RED, 1)
        assertNotNull("Generic reduction must not replace the second black mana", d.castSpell(p, spell).error)
        d.giveMana(p, Color.BLACK, 1)
        assertNull(d.castSpell(p, spell).error)
        d.resolveAll()
        assertEquals(12, d.getLifeTotal(d.player2))
    }

    @Test
    fun resolvingInFourPlayerGameDamagesAllThreeOpponentsExactlyEight() {
        val d = customScenario(BloodForTheBloodGod)
        val seats = d.initMultiplayer(List(4) { Deck.of("Plains" to 40) })
        d.passPriorityUntil(Step.PRECOMBAT_MAIN, activePlayer = seats[0])
        val spell = d.putCardInHand(seats[0], "Blood for the Blood God!")
        d.giveColorlessMana(seats[0], 8)
        d.giveMana(seats[0], Color.BLACK, 2)
        d.giveMana(seats[0], Color.RED, 1)
        assertNull(d.castSpell(seats[0], spell).error)
        d.resolveAll()
        assertEquals("Caster does not take damage", 20, d.getLifeTotal(seats[0]))
        for (other in seats.drop(1)) {
            assertEquals("Every opponent takes precisely eight damage", 12, d.getLifeTotal(other))
        }
        assertTrue("Exile applies in multiplayer too", "Blood for the Blood God!" in d.zoneNames(seats[0], Zone.EXILE))
    }
}
