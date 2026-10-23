package com.arcanatable.customcards

import com.arcanatable.customcards.support.*
import com.wingedsheep.engine.core.SelectCardsDecision
import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.CounterType
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.model.EntityId
import org.junit.Assert.*
import org.junit.Test

class ValkyriesCallScenarioTest {
    private val cid = "Cid, Timeless Artificer"

    @Test(timeout = 60000)
    fun returnsCidWithAngelFlyingAndOneCounter() {
        val d = game()
        val original = d.putCreatureOnBattlefield(d.player1, cid)
        murder(d, original)
        assertReturned(d, d.permanentsNamed(d.player1, cid).single())
    }

    @Test(timeout = 60000)
    fun returnedAngelStaysDeadWhenDestroyedAgain() {
        val d = game()
        val original = d.putCreatureOnBattlefield(d.player1, cid)
        murder(d, original)
        val returned = d.permanentsNamed(d.player1, cid).single()
        assertReturned(d, returned)
        murder(d, returned)
        assertTrue(d.permanentsNamed(d.player1, cid).isEmpty())
        assertEquals(1, d.getGraveyardCardNames(d.player1).count { it == cid })
        assertSettled(d)
    }

    @Test(timeout = 60000)
    fun keepingOriginalCidEndsTheLegendRuleSequence() {
        val d = game()
        val original = d.putCreatureOnBattlefield(d.player1, cid)
        castSecondCid(d)
        chooseLegend(d, original)
        awaitLegend(d)
        val returned = d.permanentsNamed(d.player1, cid).single { it != original }
        assertReturned(d, returned)
        chooseLegend(d, original)
        d.resolveAll()
        assertEquals(listOf(original), d.permanentsNamed(d.player1, cid))
        assertSettled(d)
    }

    @Test(timeout = 60000)
    fun keepingReturnedCidAlsoEndsTheLegendRuleSequence() {
        val d = game()
        val original = d.putCreatureOnBattlefield(d.player1, cid)
        castSecondCid(d)
        chooseLegend(d, original)
        awaitLegend(d)
        val returned = d.permanentsNamed(d.player1, cid).single { it != original }
        assertReturned(d, returned)
        chooseLegend(d, returned)
        // The other, still non-Angel Cid gets one return, then dies as an Angel.
        awaitLegend(d)
        d.permanentsNamed(d.player1, cid).forEach { assertReturned(d, it) }
        chooseLegend(d, returned)
        d.resolveAll()
        assertEquals(listOf(returned), d.permanentsNamed(d.player1, cid))
        assertSettled(d)
    }

    @Test(timeout = 60000)
    fun printedAngelAndTokenAreExcluded() {
        val d = game("Serra Angel", "Grizzly Bears")
        val angel = d.putCreatureOnBattlefield(d.player1, "Serra Angel")
        murder(d, angel)
        assertTrue(d.permanentsNamed(d.player1, "Serra Angel").isEmpty())
        val token = d.putToken(d.player1, "Grizzly Bears")
        murder(d, token)
        assertTrue(d.permanentsNamed(d.player1, "Grizzly Bears").isEmpty())
        assertSettled(d)
    }

    private fun game(vararg extra: String): GameTestDriver =
        scenario("Valkyrie's Call", cid, "Murder", *extra).also {
            it.putPermanentOnBattlefield(it.player1, "Valkyrie's Call")
        }

    private fun castSecondCid(d: GameTestDriver) {
        val second = d.putCardInHand(d.player1, cid)
        d.giveMana(d.player1, Color.WHITE)
        d.giveMana(d.player1, Color.BLUE)
        d.giveColorlessMana(d.player1, 2)
        assertNull(d.castSpell(d.player1, second).error)
        awaitLegend(d)
    }

    private fun awaitLegend(d: GameTestDriver) {
        repeat(20) {
            if (d.state.pendingDecision != null) return@repeat
            d.bothPass()
        }
        assertTrue("must reach a legend-rule choice", d.state.pendingDecision is SelectCardsDecision)
    }

    private fun chooseLegend(d: GameTestDriver, keep: EntityId) {
        val decision = d.state.pendingDecision as? SelectCardsDecision
        assertNotNull(decision)
        assertTrue(keep in decision!!.options)
        assertNull(d.submitCardSelection(d.player1, listOf(keep)).error)
    }

    private fun assertReturned(d: GameTestDriver, id: EntityId) {
        assertTrue("returned Cid is an Angel", d.projected.hasSubtype(id, "Angel"))
        assertTrue("returned Cid has flying", d.projected.hasKeyword(id, Keyword.FLYING))
        assertEquals(1, d.state.getEntity(id)!!.get<CountersComponent>()!!.getCount(CounterType.PLUS_ONE_PLUS_ONE))
    }

    private fun assertSettled(d: GameTestDriver) {
        assertNull("no repeated legend prompt", d.state.pendingDecision)
        assertTrue("no repeated resurrection trigger", d.state.stack.isEmpty())
    }

    private fun murder(d: GameTestDriver, target: EntityId) {
        val spell = d.putCardInHand(d.player1, "Murder")
        d.giveMana(d.player1, Color.BLACK, 2)
        d.giveColorlessMana(d.player1, 1)
        assertNull(d.castSpell(d.player1, spell, listOf(target)).error)
        d.resolveAll()
    }
}
