package com.arcanatable.customcards

// Written from WotC 40K Release Notes, not the worker implementation/tests.
// https://magic.wizards.com/en/news/feature/warhammer-40000-commander-release-notes-2022-09-19
import com.arcanatable.customcards.cards.BloodForTheBloodGod
import com.arcanatable.customcards.support.*
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.Deck
import org.junit.Assert.*
import org.junit.Test

class BloodForTheBloodGodIndependentCertificationTest {
    private fun payColors(d: GameTestDriver) {
        d.giveMana(d.player1, Color.BLACK, 2)
        d.giveMana(d.player1, Color.RED)
    }

    private fun wipe(d: GameTestDriver) {
        val wrath = d.putCardInHand(d.player1, "Day of Judgment")
        d.giveMana(d.player1, Color.WHITE, 2)
        d.giveColorlessMana(d.player1, 2)
        assertNull(d.castSpell(d.player1, wrath).error)
        d.resolveAll()
    }

    @Test
    fun fullCostDiscardsTheEntireOldHandDrawsEightDamagesOnlyOpponentAndExiles() {
        val d = customScenario(BloodForTheBloodGod, "Grizzly Bears")
        val marker = d.putCardInHand(d.player1, "Grizzly Bears")
        val oldHand = d.getHand(d.player1).toSet()
        val spell = d.putCardInHand(d.player1, "Blood for the Blood God!")
        payColors(d)
        d.giveColorlessMana(d.player1, 7)
        assertNotNull(d.castSpell(d.player1, spell).error)
        d.giveColorlessMana(d.player1, 1)
        assertNull(d.castSpell(d.player1, spell).error)
        d.resolveAll()
        assertTrue(d.getGraveyard(d.player1).containsAll(oldHand))
        assertTrue(d.getGraveyard(d.player1).contains(marker))
        assertEquals(8, d.getHandSize(d.player1))
        assertTrue(d.getHand(d.player1).none { it in oldHand })
        assertEquals(20, d.getLifeTotal(d.player1))
        assertEquals(12, d.getLifeTotal(d.player2))
        assertTrue(d.getExile(d.player1).contains(spell))
        assertFalse(d.getGraveyard(d.player1).contains(spell))
    }

    @Test
    fun countsCreatureDeathsFromBothPlayersIncludingTokensBeforeSpellEnteredHand() {
        val d = customScenario(BloodForTheBloodGod, "Grizzly Bears", "Day of Judgment")
        repeat(2) { d.putCreatureOnBattlefield(d.player1, "Grizzly Bears") }
        repeat(2) { d.putCreatureOnBattlefield(d.player2, "Grizzly Bears") }
        d.putToken(d.player2, "Grizzly Bears")
        wipe(d)
        assertTrue(d.getCreatures(d.player1).isEmpty())
        assertTrue(d.getCreatures(d.player2).isEmpty())
        val spell = d.putCardInHand(d.player1, "Blood for the Blood God!")
        payColors(d)
        d.giveColorlessMana(d.player1, 2)
        assertNotNull(d.castSpell(d.player1, spell).error)
        d.giveColorlessMana(d.player1, 1)
        assertNull(d.castSpell(d.player1, spell).error)
        d.resolveAll()
        assertEquals(8, d.getHandSize(d.player1))
        assertEquals(12, d.getLifeTotal(d.player2))
    }

    @Test
    fun nineDeathsCannotReduceTheColoredManaRequirement() {
        val d = customScenario(BloodForTheBloodGod, "Grizzly Bears", "Day of Judgment")
        repeat(9) { d.putCreatureOnBattlefield(d.player2, "Grizzly Bears") }
        wipe(d)
        val spell = d.putCardInHand(d.player1, "Blood for the Blood God!")
        d.giveMana(d.player1, Color.BLACK, 2)
        d.giveColorlessMana(d.player1, 1)
        assertNotNull(d.castSpell(d.player1, spell).error)
        d.giveMana(d.player1, Color.RED)
        assertNull(d.castSpell(d.player1, spell).error)
        d.resolveAll()
        assertTrue(d.getExile(d.player1).contains(spell))
    }

    @Test
    fun destroyedNoncreatureDoesNotDiscountTheSpell() {
        val d = customScenario(BloodForTheBloodGod, "Ichor Wellspring", "Shatter")
        val artifact = d.putPermanentOnBattlefield(d.player2, "Ichor Wellspring")
        val shatter = d.putCardInHand(d.player1, "Shatter")
        d.giveMana(d.player1, Color.RED)
        d.giveColorlessMana(d.player1, 1)
        assertNull(d.castSpell(d.player1, shatter, listOf(artifact)).error)
        d.resolveAll()
        assertTrue(d.getGraveyard(d.player2).contains(artifact))
        val spell = d.putCardInHand(d.player1, "Blood for the Blood God!")
        payColors(d)
        d.giveColorlessMana(d.player1, 7)
        assertNotNull(d.castSpell(d.player1, spell).error)
        d.giveColorlessMana(d.player1, 1)
        assertNull(d.castSpell(d.player1, spell).error)
    }

    @Test
    fun previousTurnsCreatureDeathsDoNotDiscountAnOpponentTurnInstant() {
        val d = customScenario(BloodForTheBloodGod, "Grizzly Bears", "Day of Judgment")
        repeat(3) { d.putCreatureOnBattlefield(d.player2, "Grizzly Bears") }
        wipe(d)
        d.passPriorityUntil(Step.PRECOMBAT_MAIN, activePlayer = d.player2)
        assertNull(d.passPriority(d.player2).error)
        val spell = d.putCardInHand(d.player1, "Blood for the Blood God!")
        payColors(d)
        d.giveColorlessMana(d.player1, 7)
        assertNotNull(d.castSpell(d.player1, spell).error)
        d.giveColorlessMana(d.player1, 1)
        assertNull(d.castSpell(d.player1, spell).error)
        d.resolveAll()
        assertEquals(12, d.getLifeTotal(d.player2))
    }

    @Test
    fun counteredSpellDoesNotDiscardDrawDealDamageOrExileItself() {
        val d = customScenario(BloodForTheBloodGod, "Counterspell")
        val spell = d.putCardInHand(d.player1, "Blood for the Blood God!")
        payColors(d)
        d.giveColorlessMana(d.player1, 8)
        assertNull(d.castSpell(d.player1, spell).error)
        val hand = d.getHand(d.player1).toSet()
        val counter = d.putCardInHand(d.player2, "Counterspell")
        d.giveMana(d.player2, Color.BLUE, 2)
        assertNull(d.passPriority(d.player1).error)
        assertNull(d.castSpellWithTargets(d.player2, counter,
            listOf(ChosenTarget.Spell(d.getTopOfStack()!!))).error)
        d.resolveAll()
        assertEquals(hand, d.getHand(d.player1).toSet())
        assertEquals(20, d.getLifeTotal(d.player2))
        assertTrue(d.getGraveyard(d.player1).contains(spell))
        assertFalse(d.getExile(d.player1).contains(spell))
    }

    @Test
    fun hitsEveryOpponentInAFourPlayerGameAndNeverTheCaster() {
        val d = CardPool.driver().apply {
            registerCard(BloodForTheBloodGod)
        }
        val players = d.initMultiplayer(List(4) { Deck.of("Plains" to 40) })
        d.passPriorityUntil(Step.PRECOMBAT_MAIN)
        val spell = d.putCardInHand(d.player1, "Blood for the Blood God!")
        payColors(d)
        d.giveColorlessMana(d.player1, 8)
        assertNull(d.castSpell(d.player1, spell).error)
        // bothPass() is intentionally a two-seat helper; pass each actual priority holder.
        repeat(80) {
            when {
                d.state.pendingDecision != null -> d.decide()
                d.state.stack.isNotEmpty() -> assertNull(d.passPriority(d.state.priorityPlayerId!!).error)
                else -> return@repeat
            }
        }
        assertTrue(d.state.stack.isEmpty())
        assertNull(d.state.pendingDecision)
        assertEquals(20, d.getLifeTotal(players.first()))
        players.drop(1).forEach { assertEquals(12, d.getLifeTotal(it)) }
        assertEquals(8, d.getHandSize(d.player1))
        assertTrue(d.getExile(d.player1).contains(spell))
    }
}
