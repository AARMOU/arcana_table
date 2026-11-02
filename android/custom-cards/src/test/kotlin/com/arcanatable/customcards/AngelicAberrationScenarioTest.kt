package com.arcanatable.customcards

import com.arcanatable.customcards.cards.AngelicAberration
import com.arcanatable.customcards.support.*
import com.wingedsheep.engine.core.SelectCardsDecision
import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.engine.state.components.identity.TokenComponent
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.CounterType
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.scripting.SetBasePowerToughnessStatic
import com.wingedsheep.sdk.scripting.filters.unified.GroupFilter
import com.wingedsheep.sdk.tooling.CardLoader
import org.junit.Assert.*
import org.junit.Test

/**
 * Angelic Aberration (M3C 39): devoid, flying, vigilance; on entering, sacrifice any number of
 * creatures each with base power or toughness 1 or less, then create that many 4/4 colorless
 * Eldrazi Angel tokens with flying and vigilance. Uses engine patch 0028 (`basePowerOrToughnessAtMost`).
 */
class AngelicAberrationScenarioTest {

    private val threeOne = card("Aberration Test Three One") {
        manaCost = "{3}"; typeLine = "Creature — Test"; power = 3; toughness = 1
    }
    private val everythingOneOne = card("Aberration Test Shrink Field") {
        manaCost = "{3}"
        typeLine = "Enchantment"
        oracleText = "Creatures you control have base power and toughness 1/1."
        staticAbility { ability = SetBasePowerToughnessStatic(1, 1, GroupFilter.AllCreaturesYouControl) }
    }

    private fun game(): GameTestDriver =
        customScenario(AngelicAberration, "Grizzly Bears", "Ornithopter", "Llanowar Elves", "Wall of Stone", "Hill Giant").apply {
            registerCards(listOf(threeOne, everythingOneOne).map { CardLoader.fromJsonPreservingIds(exportLine(it)) })
        }

    /** Casts Angelic Aberration and resolves until the enter trigger asks what to sacrifice. */
    private fun castToSacrificeChoice(d: GameTestDriver): SelectCardsDecision {
        val p = d.player1
        d.giveColorlessMana(p, 5)
        d.giveMana(p, Color.WHITE)
        assertNull(d.castSpell(p, d.putCardInHand(p, "Angelic Aberration")).error)
        repeat(20) {
            val pending = d.state.pendingDecision
            if (pending is SelectCardsDecision) return pending
            assertNull("unexpected decision $pending", pending)
            assertTrue("the trigger never asked", d.state.stack.isNotEmpty())
            d.bothPass()
        }
        error("no sacrifice decision")
    }

    private fun choose(d: GameTestDriver, picks: List<EntityId>) {
        assertNull(d.submitCardSelection(d.player1, picks).error)
        d.resolveAll()
    }

    private fun angels(d: GameTestDriver): List<EntityId> = d.getPermanents(d.player1).filter {
        d.state.getEntity(it)?.has<TokenComponent>() == true &&
            d.projected.hasSubtype(it, "Eldrazi") && d.projected.hasSubtype(it, "Angel")
    }

    @Test fun isColorlessFlyingVigilant44() {
        val d = game()
        val aberration = d.putCreatureOnBattlefield(d.player1, "Angelic Aberration")
        assertTrue("devoid: no color", d.cardRegistry.getCard("Angelic Aberration")!!.colors.isEmpty())
        assertTrue(d.projected.getColors(aberration).isEmpty())
        assertTrue(d.projected.hasKeyword(aberration, Keyword.FLYING))
        assertTrue(d.projected.hasKeyword(aberration, Keyword.VIGILANCE))
        assertEquals(4, d.projected.getPower(aberration))
        assertEquals(4, d.projected.getToughness(aberration))
    }

    @Test fun offersOnlyYourCreaturesWithBasePowerOrToughnessAtMostOne() {
        val d = game()
        val p = d.player1
        val zeroTwo = d.putCreatureOnBattlefield(p, "Ornithopter")
        val oneOne = d.putCreatureOnBattlefield(p, "Llanowar Elves")
        val zeroEight = d.putCreatureOnBattlefield(p, "Wall of Stone")
        val threeOneId = d.putCreatureOnBattlefield(p, threeOne.name)
        val bears = d.putCreatureOnBattlefield(p, "Grizzly Bears")
        val giant = d.putCreatureOnBattlefield(p, "Hill Giant")
        val theirs = d.putCreatureOnBattlefield(d.player2, "Ornithopter")

        val decision = castToSacrificeChoice(d)
        val aberration = d.findPermanent(p, "Angelic Aberration")!!
        assertEquals(0, decision.minSelections)
        assertEquals(setOf(zeroTwo, oneOne, zeroEight, threeOneId), decision.options.toSet())
        for (no in listOf(bears, giant, theirs, aberration)) assertFalse(d.getCardName(no), no in decision.options)
    }

    @Test fun createsOneAngelPerCreatureActuallySacrificed() {
        val d = game()
        val p = d.player1
        val zeroTwo = d.putCreatureOnBattlefield(p, "Ornithopter")
        val threeOneId = d.putCreatureOnBattlefield(p, threeOne.name)
        val kept = d.putCreatureOnBattlefield(p, "Llanowar Elves")

        castToSacrificeChoice(d)
        choose(d, listOf(zeroTwo, threeOneId))

        assertTrue(zeroTwo in d.state.getZone(p, Zone.GRAVEYARD))
        assertTrue(threeOneId in d.state.getZone(p, Zone.GRAVEYARD))
        assertTrue("an unchosen eligible creature stays", kept in d.state.getBattlefield())
        val tokens = angels(d)
        assertEquals(2, tokens.size)
        for (t in tokens) {
            assertEquals(4, d.projected.getPower(t))
            assertEquals(4, d.projected.getToughness(t))
            assertTrue("colorless", d.projected.getColors(t).isEmpty())
            assertTrue(d.projected.hasKeyword(t, Keyword.FLYING))
            assertTrue(d.projected.hasKeyword(t, Keyword.VIGILANCE))
            assertEquals(p, d.getController(t))
        }
        assertNotNull("Aberration stays", d.findPermanent(p, "Angelic Aberration"))
    }

    @Test fun choosingNothingSacrificesNothingAndCreatesNoAngels() {
        val d = game()
        val p = d.player1
        val elves = d.putCreatureOnBattlefield(p, "Llanowar Elves")
        castToSacrificeChoice(d)
        choose(d, emptyList())
        assertTrue(elves in d.state.getBattlefield())
        assertEquals(0, angels(d).size)
    }

    @Test fun noEligibleCreaturesMeansNoAngels() {
        val d = game()
        val p = d.player1
        val bears = d.putCreatureOnBattlefield(p, "Grizzly Bears")
        d.putCreatureOnBattlefield(d.player2, "Llanowar Elves")
        d.giveColorlessMana(p, 5)
        d.giveMana(p, Color.WHITE)
        assertNull(d.castSpell(p, d.putCardInHand(p, "Angelic Aberration")).error)
        d.resolveAll()
        assertTrue(bears in d.state.getBattlefield())
        assertEquals(0, angels(d).size)
        assertEquals(1, d.permanentsNamed(d.player2, "Llanowar Elves").size)
    }

    @Test fun baseNotCurrentStatsDecide() {
        val d = game()
        val p = d.player1
        val pumpedElves = d.putCreatureOnBattlefield(p, "Llanowar Elves")
        val shrunkBears = d.putCreatureOnBattlefield(p, "Grizzly Bears")
        d.replaceState(
            d.state
                .updateEntity(pumpedElves) { it.with(CountersComponent(mapOf(CounterType.PLUS_ONE_PLUS_ONE to 3))) }
                .updateEntity(shrunkBears) { it.with(CountersComponent(mapOf(CounterType.MINUS_ONE_MINUS_ONE to 1))) }
        )
        assertEquals(4, d.projected.getPower(pumpedElves))
        assertEquals(1, d.projected.getPower(shrunkBears))

        val decision = castToSacrificeChoice(d)
        assertTrue("a 4/4 with base 1/1 qualifies", pumpedElves in decision.options)
        assertFalse("a 1/1 with base 2/2 does not", shrunkBears in decision.options)
        choose(d, listOf(pumpedElves))
        assertEquals(1, angels(d).size)
    }

    @Test fun setBaseEffectCountsAndAberrationCanSacrificeItselfWhenItQualifies() {
        val d = game()
        val p = d.player1
        d.putPermanentOnBattlefield(p, everythingOneOne.name)
        val giant = d.putCreatureOnBattlefield(p, "Hill Giant")

        val decision = castToSacrificeChoice(d)
        val aberration = d.findPermanent(p, "Angelic Aberration")!!
        assertEquals("base 1/1 under the field", 1, d.projected.getBasePower(aberration))
        assertEquals(setOf(giant, aberration), decision.options.toSet())
        choose(d, listOf(giant, aberration))

        assertNull("Aberration sacrificed itself", d.findPermanent(p, "Angelic Aberration"))
        assertTrue(aberration in d.state.getZone(p, Zone.GRAVEYARD) || d.zoneNames(p, Zone.GRAVEYARD).contains("Angelic Aberration"))
        assertEquals("two creatures sacrificed, two Angels", 2, angels(d).size)
    }

    @Test fun sacrificedTokensCount() {
        val d = game()
        val p = d.player1
        val token = d.putToken(p, "Llanowar Elves")
        castToSacrificeChoice(d)
        choose(d, listOf(token))
        assertFalse(token in d.state.getBattlefield())
        assertEquals(1, angels(d).size)
    }
}
