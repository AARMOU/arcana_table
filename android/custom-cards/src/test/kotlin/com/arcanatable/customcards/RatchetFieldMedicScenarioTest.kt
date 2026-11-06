package com.arcanatable.customcards

import com.arcanatable.customcards.cards.RatchetFieldMedic
import com.arcanatable.customcards.support.*
import com.wingedsheep.engine.core.AlternativeCostType
import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.core.ChooseTargetsDecision
import com.wingedsheep.engine.core.PaymentStrategy
import com.wingedsheep.engine.core.YesNoDecision
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GameObjectFilter
import org.junit.Assert.*
import org.junit.Test

private val GainThreeLifeTest = card("Gain Three Life Test") {
    manaCost = "{0}"
    typeLine = "Sorcery"
    oracleText = "You gain 3 life."
    spell { effect = Effects.GainLife(3) }
    metadata { rarity = Rarity.COMMON; collectorNumber = "1" }
}

/** Two separate life-gain events from one resolution: Ratchet's trigger goes on the stack twice. */
private val GainOneTwiceTest = card("Gain One Twice Test") {
    manaCost = "{0}"
    typeLine = "Sorcery"
    oracleText = "You gain 1 life. Then you gain 1 life."
    spell { effect = Effects.GainLife(1) then Effects.GainLife(1) }
    metadata { rarity = Rarity.COMMON; collectorNumber = "2" }
}

private val SacrificeArtifactTest = card("Sacrifice Artifact Test") {
    manaCost = "{0}"
    typeLine = "Sorcery"
    oracleText = "Sacrifice an artifact."
    spell { effect = Effects.SacrificeOwn(GameObjectFilter.Artifact) }
    metadata { rarity = Rarity.COMMON; collectorNumber = "3" }
}

/**
 * Ratchet, Field Medic // Ratchet, Rescue Racer — full Oracle behaviour on engine patch 0041:
 * More Than Meets the Eye (cast converted for {1}{W}), lifelink, the "you may convert. When you do"
 * reflexive return, CR 701.28f for stacked triggers, Living metal, and the back face's once-per-turn
 * nontoken-artifact trigger.
 */
class RatchetFieldMedicScenarioTest {

    private val front = "Ratchet, Field Medic"
    private val back = "Ratchet, Rescue Racer"

    private fun scenario(vararg library: String): GameTestDriver =
        customScenario(RatchetFieldMedic, *library).apply {
            registerCard(GainThreeLifeTest)
            registerCard(GainOneTwiceTest)
            registerCard(SacrificeArtifactTest)
        }

    private fun GameTestDriver.convertOffer(player: EntityId) =
        legalActions(player).firstOrNull {
            (it.action as? CastSpell)?.alternativeCostType == AlternativeCostType.MORE_THAN_MEETS_THE_EYE
        }

    private fun GameTestDriver.castConverted(player: EntityId, card: EntityId) =
        submit(
            CastSpell(
                player, card,
                useAlternativeCost = true,
                alternativeCostType = AlternativeCostType.MORE_THAN_MEETS_THE_EYE,
                paymentStrategy = PaymentStrategy.FromPool,
            )
        )

    /** Puts Ratchet onto the battlefield back face up the way a player does: cast converted. */
    private fun GameTestDriver.racerOnBattlefield(player: EntityId): EntityId {
        val card = putCardInHand(player, front)
        giveColorlessMana(player, 1)
        giveMana(player, Color.WHITE, 1)
        assertNull(castConverted(player, card).error)
        resolveAll()
        return permanentsNamed(player, back).single()
    }

    private fun GameTestDriver.cast(player: EntityId, name: String, c: Choices = Choices()) {
        val id = putCardInHand(player, name)
        assertNull(castSpell(player, id).error)
        resolveAll(c)
    }

    @Test
    fun normalCastEntersFrontFaceUpAsALifelinkCreature() {
        val d = scenario()
        val p = d.player1
        val card = d.putCardInHand(p, front)
        d.giveColorlessMana(p, 2)
        d.giveMana(p, Color.WHITE, 1)
        assertNull(d.castSpell(p, card).error)
        d.resolveAll()
        val ratchet = d.permanentsNamed(p, front).single()
        assertTrue(d.state.projectedState.isCreature(ratchet))
        assertEquals(2, d.state.projectedState.getPower(ratchet))
        assertEquals(4, d.state.projectedState.getToughness(ratchet))
    }

    @Test
    fun moreThanMeetsTheEyeIsOfferedFromHandAndResolvesConverted() {
        val d = scenario()
        val p = d.player1
        val card = d.putCardInHand(p, front)
        d.giveColorlessMana(p, 1)
        d.giveMana(p, Color.WHITE, 1)

        val offer = d.convertOffer(p)
        assertNotNull("cast converted is a legal action from hand", offer)
        assertTrue(offer!!.affordable)
        assertEquals("{1}{W}", offer.manaCostString)
        assertEquals("HAND", offer.sourceZone)

        assertNull(d.castConverted(p, card).error)
        assertEquals("the spell on the stack is the back face (CR 712.8c)", listOf(back), d.getStackSpellNames())
        d.resolveAll()
        val racer = d.permanentsNamed(p, back).single()
        assertTrue("it enters converted", d.permanentsNamed(p, front).isEmpty())
        assertTrue("Living metal: a creature on its controller's turn", d.state.projectedState.isCreature(racer))
        assertEquals(1, d.state.projectedState.getPower(racer))
        assertEquals(4, d.state.projectedState.getToughness(racer))
    }

    @Test
    fun convertedCastNeedsItsFullAlternativeCost() {
        val d = scenario()
        val p = d.player1
        val card = d.putCardInHand(p, front)
        d.giveMana(p, Color.WHITE, 1)
        val offer = d.convertOffer(p)
        assertNotNull(offer)
        assertFalse("{W} alone cannot pay {1}{W}", offer!!.affordable)
        assertNotNull(d.castConverted(p, card).error)
        assertTrue(d.zoneNames(p, Zone.HAND).contains(front))
    }

    @Test
    fun livingMetalMakesTheVehicleACreatureOnlyDuringItsControllersTurn() {
        val d = scenario()
        val racer = d.racerOnBattlefield(d.player1)
        assertTrue(d.state.projectedState.isCreature(racer))
        d.passPriorityUntil(Step.PRECOMBAT_MAIN, d.player2)
        assertFalse("not a creature on an opponent's turn", d.state.projectedState.isCreature(racer))
        assertTrue("still an artifact", d.state.projectedState.hasType(racer, "ARTIFACT"))
        d.passPriorityUntil(Step.PRECOMBAT_MAIN, d.player1)
        assertTrue("a creature again on its controller's next turn", d.state.projectedState.isCreature(racer))
    }

    @Test
    fun gainingLifeConvertsAndReturnsAnArtifactWithManaValueUpToLifeGainedThisTurn() {
        val d = scenario("Sol Ring", "Thran Dynamo")
        val p = d.player1
        val ratchet = d.putCreatureOnBattlefield(p, front)
        val solRing = d.putCardInGraveyard(p, "Sol Ring")       // MV 1
        val dynamo = d.putCardInGraveyard(p, "Thran Dynamo")    // MV 4

        val gain = d.putCardInHand(p, "Gain Three Life Test")
        assertNull(d.castSpell(p, gain).error)
        var sawTargets = false
        repeat(40) {
            when (val decision = d.state.pendingDecision) {
                is YesNoDecision -> d.submitYesNo(decision.playerId, true)
                is ChooseTargetsDecision -> {
                    val legal = decision.legalTargets.values.flatten()
                    assertTrue("MV 1 ≤ 3 life gained", solRing in legal)
                    assertFalse("MV 4 > 3 life gained", dynamo in legal)
                    sawTargets = true
                    d.decide(Choices(targets = listOf(solRing)))
                }
                null -> if (d.state.stack.isNotEmpty()) d.bothPass() else return@repeat
                else -> d.decide()
            }
        }
        assertTrue("the reflexive trigger asked for a target", sawTargets)
        assertTrue("Ratchet converted", d.permanentsNamed(p, back).isNotEmpty())
        assertEquals("same object, converted in place", ratchet, d.permanentsNamed(p, back).single())
        val returned = d.permanentsNamed(p, "Sol Ring").single()
        assertTrue("returned tapped", d.isTapped(returned))
        assertTrue(d.zoneNames(p, Zone.GRAVEYARD).contains("Thran Dynamo"))
    }

    @Test
    fun decliningTheConversionReturnsNothing() {
        val d = scenario("Sol Ring")
        val p = d.player1
        d.putCreatureOnBattlefield(p, front)
        d.putCardInGraveyard(p, "Sol Ring")
        d.cast(p, "Gain Three Life Test", Choices(yes = false))
        assertTrue("still front face up", d.permanentsNamed(p, front).isNotEmpty())
        assertTrue(d.zoneNames(p, Zone.GRAVEYARD).contains("Sol Ring"))
        assertTrue(d.permanentsNamed(p, "Sol Ring").isEmpty())
    }

    @Test
    fun secondStackedTriggerNeitherConvertsBackNorReturnsAgain() {
        // Ruling 2022-10-14 / CR 701.28f: two life gains trigger twice, but Ratchet converts once;
        // the second trigger does nothing, so its "when you do" never happens.
        val d = scenario("Sol Ring", "Mind Stone")
        val p = d.player1
        d.putCreatureOnBattlefield(p, front)
        d.putCardInGraveyard(p, "Sol Ring")
        d.putCardInGraveyard(p, "Mind Stone")
        d.cast(p, "Gain One Twice Test", Choices(yes = true))

        assertTrue("Ratchet stays converted", d.permanentsNamed(p, back).isNotEmpty())
        assertTrue(d.permanentsNamed(p, front).isEmpty())
        val returned = listOf("Sol Ring", "Mind Stone").filter { d.permanentsNamed(p, it).isNotEmpty() }
        assertEquals("exactly one artifact comes back", 1, returned.size)
    }

    @Test
    fun rescueRacerConvertsWhenANontokenArtifactYouControlIsPutIntoAGraveyard() {
        val d = scenario("Sol Ring")
        val p = d.player1
        val racer = d.racerOnBattlefield(p)
        d.putPermanentOnBattlefield(p, "Sol Ring")
        d.cast(p, "Sacrifice Artifact Test", Choices(pick = listOf("Sol Ring")))
        assertTrue(d.zoneNames(p, Zone.GRAVEYARD).contains("Sol Ring"))
        assertEquals("converted back to the front face", racer, d.permanentsNamed(p, front).single())
    }

    @Test
    fun rescueRacerTriggerIsOncePerTurn() {
        val d = scenario("Sol Ring", "Mind Stone")
        val p = d.player1
        d.racerOnBattlefield(p)
        d.putPermanentOnBattlefield(p, "Mind Stone")
        d.putPermanentOnBattlefield(p, "Sol Ring")
        d.cast(p, "Sacrifice Artifact Test", Choices(pick = listOf("Sol Ring")))
        assertTrue("first artifact this turn converts it", d.permanentsNamed(p, front).isNotEmpty())

        // Convert it back with a life gain (returning Sol Ring, MV 1 ≤ 3), then lose another artifact.
        d.cast(p, "Gain Three Life Test", Choices(yes = true, pick = listOf("Sol Ring")))
        assertTrue(d.permanentsNamed(p, back).isNotEmpty())
        d.cast(p, "Sacrifice Artifact Test", Choices(pick = listOf("Mind Stone")))
        assertTrue(d.zoneNames(p, Zone.GRAVEYARD).contains("Mind Stone"))
        assertTrue("second batch this turn does not trigger", d.permanentsNamed(p, back).isNotEmpty())
    }

    @Test
    fun tokenArtifactsDoNotConvertRescueRacer() {
        val d = scenario()
        val p = d.player1
        d.racerOnBattlefield(p)
        d.putToken(p, "Treasure")
        d.cast(p, "Sacrifice Artifact Test", Choices(pick = listOf("Treasure")))
        assertTrue(d.getPermanents(p).none { d.getCardName(it) == "Treasure" })
        assertTrue("a token artifact does not count", d.permanentsNamed(p, back).isNotEmpty())
    }

    @Test
    fun anOpponentsArtifactDoesNotConvertRescueRacer() {
        val d = scenario("Sol Ring")
        val racer = d.racerOnBattlefield(d.player1)
        d.putPermanentOnBattlefield(d.player2, "Sol Ring")
        val ring = d.permanentsNamed(d.player2, "Sol Ring").single()
        d.moveToGraveyard(ring)
        assertEquals(racer, d.permanentsNamed(d.player1, back).single())
    }

    @Test
    fun lifelinkCombatDamageTriggersTheConversion() {
        val d = scenario("Sol Ring")
        val p = d.player1
        val ratchet = d.putCreatureOnBattlefield(p, front).also { d.removeSummoningSickness(it) }
        d.putCardInGraveyard(p, "Sol Ring")
        d.passPriorityUntil(Step.DECLARE_ATTACKERS)
        assertNull(d.declareAttackers(p, listOf(ratchet), d.player2).error)
        d.passPriorityUntil(Step.DECLARE_BLOCKERS)
        assertNull(d.declareNoBlockers(d.player2).error)
        var guard = 0
        while (d.state.step != Step.END_COMBAT && guard++ < 60) {
            when {
                d.state.pendingDecision != null -> d.decide(Choices(yes = true))
                d.state.priorityPlayerId != null -> d.passPriority(d.state.priorityPlayerId!!)
                else -> break
            }
        }
        assertEquals("lifelink gained 2", 22, d.getLifeTotal(p))
        assertTrue("converted", d.permanentsNamed(p, back).isNotEmpty())
        assertTrue("Sol Ring (MV 1 ≤ 2) returned", d.permanentsNamed(p, "Sol Ring").isNotEmpty())
    }
}
