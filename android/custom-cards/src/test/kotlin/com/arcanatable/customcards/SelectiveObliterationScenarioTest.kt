package com.arcanatable.customcards

import com.arcanatable.customcards.cards.SelectiveObliteration
import com.arcanatable.customcards.support.*
import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.state.components.identity.ControllerComponent
import com.wingedsheep.sdk.core.*
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.tooling.CardLoader
import org.junit.Assert.*
import org.junit.Test

class SelectiveObliterationScenarioTest {
    private fun setup(seats: Int = 2, startingPlayer: Int = 0): Pair<GameTestDriver, List<EntityId>> {
        val d = CardPool.driver("Grizzly Bears", "Hill Giant", "Sol Ring", "Eldrazi Displacer")
        val fixtures = listOf(
            card("Obliteration Multicolor") {
                manaCost = "{R}{G}"; typeLine = "Creature — Bear"; power = 2; toughness = 2
            },
            card("Obliteration Protected Artifact") {
                manaCost = "{R}"; typeLine = "Artifact"
                keywords(Keyword.INDESTRUCTIBLE, Keyword.HEXPROOF)
            },
            card("Obliteration Green Enchantment") {
                manaCost = "{G}"; typeLine = "Enchantment"
            },
        )
        d.registerCards((fixtures + SelectiveObliteration).map {
            CardLoader.fromJsonPreservingIds(exportLine(it))
        })
        val players = d.initMultiplayer(List(seats) { Deck.of("Plains" to 40) }, startingPlayer = startingPlayer)
        d.passPriorityUntil(Step.PRECOMBAT_MAIN, activePlayer = players[startingPlayer])
        return d to players
    }

    private fun cast(d: GameTestDriver, p: EntityId) {
        val c = d.putCardInHand(p, "Selective Obliteration")
        d.giveColorlessMana(p, 5)
        assertNull(d.submit(CastSpell(p, c, paymentStrategy = PaymentStrategy.FromPool)).error)
    }

    /** All choices precede the single exile instruction; never auto-answer a wrong decision. */
    private fun choose(d: GameTestDriver, order: List<Pair<EntityId, Color>>, unchanged: List<EntityId> = emptyList()) {
        for ((p, color) in order) {
            repeat(20) { if (d.pendingDecision == null && d.stackSize > 0) d.bothPass() }
            val decision = d.pendingDecision
            assertTrue("Expected color choice, got $decision", decision is ChooseColorDecision)
            decision as ChooseColorDecision
            assertEquals("APNAP choice belongs to this player", p, decision.playerId)
            unchanged.forEach { assertTrue("Nothing exiled before all players choose", it in d.state.getBattlefield()) }
            assertNull(d.submitDecision(p, ColorChosenResponse(decision.id, color)).error)
        }
        d.resolveAll()
    }

    @Test fun eachPlayerKeepsOnlyTheirOwnChosenMonocolor() {
        val (d, ps) = setup()
        val red1 = d.putCreatureOnBattlefield(ps[0], "Hill Giant")
        val green1 = d.putCreatureOnBattlefield(ps[0], "Grizzly Bears")
        val red2 = d.putCreatureOnBattlefield(ps[1], "Hill Giant")
        val green2 = d.putCreatureOnBattlefield(ps[1], "Grizzly Bears")
        cast(d, ps[0])
        choose(d, listOf(ps[0] to Color.RED, ps[1] to Color.GREEN), listOf(red1, green1, red2, green2))
        assertTrue(red1 in d.state.getBattlefield()); assertTrue(green2 in d.state.getBattlefield())
        assertTrue(green1 in d.state.getZone(ps[0], Zone.EXILE))
        assertTrue(red2 in d.state.getZone(ps[1], Zone.EXILE))
        assertFalse(green1 in d.state.getZone(ps[0], Zone.GRAVEYARD))
    }

    @Test fun multicolorIsExiledEvenWhenItIncludesTheChosenColor() {
        val (d, ps) = setup()
        val a = d.putCreatureOnBattlefield(ps[0], "Obliteration Multicolor")
        val b = d.putCreatureOnBattlefield(ps[1], "Obliteration Multicolor")
        cast(d, ps[0])
        choose(d, listOf(ps[0] to Color.RED, ps[1] to Color.GREEN))
        assertTrue(a in d.state.getZone(ps[0], Zone.EXILE))
        assertTrue(b in d.state.getZone(ps[1], Zone.EXILE))
    }

    @Test fun colorlessLandsArtifactsAndDevoidCreaturesSurviveEveryChoice() {
        val (d, ps) = setup()
        val keep = ps.flatMap { p -> listOf(
            d.putPermanentOnBattlefield(p, "Sol Ring"),
            d.putLandOnBattlefield(p, "Plains"),
            d.putCreatureOnBattlefield(p, "Eldrazi Displacer"),
        ) }
        cast(d, ps[0])
        choose(d, listOf(ps[0] to Color.BLUE, ps[1] to Color.BLACK))
        keep.forEach { assertTrue(it in d.state.getBattlefield()) }
    }

    @Test fun exileIsNotTargetingOrDestructionAndAppliesToNoncreaturePermanents() {
        val (d, ps) = setup()
        val artifact = d.putPermanentOnBattlefield(ps[1], "Obliteration Protected Artifact")
        val enchantment = d.putPermanentOnBattlefield(ps[1], "Obliteration Green Enchantment")
        cast(d, ps[0])
        choose(d, listOf(ps[0] to Color.WHITE, ps[1] to Color.BLUE))
        assertTrue(artifact in d.state.getZone(ps[1], Zone.EXILE))
        assertTrue(enchantment in d.state.getZone(ps[1], Zone.EXILE))
    }

    @Test fun threePlayersChooseInApnapOrderBeforeAnyPermanentLeaves() {
        val (d, ps) = setup(seats = 3, startingPlayer = 1)
        val greens = ps.map { d.putCreatureOnBattlefield(it, "Grizzly Bears") }
        cast(d, ps[1])
        choose(d, listOf(ps[1] to Color.RED, ps[2] to Color.GREEN, ps[0] to Color.BLUE), greens)
        assertTrue(greens[0] in d.state.getZone(ps[0], Zone.EXILE))
        assertTrue(greens[1] in d.state.getZone(ps[1], Zone.EXILE))
        assertTrue(greens[2] in d.state.getBattlefield())
    }

    @Test fun usesCurrentControllerChoiceButExilesIntoTheOwnersZone() {
        val (d, ps) = setup()
        val green = d.putCreatureOnBattlefield(ps[0], "Grizzly Bears")
        val red = d.putCreatureOnBattlefield(ps[0], "Hill Giant")
        listOf(green, red).forEach { id ->
            d.replaceState(d.state.updateEntity(id) { it.with(ControllerComponent(ps[1])) })
        }
        cast(d, ps[0])
        choose(d, listOf(ps[0] to Color.RED, ps[1] to Color.GREEN))
        assertTrue("New controller chose green", green in d.state.getBattlefield())
        assertFalse("Owner's red choice cannot save a stolen red creature", red in d.state.getBattlefield())
        assertTrue("Exile follows ownership", red in d.state.getZone(ps[0], Zone.EXILE))
        assertFalse(red in d.state.getZone(ps[1], Zone.EXILE))
    }

    @Test fun allColoredManaCannotPayAndMixedManaPaysExactlyTwoRequiredColorless() {
        val (d, ps) = setup()
        val p = ps[0]
        val c = d.putCardInHand(p, "Selective Obliteration")
        d.giveMana(p, Color.BLUE, 5)
        assertNotNull(d.submit(CastSpell(p, c, paymentStrategy = PaymentStrategy.FromPool)).error)
        assertEquals(5, d.manaPool(p).blue)
        assertTrue(c in d.state.getZone(p, Zone.HAND))
        d.giveColorlessMana(p, 2)
        assertNull(d.submit(CastSpell(p, c, paymentStrategy = PaymentStrategy.FromPool)).error)
        assertEquals(0, d.manaPool(p).colorless)
        assertEquals(2, d.manaPool(p).blue)
        choose(d, listOf(ps[0] to Color.RED, ps[1] to Color.GREEN))
    }

    @Test fun jsonExportIsByteStableAndPreservesTheEntirePipeline() {
        val first = exportLine(SelectiveObliteration)
        assertEquals(first, exportLine(SelectiveObliteration))
        assertEquals(first, exportLine(CardLoader.fromJsonPreservingIds(first)))
    }
}
