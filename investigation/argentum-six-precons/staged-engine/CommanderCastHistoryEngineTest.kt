package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.Outcome
import com.wingedsheep.engine.state.components.identity.CommanderComponent
import com.wingedsheep.engine.state.components.identity.CommanderRegistryComponent
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.scripting.EntersWithDynamicCounters
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/** The Swarmlord's commander-cast history is game-long, owner-scoped and supports partners. */
class CommanderCastHistoryEngineTest : FunSpec({
    val sample = card("Scenario Swarmlord Counters") {
        manaCost = "{6}"
        typeLine = "Creature — Tyranid"
        power = 5
        toughness = 5
        replacementEffect(
            EntersWithDynamicCounters(count = DynamicAmounts.commanderCasts() * 2)
        )
    }
    fun setup(): GameTestDriver = GameTestDriver().apply {
        registerCards(TestCards.all + listOf(sample))
        initMirrorMatch(Deck.of("Mountain" to 40))
        passPriorityUntil(Step.PRECOMBAT_MAIN)
    }
    fun castAndPower(d: GameTestDriver): Int {
        val p = d.activePlayer!!
        d.giveColorlessMana(p, 6)
        val card = d.putCardInHand(p, "Scenario Swarmlord Counters")
        d.castSpell(p, card).outcome shouldBe Outcome.Done
        d.bothPass()
        d.bothPass()
        val permanent = d.findPermanent(p, "Scenario Swarmlord Counters")!!
        return d.state.projectedState.getPower(permanent) ?: error("Missing projected creature power")
    }

    test("a single commander cast three times grants six +1/+1 counters") {
        val d = setup()
        val p = d.activePlayer!!
        val commander = d.putCreatureOnBattlefield(p, "Grizzly Bears")
        d.replaceState(
            d.state.updateEntity(commander) { it.with(CommanderComponent(p, castsFromCommandZone = 3)) }
                .updateEntity(p) { it.with(CommanderRegistryComponent(listOf(commander))) }
        )
        castAndPower(d) shouldBe 11
    }

    test("partner commanders' cast counts add together") {
        val d = setup()
        val p = d.activePlayer!!
        val a = d.putCreatureOnBattlefield(p, "Grizzly Bears")
        val b = d.putCreatureOnBattlefield(p, "Grizzly Bears")
        d.replaceState(
            d.state.updateEntity(a) { it.with(CommanderComponent(p, castsFromCommandZone = 1)) }
                .updateEntity(b) { it.with(CommanderComponent(p, castsFromCommandZone = 2)) }
                .updateEntity(p) { it.with(CommanderRegistryComponent(listOf(a, b))) }
        )
        castAndPower(d) shouldBe 11
    }

    test("missing commander registry counts zero, not an opponent's history") {
        val d = setup()
        val p = d.activePlayer!!
        val opponent = d.getOpponent(p)
        val commander = d.putCreatureOnBattlefield(opponent, "Grizzly Bears")
        d.replaceState(
            d.state.updateEntity(commander) { it.with(CommanderComponent(opponent, castsFromCommandZone = 4)) }
                .updateEntity(opponent) { it.with(CommanderRegistryComponent(listOf(commander))) }
        )
        castAndPower(d) shouldBe 5
    }
})
