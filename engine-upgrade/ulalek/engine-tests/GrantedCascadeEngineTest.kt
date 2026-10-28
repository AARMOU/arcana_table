package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.Outcome
import com.wingedsheep.engine.state.components.stack.TriggeredAbilityOnStackComponent
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Triggers
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.GrantKeywordToOwnSpells
import com.wingedsheep.sdk.scripting.effects.CascadeEffect
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/** Rules behavior and instance accounting for cascade granted to spells. */
class GrantedCascadeEngineTest : FunSpec({
    val sponsor = card("Scenario Zhulodok") {
        manaCost = "{6}"
        typeLine = "Artifact Creature — Eldrazi"
        power = 6
        toughness = 6
        staticAbility {
            ability = GrantKeywordToOwnSpells(
                Keyword.CASCADE, GameObjectFilter.Nonland.manaValueAtLeast(7))
        }
        staticAbility {
            ability = GrantKeywordToOwnSpells(
                Keyword.CASCADE, GameObjectFilter.Nonland.manaValueAtLeast(7))
        }
    }
    val seven = card("Scenario Seven") {
        manaCost = "{7}"
        typeLine = "Sorcery"
        spell { effect = Effects.DrawCards(1) }
    }
    val six = card("Scenario Six") {
        manaCost = "{6}"
        typeLine = "Sorcery"
        spell { effect = Effects.DrawCards(1) }
    }
    val xSpell = card("Scenario X") {
        manaCost = "{X}{1}"
        typeLine = "Sorcery"
        spell { effect = Effects.DrawCards(1) }
    }
    val authored = card("Scenario Printed Cascade") {
        manaCost = "{3}"
        typeLine = "Sorcery"
        keywords(Keyword.CASCADE)
        spell { effect = Effects.DrawCards(1) }
        triggeredAbility {
            trigger = Triggers.self.isCast()
            effect = Effects.Cascade
        }
    }
    fun setup(): GameTestDriver = GameTestDriver().apply {
        registerCards(TestCards.all + listOf(sponsor, seven, six, xSpell, authored))
        initMirrorMatch(Deck.of("Mountain" to 40))
        passPriorityUntil(Step.PRECOMBAT_MAIN)
    }
    fun triggers(d: GameTestDriver): Int = d.state.stack.count { id ->
        d.state.getEntity(id)?.get<TriggeredAbilityOnStackComponent>()?.effect is CascadeEffect
    }

    test("a spell gains two independent cascade triggers from two matching abilities") {
        val d = setup()
        val p = d.activePlayer!!
        d.putCreatureOnBattlefield(p, "Scenario Zhulodok")
        d.giveColorlessMana(p, 7)
        val spell = d.putCardInHand(p, "Scenario Seven")
        d.castSpell(p, spell).outcome shouldBe Outcome.Done
        triggers(d) shouldBe 2
    }

    test("a spell below the qualifying mana value has no granted cascade") {
        val d = setup()
        val p = d.activePlayer!!
        d.putCreatureOnBattlefield(p, "Scenario Zhulodok")
        d.giveColorlessMana(p, 6)
        val spell = d.putCardInHand(p, "Scenario Six")
        d.castSpell(p, spell).outcome shouldBe Outcome.Done
        triggers(d) shouldBe 0
    }

    test("X is included in the cast spell's mana value") {
        val d = setup()
        val p = d.activePlayer!!
        d.putCreatureOnBattlefield(p, "Scenario Zhulodok")
        d.giveColorlessMana(p, 7)
        val spell = d.putCardInHand(p, "Scenario X")
        d.castXSpell(p, spell, 6).outcome shouldBe Outcome.Done
        triggers(d) shouldBe 2
    }

    test("printed cascade does not get duplicated by the grant handler") {
        val d = setup()
        val p = d.activePlayer!!
        d.giveColorlessMana(p, 3)
        val spell = d.putCardInHand(p, "Scenario Printed Cascade")
        d.castSpell(p, spell).outcome shouldBe Outcome.Done
        triggers(d) shouldBe 1
    }

    test("the opponent's cascade-granting permanent is not your grant") {
        val d = setup()
        val p = d.activePlayer!!
        d.putCreatureOnBattlefield(d.getOpponent(p), "Scenario Zhulodok")
        d.giveColorlessMana(p, 7)
        val spell = d.putCardInHand(p, "Scenario Seven")
        d.castSpell(p, spell).outcome shouldBe Outcome.Done
        triggers(d) shouldBe 0
    }
})
