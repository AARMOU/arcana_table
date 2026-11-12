package com.arcanatable.customcards

import com.arcanatable.customcards.cards.RiseOfTheEldrazi
import com.arcanatable.customcards.support.*
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import org.junit.Assert.*
import org.junit.Test

class RiseOfTheEldraziIndependentCertificationTest {
    private val name = "Rise of the Eldrazi"

    @Test
    fun nineGenericAndThreeColorlessManaAreRequiredToCast() {
        val d = customScenario(RiseOfTheEldrazi, "Sol Ring")
        val p = d.player1
        val permanent = d.putPermanentOnBattlefield(d.player2, "Sol Ring")
        val spell = d.putCardInHand(p, name)
        d.giveMana(p, Color.BLUE, 9)
        d.giveColorlessMana(p, 2)

        assertNotNull(
            d.castSpellWithTargets(
                p,
                spell,
                listOf(ChosenTarget.Permanent(permanent), ChosenTarget.Player(d.player2)),
            ).error,
        )
        assertTrue(d.getHand(p).contains(spell))
        assertEquals(9, d.manaPool(p).blue)
        assertEquals(2, d.manaPool(p).colorless)

        d.giveColorlessMana(p, 1)
        assertNull(
            d.castSpellWithTargets(
                p,
                spell,
                listOf(ChosenTarget.Permanent(permanent), ChosenTarget.Player(d.player2)),
            ).error,
        )
        assertEquals(0, d.manaPool(p).blue)
        assertEquals(0, d.manaPool(p).colorless)
        d.resolveAll()
        assertTrue(d.zoneNames(p, Zone.EXILE).contains(name))
    }

    @Test
    fun coloredManaCannotPayTheThreeColorlessSymbols() {
        val d = customScenario(RiseOfTheEldrazi, "Sol Ring")
        val p = d.player1
        val permanent = d.putPermanentOnBattlefield(d.player2, "Sol Ring")
        val spell = d.putCardInHand(p, name)
        d.giveMana(p, Color.BLUE, 12)

        assertNotNull(
            d.castSpellWithTargets(
                p,
                spell,
                listOf(ChosenTarget.Permanent(permanent), ChosenTarget.Player(d.player2)),
            ).error,
        )

        assertTrue(d.getHand(p).contains(spell))
        assertEquals(12, d.manaPool(p).blue)
        assertEquals(0, d.manaPool(p).colorless)
        assertEquals(0, d.stackSize)
        assertTrue(d.getPermanents(d.player2).contains(permanent))
    }

    @Test
    fun resolutionDestroysTheTargetDrawsFourExilesRiseAndGrantsExactlyOneExtraTurn() {
        val draws = listOf("Plains", "Island", "Grizzly Bears", "Mountain")
        val d = customScenario(RiseOfTheEldrazi, "Sol Ring", *draws.toTypedArray())
        val p = d.player1
        val opponent = d.player2
        val permanent = d.putPermanentOnBattlefield(opponent, "Sol Ring")
        draws.forEach { d.putCardOnTopOfLibrary(opponent, it) }
        val spell = d.putCardInHand(p, name)
        val opponentHandBefore = d.getHand(opponent).size
        d.giveColorlessMana(p, 12)

        assertNull(
            d.castSpellWithTargets(
                p,
                spell,
                listOf(ChosenTarget.Permanent(permanent), ChosenTarget.Player(opponent)),
            ).error,
        )
        d.resolveAll()

        assertFalse(d.getPermanents(opponent).contains(permanent))
        assertTrue(d.zoneNames(opponent, Zone.GRAVEYARD).contains("Sol Ring"))
        assertEquals(opponentHandBefore + 4, d.getHand(opponent).size)
        assertTrue(d.zoneNames(opponent, Zone.HAND).containsAll(draws))
        assertTrue(d.zoneNames(p, Zone.EXILE).contains(name))
        assertFalse(d.zoneNames(p, Zone.GRAVEYARD).contains(name))

        val playerHandAfterResolution = d.getHand(p).size
        d.passPriorityUntil(Step.END, activePlayer = p)
        d.passPriorityUntil(Step.PRECOMBAT_MAIN, activePlayer = p)
        assertEquals(p, d.activePlayer)
        assertEquals(playerHandAfterResolution + 1, d.getHand(p).size)
        assertEquals(opponentHandBefore + 4, d.getHand(opponent).size)

        d.passPriorityUntil(Step.END, activePlayer = p)
        d.passPriorityUntil(Step.PRECOMBAT_MAIN, activePlayer = opponent)
        assertEquals(opponent, d.activePlayer)
        assertEquals(opponentHandBefore + 5, d.getHand(opponent).size)
    }

    @Test
    fun bothTargetsMustMatchTheirRequiredTypes() {
        val d = customScenario(RiseOfTheEldrazi, "Sol Ring")
        val p = d.player1
        val permanent = d.putPermanentOnBattlefield(d.player2, "Sol Ring")
        val spell = d.putCardInHand(p, name)
        d.giveColorlessMana(p, 12)

        assertNotNull(
            d.castSpellWithTargets(
                p,
                spell,
                listOf(ChosenTarget.Player(d.player2), ChosenTarget.Permanent(permanent)),
            ).error,
        )

        assertTrue(d.getHand(p).contains(spell))
        assertTrue(d.getPermanents(d.player2).contains(permanent))
        assertEquals(0, d.stackSize)
    }

    @Test
    fun riseOfTheEldraziCannotBeCountered() {
        val d = customScenario(RiseOfTheEldrazi, "Counterspell", "Sol Ring")
        val p = d.player1
        val opponent = d.player2
        val permanent = d.putPermanentOnBattlefield(opponent, "Sol Ring")
        val spell = d.putCardInHand(p, name)
        d.giveColorlessMana(p, 12)

        assertNull(
            d.castSpellWithTargets(
                p,
                spell,
                listOf(ChosenTarget.Permanent(permanent), ChosenTarget.Player(opponent)),
            ).error,
        )
        assertNull(d.passPriority(p).error)

        val counterspell = d.putCardInHand(opponent, "Counterspell")
        d.giveMana(opponent, Color.BLUE, 2)
        assertNull(
            d.castSpellWithTargets(
                opponent,
                counterspell,
                listOf(ChosenTarget.Spell(spell)),
            ).error,
        )
        assertEquals(2, d.stackSize)
        d.resolveAll()

        assertTrue(d.zoneNames(p, Zone.EXILE).contains(name))
        assertTrue(d.zoneNames(opponent, Zone.GRAVEYARD).contains("Sol Ring"))
        assertEquals(0, d.stackSize)
    }

    @Test
    fun sorceryCannotBeCastDuringAnOpponentsTurn() {
        val d = customScenario(RiseOfTheEldrazi, "Sol Ring")
        val p = d.player1
        val opponent = d.player2
        val permanent = d.putPermanentOnBattlefield(opponent, "Sol Ring")
        val spell = d.putCardInHand(p, name)
        d.passPriorityUntil(Step.PRECOMBAT_MAIN, activePlayer = opponent)
        assertNull(d.passPriority(opponent).error)
        d.giveColorlessMana(p, 12)

        assertNotNull(
            d.castSpellWithTargets(
                p,
                spell,
                listOf(ChosenTarget.Permanent(permanent), ChosenTarget.Player(opponent)),
            ).error,
        )

        assertTrue(d.getHand(p).contains(spell))
        assertTrue(d.getPermanents(opponent).contains(permanent))
        assertEquals(0, d.stackSize)
    }

    @Test
    fun sorceryCannotBeCastDuringItsControllersEndStep() {
        val d = customScenario(RiseOfTheEldrazi, "Sol Ring")
        val p = d.player1
        val opponent = d.player2
        val permanent = d.putPermanentOnBattlefield(opponent, "Sol Ring")
        val spell = d.putCardInHand(p, name)
        d.giveColorlessMana(p, 12)
        d.passPriorityUntil(Step.END, activePlayer = p)

        assertNotNull(
            d.castSpellWithTargets(
                p,
                spell,
                listOf(ChosenTarget.Permanent(permanent), ChosenTarget.Player(opponent)),
            ).error,
        )

        assertTrue(d.getHand(p).contains(spell))
        assertTrue(d.getPermanents(opponent).contains(permanent))
        assertEquals(0, d.stackSize)
    }

    @Test
    fun sorceryCannotBeCastWhileAnotherSpellIsOnTheStack() {
        val d = customScenario(RiseOfTheEldrazi, "Shock", "Sol Ring", "Grizzly Bears")
        val p = d.player1
        val opponent = d.player2
        val permanent = d.putPermanentOnBattlefield(opponent, "Sol Ring")
        val creature = d.putCreatureOnBattlefield(opponent, "Grizzly Bears")
        val shock = d.putCardInHand(p, "Shock")
        d.giveMana(p, Color.RED)
        assertNull(d.castSpell(p, shock, listOf(creature)).error)

        val spell = d.putCardInHand(p, name)
        d.giveColorlessMana(p, 12)
        assertNotNull(
            d.castSpellWithTargets(
                p,
                spell,
                listOf(ChosenTarget.Permanent(permanent), ChosenTarget.Player(opponent)),
            ).error,
        )

        assertTrue(d.getHand(p).contains(spell))
        assertEquals(listOf("Shock"), d.getStackSpellNames())
        assertTrue(d.getPermanents(opponent).contains(permanent))
    }
}
