package com.arcanatable.customcards

import com.arcanatable.customcards.cards.BenthicAnomaly
import com.arcanatable.customcards.support.*
import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.engine.state.components.identity.TokenComponent
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.sdk.core.*
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.tooling.CardLoader
import org.junit.Assert.*
import org.junit.Test

/**
 * Benthic Anomaly (M3C #46), Oracle: "Devoid / When you cast this spell, for each opponent, choose a
 * creature that player controls. Create a token that's a copy of one of those creatures, except its
 * power is equal to the total power of those creatures, its toughness is equal to the total
 * toughness of those creatures, and it's a colorless Eldrazi creature."
 *
 * Covers the cast trigger resolving before (and without) the spell, choices (not targets) per
 * opponent at 2 and 3 seats, an opponent with no creatures, projected totals, which creature is
 * copied, and the copy exceptions (colorless, Eldrazi creature instead of other types/subtypes,
 * supertypes kept, counters not copied, ETB abilities of the copy trigger). Engine patch 0033.
 */
class BenthicAnomalyScenarioTest {
    private fun setup(seats: Int = 2): Pair<GameTestDriver, List<EntityId>> {
        val d = CardPool.driver(
            "Grizzly Bears", "Hill Giant", "Gladecover Scout", "Isamaru, Hound of Konda",
            "Elvish Visionary", "Counterspell", "Zombify",
        )
        d.registerCards(listOf(CardLoader.fromJsonPreservingIds(exportLine(BenthicAnomaly))))
        val ps = d.initMultiplayer(List(seats) { Deck.of("Plains" to 40) })
        d.passPriorityUntil(Step.PRECOMBAT_MAIN)
        return d to ps
    }

    private fun cast(d: GameTestDriver, p: EntityId): EntityId {
        val c = d.putCardInHand(p, "Benthic Anomaly")
        d.giveColorlessMana(p, 6); d.giveMana(p, Color.BLUE)
        assertNull(d.castSpell(p, c).error)
        return c
    }

    /** Every seat passes until the top stack object resolves or a decision is asked. */
    private fun GameTestDriver.resolveTop() {
        val size = state.stack.size
        repeat(8) {
            if (state.pendingDecision != null || state.stack.size < size) return
            passPriority(state.priorityPlayerId!!)
        }
    }

    private fun tokens(d: GameTestDriver, p: EntityId) =
        d.getCreatures(p).filter { d.state.getEntity(it)?.has<TokenComponent>() == true }

    private fun GameTestDriver.addPlusCounters(id: EntityId, n: Int) =
        replaceState(state.updateEntity(id) { it.with(CountersComponent(mapOf(CounterType.PLUS_ONE_PLUS_ONE to n))) })

    @Test fun twoPlayersCopiesTheChosenCreatureAsAColorlessEldraziBeforeBenthicResolves() {
        val (d, ps) = setup()
        val giant = d.putCreatureOnBattlefield(ps[1], "Hill Giant")
        val benthic = cast(d, ps[0])
        d.resolveTop() // the cast trigger is on top: it resolves first
        assertTrue("Benthic is still on the stack", benthic in d.state.stack)
        val token = tokens(d, ps[0]).single()
        assertEquals("Hill Giant", d.getCardName(token))
        assertEquals(3, d.projected.getPower(token)); assertEquals(3, d.projected.getToughness(token))
        assertTrue(d.projected.getColors(token).isEmpty())
        assertEquals(setOf("CREATURE"), d.projected.getTypes(token))
        assertEquals(setOf("Eldrazi"), d.projected.getSubtypes(token))
        assertFalse(d.projected.hasSubtype(token, "Giant"))
        assertEquals("the original is untouched", ps[1], d.projected.getController(giant))
        assertTrue(d.projected.getColors(giant).isNotEmpty())
        d.resolveAll()
        assertEquals(1, d.permanentsNamed(ps[0], "Benthic Anomaly").size)
        assertTrue("Devoid", d.projected.getColors(d.permanentsNamed(ps[0], "Benthic Anomaly").single()).isEmpty())
    }

    @Test fun threePlayersTotalsBothChoicesHexproofDoesNotStopItAndCountersAreNotCopied() {
        val (d, ps) = setup(3)
        d.putCreatureOnBattlefield(ps[1], "Grizzly Bears")
        val scout = d.putCreatureOnBattlefield(ps[1], "Gladecover Scout")
        val giant = d.putCreatureOnBattlefield(ps[2], "Hill Giant")
        d.addPlusCounters(giant, 1)
        d.addPlusCounters(scout, 1)
        cast(d, ps[0])
        d.resolveTop()
        // ps[1] has two creatures: Benthic's controller chooses (hexproof Scout is choosable).
        val perOpponent = d.pendingDecision as SelectCardsDecision
        assertEquals(ps[0], perOpponent.playerId)
        assertEquals(2, perOpponent.options.size)
        assertNull(d.submitCardSelection(ps[0], listOf(scout)).error)
        // Then which of the two chosen creatures to copy.
        val which = d.pendingDecision as SelectCardsDecision
        assertEquals(setOf(scout, giant), which.options.toSet())
        assertNull(d.submitCardSelection(ps[0], listOf(scout)).error)
        val token = tokens(d, ps[0]).single()
        assertEquals("Gladecover Scout", d.getCardName(token))
        // Scout 1/1 +1 counter = 2/2, Giant 3/3 +1 counter = 4/4: totals 6/6; the token has no counters.
        assertEquals(6, d.projected.getPower(token)); assertEquals(6, d.projected.getToughness(token))
        assertNull(d.state.getEntity(token)?.get<CountersComponent>()?.counters?.values?.firstOrNull { it > 0 })
        assertTrue("copied abilities kept", d.projected.hasKeyword(token, Keyword.HEXPROOF))
        assertTrue(d.projected.getColors(token).isEmpty())
        assertEquals(setOf("Eldrazi"), d.projected.getSubtypes(token))
        d.resolveAll()
    }

    @Test fun threePlayersCopyTheOtherChosenCreatureWithTheSameTotals() {
        val (d, ps) = setup(3)
        val bears = d.putCreatureOnBattlefield(ps[1], "Grizzly Bears")
        val giant = d.putCreatureOnBattlefield(ps[2], "Hill Giant")
        cast(d, ps[0])
        d.resolveTop()
        val which = d.pendingDecision as SelectCardsDecision
        assertEquals(setOf(bears, giant), which.options.toSet())
        assertNull(d.submitCardSelection(ps[0], listOf(giant)).error)
        val token = tokens(d, ps[0]).single()
        assertEquals("Hill Giant", d.getCardName(token))
        assertEquals(5, d.projected.getPower(token)); assertEquals(5, d.projected.getToughness(token))
        d.resolveAll()
    }

    @Test fun anOpponentWithNoCreaturesAddsNothing() {
        val (d, ps) = setup(3)
        d.putCreatureOnBattlefield(ps[2], "Grizzly Bears")
        cast(d, ps[0])
        d.resolveTop()
        assertNull("one chosen creature: no prompt", d.pendingDecision)
        val token = tokens(d, ps[0]).single()
        assertEquals("Grizzly Bears", d.getCardName(token))
        assertEquals(2, d.projected.getPower(token)); assertEquals(2, d.projected.getToughness(token))
        d.resolveAll()
    }

    @Test fun noOpponentCreaturesMeansNoTokenButBenthicStillResolves() {
        val (d, ps) = setup(3)
        d.putCreatureOnBattlefield(ps[0], "Grizzly Bears") // your own creatures are never chosen
        cast(d, ps[0])
        d.resolveAll()
        assertTrue(tokens(d, ps[0]).isEmpty())
        assertEquals(1, d.permanentsNamed(ps[0], "Benthic Anomaly").size)
        assertEquals(2, d.getCreatures(ps[0]).size)
    }

    @Test fun supertypesAreKeptAndOtherTypesReplaced() {
        val (d, ps) = setup()
        d.putCreatureOnBattlefield(ps[1], "Isamaru, Hound of Konda")
        cast(d, ps[0])
        d.resolveTop()
        val token = tokens(d, ps[0]).single()
        assertTrue(d.projected.isLegendary(token))
        assertEquals(setOf("LEGENDARY", "CREATURE"), d.projected.getTypes(token))
        assertEquals(setOf("Eldrazi"), d.projected.getSubtypes(token))
        assertFalse(d.projected.hasSubtype(token, "Dog"))
        d.resolveAll()
    }

    @Test fun theCopysEnterAbilityTriggers() {
        val (d, ps) = setup()
        d.putCreatureOnBattlefield(ps[1], "Elvish Visionary")
        cast(d, ps[0])
        val hand = d.getHandSize(ps[0])
        d.resolveAll()
        assertEquals("the token's copied ETB draws a card", hand + 1, d.getHandSize(ps[0]))
        assertEquals("Elvish Visionary", d.getCardName(tokens(d, ps[0]).single()))
    }

    @Test fun counteringBenthicDoesNotStopTheCastTrigger() {
        val (d, ps) = setup()
        d.putCreatureOnBattlefield(ps[1], "Hill Giant")
        val benthic = cast(d, ps[0])
        d.passPriority(ps[0])
        val counter = d.putCardInHand(ps[1], "Counterspell")
        d.giveMana(ps[1], Color.BLUE, 2)
        assertNull(d.castSpellWithTargets(ps[1], counter, listOf(ChosenTarget.Spell(benthic))).error)
        d.resolveAll()
        assertTrue(benthic in d.state.getZone(ps[0], Zone.GRAVEYARD))
        assertEquals(3, d.projected.getPower(tokens(d, ps[0]).single()))
    }

    @Test fun benthicPutOntoTheBattlefieldWithoutBeingCastMakesNoToken() {
        val (d, ps) = setup()
        d.putCreatureOnBattlefield(ps[1], "Hill Giant")
        val benthic = d.putCardInGraveyard(ps[0], "Benthic Anomaly")
        val spell = d.putCardInHand(ps[0], "Zombify")
        d.giveColorlessMana(ps[0], 3); d.giveMana(ps[0], Color.BLACK)
        assertNull(d.castSpellWithTargets(ps[0], spell, listOf(ChosenTarget.Card(benthic, ps[0], Zone.GRAVEYARD))).error)
        d.resolveAll()
        assertEquals(1, d.permanentsNamed(ps[0], "Benthic Anomaly").size)
        assertTrue(tokens(d, ps[0]).isEmpty())
    }
}
