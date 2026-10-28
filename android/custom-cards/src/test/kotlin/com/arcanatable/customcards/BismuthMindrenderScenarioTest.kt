package com.arcanatable.customcards

import com.arcanatable.customcards.cards.BismuthMindrender
import com.arcanatable.customcards.support.*
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Step
import org.junit.Assert.*
import org.junit.Test

class BismuthMindrenderScenarioTest {
    private fun setup() = customScenario(BismuthMindrender, "Craw Wurm", "Grizzly Bears")

    /** Opponent's library top: Plains, Plains, Craw Wurm (mana value 6); Bismuth connects. */
    private fun GameTestDriver.connect(c: Choices) {
        putCardOnTopOfLibrary(player2, "Craw Wurm")
        putCardOnTopOfLibrary(player2, "Plains")
        putCardOnTopOfLibrary(player2, "Plains")
        val bismuth = putCreatureOnBattlefield(player1, "Bismuth Mindrender")
        removeSummoningSickness(bismuth)
        passPriorityUntil(Step.DECLARE_ATTACKERS)
        assertNull(declareAttackers(player1, listOf(bismuth), player2).error)
        repeat(80) {
            when {
                state.pendingDecision != null -> decide(c)
                state.step == Step.POSTCOMBAT_MAIN && state.stack.isEmpty() -> return
                else -> bothPass()
            }
        }
    }

    @Test fun exilesUntilNonlandAndYouMayCastItForLife() {
        val d = setup()
        val life = d.getLifeTotal(d.player1)
        val theirs = d.getLifeTotal(d.player2)
        d.connect(Choices(yes = true))
        assertEquals("4 combat damage", theirs - 4, d.getLifeTotal(d.player2))
        assertEquals("paid life equal to mana value 6", life - 6, d.getLifeTotal(d.player1))
        assertEquals("the stolen Wurm is mine", 1, d.permanentsNamed(d.player1, "Craw Wurm").size)
        assertEquals(listOf("Plains", "Plains"), d.getExileCardNames(d.player2).sorted())
    }

    @Test fun decliningLeavesTheCardInExileAndCostsNoLife() {
        val d = setup()
        val life = d.getLifeTotal(d.player1)
        d.connect(Choices(yes = false))
        assertEquals(life, d.getLifeTotal(d.player1))
        assertTrue(d.permanentsNamed(d.player1, "Craw Wurm").isEmpty())
        assertTrue(d.getExileCardNames(d.player2).contains("Craw Wurm"))
    }

    @Test fun castColorlessWithMenaceAndNoTriggerWithoutCombatDamage() {
        val d = setup()
        val card = d.putCardInHand(d.player1, "Bismuth Mindrender")
        assertTrue(d.state.projectedState.getColors(card).isEmpty())
        d.giveMana(d.player1, Color.BLACK); d.giveColorlessMana(d.player1, 3)
        assertNull(d.castSpell(d.player1, card).error)
        d.resolveAll()
        val bismuth = d.permanentsNamed(d.player1, "Bismuth Mindrender").single()
        assertTrue(d.projected.hasKeyword(bismuth, Keyword.MENACE))
        assertTrue(d.getExileCardNames(d.player2).isEmpty())
    }
}
