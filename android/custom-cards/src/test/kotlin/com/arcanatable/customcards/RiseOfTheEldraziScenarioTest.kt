package com.arcanatable.customcards

import com.arcanatable.customcards.cards.RiseOfTheEldrazi
import com.arcanatable.customcards.support.*
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.scripting.Duration
import com.wingedsheep.sdk.scripting.filters.unified.TargetFilter
import com.wingedsheep.sdk.scripting.references.Player
import com.wingedsheep.sdk.scripting.targets.EffectTarget
import org.junit.Assert.*
import org.junit.Test

class RiseOfTheEldraziScenarioTest {
    @Test
    fun destroysTargetPermanentDrawsFourTakesAnExtraTurnAndExilesItself() {
        val d = customScenario(RiseOfTheEldrazi, "Grizzly Bears")
        val p = d.player1
        val opponent = d.player2
        val spell = d.putCardInHand(p, "Rise of the Eldrazi")
        val land = d.putPermanentOnBattlefield(opponent, "Forest")
        val opponentHandBefore = d.getHand(opponent).size
        val turnBefore = d.state.turnNumber
        giveRiseMana(d, p)

        assertTrue(RiseOfTheEldrazi.script.cantBeCountered)
        assertNull(d.castSpell(p, spell, listOf(land, opponent)).error)
        d.resolveAll()

        assertNull(d.findPermanent(opponent, "Forest"))
        assertEquals(opponentHandBefore + 4, d.getHand(opponent).size)
        assertTrue(d.zoneNames(p, Zone.EXILE).contains("Rise of the Eldrazi"))

        d.passPriorityUntil(Step.UPKEEP, activePlayer = p)
        assertEquals(p, d.activePlayer)
        assertEquals(turnBefore + 1, d.state.turnNumber)
    }

    @Test
    fun requiresThreeTrueColorlessManaAndBothTargetKinds() {
        val d = customScenario(RiseOfTheEldrazi, "Grizzly Bears")
        val p = d.player1
        val opponent = d.player2
        val spell = d.putCardInHand(p, "Rise of the Eldrazi")
        val permanent = d.putCreatureOnBattlefield(opponent, "Grizzly Bears")
        d.giveMana(p, Color.WHITE, 12)

        assertEquals("{9}{C}{C}{C}", RiseOfTheEldrazi.manaCost)
        assertNotNull(d.castSpell(p, spell, listOf(permanent, opponent)).error)

        d.giveColorlessMana(p, 3)
        assertNull(d.castSpell(p, spell, listOf(permanent, opponent)).error)
        d.resolveAll()
    }

    @Test
    fun rejectsPlayerAsPermanentTarget() {
        val d = customScenario(RiseOfTheEldrazi, "Grizzly Bears")
        val p = d.player1
        val spell = d.putCardInHand(p, "Rise of the Eldrazi")
        giveRiseMana(d, p)

        assertNotNull(d.castSpell(p, spell, listOf(d.player2, p)).error)
    }

    @Test
    fun drawsForPlayerWhenPermanentTargetBecomesIllegal() {
        val d = customScenario(RiseOfTheEldrazi, "Grizzly Bears")
        val p = d.player1
        val opponent = d.player2
        d.registerCard(bouncePermanent())
        val spell = d.putCardInHand(p, "Rise of the Eldrazi")
        val victim = d.putCreatureOnBattlefield(opponent, "Grizzly Bears")
        val playerHandBefore = d.getHand(p).size
        giveRiseMana(d, p)
        assertNull(d.castSpell(p, spell, listOf(victim, p)).error)

        assertNull(d.passPriority(p).error)
        val bounce = d.putCardInHand(opponent, "Test Permanent Bounce")
        d.giveMana(opponent, Color.BLUE)
        assertNull(d.castSpell(opponent, bounce, listOf(victim)).error)
        d.resolveAll()

        assertTrue(d.zoneNames(opponent, Zone.HAND).contains("Grizzly Bears"))
        assertEquals(playerHandBefore + 3, d.getHand(p).size)
        assertTrue(d.zoneNames(p, Zone.EXILE).contains("Rise of the Eldrazi"))
    }

    @Test
    fun destroysPermanentAndTakesExtraTurnWhenPlayerTargetBecomesIllegal() {
        val d = customScenario(RiseOfTheEldrazi, "Grizzly Bears")
        val p = d.player1
        val opponent = d.player2
        d.registerCard(grantShroudToCaster())
        val spell = d.putCardInHand(p, "Rise of the Eldrazi")
        val victim = d.putCreatureOnBattlefield(opponent, "Grizzly Bears")
        val opponentLibraryBefore = d.state.getZone(opponent, Zone.LIBRARY).size
        val turnBefore = d.state.turnNumber
        giveRiseMana(d, p)
        assertNull(d.castSpell(p, spell, listOf(victim, opponent)).error)

        assertNull(d.passPriority(p).error)
        val shroud = d.putCardInHand(opponent, "Test Shroud")
        d.giveMana(opponent, Color.BLUE)
        assertNull(d.castSpell(opponent, shroud).error)
        d.resolveAll()

        assertNull(d.findPermanent(opponent, "Grizzly Bears"))
        assertEquals(opponentLibraryBefore, d.state.getZone(opponent, Zone.LIBRARY).size)
        assertTrue(d.zoneNames(p, Zone.EXILE).contains("Rise of the Eldrazi"))

        d.passPriorityUntil(Step.UPKEEP, activePlayer = p)
        assertEquals(p, d.activePlayer)
        assertEquals(turnBefore + 1, d.state.turnNumber)
    }

    @Test
    fun doesNotResolveOrTakeExtraTurnWhenBothTargetsBecomeIllegal() {
        val d = customScenario(RiseOfTheEldrazi, "Grizzly Bears")
        val p = d.player1
        val opponent = d.player2
        d.registerCard(bouncePermanent())
        d.registerCard(grantShroudToCaster())
        val spell = d.putCardInHand(p, "Rise of the Eldrazi")
        val victim = d.putCreatureOnBattlefield(opponent, "Grizzly Bears")
        val opponentLibraryBefore = d.state.getZone(opponent, Zone.LIBRARY).size
        val turnBefore = d.state.turnNumber
        giveRiseMana(d, p)
        assertNull(d.castSpell(p, spell, listOf(victim, opponent)).error)

        assertNull(d.passPriority(p).error)
        val bounce = d.putCardInHand(opponent, "Test Permanent Bounce")
        d.giveMana(opponent, Color.BLUE, 2)
        assertNull(d.castSpell(opponent, bounce, listOf(victim)).error)
        assertNull(d.passPriority(p).error)
        val shroud = d.putCardInHand(opponent, "Test Shroud")
        assertNull(d.castSpell(opponent, shroud).error)
        d.resolveAll()

        assertTrue(d.zoneNames(opponent, Zone.HAND).contains("Grizzly Bears"))
        assertEquals(opponentLibraryBefore, d.state.getZone(opponent, Zone.LIBRARY).size)
        assertTrue(d.zoneNames(p, Zone.GRAVEYARD).contains("Rise of the Eldrazi"))
        assertFalse(d.zoneNames(p, Zone.EXILE).contains("Rise of the Eldrazi"))

        d.passPriorityUntil(Step.UPKEEP, activePlayer = opponent)
        assertEquals(opponent, d.activePlayer)
        assertEquals(turnBefore + 1, d.state.turnNumber)
    }

    private fun giveRiseMana(d: GameTestDriver, player: com.wingedsheep.sdk.model.EntityId) {
        d.giveMana(player, Color.WHITE, 9)
        d.giveColorlessMana(player, 3)
    }

    private fun bouncePermanent() = card("Test Permanent Bounce") {
        manaCost = "{U}"
        typeLine = "Instant"
        spell {
            val permanent = target(TargetFilter.Permanent)
            effect = Effects.ReturnToHand(permanent)
        }
    }

    private fun grantShroudToCaster() = card("Test Shroud") {
        manaCost = "{U}"
        typeLine = "Instant"
        spell {
            effect = Effects.GrantShroud(
                EffectTarget.PlayerRef(Player.You),
                Duration.EndOfTurn,
            )
        }
    }
}
