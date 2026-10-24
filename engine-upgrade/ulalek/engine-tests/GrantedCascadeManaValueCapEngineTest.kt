package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.Outcome
import com.wingedsheep.engine.state.components.stack.TriggeredAbilityOnStackComponent
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.GrantKeywordToOwnSpells
import com.wingedsheep.sdk.scripting.effects.CascadeEffect
import com.wingedsheep.sdk.scripting.references.Player
import com.wingedsheep.sdk.scripting.targets.EffectTarget
import com.wingedsheep.sdk.scripting.values.DynamicAmount
import com.wingedsheep.sdk.scripting.values.TurnTracker
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/**
 * Granted cascade with a dynamic mana-value cap and a your-turn limit — Abaddon the Despoiler's
 * "During your turn, spells you cast from your hand with mana value X or less have cascade, where X is
 * the total amount of life your opponents have lost this turn."
 * The your-turn limit is exercised by the Arcana scenario test for the real card.
 */
class GrantedCascadeManaValueCapEngineTest : FunSpec({
    val sponsor = card("Scenario Abaddon") {
        manaCost = "{5}"
        typeLine = "Artifact Creature — Astartes"
        power = 5
        toughness = 5
        staticAbility {
            ability = GrantKeywordToOwnSpells(
                Keyword.CASCADE, GameObjectFilter.Any, fromZone = Zone.HAND,
                maxManaValue = DynamicAmount.TurnTracking(Player.EachOpponent, TurnTracker.LIFE_LOST_AMOUNT),
                duringYourTurnOnly = true,
            )
        }
    }
    val drain = card("Scenario Drain") {
        manaCost = "{1}"
        typeLine = "Instant"
        spell { effect = Effects.LoseLife(3, EffectTarget.PlayerRef(Player.EachOpponent)) }
    }
    val three = card("Scenario Three") {
        manaCost = "{3}"
        typeLine = "Instant"
        spell { effect = Effects.DrawCards(1) }
    }
    val four = card("Scenario Four") {
        manaCost = "{4}"
        typeLine = "Instant"
        spell { effect = Effects.DrawCards(1) }
    }
    fun setup(): GameTestDriver = GameTestDriver().apply {
        registerCards(TestCards.all + listOf(sponsor, drain, three, four))
        initMirrorMatch(Deck.of("Mountain" to 40))
        passPriorityUntil(Step.PRECOMBAT_MAIN)
    }
    fun cascades(d: GameTestDriver): Int = d.state.stack.count { id ->
        d.state.getEntity(id)?.get<TriggeredAbilityOnStackComponent>()?.effect is CascadeEffect
    }
    fun GameTestDriver.drainOpponents(p: com.wingedsheep.sdk.model.EntityId) {
        giveColorlessMana(p, 1)
        castSpell(p, putCardInHand(p, "Scenario Drain")).outcome shouldBe Outcome.Done
        bothPass()
    }

    test("no life lost yet: X is 0, so a three-drop has no cascade") {
        val d = setup()
        val p = d.activePlayer!!
        d.putCreatureOnBattlefield(p, "Scenario Abaddon")
        d.giveColorlessMana(p, 3)
        d.castSpell(p, d.putCardInHand(p, "Scenario Three")).outcome shouldBe Outcome.Done
        cascades(d) shouldBe 0
    }

    test("after opponents lose 3 life, a spell with mana value 3 has cascade") {
        val d = setup()
        val p = d.activePlayer!!
        d.putCreatureOnBattlefield(p, "Scenario Abaddon")
        d.drainOpponents(p)
        d.giveColorlessMana(p, 3)
        d.castSpell(p, d.putCardInHand(p, "Scenario Three")).outcome shouldBe Outcome.Done
        cascades(d) shouldBe 1
    }

    test("a spell with mana value above X has no cascade") {
        val d = setup()
        val p = d.activePlayer!!
        d.putCreatureOnBattlefield(p, "Scenario Abaddon")
        d.drainOpponents(p)
        d.giveColorlessMana(p, 4)
        d.castSpell(p, d.putCardInHand(p, "Scenario Four")).outcome shouldBe Outcome.Done
        cascades(d) shouldBe 0
    }

    test("the grant's controller must be the caster") {
        val d = setup()
        val p = d.activePlayer!!
        d.putCreatureOnBattlefield(d.getOpponent(p), "Scenario Abaddon")
        d.drainOpponents(p)
        d.giveColorlessMana(p, 3)
        d.castSpell(p, d.putCardInHand(p, "Scenario Three")).outcome shouldBe Outcome.Done
        cascades(d) shouldBe 0
    }
})
