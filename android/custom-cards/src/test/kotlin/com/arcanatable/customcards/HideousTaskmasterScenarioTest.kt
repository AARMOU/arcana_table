package com.arcanatable.customcards

import com.arcanatable.customcards.cards.HideousTaskmaster
import com.arcanatable.customcards.support.*
import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.engine.state.components.identity.ControllerComponent
import com.wingedsheep.sdk.core.*
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.tooling.CardLoader
import org.junit.Assert.*
import org.junit.Test

/**
 * Hideous Taskmaster (M3C #57), Oracle: "Devoid / When you cast this spell, for each opponent, gain
 * control of up to one target creature that player controls until end of turn. Untap those
 * creatures. They gain trample, haste, and annihilator 1 until end of turn. / Trample, haste,
 * annihilator 1."
 *
 * One optional target per opponent, chosen as the cast trigger goes on the stack above the spell; it
 * resolves even if the spell is countered. A target no longer controlled by the opponent it was
 * chosen for is illegal on resolution (CR 608.2b; engine patch 0032) and the other targets still
 * resolve. Covers 2-player and 3-player tables.
 */
class HideousTaskmasterScenarioTest {
    private fun setup(seats: Int = 2): Pair<GameTestDriver, List<EntityId>> {
        val d = CardPool.driver("Grizzly Bears", "Hill Giant", "Counterspell", "Zombify")
        d.registerCards(listOf(CardLoader.fromJsonPreservingIds(exportLine(HideousTaskmaster))))
        val ps = d.initMultiplayer(List(seats) { Deck.of("Plains" to 40) })
        d.passPriorityUntil(Step.PRECOMBAT_MAIN)
        return d to ps
    }
    private fun cast(d: GameTestDriver, p: EntityId): EntityId {
        val c = d.putCardInHand(p, "Hideous Taskmaster")
        d.giveColorlessMana(p, 6); d.giveMana(p, Color.RED)
        assertNull(d.castSpell(p, c).error)
        return c
    }

    private fun controlled(d: GameTestDriver, p: EntityId) = d.state.getBattlefield().filter { d.projected.getController(it) == p }

    @Test fun stealsOneCreaturePerOpponentOnCastUntapsAndGrantsKeywords() {
        val (d, ps) = setup(3)
        val a = d.putCreatureOnBattlefield(ps[1], "Grizzly Bears")
        val b = d.putCreatureOnBattlefield(ps[2], "Hill Giant")
        d.tapPermanent(a); d.tapPermanent(b)
        val card = cast(d, ps[0])
        assertTrue(d.pendingDecision is ChooseTargetsDecision)
        assertNull(d.submitTargetSelection(ps[0], listOf(a, b)).error)
        repeat(20) {
            if (a !in controlled(d, ps[0])) d.bothPass()
        }
        assertTrue("Cast trigger resolves before the creature spell", card in d.state.stack)
        for (id in listOf(a, b)) {
            assertTrue(id in controlled(d, ps[0])); assertFalse(d.isTapped(id))
            assertTrue(d.projected.hasKeyword(id, Keyword.HASTE))
            assertTrue(d.projected.hasKeyword(id, Keyword.TRAMPLE))
        }
        d.resolveAll()
        assertTrue(d.projected.getColors(card).isEmpty())
        assertTrue(d.projected.hasKeyword(card, Keyword.HASTE))
        assertTrue(d.projected.hasKeyword(card, Keyword.TRAMPLE))
    }

    @Test fun rejectsTwoCreaturesFromTheSameOpponentAndRejectsOwnCreature() {
        val (d, ps) = setup(3)
        val a = d.putCreatureOnBattlefield(ps[1], "Grizzly Bears")
        val b = d.putCreatureOnBattlefield(ps[1], "Hill Giant")
        val own = d.putCreatureOnBattlefield(ps[0], "Grizzly Bears")
        cast(d, ps[0])
        assertNotNull(d.submitTargetSelection(ps[0], listOf(a, b)).error)
        assertNotNull(d.submitTargetSelection(ps[0], listOf(own)).error)
        assertNull(d.submitTargetSelection(ps[0], listOf(a)).error)
        d.resolveAll()
        assertTrue(a in controlled(d, ps[0])); assertTrue(b in controlled(d, ps[1]))
    }

    @Test fun zeroTargetsIsLegalAndDoesNotStealAnything() {
        val (d, ps) = setup()
        val bear = d.putCreatureOnBattlefield(ps[1], "Grizzly Bears")
        cast(d, ps[0])
        assertNull(d.submitTargetSelection(ps[0], emptyList()).error)
        d.resolveAll()
        assertTrue(bear in controlled(d, ps[1]))
        assertEquals(1, d.permanentsNamed(ps[0], "Hideous Taskmaster").size)
    }

    @Test fun stolenCreatureHasRealAnnihilatorAndAllTemporaryGrantsExpire() {
        val (d, ps) = setup()
        val bear = d.putCreatureOnBattlefield(ps[1], "Grizzly Bears")
        d.putLandOnBattlefield(ps[1], "Forest")
        d.putLandOnBattlefield(ps[1], "Forest")
        cast(d, ps[0]); d.resolveAll(Choices(targets = listOf(bear)))
        d.passPriorityUntil(Step.DECLARE_ATTACKERS)
        assertNull("Granted haste allows attacking now", d.declareAttackers(ps[0], listOf(bear), ps[1]).error)
        d.resolveAll()
        assertEquals("Defender sacrifices exactly one permanent before blockers", 1, controlled(d, ps[1]).size)
        assertEquals(1, d.zoneNames(ps[1], Zone.GRAVEYARD).count { it == "Forest" })
        d.passPriorityUntil(Step.UPKEEP, activePlayer = ps[1])
        assertTrue("Control returns", bear in controlled(d, ps[1]))
        assertFalse(d.projected.hasKeyword(bear, Keyword.HASTE))
        assertFalse(d.projected.hasKeyword(bear, Keyword.TRAMPLE))
        // The granted annihilator ended too: attacking with it on its owner's turn costs nothing.
        val mine = controlled(d, ps[0]).toSet()
        d.passPriorityUntil(Step.DECLARE_ATTACKERS, activePlayer = ps[1])
        assertNull(d.declareAttackers(ps[1], listOf(bear), ps[0]).error)
        assertNull("no annihilator trigger asks for a sacrifice", d.pendingDecision)
        assertTrue(d.state.stack.isEmpty())
        assertEquals(mine, controlled(d, ps[0]).toSet())
    }

    @Test fun twoOpponentsTwoTargetsOnlyTheMovedOneIsIllegal() {
        val (d, ps) = setup(3)
        val a = d.putCreatureOnBattlefield(ps[1], "Grizzly Bears")
        val b = d.putCreatureOnBattlefield(ps[2], "Hill Giant")
        cast(d, ps[0])
        assertNull(d.submitTargetSelection(ps[0], listOf(a, b)).error)
        // a was chosen for ps[1]; ps[2] now controls it, so it is still an opponent's creature but
        // no longer "a creature that player controls".
        d.replaceState(d.state.updateEntity(a) { it.with(ControllerComponent(ps[2])) })
        d.resolveAll()
        assertEquals(ps[2], d.projected.getController(a))
        assertFalse(d.projected.hasKeyword(a, Keyword.HASTE))
        assertTrue("the other opponent's target still resolves", b in controlled(d, ps[0]))
        assertTrue(d.projected.hasKeyword(b, Keyword.HASTE))
        assertTrue(d.projected.hasKeyword(b, Keyword.TRAMPLE))
        assertEquals(1, d.permanentsNamed(ps[0], "Hideous Taskmaster").size)
    }

    @Test fun twoOpponentsUpToOneEachAllowsJustOne() {
        val (d, ps) = setup(3)
        val a = d.putCreatureOnBattlefield(ps[1], "Grizzly Bears")
        val b = d.putCreatureOnBattlefield(ps[2], "Hill Giant")
        cast(d, ps[0])
        assertNull(d.submitTargetSelection(ps[0], listOf(b)).error)
        d.resolveAll()
        assertTrue(b in controlled(d, ps[0]))
        assertTrue(a in controlled(d, ps[1]))
    }

    @Test fun stolenCreatureAnnihilatorHitsTheOpponentItAttacksInMultiplayer() {
        val (d, ps) = setup(3)
        val a = d.putCreatureOnBattlefield(ps[1], "Grizzly Bears")
        d.putLandOnBattlefield(ps[2], "Forest"); d.putLandOnBattlefield(ps[2], "Forest")
        val before1 = controlled(d, ps[1]).size
        cast(d, ps[0]); d.resolveAll(Choices(targets = listOf(a)))
        assertTrue(a in controlled(d, ps[0]))
        d.passPriorityUntil(Step.DECLARE_ATTACKERS)
        assertNull(d.declareAttackers(ps[0], listOf(a), ps[2]).error)
        d.resolveAll()
        assertEquals("the defending player sacrifices one", 1, controlled(d, ps[2]).size)
        assertEquals("the creature's owner is not the defending player", before1 - 1, controlled(d, ps[1]).size)
    }

    @Test fun taskmastersOwnAnnihilatorMakesTheDefenderSacrificeExactlyOne() {
        val (d, ps) = setup()
        d.putLandOnBattlefield(ps[1], "Forest"); d.putLandOnBattlefield(ps[1], "Forest")
        val c = cast(d, ps[0]); d.resolveAll()
        d.passPriorityUntil(Step.DECLARE_ATTACKERS)
        assertNull(d.declareAttackers(ps[0], listOf(c), ps[1]).error)
        d.resolveAll()
        assertEquals(1, controlled(d, ps[1]).size)
    }

    @Test fun reanimationDoesNotTriggerTheCastAbility() {
        val (d, ps) = setup()
        val bear = d.putCreatureOnBattlefield(ps[1], "Grizzly Bears")
        val taskmaster = d.putCardInGraveyard(ps[0], "Hideous Taskmaster")
        val spell = d.putCardInHand(ps[0], "Zombify")
        d.giveColorlessMana(ps[0], 3); d.giveMana(ps[0], Color.BLACK)
        assertNull(d.castSpellWithTargets(ps[0], spell, listOf(ChosenTarget.Card(taskmaster, ps[0], Zone.GRAVEYARD))).error)
        d.resolveAll()
        assertEquals(1, d.permanentsNamed(ps[0], "Hideous Taskmaster").size)
        assertTrue(bear in controlled(d, ps[1]))
        assertFalse(d.projected.hasKeyword(bear, Keyword.HASTE))
    }

    @Test fun counteringTheCreatureSpellDoesNotRemoveItsCastTrigger() {
        val (d, ps) = setup()
        val bear = d.putCreatureOnBattlefield(ps[1], "Grizzly Bears")
        val taskmaster = cast(d, ps[0])
        assertNull(d.submitTargetSelection(ps[0], listOf(bear)).error)
        d.passPriority(ps[0])
        val counter = d.putCardInHand(ps[1], "Counterspell")
        d.giveMana(ps[1], Color.BLUE, 2)
        assertNull(d.castSpellWithTargets(ps[1], counter, listOf(ChosenTarget.Spell(taskmaster))).error)
        d.resolveAll()
        assertTrue(taskmaster in d.state.getZone(ps[0], Zone.GRAVEYARD))
        assertTrue(bear in controlled(d, ps[0]))
        assertTrue(d.projected.hasKeyword(bear, Keyword.TRAMPLE))
    }

    @Test fun targetChangingToAnotherOpponentIsIllegalAtResolution() {
        val (d, ps) = setup(3)
        val bear = d.putCreatureOnBattlefield(ps[1], "Grizzly Bears")
        cast(d, ps[0])
        assertNull(d.submitTargetSelection(ps[0], listOf(bear)).error)
        // An intervening control effect: it no longer belongs to the opponent it was chosen for.
        d.replaceState(d.state.updateEntity(bear) { it.with(ControllerComponent(ps[2])) })
        assertEquals("Fixture transferred control before resolution", ps[2], d.projected.getController(bear))
        d.resolveAll()
        assertEquals("A different opponent's creature is no longer the chosen player's creature", ps[2], d.projected.getController(bear))
        assertFalse(d.projected.hasKeyword(bear, Keyword.HASTE))
    }

    @Test fun vanishedTargetDoesNotStopTheCreatureSpellResolving() {
        val (d, ps) = setup()
        val bear = d.putCreatureOnBattlefield(ps[1], "Grizzly Bears")
        cast(d, ps[0])
        assertNull(d.submitTargetSelection(ps[0], listOf(bear)).error)
        d.moveToExile(bear)
        d.resolveAll()
        assertEquals(1, d.permanentsNamed(ps[0], "Hideous Taskmaster").size)
        assertTrue(bear in d.state.getZone(ps[1], Zone.EXILE))
    }

    @Test fun colorlessDevoidDoesNotRemoveThePrintedRedManaRequirement() {
        val (d, ps) = setup()
        val c = d.putCardInHand(ps[0], "Hideous Taskmaster")
        d.giveColorlessMana(ps[0], 7)
        assertNotNull(d.castSpell(ps[0], c).error)
        assertTrue(c in d.state.getZone(ps[0], Zone.HAND))
        assertEquals(7, d.manaPool(ps[0]).colorless)
        d.giveMana(ps[0], Color.RED)
        assertNull(d.castSpell(ps[0], c).error)
        d.resolveAll()
        assertEquals(1, d.manaPool(ps[0]).colorless)
    }
}
