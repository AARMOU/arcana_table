package com.arcanatable.customcards

import com.arcanatable.customcards.cards.VileRedeemer
import com.arcanatable.customcards.support.*
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.model.EntityId
import org.junit.Assert.*
import org.junit.Test

class VileRedeemerScenarioTest {
    private fun setup() = customScenario(VileRedeemer, "Grizzly Bears", "Kozilek's Return")

    private fun GameTestDriver.scions(p: EntityId) = getCreatures(p).filter { projected.hasSubtype(it, "Scion") }

    /** Two of my nontoken Bears, one of my tokens and one opposing Bear die to Kozilek's Return. */
    private fun GameTestDriver.wipe() {
        putCreatureOnBattlefield(player1, "Grizzly Bears")
        putCreatureOnBattlefield(player1, "Grizzly Bears")
        putToken(player1, "Grizzly Bears")
        putCreatureOnBattlefield(player2, "Grizzly Bears")
        val wipe = putCardInHand(player1, "Kozilek's Return")
        giveMana(player1, Color.RED); giveColorlessMana(player1, 2)
        assertNull(castSpell(player1, wipe).error)
        resolveAll()
        assertTrue(getCreatures(player1).isEmpty())
    }

    private fun GameTestDriver.castRedeemer(pay: Boolean) {
        val card = putCardInHand(player1, "Vile Redeemer")
        giveMana(player1, Color.GREEN); giveColorlessMana(player1, 3)
        assertNull(castSpell(player1, card).error)
        resolveAll(Choices(yes = pay))
    }

    @Test fun payingColorlessMakesAScionPerNontokenCreatureOfYoursThatDied() {
        val d = setup()
        d.wipe()
        d.castRedeemer(pay = true)
        assertEquals(1, d.permanentsNamed(d.player1, "Vile Redeemer").size)
        assertEquals("two nontoken Bears of mine; not the token, not the opponent's", 2, d.scions(d.player1).size)
        assertEquals(0, d.manaPool(d.player1).colorless)
    }

    @Test fun decliningMakesNoScions() {
        val d = setup()
        d.wipe()
        d.castRedeemer(pay = false)
        assertTrue(d.scions(d.player1).isEmpty())
        assertEquals(1, d.permanentsNamed(d.player1, "Vile Redeemer").size)
    }

    @Test fun nothingDiedMeansNoScionsAndFlashColorless() {
        val d = setup()
        val card = d.putCardInHand(d.player1, "Vile Redeemer")
        assertTrue(d.state.projectedState.getColors(card).isEmpty())
        d.castRedeemer(pay = true)
        assertTrue(d.scions(d.player1).isEmpty())
        val redeemer = d.permanentsNamed(d.player1, "Vile Redeemer").single()
        assertTrue(d.projected.hasKeyword(redeemer, Keyword.FLASH))
    }
}
