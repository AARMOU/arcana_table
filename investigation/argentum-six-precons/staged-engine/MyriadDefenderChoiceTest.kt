package com.wingedsheep.engine.multiplayer

import com.wingedsheep.engine.core.ChooseTargetsDecision
import com.wingedsheep.engine.core.YesNoDecision
import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.engine.state.components.battlefield.TappedComponent
import com.wingedsheep.engine.state.components.combat.AttackingComponent
import com.wingedsheep.engine.state.components.identity.TokenComponent
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.sdk.core.CounterType
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Triggers
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.model.EntityId
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf

/** True CR 702.116a choices: the visited opponent OR a planeswalker that opponent controls. */
class MyriadDefenderChoiceTest : FunSpec({
    val myriad = card("Scenario Myriad Drone") {
        manaCost = "{2}{G}"
        typeLine = "Creature — Eldrazi Drone"
        power = 2
        toughness = 3
        keywords(Keyword.MYRIAD)
        triggeredAbility {
            trigger = Triggers.self.leaves()
            effect = Effects.CreateEldraziSpawn()
        }
    }
    val walker = card("Scenario Myriad Planeswalker") {
        manaCost = "{3}"
        typeLine = "Legendary Planeswalker — Test"
        startingLoyalty = 5
    }

    fun driver(count: Int): Pair<GameTestDriver, List<EntityId>> {
        val d = GameTestDriver()
        d.registerCards(TestCards.all + listOf(myriad, walker))
        val players = d.initMultiplayer(List(count) { Deck.of("Forest" to 40) })
        d.passPriorityUntil(Step.PRECOMBAT_MAIN)
        return d to players
    }

    fun GameTestDriver.walkerFor(player: EntityId): EntityId =
        putPermanentOnBattlefield(player, "Scenario Myriad Planeswalker").also { id ->
            replaceState(state.updateEntity(id) {
                it.with(CountersComponent().withAdded(CounterType.LOYALTY, 5))
            })
        }

    fun GameTestDriver.beginAttack(attacker: EntityId, p1: EntityId, defender: EntityId) {
        removeSummoningSickness(attacker)
        passPriorityUntil(Step.DECLARE_ATTACKERS)
        declareAttackers(p1, listOf(attacker), defender).error shouldBe null
    }

    fun GameTestDriver.resolveUntilDecision() {
        var limit = 0
        while (pendingDecision == null && stackSize > 0 && limit++ < 80) {
            passPriority(priorityPlayer!!)
        }
    }

    fun GameTestDriver.acceptMyriad(p1: EntityId) {
        resolveUntilDecision()
        val yes = pendingDecision.shouldBeInstanceOf<YesNoDecision>()
        withClue("source controller decides whether to create the copy") { yes.playerId shouldBe p1 }
        submitYesNo(p1, true).error shouldBe null
        resolveUntilDecision()
    }

    fun GameTestDriver.tokensOf(p: EntityId): List<EntityId> =
        getCreatures(p).filter { state.getEntity(it)?.has<TokenComponent>() == true }

    fun GameTestDriver.attacks(id: EntityId): EntityId? =
        state.getEntity(id)?.get<AttackingComponent>()?.defenderId

    test("three players: offers only the visited opponent and their planeswalker, before token creation") {
        val (d, p) = driver(3)
        val (p1, p2, p3) = p
        val wrongWalker = d.walkerFor(p2)
        val correctWalker = d.walkerFor(p3)
        val attacker = d.putCreatureOnBattlefield(p1, "Scenario Myriad Drone")
        d.beginAttack(attacker, p1, p2)
        d.acceptMyriad(p1)

        val choice = d.pendingDecision.shouldBeInstanceOf<ChooseTargetsDecision>()
        choice.playerId shouldBe p1
        choice.legalTargets[0]!! shouldContainExactly listOf(p3, correctWalker)
        withClue("the token does not exist before the defender is chosen") {
            d.tokensOf(p1) shouldHaveSize 0
        }
        d.submitTargetSelection(p1, listOf(wrongWalker)).error shouldNotBe null
        d.submitTargetSelection(p1, listOf(p2)).error shouldNotBe null
        d.submitTargetSelection(p1, listOf(correctWalker)).error shouldBe null
        d.resolveUntilDecision()
        val copy = d.tokensOf(p1).single()
        d.attacks(copy) shouldBe correctWalker
        d.state.getEntity(copy)!!.get<AttackingComponent>()!!.defendingPlayerId shouldBe p3
        d.state.getEntity(copy)!!.has<TappedComponent>() shouldBe true
        d.attacks(attacker) shouldBe p2

        d.passPriorityUntil(Step.END_COMBAT)
        // Copy hits the planeswalker, rather than damaging that opponent.
        d.state.getEntity(correctWalker)!!.get<CountersComponent>()!!.getCount(CounterType.LOYALTY) shouldBe 3
        d.getLifeTotal(p3) shouldBe 20
    }

    test("four players: independently choose p3 planeswalker and p4 player; copies are exiled at end of combat") {
        val (d, p) = driver(4)
        val (p1, p2, p3, p4) = p
        val thirdWalker = d.walkerFor(p3)
        val fourthWalker = d.walkerFor(p4)
        val attacker = d.putCreatureOnBattlefield(p1, "Scenario Myriad Drone")
        d.beginAttack(attacker, p1, p2)

        d.acceptMyriad(p1)
        val thirdChoice = d.pendingDecision.shouldBeInstanceOf<ChooseTargetsDecision>()
        thirdChoice.legalTargets[0]!! shouldContainExactly listOf(p3, thirdWalker)
        d.submitTargetSelection(p1, listOf(thirdWalker)).error shouldBe null

        d.acceptMyriad(p1)
        val fourthChoice = d.pendingDecision.shouldBeInstanceOf<ChooseTargetsDecision>()
        fourthChoice.legalTargets[0]!! shouldContainExactly listOf(p4, fourthWalker)
        d.submitTargetSelection(p1, listOf(p4)).error shouldBe null
        d.resolveUntilDecision()

        val copies = d.tokensOf(p1)
        copies shouldHaveSize 2
        copies.map { d.attacks(it) } shouldContainExactly listOf(thirdWalker, p4)
        copies.forEach { d.state.getEntity(it)!!.has<TappedComponent>() shouldBe true }

        d.passPriorityUntil(Step.POSTCOMBAT_MAIN)
        // Diagnostic output is deliberately emitted before either assertion: discriminate
        // a missing leave trigger from an unresolved stack, wrong controller, or wrong token name.
        println("MYRIAD_TRACE phase=${d.state.step} stack=${d.stackSize} pending=${d.pendingDecision?.javaClass?.simpleName} " +
            "p1Creatures=${d.getCreatures(p1).map(d::getCardName)} " +
            "p2Creatures=${d.getCreatures(p2).map(d::getCardName)} " +
            "p3Creatures=${d.getCreatures(p3).map(d::getCardName)} " +
            "p4Creatures=${d.getCreatures(p4).map(d::getCardName)}")
        withClue("myriad exiles the copies but not the original") {
            d.getCreatures(p1).count { d.getCardName(it) == "Scenario Myriad Drone" } shouldBe 1
        }
        withClue("each exiled copy triggers the copied leaves ability exactly once") {
            d.getCreatures(p1).count { d.getCardName(it) == "Eldrazi Spawn" } shouldBe 2
        }
    }

    test("two players: no choice or token is created") {
        val (d, p) = driver(2)
        val (p1, p2) = p
        val attacker = d.putCreatureOnBattlefield(p1, "Scenario Myriad Drone")
        d.beginAttack(attacker, p1, p2)
        d.resolveUntilDecision()
        d.pendingDecision shouldBe null
        d.tokensOf(p1) shouldHaveSize 0
    }
})
