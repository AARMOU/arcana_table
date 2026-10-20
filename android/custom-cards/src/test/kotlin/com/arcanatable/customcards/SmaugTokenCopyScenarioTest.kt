package com.arcanatable.customcards

import com.arcanatable.customcards.support.*
import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.core.SelectCardsDecision
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import org.junit.Assert.*
import org.junit.Test

class SmaugTokenCopyScenarioTest {
    @Test
    fun jollyBalloonManSmaugCopyCreatesFourteenTreasuresWhenItDiesToLegendRule() {
        val d = scenario("There and Back Again", "The Jolly Balloon Man")
        val p = d.player1
        val smaug = createSmaug(d)

        val jolly = d.putCreatureOnBattlefield(p, "The Jolly Balloon Man")
        d.removeSummoningSickness(jolly)
        d.giveColorlessMana(p, 1)
        val ability = d.cardRegistry.requireCard("The Jolly Balloon Man").script.activatedAbilities.single()
        assertNull(d.submit(ActivateAbility(
            playerId = p,
            sourceId = jolly,
            abilityId = ability.id,
            targets = listOf(ChosenTarget.Permanent(smaug)),
        )).error)

        // Resolve the copy effect, then choose to keep the original Smaug so the token copy dies.
        d.bothPass()
        val legendChoice = d.state.pendingDecision as? SelectCardsDecision
        assertNotNull("the legendary Smaug copy should ask which one to keep", legendChoice)
        assertTrue("the original Smaug should be one of the choices", smaug in legendChoice!!.options)
        assertNull(d.submitCardSelection(p, listOf(smaug)).error)
        d.resolveAll()

        assertEquals(14, d.permanentsNamed(p, "Treasure").size)
    }

    @Test
    fun originalSmaugTokenCreatesFourteenTreasuresWhenDestroyed() {
        val d = scenario("There and Back Again", "Murder")
        val p = d.player1
        val smaug = createSmaug(d)

        d.giveMana(p, Color.BLACK, 2)
        d.giveColorlessMana(p, 1)
        val murder = d.putCardInHand(p, "Murder")
        assertNull(d.castSpell(p, murder, listOf(smaug)).error)
        d.resolveAll()

        assertEquals(14, d.permanentsNamed(p, "Treasure").size)
        assertTrue(d.zoneNames(p, Zone.GRAVEYARD).contains("Murder"))
    }

    private fun createSmaug(d: com.arcanatable.customcards.support.GameTestDriver): com.wingedsheep.sdk.model.EntityId {
        val p = d.player1
        val saga = d.putCardInHand(p, "There and Back Again")
        d.giveColorlessMana(p, 3)
        d.giveMana(p, Color.RED, 2)
        assertNull(d.castSpell(p, saga).error)
        d.resolveAll() // chapter I

        // Leave Mountains in the library for chapter II's search; draws between chapters consume
        // some cards, so seed several copies.
        repeat(4) { d.putCardOnTopOfLibrary(p, "Mountain") }
        repeat(2) {
            d.passPriorityUntil(Step.UPKEEP, activePlayer = d.player2)
            d.passPriorityUntil(Step.PRECOMBAT_MAIN, activePlayer = p)
            d.resolveAll(Choices(pick = listOf("Mountain")))
        }
        return d.permanentsNamed(p, "Smaug").single()
    }
}
