package com.arcanatable.customcards

import com.arcanatable.customcards.cards.AndTheyShallKnowNoFear
import com.arcanatable.customcards.support.*
import com.wingedsheep.engine.core.ChooseOptionDecision
import com.wingedsheep.engine.core.OptionChosenResponse
import com.wingedsheep.sdk.core.*
import org.junit.Assert.*
import org.junit.Test

class AndTheyShallKnowNoFearScenarioTest {
    private fun castAndChooseType(d: GameTestDriver, chosenType: String) {
        val p = d.player1
        val spell = d.putCardInHand(p, "And They Shall Know No Fear")
        d.giveColorlessMana(p, 1)
        d.giveMana(p, Color.WHITE)
        assertNull(d.castSpell(p, spell).error)
        repeat(30) {
            if (d.state.pendingDecision != null) return@repeat
            if (d.state.stack.isNotEmpty()) d.bothPass()
        }
        val decision = d.state.pendingDecision
        assertTrue("Expected creature-type choice", decision is ChooseOptionDecision)
        val opts = decision as ChooseOptionDecision
        val selected = opts.options.indexOf(chosenType)
        assertTrue("Creature type $chosenType not in options", selected >= 0)
        assertNull(d.submitDecision(opts.playerId, OptionChosenResponse(opts.id, selected)).error)
        d.resolveAll()
    }

    @Test fun selectedElfIsPumpedAndIndestructibleOnlyOnControllersBattlefieldUntilEndOfTurn() {
        val d = customScenario(AndTheyShallKnowNoFear, "Llanowar Elves", "Grizzly Bears")
        val p = d.player1
        val elf = d.putCreatureOnBattlefield(p, "Llanowar Elves")
        val bear = d.putCreatureOnBattlefield(p, "Grizzly Bears")
        val opponentElf = d.putCreatureOnBattlefield(d.player2, "Llanowar Elves")
        castAndChooseType(d, "Elf")

        assertEquals(2, d.projected.getPower(elf))
        assertEquals(1, d.projected.getToughness(elf))
        assertTrue(d.projected.hasKeyword(elf, Keyword.INDESTRUCTIBLE))
        assertEquals(2, d.projected.getPower(bear))
        assertFalse(d.projected.hasKeyword(bear, Keyword.INDESTRUCTIBLE))
        assertEquals(1, d.projected.getPower(opponentElf))
        assertFalse(d.projected.hasKeyword(opponentElf, Keyword.INDESTRUCTIBLE))

        val newElf = d.putCreatureOnBattlefield(p, "Llanowar Elves")
        assertEquals(1, d.projected.getPower(newElf))
        assertFalse(d.projected.hasKeyword(newElf, Keyword.INDESTRUCTIBLE))

        d.passPriorityUntil(Step.PRECOMBAT_MAIN, activePlayer = d.player2)
        assertEquals(1, d.projected.getPower(elf))
        assertFalse(d.projected.hasKeyword(elf, Keyword.INDESTRUCTIBLE))
    }

    @Test fun canChooseBearAndLeaveFriendlyElvesUnchanged() {
        val d = customScenario(AndTheyShallKnowNoFear, "Llanowar Elves", "Grizzly Bears")
        val p = d.player1
        val bear = d.putCreatureOnBattlefield(p, "Grizzly Bears")
        val elf = d.putCreatureOnBattlefield(p, "Llanowar Elves")
        castAndChooseType(d, "Bear")
        assertEquals(3, d.projected.getPower(bear))
        assertTrue(d.projected.hasKeyword(bear, Keyword.INDESTRUCTIBLE))
        assertEquals(1, d.projected.getPower(elf))
        assertFalse(d.projected.hasKeyword(elf, Keyword.INDESTRUCTIBLE))
    }
}
