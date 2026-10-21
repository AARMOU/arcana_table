package com.arcanatable.customcards

import com.arcanatable.customcards.support.*
import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.core.SelectCardsDecision
import com.wingedsheep.engine.state.components.battlefield.TappedComponent
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.EntityId
import org.junit.Assert.*
import org.junit.Test

/** Exercises the app's shipped card data through the real engine, without a Smaug-specific override. */
class SmaugTokenCopyScenarioTest {
    @Test(timeout = 60000)
    fun originalSmaugCreatesFourteenTreasuresWhenDestroyed() {
        val d = scenario("There and Back Again", "Murder")
        val smaug = createSmaug(d)
        murder(d, smaug)
        assertEquals(14, d.permanentsNamed(d.player1, "Treasure").size)
    }

    @Test(timeout = 60000)
    fun copiedSmaugCreatesFourteenTreasuresWhenItDiesToLegendRule() {
        val d = scenario("There and Back Again", "The Jolly Balloon Man")
        val original = createSmaug(d)
        val jolly = readyJolly(d)
        copySmaugKeeping(d, jolly, original, original)
        assertEquals(14, d.permanentsNamed(d.player1, "Treasure").size)
        assertEquals(listOf(original), d.permanentsNamed(d.player1, "Smaug"))
    }

    @Test(timeout = 60000)
    fun keepingTheCopyPreservesItsDeathTriggerWithoutDoublingEitherPayout() {
        val d = scenario("There and Back Again", "The Jolly Balloon Man", "Murder")
        val original = createSmaug(d)
        val jolly = readyJolly(d)
        val copy = copySmaugKeeping(d, jolly, original, keep = null)
        assertEquals("the original pays out once", 14, d.permanentsNamed(d.player1, "Treasure").size)
        murder(d, copy)
        assertEquals("the copy pays out once too", 28, d.permanentsNamed(d.player1, "Treasure").size)
        assertTrue(d.permanentsNamed(d.player1, "Smaug").isEmpty())
    }

    @Test(timeout = 60000)
    fun aCopyOfACopyAlsoRetainsTheDeathTrigger() {
        val d = scenario("There and Back Again", "The Jolly Balloon Man")
        val original = createSmaug(d)
        val jolly = readyJolly(d)
        val firstCopy = copySmaugKeeping(d, jolly, original, keep = null)
        assertEquals(14, d.permanentsNamed(d.player1, "Treasure").size)

        // Arrange another activation in the same main phase, before the delayed sacrifice.
        d.replaceState(d.state.updateEntity(jolly) { it.without<TappedComponent>() })
        copySmaugKeeping(d, jolly, firstCopy, keep = firstCopy)
        assertEquals(28, d.permanentsNamed(d.player1, "Treasure").size)
        assertEquals(listOf(firstCopy), d.permanentsNamed(d.player1, "Smaug"))
    }

    @Test(timeout = 60000)
    fun retainedSmaugCopyPaysOutWhenTheBalloonIsSacrificedAtEndStep() {
        val d = scenario("There and Back Again", "The Jolly Balloon Man")
        val original = createSmaug(d)
        val jolly = readyJolly(d)
        copySmaugKeeping(d, jolly, original, keep = null)
        assertEquals(14, d.permanentsNamed(d.player1, "Treasure").size)

        d.passPriorityUntil(Step.END)
        d.resolveAll()
        assertTrue(d.permanentsNamed(d.player1, "Smaug").isEmpty())
        assertEquals(28, d.permanentsNamed(d.player1, "Treasure").size)
    }

    @Test(timeout = 60000)
    fun copyingAnUnrelatedCreatureDoesNotAddSmaugsDeathTrigger() {
        val d = scenario("The Jolly Balloon Man", "Grizzly Bears")
        val p = d.player1
        val bears = d.putCreatureOnBattlefield(p, "Grizzly Bears")
        val jolly = readyJolly(d)
        activateCopy(d, jolly, bears)
        d.resolveAll()
        assertEquals(2, d.permanentsNamed(p, "Grizzly Bears").size)
        d.passPriorityUntil(Step.END)
        d.resolveAll()
        assertEquals(listOf(bears), d.permanentsNamed(p, "Grizzly Bears"))
        assertEquals(0, d.permanentsNamed(p, "Treasure").size)
    }

    private fun readyJolly(d: GameTestDriver): EntityId =
        d.putCreatureOnBattlefield(d.player1, "The Jolly Balloon Man").also(d::removeSummoningSickness)

    private fun activateCopy(d: GameTestDriver, jolly: EntityId, target: EntityId) {
        d.giveColorlessMana(d.player1, 1)
        val ability = d.cardRegistry.requireCard("The Jolly Balloon Man").script.activatedAbilities.single()
        assertNull(d.submit(ActivateAbility(
            playerId = d.player1,
            sourceId = jolly,
            abilityId = ability.id,
            targets = listOf(ChosenTarget.Permanent(target)),
        )).error)
    }

    /** null keeps the new copy; a supplied id keeps that existing Smaug. */
    private fun copySmaugKeeping(d: GameTestDriver, jolly: EntityId, target: EntityId, keep: EntityId?): EntityId {
        activateCopy(d, jolly, target)
        d.bothPass()
        val choice = d.state.pendingDecision as? SelectCardsDecision
        assertNotNull("copying Smaug must ask which legendary permanent to keep", choice)
        val copies = d.permanentsNamed(d.player1, "Smaug")
        assertEquals(2, copies.size)
        val newCopy = copies.single { it != target }
        val survivor = keep ?: newCopy
        assertTrue(survivor in choice!!.options)
        assertNull(d.submitCardSelection(d.player1, listOf(survivor)).error)
        d.resolveAll()
        assertNull(d.state.pendingDecision)
        assertTrue(d.state.stack.isEmpty())
        return survivor
    }

    private fun murder(d: GameTestDriver, target: EntityId) {
        val spell = d.putCardInHand(d.player1, "Murder")
        d.giveMana(d.player1, Color.BLACK, 2)
        d.giveColorlessMana(d.player1, 1)
        assertNull(d.castSpell(d.player1, spell, listOf(target)).error)
        d.resolveAll()
    }

    private fun createSmaug(d: GameTestDriver): EntityId {
        val p = d.player1
        val saga = d.putCardInHand(p, "There and Back Again")
        d.giveColorlessMana(p, 3)
        d.giveMana(p, Color.RED, 2)
        assertNull(d.castSpell(p, saga).error)
        d.resolveAll()

        repeat(4) { d.putCardOnTopOfLibrary(p, "Mountain") }
        repeat(2) {
            d.passPriorityUntil(Step.UPKEEP, activePlayer = d.player2)
            d.passPriorityUntil(Step.PRECOMBAT_MAIN, activePlayer = p)
            d.resolveAll(Choices(pick = listOf("Mountain")))
        }
        return d.permanentsNamed(p, "Smaug").single()
    }
}
