package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.scripting.AttackTax
import com.wingedsheep.sdk.scripting.OpponentsMustAttackWithCreature
import com.wingedsheep.sdk.scripting.values.DynamicAmount
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

class OpponentsMustAttackWithCreatureTest : FunSpec({
    val seeker = card("Scenario Seeker of Slaanesh") {
        manaCost = "{3}{R}"
        typeLine = "Creature — Demon"
        power = 3
        toughness = 3
        staticAbility { ability = OpponentsMustAttackWithCreature }
    }
    val prison = card("Scenario Attack Prison") {
        manaCost = "{2}{W}"
        typeLine = "Enchantment"
        staticAbility { ability = AttackTax(DynamicAmount.Fixed(2)) }
    }
    fun setup(): GameTestDriver = GameTestDriver().apply {
        registerCards(TestCards.all + listOf(seeker, prison))
        initMirrorMatch(Deck.of("Forest" to 40))
        passPriorityUntil(Step.PRECOMBAT_MAIN)
    }

    test("at least one attack is required when a creature is able") {
        val d = setup()
        val p = d.player1
        val creature = d.putCreatureOnBattlefield(p, "Centaur Courser")
        d.removeSummoningSickness(creature)
        d.putCreatureOnBattlefield(d.player2, "Scenario Seeker of Slaanesh")
        d.passPriorityUntil(Step.DECLARE_ATTACKERS)
        d.declareAttackers(p, emptyMap()).error shouldNotBe null
        d.declareAttackers(p, mapOf(creature to d.player2)).error shouldBe null
    }

    test("another opponent may defend instead of Seeker's controller in a three-player game") {
        val d = GameTestDriver()
        d.registerCards(TestCards.all + listOf(seeker, prison))
        val players = d.initMultiplayer(List(3) { Deck.of("Forest" to 40) })
        val p = players[0]
        d.passPriorityUntil(Step.PRECOMBAT_MAIN)
        val creature = d.putCreatureOnBattlefield(p, "Centaur Courser")
        d.removeSummoningSickness(creature)
        d.putCreatureOnBattlefield(players[1], "Scenario Seeker of Slaanesh")
        d.passPriorityUntil(Step.DECLARE_ATTACKERS)
        d.declareAttackers(p, emptyMap()).error shouldNotBe null
        d.declareAttackers(p, mapOf(creature to players[2])).error shouldBe null
    }

    test("a creature under summoning sickness does not force an impossible attack") {
        val d = setup()
        val p = d.player1
        d.putCreatureOnBattlefield(p, "Centaur Courser")
        d.putCreatureOnBattlefield(d.player2, "Scenario Seeker of Slaanesh")
        d.passPriorityUntil(Step.END)
        d.state.step shouldBe Step.END
    }

    test("mandatory attack does not force payment of an attack tax") {
        val d = setup()
        val p = d.player1
        val creature = d.putCreatureOnBattlefield(p, "Centaur Courser")
        d.removeSummoningSickness(creature)
        d.putCreatureOnBattlefield(d.player2, "Scenario Seeker of Slaanesh")
        d.putPermanentOnBattlefield(d.player2, "Scenario Attack Prison")
        d.passPriorityUntil(Step.DECLARE_ATTACKERS)
        d.declareAttackers(p, emptyMap()).error shouldBe null
    }

    test("the seeker controller can freely decline combat on their own turn") {
        val d = setup()
        val p = d.player1
        val creature = d.putCreatureOnBattlefield(p, "Centaur Courser")
        d.removeSummoningSickness(creature)
        d.putCreatureOnBattlefield(p, "Scenario Seeker of Slaanesh")
        d.passPriorityUntil(Step.DECLARE_ATTACKERS)
        d.declareAttackers(p, emptyMap()).error shouldBe null
    }
})
